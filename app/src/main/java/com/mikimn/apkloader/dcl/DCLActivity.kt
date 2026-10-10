package com.mikimn.apkloader.dcl

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentProvider
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.util.Predicate
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.apk.ManifestAwarePlugin
import com.mikimn.apkloader.pm.PackageManagerAggregate
import com.mikimn.apkloader.reflection.FieldMapper
import com.mikimn.apkloader.reflection.tryGetField
import com.mikimn.apkloader.reflection.tryGetMethod
import com.mikimn.apkloader.reflection.tryGetValue
import com.mikimn.apkloader.shadow.ShadowActivity
import com.mikimn.apkloader.shadow.ShadowApplication
import com.mikimn.apkloader.utils.AssetReader
import dalvik.system.PathClassLoader
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Field
import java.net.URLClassLoader
import java.util.ServiceLoader


class DCLActivity : ComponentActivity() {
    private var shadowActivity: Activity? = null
    /** [LoadedApk.name] of the APK whose activity this instance hosts. */
    private var hostedApkName: String? = null

    companion object {
        /**
         * Activity.mWindowAdded tracks whether *this specific Activity object* has had its
         * decor view added to the real WindowManager. The shadow Activity's own copy of this
         * field is permanently false (nothing ever drives real ActivityThread window-attachment
         * machinery on the shadow object itself), so blindly syncing it onto the host after
         * every lifecycle call corrupts the host's real window-attachment bookkeeping: on the
         * very next resume of an already-window-added host (e.g. returning from a child
         * activity higher in the same task's back stack), makeVisible()'s `if (!mWindowAdded)`
         * guard is bypassed and it tries to re-add the already-attached decor view, crashing
         * with "View ... has already been added to the window manager".
         */
        private val LIFECYCLE_COPY_FILTER = Predicate<Pair<java.lang.reflect.Field, Any?>> {
            it.first.name != "mWindowAdded"
        }
        const val KEY_ACTIVITY_CLASS = "activityClassName"
        const val KEY_APK_ASSET_FILE_NAME = "apkAssetFileName"
        /** Set by [ActivityTaskManagerHook] when it retargets an intent to a proxy pool slot. */
        const val KEY_LOADED_APK_NAME = "loadedApkName"

        /**
         * Launches [apkPath] - a bundled asset name or an absolute on-device APK path (e.g. an
         * installed app's `publicSourceDir`) - through DCLActivity. The target activity is
         * resolved from the APK's own manifest launcher in [onCreate].
         */
        fun intentForAPK(context: Context, apkPath: String): Intent {
            return Intent(context, DCLActivity::class.java)
                .putExtra(KEY_APK_ASSET_FILE_NAME, apkPath)
        }

        /**
         * Retargets [baseIntent] at the host's DCLActivity, recording the real target class as
         * an extra.
         */
        fun forActivityClass(
            baseIntent: Intent,
            hostPackageName: String,
            activityClassName: String
        ): Intent {
            return baseIntent.apply {
                component = ComponentName(hostPackageName, DCLActivity::class.java.name)
                putExtra(KEY_ACTIVITY_CLASS, activityClassName)
            }
        }
    }

    fun attachActivity(activity: Activity) {
        shadowActivity = activity
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DCLContext(newBase))
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .permitAll()
                .permitDiskReads()
                .permitDiskWrites()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(VmPolicy.Builder().permitNonSdkApiUsage().apply {
            if (Build.VERSION.SDK_INT >= 31) {
                permitUnsafeIntentLaunch()
            }
        }.build())

        val loader = classLoader as FileTrackingClassLoader
        val apkAssetFileName = intent.getStringExtra(KEY_APK_ASSET_FILE_NAME)
        val loadedApkName = intent.getStringExtra(KEY_LOADED_APK_NAME)
        val bContext = if (baseContext is DCLContext) baseContext as DCLContext else null

