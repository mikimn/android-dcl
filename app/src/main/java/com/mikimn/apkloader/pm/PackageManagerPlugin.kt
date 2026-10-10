package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo

interface PackageManagerPlugin {
    fun getActivityInfo(component: ComponentName, flags: Int): ActivityInfo

    fun getReceiverInfo(component: ComponentName, flags: Int): ActivityInfo

    fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo

    fun getProviderInfo(component: ComponentName, flags: Int): ProviderInfo

    fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo

    fun getPackageInfo(packageName: String, flags: Int): PackageInfo

    fun resolveActivity(intent: Intent, flags: Int): ResolveInfo?

    // The query methods below return null when this plugin has no opinion (the aggregate then
    // consults the next plugin / the real PackageManager). A non-null list, even an empty one,
    // is this plugin's answer, and is merged with the real PackageManager's results.

    fun queryIntentActivities(intent: Intent, flags: Int): List<ResolveInfo>? = null

    fun queryIntentServices(intent: Intent, flags: Int): List<ResolveInfo>? = null

    fun queryBroadcastReceivers(intent: Intent, flags: Int): List<ResolveInfo>? = null

    fun getLaunchIntentForPackage(packageName: String): Intent? = null

    /** Where [packageName] was installed from; null when this plugin has no opinion. */
    fun getInstallSourceInfo(packageName: String): InstallSourceInfo? = null
}
