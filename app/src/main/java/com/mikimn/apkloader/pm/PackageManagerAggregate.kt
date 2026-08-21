package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.util.Log

class PackageManagerAggregate(base: PackageManager, plugins: Array<PackageManagerPlugin>): PackageManagerWrapper(base) {
    private val pluginList: MutableList<PackageManagerPlugin> = plugins.toMutableList()

    override fun getActivityInfo(p0: ComponentName, p1: Int): ActivityInfo {
        for (plugin in pluginList) {
            try {
                return plugin.getActivityInfo(p0, p1)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }
        return super.getActivityInfo(p0, p1)
    }

    override fun getReceiverInfo(p0: ComponentName, p1: Int): ActivityInfo {
        for (plugin in pluginList) {
            try {
                return plugin.getReceiverInfo(p0, p1)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }
        return super.getReceiverInfo(p0, p1)
    }

    override fun getServiceInfo(p0: ComponentName, p1: Int): ServiceInfo {
        for (plugin in pluginList) {
            try {
                return plugin.getServiceInfo(p0, p1)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }
        return super.getServiceInfo(p0, p1)
    }

    override fun getProviderInfo(p0: ComponentName, p1: Int): ProviderInfo {
        for (plugin in pluginList) {
            try {
                return plugin.getProviderInfo(p0, p1)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }
        return super.getProviderInfo(p0, p1)
    }

    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
        Log.e("PackageManagerAggregate", "getApplicationInfo($packageName, $flags)")
        for (plugin in pluginList) {
            try {
                return plugin.getApplicationInfo(packageName, flags)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }

        return super.getApplicationInfo(packageName, flags)
    }

    override fun getPackageInfo(p0: String, p1: Int): PackageInfo {
        for (plugin in pluginList) {
            try {
                return plugin.getPackageInfo(p0, p1)
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }

        return super.getPackageInfo(p0, p1)
    }

    override fun resolveActivity(p0: Intent, p1: Int): ResolveInfo? {
        for (plugin in pluginList) {
            try {
                return plugin.resolveActivity(p0, p1) ?: throw NameNotFoundException()
            } catch (_: NameNotFoundException) {
                // blank on purpose
            }
        }

        return super.resolveActivity(p0, p1)
    }

    fun addPlugin(plugin: PackageManagerPlugin) {
        pluginList.add(0, plugin)
    }

    fun removePlugin(plugin: PackageManagerPlugin) {
        pluginList.remove(plugin)
    }
}