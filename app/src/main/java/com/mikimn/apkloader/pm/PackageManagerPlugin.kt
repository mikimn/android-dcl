package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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
}