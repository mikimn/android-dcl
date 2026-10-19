package com.mikimn.apkloader.dcl

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * What a proxy slot hosts when its launch intent fails [RoutingMac.verify]: it finishes immediately. Refusing by
 * throwing from the component factory would crash the whole process, i.e. let whoever forged the intent kill the host.
 */
class RefusedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.w("RefusedActivity", "Refusing a proxy-slot launch with unsigned or altered routing extras")
        finish()
    }
}
