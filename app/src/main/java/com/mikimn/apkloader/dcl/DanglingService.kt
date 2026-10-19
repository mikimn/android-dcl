package com.mikimn.apkloader.dcl

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Stands in for a service the host manifest declares but whose class this process cannot load: a leftover entry for a
 * loaded app's service (the class lives only in that app's APK, which is not loaded yet in a fresh process). The
 * system creating it must not take the host down with `ClassNotFoundException`, so it just stops itself.
 */
class DanglingService : Service() {
    override fun onCreate() {
        super.onCreate()
        Log.w("DanglingService", "Stopping a service whose class is not available in this process")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
