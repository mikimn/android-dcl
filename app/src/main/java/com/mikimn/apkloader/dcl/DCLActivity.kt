package com.mikimn.apkloader.dcl

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentProvider
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.activity.ComponentActivity
import com.mikimn.apkloader.ENTRY_POINTS
import com.mikimn.apkloader.apk.ManifestAwarePlugin
import com.mikimn.apkloader.pm.PackageManagerAggregate
import com.mikimn.apkloader.reflection.FieldMapper
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
import java.net.URLClassLoader
import java.util.ServiceLoader


class DCLActivity : ComponentActivity() {
    private var shadowActivity: Activity? = null

    companion object {
        const val KEY_ACTIVITY_CLASS = "activityClassName"
        private const val KEY_APPLICATION_CLASS = "applicationClassName"
        const val KEY_APK_ASSET_FILE_NAME = "apkAssetFileName"
        /** Set by [ActivityTaskManagerHook] when it retargets an intent to a proxy pool slot. */
        const val KEY_LOADED_APK_NAME = "loadedApkName"

        fun intentForAPK(assetName: String): Intent {
            if (assetName.endsWith("base.apk") || assetName.startsWith("/system/")) {
                // From device
                return Intent().apply {
                    component = DCLActivity::class.java.`package`?.let {
                        ComponentName(
                            "com.mikimn.apkloader",
                            DCLActivity::class.java.name
                        )
                    }
                }.apply {
                    // putExtra(KEY_ACTIVITY_CLASS, entryPoint.mainActivityClassName)
                    // putExtra(KEY_APPLICATION_CLASS, entryPoint.applicationClassName)
                    putExtra(KEY_APK_ASSET_FILE_NAME, assetName)
                }
            }

            val entryPoint =
                ENTRY_POINTS.getOrElse(assetName) { throw IllegalArgumentException("No entry points for $assetName") }
            return Intent().apply {
                component = DCLActivity::class.java.`package`?.let {
                    ComponentName(
                        it.name,
                        entryPoint.mainActivityClassName
                    )
                }
            }.apply {
                putExtra(KEY_ACTIVITY_CLASS, entryPoint.mainActivityClassName)
                putExtra(KEY_APPLICATION_CLASS, entryPoint.applicationClassName)
                putExtra(KEY_APK_ASSET_FILE_NAME, assetName)
            }
        }

        fun forActivityClass(baseIntent: Intent, activityClassName: String): Intent {
            return baseIntent.apply {
                component = DCLActivity::class.java.`package`?.let {
                    ComponentName(
                        // TODO Make this dynamically resolved
                        "com.mikimn.apkloader",
                        DCLActivity::class.java.name
                    )
                }
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

        val loadedApk = if (apkAssetFileName != null) {
            // Delegated activity attachment
            val reader = AssetReader(this)
            val apkFile = File(apkAssetFileName)

            val apkData = if (apkFile.exists()) {
                // Read as file
                reader.readStream(FileInputStream(apkFile))
            } else {
                // Read as asset
                reader.readFile(apkAssetFileName)
            }

            val loadedApk = loader.addApkFile(apkAssetFileName, apkData, resources)

            if (packageManager is PackageManagerAggregate) {
                val pma = packageManager as PackageManagerAggregate
                pma.addPlugin(ManifestAwarePlugin(loadedApk.manifestReader!!))
            }

            loadedApk
        } else if (loadedApkName != null) {
            // In-app navigation to an already-loaded APK's own activity, retargeted
            // through a DCLActivityProxyPool slot by ActivityTaskManagerHook.
            loader.apkFile(loadedApkName)!!
        } else {
            loader.last!!
        }
        initResourceLoader(loader)
        bContext?.setShadowPackageName(loadedApk.manifestReader!!.getApplicationInfo().packageName)

        val manifestReader = loadedApk.manifestReader
        val appInfo = manifestReader?.getApplicationInfo()

        val activityClassName = intent.getStringExtra(KEY_ACTIVITY_CLASS)
            ?: manifestReader?.getLauncherActivity()?.name

        if (activityClassName == null) {
            throw IllegalArgumentException("DCLActivity must be initialized with forActivityClass only")
        }

        // val applicationClassName = intent.getStringExtra(KEY_APPLICATION_CLASS)
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

        // Initialize providers
        val providers = manifestReader?.getProviders() ?: emptyList()

        for (providerInfo in providers) {
            val providerClass = loader.loadClass(providerInfo.name)
            val provider = providerClass.getDeclaredConstructor().newInstance() as ContentProvider

            // TODO(@mikimn): Remove, replace with general provider resolver
            if (!providerInfo.name.contains("MlKitInitProvider")) {
                provider.attachInfo(baseContext, providerInfo)
                // Should not be called, because attachInfo already does that
                //  https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/ContentProvider.java;l=2649;drc=61197364367c9e404c7da6900658f1b16c42d0da
                // provider.onCreate()
            }
        }

        // Initialize Application
        val newAppInfo = ApplicationInfo(applicationInfo)
        appInfo?.let { FieldMapper.copy(newAppInfo, it) }

        val shadowApp = applicationClassName?.let {
            ShadowApplication.createShadowApplication(loader, it, application, baseContext)
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
                ShadowApplication.onCreate(it)
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

                initShadowActivity(shadowActivity!!, shadowApp, newActivityInfo, savedInstanceState)

                isWaitingOnHandler = false;
            }
//        }

//        while (isWaitingOnHandler) {
//            Log.d("DCLActivity", "Thread.sleep")
//            Thread.sleep(100)
//        }
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

        FieldMapper.copy(this, activity)
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
            FieldMapper.copy(this, shadowActivity!!)
        }
    }
}