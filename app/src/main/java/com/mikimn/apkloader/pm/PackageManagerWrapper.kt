package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ChangedPackages
import android.content.pm.FeatureInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.InstrumentationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.SharedLibraryInfo
import android.content.pm.VersionedPackage
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import com.mikimn.apkloader.reflection.tryGetMethod

open class PackageManagerWrapper(private val basePM: PackageManager) : PackageManager() {
    private inline fun logCurrentMethod(vararg args: Any) {
        val m = Thread.currentThread().stackTrace.filter { !it.methodName.lowercase().contains("trace") }[0]
        Log.d("DCLContext", "INVOKE ${m.methodName}(${args.joinToString(", ")})")
    }

    // Intentional
    // TODO: Why is this here?
    @Suppress("unused")
    fun getPermissionControllerPackageName(): String {
        val ret = basePM.javaClass.tryGetMethod("getPermissionControllerPackageName")?.invoke(basePM)
        return (ret ?: "") as String
    }

    // PackageItemInfo.loadIcon()/loadUnbadgedIcon() call this internally
    // (PackageManager.loadItemIcon/loadUnbadgedItemIcon) - it's a hidden/system API absent from
    // the compileSdk stub, so PackageManagerWrapper (extends the real, abstract PackageManager)
    // was never forced to implement it, and nothing exercised it until the main screen started
    // rendering real app icons via ApplicationInfo.loadIcon(). Without this, any such call hits
    // the framework's own abstract method with no override -> AbstractMethodError. Same fix
    // shape as getPermissionControllerPackageName above: reflect it onto the real PackageManager.
    @Suppress("unused")
    fun loadItemIcon(itemInfo: PackageItemInfo, appInfo: ApplicationInfo?): Drawable? {
        logCurrentMethod(itemInfo, appInfo ?: "null")
        val method = basePM.javaClass.tryGetMethod(
            "loadItemIcon", PackageItemInfo::class.java, ApplicationInfo::class.java
        )
        return method?.invoke(basePM, itemInfo, appInfo) as? Drawable
    }

    // PackageManager's own default implementation just throws
    // UnsupportedOperationException("getInstallSourceInfo not implemented") - unlike
    // loadItemIcon above, this one *is* a normal public SDK method (added API 30), just never
    // overridden here, so any caller (e.g. Google Mobile Ads' fraud-signal collection, which
    // crashed loading an interstitial ad in a real installed app) hits that stub instead of
    // the real PackageManager.
    override fun getInstallSourceInfo(packageName: String): InstallSourceInfo {
        logCurrentMethod(packageName)
        return basePM.getInstallSourceInfo(packageName)
    }

    override fun getActivityInfo(p0: ComponentName, p1: Int): ActivityInfo {
        logCurrentMethod(p0, p1)
        return basePM.getActivityInfo(p0, p1)
    }

    override fun getReceiverInfo(p0: ComponentName, p1: Int): ActivityInfo {
        logCurrentMethod()
        return basePM.getReceiverInfo(p0, p1)
    }

    override fun getServiceInfo(p0: ComponentName, p1: Int): ServiceInfo {
        logCurrentMethod(p0, p1)
        return basePM.getServiceInfo(p0, p1)
    }

    override fun getProviderInfo(p0: ComponentName, p1: Int): ProviderInfo {
        logCurrentMethod(p0, p1)
        return basePM.getProviderInfo(p0, p1)
    }

    override fun getInstalledPackages(p0: Int): MutableList<PackageInfo> {
        logCurrentMethod()
        return basePM.getInstalledPackages(p0)
    }

    override fun getPackagesHoldingPermissions(
        p0: Array<out String>,
        p1: Int
    ): MutableList<PackageInfo> {
        logCurrentMethod()
        return basePM.getPackagesHoldingPermissions(p0, p1)
    }

    override fun checkPermission(p0: String, p1: String): Int {
        logCurrentMethod(p0, p1)
        return basePM.checkPermission(p0, p1)
    }

    override fun isPermissionRevokedByPolicy(p0: String, p1: String): Boolean {
        logCurrentMethod()
        return basePM.isPermissionRevokedByPolicy(p0, p1)
    }

