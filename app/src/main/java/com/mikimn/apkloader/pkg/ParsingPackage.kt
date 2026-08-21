package com.mikimn.apkloader.pkg

import android.content.Intent
import android.content.pm.ConfigurationInfo
import android.content.pm.FeatureGroupInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.annotation.CallSuper
import java.security.PublicKey


/**
 * Methods used for mutation during direct package parsing.
 *
 * @hide
 */
interface ParsingPackage {
    fun addActivity(parsedActivity: ParsedActivity?): ParsingPackage?

    fun addAdoptPermission(adoptPermission: String?): ParsingPackage?

//    fun addApexSystemService(parsedApexSystemService: ParsedApexSystemService?): ParsingPackage?

    fun addConfigPreference(configPreference: ConfigurationInfo?): ParsingPackage?

    fun addFeatureGroup(featureGroup: FeatureGroupInfo?): ParsingPackage?

    fun addImplicitPermission(permission: String?): ParsingPackage?

//    fun addInstrumentation(instrumentation: ParsedInstrumentation?): ParsingPackage?

    fun addKeySet(keySetName: String?, publicKey: PublicKey?): ParsingPackage?

    fun addLibraryName(libraryName: String?): ParsingPackage?

    fun addOriginalPackage(originalPackage: String?): ParsingPackage?

    fun addOverlayable(overlayableName: String?, actorName: String?): ParsingPackage?

//    fun addPermission(permission: ParsedPermission?): ParsingPackage?

//    fun addPermissionGroup(permissionGroup: ParsedPermissionGroup?): ParsingPackage?

//    fun addPreferredActivityFilter(
//        className: String?,
//        intentInfo: ParsedIntentInfo?
//    ): ParsingPackage?

    /** Add a property to the application scope  */
    fun addProperty(property: PackageManager.Property?): ParsingPackage?

    fun addProtectedBroadcast(protectedBroadcast: String?): ParsingPackage?

    // fun addProvider(parsedProvider: ParsedProvider?): ParsingPackage?

    // fun addAttribution(attribution: ParsedAttribution?): ParsingPackage?

    // fun addReceiver(parsedReceiver: ParsedActivity?): ParsingPackage?

    fun addReqFeature(reqFeature: FeatureInfo?): ParsingPackage?

    // fun addUsesPermission(parsedUsesPermission: ParsedUsesPermission?): ParsingPackage?

    // fun addService(parsedService: ParsedService?): ParsingPackage?

    fun addUsesLibrary(libraryName: String?): ParsingPackage?

    fun addUsesOptionalLibrary(libraryName: String?): ParsingPackage?

    fun addUsesNativeLibrary(libraryName: String?): ParsingPackage?

    fun addUsesOptionalNativeLibrary(libraryName: String?): ParsingPackage?

    fun addUsesSdkLibrary(
        libraryName: String?, versionMajor: Long,
        certSha256Digests: Array<String?>?, usesSdkLibrariesOptional: Boolean
    ): ParsingPackage?

    fun addUsesStaticLibrary(
        libraryName: String?, version: Long,
        certSha256Digests: Array<String?>?
    ): ParsingPackage?

    fun addQueriesIntent(intent: Intent?): ParsingPackage?

    fun addQueriesPackage(packageName: String?): ParsingPackage?

    fun addQueriesProvider(authority: String?): ParsingPackage?

    /** Adds a feature flag (`android:featureFlag` attribute) encountered in the manifest.  */
    fun addFeatureFlag(flagPackageAndName: String, flagValue: Boolean?): ParsingPackage?

    // /** Sets a process name -> [ParsedProcess] map coming from the <processes> tag. </processes> */
    // fun setProcesses(processes: Map<String?, ParsedProcess?>): ParsingPackage?

    fun asSplit(
        splitNames: Array<String?>?,
        splitCodePaths: Array<String?>?,
        splitRevisionCodes: IntArray?,
        splitDependencies: SparseArray<IntArray?>?
    ): ParsingPackage?

    fun setMetaData(metaData: Bundle?): ParsingPackage?

    fun setForceQueryable(forceQueryable: Boolean): ParsingPackage?

    fun setMaxAspectRatio(maxAspectRatio: Float): ParsingPackage?

    fun setMinAspectRatio(minAspectRatio: Float): ParsingPackage?

    fun setPermission(permission: String?): ParsingPackage?

    fun setProcessName(processName: String?): ParsingPackage?

    fun setSharedUserId(sharedUserId: String?): ParsingPackage?

    fun setStaticSharedLibraryName(staticSharedLibName: String?): ParsingPackage?

    fun setTaskAffinity(taskAffinity: String?): ParsingPackage?

    fun setTargetSdkVersion(targetSdkVersion: Int): ParsingPackage?

