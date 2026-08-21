package com.mikimn.apkloader.shadow

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.core.util.Predicate
import com.mikimn.apkloader.dcl.DCLApplication
import com.mikimn.apkloader.dcl.DCLContext
import com.mikimn.apkloader.reflection.FieldMapper
import com.mikimn.apkloader.reflection.tryGetField
import com.mikimn.apkloader.reflection.tryGetMethod
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException

object ShadowApplication {
    private fun fixPackageInfoDependency(context: Context, application: Application) {
        if (context is ContextWrapper) {
            return fixPackageInfoDependency(context.baseContext, application)
        }

        // Quickfix to cyclic dependency
        val fPackageInfo = context.javaClass.tryGetField("mPackageInfo")
        fPackageInfo?.isAccessible = true
        val mPackageInfo = fPackageInfo?.get(context)
        val fPackageApplication = mPackageInfo?.javaClass?.tryGetField("mApplication")
        fPackageApplication?.isAccessible = true
        fPackageApplication?.set(mPackageInfo, application)
    }

    private fun fixMainThreadDependency(context: Context, application: Application) {
        if (context is ContextWrapper) {
            return fixMainThreadDependency(context.baseContext, application)
        }

        // Quickfix to cyclic dependency
        val fMainThread = context.javaClass.tryGetField("mMainThread")
        fMainThread?.isAccessible = true
        val mMainThread = fMainThread?.get(context)
        val fInitialApp = mMainThread?.javaClass?.tryGetField("mInitialApplication")
        fInitialApp?.isAccessible = true
        fInitialApp?.set(mMainThread, application)
    }

    fun createShadowApplication(
        loader: ClassLoader,
        className: String,
        baseApplication: Application,
        context: Context
    ): Application {
//        if (context is ContextWrapper) {
//            return createShadowApplication(loader, className, baseApplication, context.baseContext)
//        }

        val appClass = loader.loadClass(className)
        val shadowApp = Instrumentation.newApplication(appClass, context)

        // TODO Delete, using Instrumentation is better
        // val shadowApp: Application = appClass.newInstance() as Application

        // val attachBaseContext = shadowApp.javaClass.tryGetMethod("attachBaseContext", Context::class.java)
        // attachBaseContext?.invoke(shadowApp, context)

        FieldMapper.copy(shadowApp, baseApplication)

        fixPackageInfoDependency(context, shadowApp)
        fixMainThreadDependency(context, shadowApp)

        return shadowApp
    }

    fun onCreate(application: Application) {
        val appOnCreate = application.javaClass.tryGetMethod("onCreate")
        try {
            appOnCreate?.invoke(application)
        } catch (e: InvocationTargetException) {
            // Some apps' Application.onCreate() does early, non-essential setup - e.g.
            // Google's own "Primes" telemetry init queries the real GservicesProvider,
            // which requires the READ_GSERVICES signature permission that only a
            // Google-signed app can legitimately hold. A loaded-but-not-installed app
            // runs under our own host's real identity, so that permission check
            // genuinely, unavoidably fails here regardless of our own virtualization -
            // best effort not to let that early failure take down the whole process.
            Log.e("ShadowApplication", "Application.onCreate() threw, continuing best-effort", e.targetException)
        }
    }
}