    override fun addPermission(p0: PermissionInfo): Boolean {
        logCurrentMethod()
        return basePM.addPermission(p0)
    }

    override fun addPermissionAsync(p0: PermissionInfo): Boolean {
        logCurrentMethod()
        return basePM.addPermissionAsync(p0)
    }

    override fun removePermission(p0: String) {
        logCurrentMethod()
        return basePM.removePermission(p0)
    }

    override fun checkSignatures(p0: String, p1: String): Int {
        logCurrentMethod()
        return basePM.checkSignatures(p0, p1)
    }

    override fun checkSignatures(p0: Int, p1: Int): Int {
        logCurrentMethod()
        return basePM.checkSignatures(p0, p1)
    }

    override fun getPackagesForUid(p0: Int): Array<String>? {
        logCurrentMethod()
        return basePM.getPackagesForUid(p0)
    }

    override fun getNameForUid(p0: Int): String? {
        logCurrentMethod()
        return basePM.getNameForUid(p0)
    }

    override fun getInstalledApplications(p0: Int): MutableList<ApplicationInfo> {
        logCurrentMethod()
        return basePM.getInstalledApplications(p0)
    }

    override fun isInstantApp(): Boolean {
        logCurrentMethod()
        return basePM.isInstantApp
    }

    override fun isInstantApp(p0: String): Boolean {
        logCurrentMethod()
        return basePM.isInstantApp(p0)
    }

    override fun getInstantAppCookieMaxBytes(): Int {
        logCurrentMethod()
        return basePM.instantAppCookieMaxBytes
    }

    override fun getInstantAppCookie(): ByteArray {
        logCurrentMethod()
        return basePM.instantAppCookie
    }

    override fun clearInstantAppCookie() {
        logCurrentMethod()
        return basePM.clearInstantAppCookie()
    }

    override fun updateInstantAppCookie(p0: ByteArray?) {
        logCurrentMethod()
        return basePM.updateInstantAppCookie(p0)
    }

    override fun getSystemSharedLibraryNames(): Array<String>? {
        logCurrentMethod()
        return basePM.systemSharedLibraryNames
    }

    override fun getSharedLibraries(p0: Int): MutableList<SharedLibraryInfo> {
        logCurrentMethod()
        return basePM.getSharedLibraries(p0)
    }

    override fun getChangedPackages(p0: Int): ChangedPackages? {
        logCurrentMethod()
        return basePM.getChangedPackages(p0)
    }

    override fun getSystemAvailableFeatures(): Array<FeatureInfo> {
        logCurrentMethod()
        return basePM.systemAvailableFeatures
    }

    override fun hasSystemFeature(p0: String): Boolean {
        logCurrentMethod(p0)
        return basePM.hasSystemFeature(p0)
    }

    override fun hasSystemFeature(p0: String, p1: Int): Boolean {
        logCurrentMethod(p0, p1)
        return basePM.hasSystemFeature(p0, p1)
    }

    override fun resolveActivity(p0: Intent, p1: Int): ResolveInfo? {
        Log.d("DCLContext", "resolveActivity($p0, $p1)")
        Log.d("DCLContext", Thread.currentThread().stackTrace.joinToString("\n"))

        return basePM.resolveActivity(p0, p1)
    }

