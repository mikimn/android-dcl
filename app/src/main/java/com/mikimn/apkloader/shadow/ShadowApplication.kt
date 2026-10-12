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
    // The host's genuine Application and the platform LoadedApk whose mApplication we overwrite while a loaded
    // app's Application is created and runs onCreate(). That field is what the system hands to every component
    // it instantiates for the host (makeApplication), so it must go back afterwards or a *different* loaded app
    // (or the host's own activity) would be attached to this app's Application (#22).
    private var hostApplication: Application? = null
    private var hostPackageInfo: Any? = null

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
        synchronized(this) {
            if (hostApplication == null) {
                hostApplication = fPackageApplication?.get(mPackageInfo) as? Application
                hostPackageInfo = mPackageInfo
            }
        }
        fPackageApplication?.set(mPackageInfo, application)
    }

    /** Puts the host's own Application back as the platform's, undoing [fixPackageInfoDependency]. */
    private fun restoreHostApplication() {
        val (info, host) = synchronized(this) { hostPackageInfo to hostApplication }
        if (info == null || host == null) return
        info.javaClass.tryGetField("mApplication")?.apply { isAccessible = true }?.set(info, host)
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

        // mBase stays the context the app was just attached with (the loaded package's own).
        FieldMapper.copy(shadowApp, baseApplication, Predicate { it.first.name != "mBase" })

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
        } finally {
            restoreHostApplication()
        }
    }
}