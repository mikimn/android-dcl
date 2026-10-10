package com.mikimn.apkloader.apk

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
        return reader.getReceiverInfo(component) ?: throw NameNotFoundException(component.className)
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
        if (aInfo.packageName != packageName) throw NameNotFoundException(packageName)

        return PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = aInfo
            versionName = reader.getVersionName()
            longVersionCode = reader.getLongVersionCode()
            if (flags and PackageManager.GET_PERMISSIONS != 0) {
                requestedPermissions = reader.getRequestedPermissions().toTypedArray()
            }
            if (flags and PackageManager.GET_ACTIVITIES != 0) {
                activities = reader.parseActivities().map { it.first }.toTypedArray() // real <activity> elements only
            }
            if (flags and PackageManager.GET_RECEIVERS != 0) {
                receivers = reader.parseReceivers().map { it.first }.toTypedArray()
            }
            if (flags and PackageManager.GET_SERVICES != 0) {
                services = reader.getServices().toTypedArray()
            }
            if (flags and PackageManager.GET_PROVIDERS != 0) {
                providers = reader.getProviders().toTypedArray()
            }
        }
    }

    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? =
        queryIntentActivities(intent, flags)?.firstOrNull()

    override fun queryIntentActivities(intent: Intent, flags: Int): List<ResolveInfo> =
        ComponentMatcher.resolve(aInfo.packageName, reader.parseActivitiesAndAliases(), intent, flags) { info ->
            ResolveInfo().apply { activityInfo = info }
        }

    override fun queryBroadcastReceivers(intent: Intent, flags: Int): List<ResolveInfo> =
        ComponentMatcher.resolve(aInfo.packageName, reader.parseReceivers(), intent, flags) { info ->
            ResolveInfo().apply { activityInfo = info }
        }

    override fun queryIntentServices(intent: Intent, flags: Int): List<ResolveInfo> =
        ComponentMatcher.resolve(aInfo.packageName, reader.parseServices(), intent, flags) { info ->
            ResolveInfo().apply { serviceInfo = info }
        }

    override fun getLaunchIntentForPackage(packageName: String): Intent? {
        if (aInfo.packageName != packageName) return null
        val launcher = reader.getLauncherActivity() ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(packageName, launcher.name)
    }
}