    override fun queryIntentActivities(p0: Intent, p1: Int): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentActivities(p0, p1)
    }

    override fun queryIntentActivityOptions(
        p0: ComponentName?,
        p1: Array<out Intent>?,
        p2: Intent,
        p3: Int
    ): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentActivityOptions(p0, p1, p2, p3)
    }

    override fun queryBroadcastReceivers(p0: Intent, p1: Int): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryBroadcastReceivers(p0, p1)
    }

    override fun resolveService(p0: Intent, p1: Int): ResolveInfo? {
        logCurrentMethod(p0, p1)
        return basePM.resolveService(p0, p1)
    }

    override fun queryIntentServices(p0: Intent, p1: Int): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentServices(p0, p1)
    }

    override fun queryIntentContentProviders(p0: Intent, p1: Int): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentContentProviders(p0, p1)
    }

    override fun resolveContentProvider(p0: String, p1: Int): ProviderInfo? {
        logCurrentMethod(p0, p1)
        return basePM.resolveContentProvider(p0, p1)
    }

    override fun queryContentProviders(
        p0: String?,
        p1: Int,
        p2: Int
    ): MutableList<ProviderInfo> {
        logCurrentMethod()
        return basePM.queryContentProviders(p0, p1, p2)
    }

    override fun getInstrumentationInfo(p0: ComponentName, p1: Int): InstrumentationInfo {
        logCurrentMethod()
        return basePM.getInstrumentationInfo(p0, p1)
    }

    override fun queryInstrumentation(p0: String, p1: Int): MutableList<InstrumentationInfo> {
        logCurrentMethod()
        return basePM.queryInstrumentation(p0, p1)
    }

    override fun getDrawable(p0: String, p1: Int, p2: ApplicationInfo?): Drawable? {
        logCurrentMethod()
        return basePM.getDrawable(p0, p1, p2)
    }

    override fun getActivityIcon(p0: ComponentName): Drawable {
        logCurrentMethod()
        return basePM.getActivityIcon(p0)
    }

    override fun getActivityIcon(p0: Intent): Drawable {
        logCurrentMethod()
        return basePM.getActivityIcon(p0)
    }

    override fun getActivityBanner(p0: ComponentName): Drawable? {
        logCurrentMethod()
        return basePM.getActivityBanner(p0)
    }

    override fun getActivityBanner(p0: Intent): Drawable? {
        logCurrentMethod()
        return basePM.getActivityBanner(p0)
    }

    override fun getDefaultActivityIcon(): Drawable {
        logCurrentMethod()
        return basePM.defaultActivityIcon
    }

    override fun getApplicationIcon(p0: ApplicationInfo): Drawable {
        logCurrentMethod()
        return basePM.getApplicationIcon(p0)
    }

    override fun getApplicationIcon(p0: String): Drawable {
        logCurrentMethod()
        return basePM.getApplicationIcon(p0)
    }

    override fun getApplicationBanner(p0: ApplicationInfo): Drawable? {
        logCurrentMethod()
        return basePM.getApplicationBanner(p0)
    }

    override fun getApplicationBanner(p0: String): Drawable? {
        logCurrentMethod()
        return basePM.getApplicationBanner(p0)
    }

    override fun getActivityLogo(p0: ComponentName): Drawable? {
        logCurrentMethod()
        return basePM.getActivityLogo(p0)
    }

    override fun getActivityLogo(p0: Intent): Drawable? {
        logCurrentMethod()
        return basePM.getActivityLogo(p0)
    }

    override fun getApplicationLogo(p0: ApplicationInfo): Drawable? {
        logCurrentMethod()
        return basePM.getApplicationLogo(p0)
    }

    override fun getApplicationLogo(p0: String): Drawable? {
        logCurrentMethod()
        return basePM.getApplicationLogo(p0)
    }

    override fun getUserBadgedIcon(p0: Drawable, p1: UserHandle): Drawable {
        logCurrentMethod()
        return basePM.getUserBadgedIcon(p0, p1)
    }

    override fun getUserBadgedDrawableForDensity(
        p0: Drawable,
        p1: UserHandle,
        p2: Rect?,
        p3: Int
    ): Drawable {
        logCurrentMethod()
        return basePM.getUserBadgedDrawableForDensity(p0, p1, p2, p3)
    }

    override fun getUserBadgedLabel(p0: CharSequence, p1: UserHandle): CharSequence {
        logCurrentMethod()
        return basePM.getUserBadgedLabel(p0, p1)
    }

    override fun getText(p0: String, p1: Int, p2: ApplicationInfo?): CharSequence? {
        logCurrentMethod()
        return basePM.getText(p0, p1, p2)
    }

    override fun getXml(p0: String, p1: Int, p2: ApplicationInfo?): XmlResourceParser? {
        logCurrentMethod()
        return basePM.getXml(p0, p1, p2)
    }

    override fun getApplicationLabel(p0: ApplicationInfo): CharSequence {
        logCurrentMethod()
        return basePM.getApplicationLabel(p0)
    }

    override fun getResourcesForActivity(p0: ComponentName): Resources {
        logCurrentMethod()
        return basePM.getResourcesForActivity(p0)
    }

    override fun getResourcesForApplication(p0: ApplicationInfo): Resources {
        logCurrentMethod()
        return basePM.getResourcesForApplication(p0)
    }

    override fun getResourcesForApplication(p0: String): Resources {
        logCurrentMethod()
        return basePM.getResourcesForApplication(p0)
    }

    override fun verifyPendingInstall(p0: Int, p1: Int) {
        logCurrentMethod()
        return basePM.verifyPendingInstall(p0, p1)
    }

    override fun extendVerificationTimeout(p0: Int, p1: Int, p2: Long) {
        logCurrentMethod()
        return basePM.extendVerificationTimeout(p0, p1, p2)
    }

    override fun setInstallerPackageName(p0: String, p1: String?) {
        logCurrentMethod()
        return basePM.setInstallerPackageName(p0, p1)
    }

    override fun getInstallerPackageName(p0: String): String? {
        logCurrentMethod()
        return basePM.getInstallerPackageName(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun addPackageToPreferred(p0: String) {
        return basePM.addPackageToPreferred(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun removePackageFromPreferred(p0: String) {
        return basePM.removePackageFromPreferred(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun getPreferredPackages(p0: Int): MutableList<PackageInfo> {
        return basePM.getPreferredPackages(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun addPreferredActivity(
        p0: IntentFilter,
        p1: Int,
        p2: Array<out ComponentName>?,
        p3: ComponentName
    ) {
        return basePM.addPreferredActivity(p0, p1, p2, p3)
    }

    @Deprecated("Deprecated in Java")
    override fun clearPackagePreferredActivities(p0: String) {
        return basePM.clearPackagePreferredActivities(p0)
    }

    @Deprecated("Deprecated in Java")
    override fun getPreferredActivities(
        p0: MutableList<IntentFilter>,
        p1: MutableList<ComponentName>,
        p2: String?
    ): Int {
        return basePM.getPreferredActivities(p0, p1, p2)
    }

    override fun setComponentEnabledSetting(p0: ComponentName, p1: Int, p2: Int) {
        logCurrentMethod()
        // return basePM.setComponentEnabledSetting(p0, p1, p2)
    }

    override fun getComponentEnabledSetting(p0: ComponentName): Int {
        logCurrentMethod()
        return PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        // return basePM.getComponentEnabledSetting(p0)
    }

    override fun setApplicationEnabledSetting(p0: String, p1: Int, p2: Int) {
        logCurrentMethod()
        return basePM.setApplicationEnabledSetting(p0, p1, p2)
    }

    override fun getApplicationEnabledSetting(p0: String): Int {
        logCurrentMethod()
        return basePM.getApplicationEnabledSetting(p0)
    }

    override fun isSafeMode(): Boolean {
        logCurrentMethod()
        return basePM.isSafeMode
    }

    override fun setApplicationCategoryHint(p0: String, p1: Int) {
        logCurrentMethod()
        return basePM.setApplicationCategoryHint(p0, p1)
    }

    override fun getPackageInstaller(): PackageInstaller {
        logCurrentMethod()
        return basePM.packageInstaller
    }

    override fun canRequestPackageInstalls(): Boolean {
        logCurrentMethod()
        return basePM.canRequestPackageInstalls()
    }

    override fun getPackageInfo(p0: String, p1: Int): PackageInfo {
        logCurrentMethod(p0, p1)
//        if (p0 == "com.google.android.gms") {
//            throw NameNotFoundException()
//        }

        return basePM.getPackageInfo(p0, p1).apply {
            applicationInfo = getApplicationInfo(p0, p1)
        }
    }

    @RequiresApi(33)
    override fun getPackageInfo(packageName: String, flags: PackageInfoFlags): PackageInfo {
        logCurrentMethod()
        return basePM.getPackageInfo(packageName, flags)
    }

    override fun getPackageInfo(p0: VersionedPackage, p1: Int): PackageInfo {
        logCurrentMethod()
        return basePM.getPackageInfo(p0, p1)
    }

    @RequiresApi(33)
    override fun getPackageInfo(
        versionedPackage: VersionedPackage,
        flags: PackageInfoFlags
    ): PackageInfo {
        logCurrentMethod()
        return basePM.getPackageInfo(versionedPackage, flags)
    }

    override fun currentToCanonicalPackageNames(p0: Array<out String>): Array<String> {
        logCurrentMethod()
        return basePM.currentToCanonicalPackageNames(p0)
    }

    override fun canonicalToCurrentPackageNames(p0: Array<out String>): Array<String> {
        logCurrentMethod()
        return basePM.canonicalToCurrentPackageNames(p0)
    }

    override fun getLaunchIntentForPackage(p0: String): Intent? {
        logCurrentMethod()
        return basePM.getLaunchIntentForPackage(p0)
    }

    override fun getLeanbackLaunchIntentForPackage(p0: String): Intent? {
        logCurrentMethod()
        return basePM.getLeanbackLaunchIntentForPackage(p0)
    }

    override fun getPackageGids(p0: String): IntArray {
        logCurrentMethod()
        return basePM.getPackageGids(p0)
    }

    override fun getPackageGids(p0: String, p1: Int): IntArray {
        logCurrentMethod()
        return basePM.getPackageGids(p0, p1)
    }

    override fun getPackageUid(p0: String, p1: Int): Int {
        logCurrentMethod()
        return basePM.getPackageUid(p0, p1)
    }

    override fun getPermissionInfo(p0: String, p1: Int): PermissionInfo {
        logCurrentMethod()
        return basePM.getPermissionInfo(p0, p1)
    }

    override fun queryPermissionsByGroup(p0: String?, p1: Int): MutableList<PermissionInfo> {
        logCurrentMethod()
        return basePM.queryPermissionsByGroup(p0, p1)
    }

    override fun getPermissionGroupInfo(p0: String, p1: Int): PermissionGroupInfo {
        logCurrentMethod()
        return basePM.getPermissionGroupInfo(p0, p1)
    }

    override fun getAllPermissionGroups(p0: Int): MutableList<PermissionGroupInfo> {
        logCurrentMethod()
        return basePM.getAllPermissionGroups(p0)
    }

    override fun getApplicationInfo(p0: String, p1: Int): ApplicationInfo {
        logCurrentMethod(p0, p1)
        return basePM.getApplicationInfo(p0, p1)
    }

    @RequiresApi(31)
    override fun queryActivityProperty(propertyName: String): MutableList<Property> {
        logCurrentMethod()
        return basePM.queryActivityProperty(propertyName)
    }

    @RequiresApi(33)
    override fun getReceiverInfo(
        component: ComponentName,
        flags: ComponentInfoFlags
    ): ActivityInfo {
        logCurrentMethod()
        return basePM.getReceiverInfo(component, flags)
    }

    @RequiresApi(33)
    override fun getInstalledPackages(flags: PackageInfoFlags): MutableList<PackageInfo> {
        logCurrentMethod()
        return basePM.getInstalledPackages(flags)
    }

    @RequiresApi(33)
    override fun resolveActivity(intent: Intent, flags: ResolveInfoFlags): ResolveInfo? {
        logCurrentMethod()
        return basePM.resolveActivity(intent, flags)
    }

    @RequiresApi(33)
    override fun queryIntentActivities(
        intent: Intent,
        flags: ResolveInfoFlags
    ): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentActivities(intent, flags)
    }

    @RequiresApi(33)
    override fun queryIntentActivityOptions(
        caller: ComponentName?,
        specifics: MutableList<Intent>?,
        intent: Intent,
        flags: ResolveInfoFlags
    ): MutableList<ResolveInfo> {
        logCurrentMethod()
        return basePM.queryIntentActivityOptions(caller, specifics, intent, flags)
    }
}