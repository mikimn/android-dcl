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
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi

class PackageManagerAggregate(base: PackageManager, plugins: Array<PackageManagerPlugin>): PackageManagerWrapper(base) {
    private val pluginList: MutableList<PackageManagerPlugin> = plugins.toMutableList()

    /**
     * What a loaded APK should see as its own identity when it resolves our uid (this process's
     * uid is shared with the host). Apps verify "who is calling me" this way, e.g. a ContentProvider
     * that only accepts `getNameForUid(Binder.getCallingUid()) == <own package>`, and an in-process
     * call from the loaded app to its own provider would otherwise be rejected as the host.
     * Returns null when the current caller isn't the loaded APK's own code.
     */
    var ownUidPackageResolver: (() -> String?)? = null

    override fun getNameForUid(p0: Int): String? {
        if (p0 == Process.myUid()) ownUidPackageResolver?.invoke()?.let { return it }
        return super.getNameForUid(p0)
    }

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

    // API 33 added `*Flags` overloads of the lookups above. Apps targeting 33+ call those, and the
    // base-class wrapper would hand them straight to the real PackageManager, silently bypassing
    // every plugin (e.g. ManifestAwarePlugin). Route them through the Int-flag versions, which is
    // what the framework itself does. `flags.value` is a Long; `.toInt()` deliberately truncates
    // it to the low 32 bits exactly like the framework's own `(int) flags.getValue()`, so any
    // flag above bit 31 is dropped here as it would be there.
    @RequiresApi(33)
    override fun getActivityInfo(p0: ComponentName, p1: ComponentInfoFlags): ActivityInfo =
        getActivityInfo(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun getReceiverInfo(p0: ComponentName, p1: ComponentInfoFlags): ActivityInfo =
        getReceiverInfo(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun getServiceInfo(p0: ComponentName, p1: ComponentInfoFlags): ServiceInfo =
        getServiceInfo(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun getProviderInfo(p0: ComponentName, p1: ComponentInfoFlags): ProviderInfo =
        getProviderInfo(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun getApplicationInfo(p0: String, p1: ApplicationInfoFlags): ApplicationInfo =
        getApplicationInfo(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun getPackageInfo(packageName: String, flags: PackageInfoFlags): PackageInfo =
        getPackageInfo(packageName, flags.value.toInt())

    @RequiresApi(33)
    override fun resolveActivity(intent: Intent, flags: ResolveInfoFlags): ResolveInfo? =
        resolveActivity(intent, flags.value.toInt())

    fun addPlugin(plugin: PackageManagerPlugin) {
        pluginList.add(0, plugin)
    }

    fun removePlugin(plugin: PackageManagerPlugin) {
        pluginList.remove(plugin)
    }
}