package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy

class DCLApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StrictMode.setThreadPolicy(ThreadPolicy.Builder().permitAll().build())
        StrictMode.setVmPolicy(VmPolicy.Builder().permitNonSdkApiUsage().apply {
            if (Build.VERSION.SDK_INT >= 31) {
                permitUnsafeIntentLaunch()
            }
        }.build())
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(DCLContext(base))
    }
}