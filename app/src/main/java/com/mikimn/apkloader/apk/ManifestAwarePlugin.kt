package com.mikimn.apkloader.apk

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
import android.util.Log
import com.mikimn.apkloader.pm.PackageManagerPlugin
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Answers `PackageManager` queries about a loaded (not installed) package from its parsed manifest.
 *
 * @param archiveInfo supplies the APK's signing data (`PackageInfo.signatures` / `signingInfo`),
 * which only the platform can verify from the APK file itself: typically
 * `pm.getPackageArchiveInfo(apkPath, GET_SIGNING_CERTIFICATES or GET_SIGNATURES)`. Evaluated
 * lazily, at most once, and only when a caller asks for signatures; null or a failure means
 * "unknown" and the fields stay unset.
 */
class ManifestAwarePlugin(
    private val reader: AndroidManifestReader,
    archiveInfo: () -> PackageInfo? = { null }
) : PackageManagerPlugin {
    private val aInfo: ApplicationInfo = reader.getApplicationInfo()
    private val archive: PackageInfo? by lazy { runCatching(archiveInfo).getOrNull() }

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
            // Apps check their own signature (anti-tamper, "is this the genuine app?"); without it
            // they see none and typically refuse to run (the suspected cause of Meme Generator's
            // "Security error" dialog).
            @Suppress("DEPRECATION")
            if (flags and PackageManager.GET_SIGNATURES != 0) {
                signatures = archive?.signatures
            }
            if (flags and PackageManager.GET_SIGNING_CERTIFICATES != 0) {
                signingInfo = archive?.signingInfo
            }
        }
    }

    override fun ownsPackage(packageName: String): Boolean = aInfo.packageName == packageName

    override fun signingCertificates(packageName: String): List<ByteArray>? {
        if (aInfo.packageName != packageName) return null
        val info = archive ?: return null
        @Suppress("DEPRECATION")
        val signers = info.signingInfo?.apkContentsSigners ?: info.signatures
        return signers?.map { it.toByteArray() }?.takeIf { it.isNotEmpty() }
    }

    /**
     * An honest "no installer": a loaded package was not installed by anyone, so initiating,
     * originating and installing packages are all null. It must never claim a store (e.g.
     * `com.android.vending`), which would be lying to installer-verification checks (#31). Returns
     * null (falls through to the real PackageManager, which throws NameNotFoundException) if the
     * hidden constructor is not available on this Android version. The constructor trick (widest
     * declared constructor, null/0/false arguments) was verified on API 30 only; the arity is known to
     * differ on later releases, which is why failure is a fall-through and not an error.
     */
    override fun getInstallSourceInfo(packageName: String): InstallSourceInfo? =
        if (aInfo.packageName == packageName) noInstallSource else null

    private val noInstallSource: InstallSourceInfo? by lazy {
        try {
            // The constructor is hidden and its arity grew across releases (4 args on 30, more on 34+):
            // take the widest one and pass null/0/false for everything.
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/InstallSourceInfo;")
            val ctor = InstallSourceInfo::class.java.declaredConstructors.maxByOrNull { it.parameterCount }!!
            ctor.isAccessible = true
            val args = ctor.parameterTypes.map {
                when (it) {
                    Int::class.javaPrimitiveType -> 0
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }
            ctor.newInstance(*args.toTypedArray()) as InstallSourceInfo
        } catch (e: Throwable) {
            Log.w("ManifestAwarePlugin", "Cannot construct InstallSourceInfo on this Android version", e)
            null
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