    fun setUiOptions(uiOptions: Int): ParsingPackage?

    fun setHardwareAccelerated(hardwareAccelerated: Boolean): ParsingPackage?

    fun setResizeableActivity(resizeable: Boolean?): ParsingPackage?

    fun setResizeableActivityViaSdkVersion(resizeableViaSdkVersion: Boolean): ParsingPackage?

    fun setAllowAudioPlaybackCapture(allowAudioPlaybackCapture: Boolean): ParsingPackage?

    fun setBackupAllowed(allowBackup: Boolean): ParsingPackage?

    fun setClearUserDataAllowed(allowClearUserData: Boolean): ParsingPackage?

    fun setClearUserDataOnFailedRestoreAllowed(
        allowClearUserDataOnFailedRestore: Boolean
    ): ParsingPackage?

    fun setTaskReparentingAllowed(allowTaskReparenting: Boolean): ParsingPackage?

    fun setResourceOverlay(isResourceOverlay: Boolean): ParsingPackage?

    fun setBackupInForeground(backupInForeground: Boolean): ParsingPackage?

    fun setSaveStateDisallowed(cantSaveState: Boolean): ParsingPackage?

    fun setDebuggable(debuggable: Boolean): ParsingPackage?

    fun setDefaultToDeviceProtectedStorage(defaultToDeviceProtectedStorage: Boolean): ParsingPackage?

    fun setDirectBootAware(directBootAware: Boolean): ParsingPackage?

    fun setExternalStorage(externalStorage: Boolean): ParsingPackage?

    fun setExtractNativeLibrariesRequested(extractNativeLibs: Boolean): ParsingPackage?

    fun setFullBackupOnly(fullBackupOnly: Boolean): ParsingPackage?

    fun setDeclaredHavingCode(hasCode: Boolean): ParsingPackage?

    fun setUserDataFragile(hasFragileUserData: Boolean): ParsingPackage?

    fun setGame(isGame: Boolean): ParsingPackage?

    fun setIsolatedSplitLoading(isolatedSplitLoading: Boolean): ParsingPackage?

    fun setKillAfterRestoreAllowed(killAfterRestore: Boolean): ParsingPackage?

    fun setLargeHeap(largeHeap: Boolean): ParsingPackage?

    fun setMultiArch(multiArch: Boolean): ParsingPackage?

    fun setPartiallyDirectBootAware(partiallyDirectBootAware: Boolean): ParsingPackage?

    fun setPersistent(persistent: Boolean): ParsingPackage?

    fun setProfileableByShell(profileableByShell: Boolean): ParsingPackage?

    fun setProfileable(profileable: Boolean): ParsingPackage?

    fun setRequestLegacyExternalStorage(requestLegacyExternalStorage: Boolean): ParsingPackage?

    fun setAllowNativeHeapPointerTagging(allowNativeHeapPointerTagging: Boolean): ParsingPackage?

    fun setAutoRevokePermissions(autoRevokePermissions: Int): ParsingPackage?

    fun setPreserveLegacyExternalStorage(preserveLegacyExternalStorage: Boolean): ParsingPackage?

    fun setRestoreAnyVersion(restoreAnyVersion: Boolean): ParsingPackage?

    fun setSdkLibraryName(sdkLibName: String?): ParsingPackage?

    fun setSdkLibVersionMajor(sdkLibVersionMajor: Int): ParsingPackage?

    fun setSdkLibrary(sdkLibrary: Boolean): ParsingPackage?

    fun setSplitHasCode(splitIndex: Int, splitHasCode: Boolean): ParsingPackage?

    fun setStaticSharedLibrary(staticSharedLibrary: Boolean): ParsingPackage?

    fun setRtlSupported(supportsRtl: Boolean): ParsingPackage?

    fun setTestOnly(testOnly: Boolean): ParsingPackage?

    fun setUseEmbeddedDex(useEmbeddedDex: Boolean): ParsingPackage?

    fun setCleartextTrafficAllowed(usesCleartextTraffic: Boolean): ParsingPackage?

    fun setNonSdkApiRequested(usesNonSdkApi: Boolean): ParsingPackage?

    fun setVisibleToInstantApps(visibleToInstantApps: Boolean): ParsingPackage?

    fun setVmSafeMode(vmSafeMode: Boolean): ParsingPackage?

    fun removeUsesOptionalLibrary(libraryName: String?): ParsingPackage?

    fun removeUsesOptionalNativeLibrary(libraryName: String?): ParsingPackage?

    fun setAnyDensity(anyDensity: Int): ParsingPackage?

    fun setAppComponentFactory(appComponentFactory: String?): ParsingPackage?

    fun setBackupAgentName(backupAgentName: String?): ParsingPackage?

    fun setBannerResourceId(banner: Int): ParsingPackage?

