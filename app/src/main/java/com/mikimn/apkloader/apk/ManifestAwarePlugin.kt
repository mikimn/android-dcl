package com.mikimn.apkloader.apk

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.util.Log
import com.mikimn.apkloader.pm.PackageManagerPlugin

class ManifestAwarePlugin(private val reader: AndroidManifestReader) : PackageManagerPlugin {
    private val aInfo: ApplicationInfo = reader.getApplicationInfo()

    override fun getActivityInfo(component: ComponentName, flags: Int): ActivityInfo {
        return reader.getActivityInfo(component, flags)
    }

    override fun getReceiverInfo(component: ComponentName, flags: Int): ActivityInfo {
        throw NameNotFoundException(component.className)
    }

    override fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo {
        return reader.getServices().firstOrNull {
            it.name == component.className
        } ?: throw NameNotFoundException(component.className)
    }

    override fun getProviderInfo(component: ComponentName, flags: Int): ProviderInfo {
        return reader.getProviders().firstOrNull {
            it.name == component.className
        } ?: throw NameNotFoundException(component.className)
    }

    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
//        Log.e("ManifestAwarePlugin", "getApplicationInfo($packageName, $flags)")
//        if (aInfo.packageName == packageName) {
//
//            for (key in aInfo.metaData.keySet()) {
//                Log.e("ManifestAwarePlugin", "$key = ${aInfo.metaData.get(key)}")
//            }
//
//            return aInfo
//        }

        return aInfo
    }

    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        if (aInfo.packageName == packageName) {
            return PackageInfo().apply {
                applicationInfo = aInfo
            }
        }

        throw NameNotFoundException(packageName)
    }

    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        for (activity in reader.parseActivities()) {
            val aInfo = activity.first
            val iFilters = activity.second
            for (iFilter in iFilters) {
                if (iFilter.hasAction(intent.action)) {
                    return ResolveInfo().apply {
                        activityInfo = aInfo
                        filter = iFilter
                    }
                }
            }
        }

        return null
    }
}