        // KEY_LOADED_APK_NAME (an in-app navigation hop retargeted through a
        // DCLActivityProxyPool slot by ActivityTaskManagerHook) names an APK that was already
        // loaded in this process - unless the process was killed since, and Android is now
        // restoring this activity from recents or the back stack into a fresh, empty process.
        // Both extras hold the same kind of value (LoadedApk.name: an asset name or an absolute
        // device path), so either way it can simply be (re)loaded by name.
        val apkName = apkAssetFileName ?: loadedApkName
        val loadedApk = if (apkName != null) {
            loader.apkFile(apkName) ?: loadApk(loader, apkName)
        } else {
            // Only reachable through a legacy app-specific host manifest entry (see
            // DCLAppComponentFactory.instantiateDCLActivity), which carries no APK name at all.
            loader.last ?: throw IllegalStateException(
                "No APK to host for ${intent.component}: no $KEY_APK_ASSET_FILE_NAME/" +
                    "$KEY_LOADED_APK_NAME extra and nothing loaded in this process"
            )
        }
        hostedApkName = loadedApk.name
        initResourceLoader(loader)
        bContext?.setShadowPackageName(loadedApk.manifestReader!!.getApplicationInfo().packageName)

        val manifestReader = loadedApk.manifestReader
        val appInfo = manifestReader?.getApplicationInfo()

        val activityClassName = intent.getStringExtra(KEY_ACTIVITY_CLASS)
            ?: manifestReader?.getLauncherActivity()?.name

        if (activityClassName == null) {
            throw IllegalArgumentException("No target activity: pass $KEY_ACTIVITY_CLASS or load an APK with a launcher activity")
        }

        val applicationClassName = appInfo?.name

        val aInfo = manifestReader?.getActivityInfo(
            ComponentName(
                appInfo?.packageName ?: "",
                activityClassName
            ), 0
        )?.let {
            val wrapped = ActivityInfo(this.tryGetValue("mActivityInfo"))
            FieldMapper.copy(wrapped, it)
            wrapped
        }
        aInfo?.let { applyActivityAttributes(it) }

        // Initialize providers
        val providers = manifestReader?.getProviders() ?: emptyList()

        for (providerInfo in providers) {
            try {
                val providerClass = loader.loadClass(providerInfo.name)
                val provider = providerClass.getDeclaredConstructor().newInstance() as ContentProvider

                // TODO(@mikimn): Remove, replace with general provider resolver
                if (!providerInfo.name.contains("MlKitInitProvider")) {
                    provider.attachInfo(baseContext, providerInfo)
                    // Should not be called, because attachInfo already does that
                    //  https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/ContentProvider.java;l=2649;drc=61197364367c9e404c7da6900658f1b16c42d0da
                    // provider.onCreate()
                    ProviderRegistry.register(provider, providerInfo)
                }
            } catch (e: Throwable) {
                // Best effort: a provider that can't attach in the shadowed environment
                // (e.g. a non-exported provider's own export check, or a static
                // initializer assuming a real installed-app Context) shouldn't take
                // down the whole activity load.
                Log.e("DCLActivity", "Failed to initialize provider ${providerInfo.name}", e)
            }
        }

        // Initialize Application
        val newAppInfo = ApplicationInfo(applicationInfo)
        appInfo?.let { FieldMapper.copy(newAppInfo, it) }

        // Reuse the same shadow Application instance across every proxy-pool slot for
        // this loaded APK - see LoadedApk.shadowApplication for why a fresh one per
        // activity is wrong, not just wasteful.
        val isNewShadowApp = loadedApk.shadowApplication == null
        val shadowApp = applicationClassName?.let {
            loadedApk.shadowApplication
                ?: ShadowApplication.createShadowApplication(loader, it, application, baseContext)
                    .also { created -> loadedApk.shadowApplication = created }
        }

        val ht = HandlerThread("Emulator")
        ht.start()

        // Application runs on the main looper instead of the UI thread
        val handler = Handler(mainLooper)

