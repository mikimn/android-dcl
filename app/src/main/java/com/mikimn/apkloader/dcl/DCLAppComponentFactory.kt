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

    override fun instantiateClassLoader(cl: ClassLoader, aInfo: ApplicationInfo): ClassLoader {
        Log.e("DCLAppComponentFactory", "instantiateClassLoader")
        // return super.instantiateClassLoader(cl, aInfo)
        val defaultClassLoader = super.instantiateClassLoader(cl, aInfo)
        val newClassLoader = FileTrackingClassLoader(defaultClassLoader)
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
            DCLActivity.forActivityClass(baseIntent, activityClassName)
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
        if (className == DCLActivity::class.java.name || className == MainActivity::class.java.name) {
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

        return instantiateDCLActivity(cl, className, intent ?: Intent())
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
        return super.instantiateService(cl, className, intent)
    }

    override fun instantiateProvider(cl: ClassLoader, className: String): ContentProvider {
        Log.e("DCLAppComponentFactory", "instantiateProvider($className)")
        return super.instantiateProvider(cl, className)
    }
}