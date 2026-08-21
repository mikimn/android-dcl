package com.mikimn.apkloader.plugins

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import com.mikimn.apkloader.pm.PackageManagerWrapper

class PlayServicesBlockingPackageManager(base: PackageManager): PackageManagerWrapper(base) {
    companion object {
        val ACTIVITY_WHITELIST = arrayOf(
            "com.google.ads.AdActivity",
//            "net.oneplus.weather.app.MainActivity"
        )
    }

    override fun resolveActivity(p0: Intent, p1: Int): ResolveInfo? {
        if (p0.component?.className in ACTIVITY_WHITELIST) {
            return ResolveInfo()
        }

        return super.resolveActivity(p0, p1)
    }

    override fun getActivityInfo(p0: ComponentName, p1: Int): ActivityInfo {
        if (p0.className in ACTIVITY_WHITELIST) {
            return ActivityInfo()
        }

        return super.getActivityInfo(p0, p1)
    }

    override fun getProviderInfo(p0: ComponentName, p1: Int): ProviderInfo {
//        if (p0.className.endsWith("TcInfoContentProvider")) {
//            return ProviderInfo().apply {
//                readPermission = "com.truecaller.permission.sdk.internal.read_account_state"
//                authority = "com.truecaller.TcAccountStateProvider;com.truecaller.TcInfoContentProvider"
//                exported = true
//            }
//        }
        return super.getProviderInfo(p0, p1)
    }

    override fun getPackageInfo(p0: String, p1: Int): PackageInfo {
        if (p0 == "com.google.android.gms") {
            throw NameNotFoundException()
        }

        return super.getPackageInfo(p0, p1)
    }
}