        var isWaitingOnHandler = true
        //handler.post {
            Log.d("DCLActivity", "handler.post")
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .permitAll()
                    .build()
            )
            StrictMode.setVmPolicy(VmPolicy.Builder().permitNonSdkApiUsage().apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    permitUnsafeIntentLaunch()
                }
            }.build())

            shadowApp?.let {
                bContext?.setShadowApplication(it)
                if (isNewShadowApp) {
                    ShadowApplication.onCreate(it)
                }
            }

            runOnUiThread {
                Log.d("DCLActivity", "runOnUiThread")
                // Initialize activity
                // Best effort
                val newActivityInfo =
                    aInfo ?: ActivityInfo(packageManager.getActivityInfo(componentName, 0)).apply {
                        applicationInfo = appInfo
                    }

                if (shadowActivity == null) {
                    shadowActivity = loadedApk.loadClass(activityClassName).newInstance() as Activity
                }

                // The saved state may hold the loaded app's own Parcelables (view/fragment state),
                // which a Bundle can only unparcel with the APK's classloader, not the host's.
                loadedApk.loader?.let { savedInstanceState?.classLoader = it }
                initShadowActivity(shadowActivity!!, shadowApp, newActivityInfo, savedInstanceState)

                isWaitingOnHandler = false;
            }
//        }

//        while (isWaitingOnHandler) {
//            Log.d("DCLActivity", "Thread.sleep")
//            Thread.sleep(100)
//        }
    }

    /** Reads [apkName] (an absolute device path, else an asset name) and loads it. */
    private fun loadApk(loader: FileTrackingClassLoader, apkName: String): LoadedApk {
        val reader = AssetReader(this)
        val apkFile = File(apkName)

        val apkData = if (apkFile.exists()) {
            FileInputStream(apkFile).use { reader.readStream(it) }
        } else {
            reader.readFile(apkName)
        }

        val loadedApk = loader.addApkFile(apkName, apkData, resources)

        (packageManager as? PackageManagerAggregate)
            ?.addPlugin(ManifestAwarePlugin(loadedApk.manifestReader!!))

        return loadedApk
    }

    private fun initShadowActivity(
        activity: Activity,
        shadowApp: Application?,
        newActivityInfo: ActivityInfo,
        savedInstanceState: Bundle?
    ) {
        ShadowActivity.attachActivity(newActivityInfo, this, activity, shadowApp)

        if (newActivityInfo.themeResource != 0) {
            activity.setTheme(newActivityInfo.themeResource)
        }

        val instrumentation = activity.tryGetValue<Instrumentation>("mInstrumentation")!!
        instrumentation.callActivityOnCreate(activity, savedInstanceState)
        disableAutoGameSignIn(activity)

        FieldMapper.copy(this, activity, LIFECYCLE_COPY_FILTER)
    }

    /**
     * Google's ~2014 BaseGameActivity/GameHelper sample helper (bundled by
     * flappy-bird-1-3.apk, and likely other apps of that era) auto-connects to
     * Play Games Services from onStart() unless GameHelper.setConnectOnStart(false)
     * was called first - which this app's own code never does. A real installed
     * app gets a legitimate sign-in flow; a loaded-but-not-installed app can't,
     * since Play Services validates the calling app's package/signing identity
     * against what's registered for the App ID, which will never match ours.
     * Left alone, the async sign-in callback throws
     * IllegalStateException("A fatal developer error has occurred") on the main
     * thread and kills the whole process (see docs/apk-test-log.md).
     *
     * GameHelper's field names below are obfuscated and specific to this exact
     * bundled build - this is a one-off, app-specific patch (matching the
     * existing MlKitInitProvider special-case above), not a generic mechanism.
     * Silently a no-op for any activity that isn't a BaseGameActivity.
     */
    private fun disableAutoGameSignIn(activity: Activity) {
        try {
            var cls: Class<*>? = activity.javaClass
            var gameHelper: Any? = null
            while (cls != null && gameHelper == null) {
                gameHelper = cls.declaredFields
                    .firstOrNull { it.type.name == "com.google.example.games.basegameutils.a" }
                    ?.apply { isAccessible = true }
                    ?.get(activity)
                cls = cls.superclass
            }
            val connectOnStart = gameHelper?.javaClass?.tryGetField("l") ?: return
            connectOnStart.setBoolean(gameHelper, false)
            Log.i("DCLActivity", "Disabled GameHelper auto-connect-on-start for $activity")
        } catch (e: Throwable) {
            Log.e("DCLActivity", "Failed to disable GameHelper auto-connect", e)
        }
    }