    fun setCategory(category: Int): ParsingPackage?

    fun setClassLoaderName(classLoaderName: String?): ParsingPackage?

    fun setApplicationClassName(className: String?): ParsingPackage?

    fun setCompatibleWidthLimitDp(compatibleWidthLimitDp: Int): ParsingPackage?

    fun setDescriptionResourceId(descriptionRes: Int): ParsingPackage?

    fun setEnabled(enabled: Boolean): ParsingPackage?

    fun setGwpAsanMode(gwpAsanMode: Int): ParsingPackage?

    fun setMemtagMode(memtagMode: Int): ParsingPackage?

    fun setNativeHeapZeroInitialized(nativeHeapZeroInitialized: Int): ParsingPackage?

    /** Manifest option pageSizeCompat will populate this field  */
    fun setPageSizeAppCompatFlags(value: Int): ParsingPackage?

    fun setRequestRawExternalStorageAccess(requestRawExternalStorageAccess: Boolean?): ParsingPackage?

    fun setCrossProfile(crossProfile: Boolean): ParsingPackage?

    fun setFullBackupContentResourceId(fullBackupContentRes: Int): ParsingPackage?

    fun setDataExtractionRulesResourceId(dataExtractionRulesRes: Int): ParsingPackage?

    fun setHasDomainUrls(hasDomainUrls: Boolean): ParsingPackage?

    fun setIconResourceId(iconRes: Int): ParsingPackage?

    fun setInstallLocation(installLocation: Int): ParsingPackage?

    fun setLeavingSharedUser(leavingSharedUser: Boolean): ParsingPackage?

    fun setLabelResourceId(labelRes: Int): ParsingPackage?

    fun setLargestWidthLimitDp(largestWidthLimitDp: Int): ParsingPackage?

    fun setLogoResourceId(logo: Int): ParsingPackage?

    fun setManageSpaceActivityName(manageSpaceActivityName: String?): ParsingPackage?

    fun setMinExtensionVersions(minExtensionVersions: SparseIntArray?): ParsingPackage?

    fun setMinSdkVersion(minSdkVersion: Int): ParsingPackage?

    fun setMaxSdkVersion(maxSdkVersion: Int): ParsingPackage?

    fun setNetworkSecurityConfigResourceId(networkSecurityConfigRes: Int): ParsingPackage?

    fun setNonLocalizedLabel(nonLocalizedLabel: CharSequence?): ParsingPackage?

    fun setOverlayCategory(overlayCategory: String?): ParsingPackage?

    fun setOverlayIsStatic(overlayIsStatic: Boolean): ParsingPackage?

    fun setOverlayPriority(overlayPriority: Int): ParsingPackage?

    fun setOverlayTarget(overlayTarget: String?): ParsingPackage?

    fun setOverlayTargetOverlayableName(overlayTargetOverlayableName: String?): ParsingPackage?

    fun setRequiredAccountType(requiredAccountType: String?): ParsingPackage?

    fun setRequiredForAllUsers(requiredForAllUsers: Boolean): ParsingPackage?

    fun setRequiresSmallestWidthDp(requiresSmallestWidthDp: Int): ParsingPackage?

    fun setResizeable(resizeable: Int): ParsingPackage?

    fun setRestrictUpdateHash(restrictUpdateHash: ByteArray?): ParsingPackage?

    fun setRestrictedAccountType(restrictedAccountType: String?): ParsingPackage?

    fun setRoundIconResourceId(roundIconRes: Int): ParsingPackage?

    fun setSharedUserLabelResourceId(sharedUserLabelRes: Int): ParsingPackage?

    // fun setSigningDetails(signingDetails: SigningDetails): ParsingPackage?

    fun setSplitClassLoaderName(splitIndex: Int, classLoaderName: String?): ParsingPackage?

    fun setStaticSharedLibraryVersion(staticSharedLibraryVersion: Long): ParsingPackage?

    fun setUpdatableSystem(value: Boolean): ParsingPackage?

    /**
     * Sets a system app that is allowed to update another system app
     */
    fun setEmergencyInstaller(emergencyInstaller: String?): ParsingPackage?

    fun setLargeScreensSupported(supportsLargeScreens: Int): ParsingPackage?

    fun setNormalScreensSupported(supportsNormalScreens: Int): ParsingPackage?

    fun setSmallScreensSupported(supportsSmallScreens: Int): ParsingPackage?

    fun setExtraLargeScreensSupported(supportsExtraLargeScreens: Int): ParsingPackage?

    fun setTargetSandboxVersion(targetSandboxVersion: Int): ParsingPackage?

    fun setThemeResourceId(theme: Int): ParsingPackage?

    fun setRequestForegroundServiceExemption(requestForegroundServiceExemption: Boolean): ParsingPackage?

