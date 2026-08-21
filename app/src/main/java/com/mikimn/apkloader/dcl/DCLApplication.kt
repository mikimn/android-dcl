package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log

class DCLApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitAll().build())
        StrictMode.setVmPolicy(VmPolicy.Builder().permitNonSdkApiUsage().apply {
            if (Build.VERSION.SDK_INT >= 31) {
                permitUnsafeIntentLaunch()
            }
        }.build())

        installUnavoidablePermissionDenialGuard()

        (classLoader as? FileTrackingClassLoader)?.let {
            ActivityTaskManagerHook.install(it, packageName)
        }
    }

    /**
     * Some loaded apps' own background threads query real, signature-protected system
     * providers (e.g. Google's GservicesProvider, queried by Play Services' "Primes"
     * telemetry) that only a Google-signed app can legitimately hold permission for.
     * A loaded-but-not-installed app runs under our own host's real identity, so this
     * SecurityException is unavoidable no matter what our own virtualization layer
     * does - and unlike a main-thread call we can wrap in try/catch (see
     * ShadowApplication.onCreate), a DIFFERENT thread's uncaught exception kills the
     * whole process regardless of what the main thread does. Narrowly swallow only
     * this exact, identifiable failure signature; anything else (including real bugs
     * in our own code) still crashes normally and visibly.
     */
    private fun installUnavoidablePermissionDenialGuard() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isUnavoidablePermissionDenial(throwable)) {
                Log.e(
                    "DCLApplication",
                    "Ignoring unavoidable OS permission denial on thread ${thread.name}",
                    throwable
                )
            } else {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isUnavoidablePermissionDenial(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is SecurityException && cause.message?.contains("Permission Denial") == true) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(DCLContext(base))
    }
}