//    private fun attachClassLoader(base: Context, loader: ClassLoader) {
//        if (base is ContextWrapper) {
//            attachClassLoader(base.baseContext, loader)
//        } else {
//            val fClassLoader = base.javaClass.tryGetField("mClassLoader")
//            fClassLoader?.isAccessible = true
//            fClassLoader?.set(base, loader)
//        }
//    }

    private fun initResourceLoader(loader: FileTrackingClassLoader) {
        val rl = loader.resourcesLoader
        resources.addLoaders(rl)
        baseContext.resources.addLoaders(rl)
        application.resources.addLoaders(rl)
    }

    @SuppressLint("MissingSuperCall")
    override fun onStop() {
        // super.onStop()
        overrideLifecycleCall("onStop")
    }

    @SuppressLint("MissingSuperCall")
    override fun onStart() {
        // super.onStart()
        overrideLifecycleCall("onStart")
    }

    @SuppressLint("MissingSuperCall")
    override fun onRestart() {
        // super.onRestart()
        overrideLifecycleCall("onRestart")
    }

    @SuppressLint("MissingSuperCall")
    override fun onResume() {
        // super.onResume()
        overrideLifecycleCall("onResume")
    }

    @SuppressLint("MissingSuperCall")
    override fun onPostResume() {
        // super.onPostResume()
        overrideLifecycleCall("onPostResume")
    }

    override fun onAttachedToWindow() {
        // super.onAttachedToWindow()
        overrideLifecycleCall("onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        // super.onDetachedFromWindow()
        overrideLifecycleCall("onDetachedFromWindow")
    }

    @SuppressLint("MissingSuperCall")
    override fun onPause() {
        // super.onPause()
        overrideLifecycleCall("onPause")
    }

    @SuppressLint("MissingSuperCall")
    override fun onDestroy() {
        // super.onDestroy()
        overrideLifecycleCall("onDestroy")
    }

    @SuppressLint("MissingSuperCall")
    override fun onPostCreate(savedInstanceState: Bundle?) {
        // super.onPostCreate(savedInstanceState)
        overrideLifecycleCall("onPostCreate", Bundle::class.java to savedInstanceState)
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        overrideLifecycleCall("onSaveInstanceState", Bundle::class.java to outState)
    }

    @SuppressLint("MissingSuperCall")
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // The saved state may hold the loaded app's own Parcelables (view/fragment state),
        // which a Bundle can only unparcel with the APK's classloader, not the host's.
        shadowActivity?.let { savedInstanceState.classLoader = it.javaClass.classLoader }
        overrideLifecycleCall("onRestoreInstanceState", Bundle::class.java to savedInstanceState)
    }

    // The shadow Activity is attached with this host's mToken, so AMS delivers every result
    // the shadow asked for (startActivityForResult, requestPermissions, androidx
    // registerForActivityResult - which is built on both) to *this* Activity instead.
    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        overrideLifecycleCall(
            "onActivityResult",
            Int::class.javaPrimitiveType!! to requestCode,
            Int::class.javaPrimitiveType!! to resultCode,
            Intent::class.java to data
        )
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Activity.requestPermissions refuses to start a new request while
        // mHasCurrentPermissionsRequest is set, and only dispatchRequestPermissionsResult
        // clears it - which the framework just ran on this host, not on the shadow that
        // actually made the request. Clear it on the shadow too, or every later
        // permission request from the loaded app is silently dropped.
        setShadowActivityField("mHasCurrentPermissionsRequest", false)
        overrideLifecycleCall(
            "onRequestPermissionsResult",
            Int::class.javaPrimitiveType!! to requestCode,
            Array<String>::class.java to permissions,
            IntArray::class.java to grantResults
        )
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        // A new intent can reach an existing host instance (DCLActivity is singleTask, and
        // SINGLE_TOP/CLEAR_TOP launches can land on a proxy slot) while it's hosting a
        // different loaded activity than the one the intent targets - only hand it to the
        // shadow if it's actually for that shadow's class.
        val targetClass = intent.getStringExtra(KEY_ACTIVITY_CLASS)
        val targetApk = intent.getStringExtra(KEY_APK_ASSET_FILE_NAME)
            ?: intent.getStringExtra(KEY_LOADED_APK_NAME)
        val shadow = shadowActivity
        if (shadow == null ||
            (targetClass != null && targetClass != shadow.javaClass.name) ||
            (targetApk != null && targetApk != hostedApkName)
        ) {
            Log.w(
                "DCLActivity",
                "Dropping onNewIntent for $targetApk/$targetClass, hosting $hostedApkName/${shadow?.javaClass?.name}"
            )
            return
        }
        overrideLifecycleCall("onNewIntent", Intent::class.java to intent)
    }

    // What the loaded activity declared for itself, which the host's own (placeholder) manifest
    // entry cannot express per activity - see DCLActivityProxyPool for the part that can.
    private var hostedConfigChanges = 0
    private var lastConfiguration: Configuration? = null

    private fun applyActivityAttributes(info: ActivityInfo) {
        hostedConfigChanges = info.configChanges
        lastConfiguration = Configuration(resources.configuration)

        if (info.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            requestedOrientation = info.screenOrientation
        }
        if (info.softInputMode != 0) {
            window.setSoftInputMode(info.softInputMode)
        }
        if (info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0) {
            getSystemService(ActivityManager::class.java).appTasks
                .firstOrNull { it.taskInfo.taskId == taskId }
                ?.setExcludeFromRecents(true)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onConfigurationChanged(newConfig: Configuration) {
        // The host declares it handles every config change (the "Cfg" proxy pools) so a
        // rotation doesn't recreate it under an app that handles rotation itself. For a change
        // the loaded app did NOT opt into, the real system would have recreated it: do that.
        val previous = lastConfiguration
        lastConfiguration = Configuration(newConfig)
        if (previous != null &&
            DCLActivityProxyPool.needsRecreate(previous.diff(newConfig), hostedConfigChanges)
        ) {
            // The platform insists the host's own Activity.onConfigurationChanged ran (mCalled). On
            // the forward path below that flag arrives from the shadow via the state sync; here
            // nothing is forwarded, so call it ourselves.
            super.onConfigurationChanged(newConfig)
            recreate()
            return
        }

        // ActivityThread updates mCurrentConfig on the Activity it knows about (this host)
        // before calling onConfigurationChanged - mirror that on the shadow first, or the
        // state sync afterwards would copy the shadow's stale configuration back over ours.
        setShadowActivityField("mCurrentConfig", Configuration(newConfig))
        overrideLifecycleCall("onConfigurationChanged", Configuration::class.java to newConfig)
    }

    /**
     * Sets one of android.app.Activity's own (hidden) fields on the shadow instance.
     * Hidden fields outside the SDK greylist aren't visible to plain reflection, so fall back
     * to HiddenApiBypass; and log rather than silently skip if the field is gone entirely
     * (renamed in a newer AOSP).
     */
    private fun setShadowActivityField(name: String, value: Any?) {
        val shadow = shadowActivity ?: return
        try {
            val field = Activity::class.java.tryGetField(name)
                ?: HiddenApiBypass.getInstanceFields(Activity::class.java)
                    .filterIsInstance<Field>()
                    .firstOrNull { it.name == name }
            if (field == null) {
                Log.w("DCLActivity", "Activity.$name not found, shadow state may be stale")
                return
            }
            field.isAccessible = true
            field.set(shadow, value)
        } catch (e: Exception) {
            Log.e("DCLActivity", "Failed to set Activity.$name on shadow", e)
        }
    }

    private fun overrideLifecycleCall(
        methodName: String,
        vararg parameters: Pair<Class<*>, Any?>
    ) = overrideLifecycleCall(methodName, true, *parameters)

    private fun overrideLifecycleCall(
        methodName: String,
        propagateState: Boolean,
        vararg parameters: Pair<Class<*>, Any?>
    ) {
        val shadowActivityClass = shadowActivity!!::class.java

        val parameterTypes = parameters.map { it.first }.toTypedArray()
        val values = parameters.map { it.second }.toTypedArray()
        try {
            val refMethod = shadowActivityClass.tryGetMethod(methodName, *parameterTypes)
            refMethod?.let {
                refMethod.isAccessible = true
                Log.i("DCLActivity", "[Lifecycle] $methodName(${values.joinToString(", ")})")
                refMethod.invoke(shadowActivity, *values)
            }

            if (refMethod == null) {
                Log.i("DCLActivity", "$methodName = null")
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        if (propagateState) {
            // Update state back to this activity
            FieldMapper.copy(this, shadowActivity!!, LIFECYCLE_COPY_FILTER)
        }
    }
}