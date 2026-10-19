package com.mikimn.apkloader.dcl

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppComponentFactory
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.app.CoreComponentFactory
import com.mikimn.apkloader.MainActivity
import dalvik.system.PathClassLoader
import kotlinx.coroutines.internal.MainDispatcherFactory

@SuppressLint("RestrictedApi")
class DCLAppComponentFactory : CoreComponentFactory() {
    /** This (host) app's own package, from the ApplicationInfo handed to instantiateClassLoader. */
    private var hostPackageName: String? = null

    override fun instantiateClassLoader(cl: ClassLoader, aInfo: ApplicationInfo): ClassLoader {
        Log.e("DCLAppComponentFactory", "instantiateClassLoader")
        hostPackageName = aInfo.packageName
        // return super.instantiateClassLoader(cl, aInfo)
        val defaultClassLoader = super.instantiateClassLoader(cl, aInfo)
        val newClassLoader = FileTrackingClassLoader(defaultClassLoader)
        // Expected once per process. If it ever ran twice the framework would get this second loader (and so
        // would new components) while `process` keeps naming the first, i.e. two APK registries: say so.
        if (FileTrackingClassLoader.process == null) {
            FileTrackingClassLoader.process = newClassLoader
        } else {
            Log.w("DCLAppComponentFactory", "A second FileTrackingClassLoader was created in this process; APKs loaded through it are invisible to the process-wide one")
        }
        Thread.currentThread().contextClassLoader = newClassLoader
        return newClassLoader
        // return BaseDexClassLoader("", null, null, FileTrackingClassLoader(super.instantiateClassLoader(cl, aInfo)))
    }

    override fun instantiateApplication(cl: ClassLoader, className: String): Application {
        Log.e("DCLAppComponentFactory", "instantiateApplication($className)")
        return super.instantiateApplication(cl, className)
    }

    private fun instantiateDCLActivity(
        loader: ClassLoader,
        activityClassName: String,
        baseIntent: Intent
    ): Activity {
        Log.e("DCLAppComponentFactory", "instantiateDCLActivity($baseIntent)")
        val realActivity = super.instantiateActivity(
            loader,
            DCLActivity::class.java.name,
            DCLActivity.forActivityClass(
                baseIntent,
                checkNotNull(hostPackageName ?: baseIntent.component?.packageName) {
                    "Host package unknown: instantiateClassLoader hasn't run and $baseIntent has no component"
                },
                activityClassName
            )
        ) as DCLActivity

        if (!baseIntent.hasExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME)) {
            // Apk is already loaded, do not delegate attachment
            val shadowActivity = super.instantiateActivity(loader, activityClassName, baseIntent)
            realActivity.attachActivity(shadowActivity)
        }

        return realActivity
    }

    override fun instantiateActivity(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): Activity {
        Log.e("DCLAppComponentFactory", "instantiateActivity($className, $intent)")
        if (className == MainActivity::class.java.name) {
            // The launcher is exported: it must not honor an APK named in the intent of whoever started it.
            return super.instantiateActivity(cl, className, intent)
        }
        if (DCLActivityProxyPool.isProxyClassName(className) && !RoutingMac.verify(intent)) {
            // Extras this loader did not sign (a PendingIntent holder's fillIn, or an intent built by hand): see RoutingMac.
            Log.w("DCLAppComponentFactory", "Refusing $className: its routing extras are unsigned or altered")
            return super.instantiateActivity(cl, RefusedActivity::class.java.name, null)
        }
        // Only the loader's own activities (DCLActivity and the proxy slots) act on an APK named in the intent.
        // Before the platform reads this activity's saved state: see ApkLoading.preload.
        if (className == DCLActivity::class.java.name || DCLActivityProxyPool.isProxyClassName(className)) {
            ApkLoading.currentApplication()?.let { ApkLoading.preload(it, cl, intent) }
        }
        if (className == DCLActivity::class.java.name) {
            return super.instantiateActivity(cl, className, intent)
        }

        if (DCLActivityProxyPool.isProxyClassName(className)) {
            // ActivityTaskManagerHook already stashed the real target class/APK as
            // intent extras before retargeting here - just host a plain DCLActivity,
            // it reads those extras itself. No eager shadow-instantiation needed (and
            // none possible: unlike instantiateDCLActivity's whitelist path below,
            // there's no real compiled class named after a proxy pool slot).
            return super.instantiateActivity(cl, DCLActivity::class.java.name, intent)
        }

        // A legacy app-specific entry (no APK in its intent by design): ignore any APK extras it was started with.
        val sanitized = Intent(intent ?: Intent()).apply {
            removeExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME)
            removeExtra(DCLActivity.KEY_LOADED_APK_NAME)
        }
        return instantiateDCLActivity(cl, className, sanitized)
    }

    override fun instantiateReceiver(
        cl: ClassLoader,
        className: String,
        intent: Intent?
    ): BroadcastReceiver {
        Log.e("DCLAppComponentFactory", "instantiateReceiver($className, $intent)")
        return super.instantiateReceiver(cl, className, intent)
    }

    override fun instantiateService(cl: ClassLoader, className: String, intent: Intent?): Service {
        Log.e("DCLAppComponentFactory", "instantiateService($className, $intent)")
        if (DCLServiceProxyPool.isProxyClassName(className)) {
            // Host a plain DCLService, which finds its loaded service from its slot (no class named after a
            // slot exists, as with the activity pool). The intent is null here on Android 11.
            return super.instantiateService(cl, DCLService::class.java.name, intent)
        }
        return super.instantiateService(cl, className, intent)
    }

    override fun instantiateProvider(cl: ClassLoader, className: String): ContentProvider {
        Log.e("DCLAppComponentFactory", "instantiateProvider($className)")
        return super.instantiateProvider(cl, className)
    }
}