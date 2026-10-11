package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
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

    // Apps verify a caller either by name (above) or by package list; answer both the same way.
    override fun getPackagesForUid(p0: Int): Array<String>? {
        if (p0 == Process.myUid()) ownUidPackageResolver?.invoke()?.let { return arrayOf(it) }
        return super.getPackagesForUid(p0)
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

    // --- intent queries: plugin answers first, then whatever the real PackageManager knows ---

    private fun queryWithPlugins(
        fromPlugin: (PackageManagerPlugin) -> List<ResolveInfo>?,
        fromBase: () -> List<ResolveInfo>
    ): MutableList<ResolveInfo> {
        val result = mutableListOf<ResolveInfo>()
        for (plugin in pluginList) fromPlugin(plugin)?.let { result.addAll(it) }
        result.addAll(fromBase())
        return result
    }

    override fun queryIntentActivities(p0: Intent, p1: Int): MutableList<ResolveInfo> =
        queryWithPlugins({ it.queryIntentActivities(p0, p1) }, { super.queryIntentActivities(p0, p1) })

    override fun queryIntentServices(p0: Intent, p1: Int): MutableList<ResolveInfo> =
        queryWithPlugins({ it.queryIntentServices(p0, p1) }, { super.queryIntentServices(p0, p1) })

    override fun queryBroadcastReceivers(p0: Intent, p1: Int): MutableList<ResolveInfo> =
        queryWithPlugins({ it.queryBroadcastReceivers(p0, p1) }, { super.queryBroadcastReceivers(p0, p1) })

    override fun resolveService(p0: Intent, p1: Int): ResolveInfo? =
        queryIntentServices(p0, p1).firstOrNull()

    override fun getLaunchIntentForPackage(p0: String): Intent? {
        for (plugin in pluginList) plugin.getLaunchIntentForPackage(p0)?.let { return it }
        return super.getLaunchIntentForPackage(p0)
    }

    @RequiresApi(33)
    override fun queryIntentActivities(p0: Intent, p1: ResolveInfoFlags): MutableList<ResolveInfo> =
        queryIntentActivities(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun queryIntentServices(p0: Intent, p1: ResolveInfoFlags): MutableList<ResolveInfo> =
        queryIntentServices(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun queryBroadcastReceivers(p0: Intent, p1: ResolveInfoFlags): MutableList<ResolveInfo> =
        queryBroadcastReceivers(p0, p1.value.toInt())

    @RequiresApi(33)
    override fun resolveService(p0: Intent, p1: ResolveInfoFlags): ResolveInfo? =
        resolveService(p0, p1.value.toInt())

    private fun ownedByPlugin(packageName: String) = pluginList.any { it.ownsPackage(packageName) }

    /** Raw signing certificates: a loaded package's from its plugin, an installed one's from the platform. */
    private fun certificatesOf(packageName: String): List<ByteArray>? {
        for (plugin in pluginList) if (plugin.ownsPackage(packageName)) return plugin.signingCertificates(packageName) ?: emptyList()
        return try {
            super.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                ?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } catch (_: NameNotFoundException) {
            null // not installed
        }
    }

    // The pre-API-30 installer check many apps still use: a loaded package was installed by nobody.
    @Suppress("DEPRECATION")
    override fun getInstallerPackageName(p0: String): String? =
        if (ownedByPlugin(p0)) null else super.getInstallerPackageName(p0)

    // A loaded app runs in the host's process, so it has the host's uid.
    override fun getPackageUid(p0: String, p1: Int): Int =
        if (ownedByPlugin(p0)) Process.myUid() else super.getPackageUid(p0, p1)

    @RequiresApi(33)
    override fun getPackageUid(p0: String, p1: PackageInfoFlags): Int = getPackageUid(p0, p1.value.toInt())

    // Own-signature checks: does this certificate sign that package? Compared against the platform's
    // verification of the loaded APK, never forged.
    override fun hasSigningCertificate(p0: String, p1: ByteArray, p2: Int): Boolean {
        val certs = if (ownedByPlugin(p0)) certificatesOf(p0) else null
        return if (certs != null) certs.any { matchesCertificate(it, p1, p2) } else super.hasSigningCertificate(p0, p1, p2)
    }

    private fun matchesCertificate(cert: ByteArray, expected: ByteArray, type: Int): Boolean =
        if (type == PackageManager.CERT_INPUT_SHA256) {
            java.security.MessageDigest.getInstance("SHA-256").digest(cert).contentEquals(expected)
        } else {
            cert.contentEquals(expected)
        }

    override fun checkSignatures(p0: String, p1: String): Int {
        if (!ownedByPlugin(p0) && !ownedByPlugin(p1)) return super.checkSignatures(p0, p1)
        val first = certificatesOf(p0) ?: return PackageManager.SIGNATURE_UNKNOWN_PACKAGE
        val second = certificatesOf(p1) ?: return PackageManager.SIGNATURE_UNKNOWN_PACKAGE
        return when {
            first.isEmpty() && second.isEmpty() -> PackageManager.SIGNATURE_NEITHER_SIGNED
            first.isEmpty() -> PackageManager.SIGNATURE_FIRST_NOT_SIGNED
            second.isEmpty() -> PackageManager.SIGNATURE_SECOND_NOT_SIGNED
            first.map { it.toList() }.toSet() == second.map { it.toList() }.toSet() -> PackageManager.SIGNATURE_MATCH
            else -> PackageManager.SIGNATURE_NO_MATCH
        }
    }

    override fun getInstallSourceInfo(p0: String): InstallSourceInfo {
        for (plugin in pluginList) plugin.getInstallSourceInfo(p0)?.let { return it }
        return super.getInstallSourceInfo(p0)
    }

    fun addPlugin(plugin: PackageManagerPlugin) {
        pluginList.add(0, plugin)
    }

    fun removePlugin(plugin: PackageManagerPlugin) {
        pluginList.remove(plugin)
    }
}