    fun setUpgradeKeySets(upgradeKeySets: Set<String?>): ParsingPackage?

    fun set32BitAbiPreferred(use32BitAbi: Boolean): ParsingPackage?

    fun setVolumeUuid(volumeUuid: String?): ParsingPackage?

    fun setZygotePreloadName(zygotePreloadName: String?): ParsingPackage?

    fun setAllowCrossUidActivitySwitchFromBelow(
        allowCrossUidActivitySwitchFromBelow: Boolean
    ): ParsingPackage?

    fun sortActivities(): ParsingPackage?

    fun sortReceivers(): ParsingPackage?

    fun sortServices(): ParsingPackage?

    fun setBaseRevisionCode(baseRevisionCode: Int): ParsingPackage?

    fun setVersionCode(vesionCode: Int): ParsingPackage?

    fun setVersionCodeMajor(vesionCodeMajor: Int): ParsingPackage?

    fun setVersionName(versionName: String?): ParsingPackage?

    fun setCompileSdkVersion(compileSdkVersion: Int): ParsingPackage?

    fun setCompileSdkVersionCodeName(compileSdkVersionCodeName: String?): ParsingPackage?

    fun setAttributionsAreUserVisible(attributionsAreUserVisible: Boolean): ParsingPackage?

    fun setResetEnabledSettingsOnAppDataCleared(
        resetEnabledSettingsOnAppDataCleared: Boolean
    ): ParsingPackage?

    fun setLocaleConfigResourceId(localeConfigRes: Int): ParsingPackage?

    /**
     * Sets the trusted host certificates of apps that are allowed to embed activities of this
     * application.
     */
    fun setKnownActivityEmbeddingCerts(knownActivityEmbeddingCerts: Set<String?>?): ParsingPackage?

    fun setOnBackInvokedCallbackEnabled(enableOnBackInvokedCallback: Boolean): ParsingPackage?

    /**
     * Set the drawable resources id array of the alternate icons that are parsing from the
     * AndroidManifest file
     */
    fun setAlternateLauncherIconResIds(alternateLauncherIconResIds: IntArray?): ParsingPackage?

    /**
     * Set the string resources id array of the alternate labels that are parsing from the
     * AndroidManifest file
     */
    fun setAlternateLauncherLabelResIds(alternateLauncherLabelResIds: IntArray?): ParsingPackage?

    @CallSuper
    fun hideAsParsed(): ParsedPackage?

    // The remaining methods are copied out of [AndroidPackage] so that the parsing variant does
    // not implement the final API interface and can't accidentally be used without finalizing
    // the parsing process.
    val activities: List<Any?>

    val attributions: List<Any?>

    val baseApkPath: String

    val classLoaderName: String?

    val configPreferences: List<ConfigurationInfo?>

    val instrumentations: List<Any?>

    val keySetMapping: Map<String?, Any?>

    val libraryNames: List<String?>

    val maxAspectRatio: Float

    val maxSdkVersion: Int

    val metaData: Bundle?

    val minAspectRatio: Float

    val minSdkVersion: Int

    val packageName: String?

    val permission: String?

    val permissions: List<Any?>

    val processName: String

    val providers: List<Any?>

    val receivers: List<Any?>

    val requestedPermissions: Set<String?>

    val resizeableActivity: Boolean?

    val sdkLibraryName: String?

    val services: List<Any?>

    val sharedUserId: String?

    val splitCodePaths: Array<String?>

    val splitNames: Array<String?>

    val staticSharedLibraryName: String?

    val targetSdkVersion: Int

    val taskAffinity: String?

    val uiOptions: Int

    val usesLibraries: List<String?>

    val usesNativeLibraries: List<String?>

    val usesPermissions: List<Any?>

    val usesSdkLibraries: List<String?>

    val usesSdkLibrariesVersionsMajor: LongArray?

    val usesStaticLibraries: List<String?>

    val zygotePreloadName: String?

    val isAllowCrossUidActivitySwitchFromBelow: Boolean

    val isBackupAllowed: Boolean

    val isTaskReparentingAllowed: Boolean

    val isAnyDensity: Boolean

    val isHardwareAccelerated: Boolean

    val isSaveStateDisallowed: Boolean

    val isProfileable: Boolean

    val isProfileableByShell: Boolean

    val isResizeable: Boolean

    val isResizeableActivityViaSdkVersion: Boolean

    val isStaticSharedLibrary: Boolean

    val isExtraLargeScreensSupported: Boolean

    val isLargeScreensSupported: Boolean

    val isNormalScreensSupported: Boolean

    val isSmallScreensSupported: Boolean

    fun setIntentMatchingFlags(intentMatchingFlags: Int): ParsingPackage?

    /**
     * Returns the intent matching flags.
     */
    val intentMatchingFlags: Int
}