package com.mikimn.apkloader.pkg

import android.R
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ConfigurationInfo
import android.content.pm.FeatureGroupInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.storage.StorageManager
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.DP
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.annotation.XmlRes
import androidx.compose.runtime.Immutable
import java.util.UUID


/**
 * The representation of an application on disk, as parsed from its split APKs' manifests.
 *
 * Metadata available here is mostly device-state independent and indicates what the application
 * author declared for their app.
 *
 * This is the system server in-process API equivalent of the public API [ApplicationInfo].
 * Note that because [ApplicationInfo] is stateful, several methods that exist on it may not
 * be available here and need to be read through [PackageState] or [PackageUserState].
 *
 * All instances of [AndroidPackage] are associated with a [PackageState], and the
 * only way to retrieve one is through [PackageState]. Note that the inverse does not apply
 * and [AndroidPackage] may be null in several cases. See
 * [PackageState.getAndroidPackage].
 *
 * The data available here is immutable and will throw [UnsupportedOperationException] if any
 * collection type is mutated.
 *
 * @hide
 */
// @SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
interface AndroidPackage {
    val alternateLauncherIconResIds: IntArray?

    val alternateLauncherLabelResIds: IntArray?

    val applicationClassName: String?

    val appComponentFactory: String?

    val backupAgentName: String?

    @get:DrawableRes
    val bannerResourceId: Int

    /**
     * @see PackageInfo.baseRevisionCode
     *
     * @see R.styleable.AndroidManifest_revisionCode
     */
    val baseRevisionCode: Int

    /**
     * @see ApplicationInfo.category
     *
     * @see R.styleable.AndroidManifestApplication_appCategory
     */
    val category: Int

    val classLoaderName: String?

    @get:Dimension(unit = DP)
    val compatibleWidthLimitDp: Int

    @get:XmlRes
    val dataExtractionRulesResourceId: Int

    @get:StringRes
    val descriptionResourceId: Int

    @get:XmlRes
    val fullBackupContentResourceId: Int

    // @get:ApplicationInfo.GwpAsanMode
    val gwpAsanMode: Int

    @get:DrawableRes
    val iconResourceId: Int

    @get:StringRes
    val labelResourceId: Int

    @get:Dimension(unit = DP)
    val largestWidthLimitDp: Int

    /**
     * Library names this package is declared as, for use by other packages with "uses-library".
     *
     * @see R.styleable.AndroidManifestLibrary
     */
    val libraryNames: List<String?>

    @get:DrawableRes
    val logoResourceId: Int

    @get:XmlRes
    val localeConfigResourceId: Int

    /**
     * @see PackageInfo.getLongVersionCode
     * @see R.styleable.AndroidManifest_versionCode
     *
     * @see R.styleable.AndroidManifest_versionCodeMajor
     */
    val longVersionCode: Long

    /**
     * @see ApplicationInfo.maxAspectRatio
     *
     * @see R.styleable.AndroidManifestApplication_maxAspectRatio
     */
    val maxAspectRatio: Float

    /**
     * @see ApplicationInfo.minAspectRatio
     *
     * @see R.styleable.AndroidManifestApplication_minAspectRatio
     */
    val minAspectRatio: Float

    // @get:ApplicationInfo.NativeHeapZeroInitialized
    val nativeHeapZeroInitialized: Int

    @get:XmlRes
    val networkSecurityConfigResourceId: Int

    val requiredAccountType: String?

    @get:Dimension(unit = DP)
    val requiresSmallestWidthDp: Int

    val restrictedAccountType: String?

    val emergencyInstaller: String?

    @get:DrawableRes
    val roundIconResourceId: Int

    val sdkLibraryName: String?

    val sharedUserId: String?

    @get:StringRes
    val sharedUserLabelResourceId: Int

    /**
     * @return List of all splits for a package. Note that base.apk is considered a
     * split and will be provided as index 0 of the list.
     */
    val splits: List<Any?>

    val staticSharedLibraryName: String?

    /**
     * @see R.styleable.AndroidManifestStaticLibrary_version
     *
     * @hide
     */
    val staticSharedLibraryVersion: Long

    /**
     * @return The [UUID] for use with [StorageManager] APIs identifying where this
     * package was installed.
     */
    val storageUuid: UUID

    /**
     * @see ApplicationInfo.targetSdkVersion
     *
     * @see R.styleable.AndroidManifestUsesSdk_targetSdkVersion
     */
    val targetSdkVersion: Int

    @get:StyleRes
    val themeResourceId: Int

    /**
     * @see ApplicationInfo.uiOptions
     *
     * @see R.styleable.AndroidManifestApplication_uiOptions
     */
    val uiOptions: Int

    val versionName: String?

    val zygotePreloadName: String?

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE
     *
     * @see R.styleable.AndroidManifestApplication_allowAudioPlaybackCapture
     */
    val isAllowAudioPlaybackCapture: Boolean

    /**
     * @see ApplicationInfo.FLAG_ALLOW_BACKUP
     *
     * @see R.styleable.AndroidManifestApplication_allowBackup
     */
    val isBackupAllowed: Boolean

    /**
     * @see ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA
     *
     * @see R.styleable.AndroidManifestApplication_allowClearUserData
     */
    val isClearUserDataAllowed: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE
     *
     * @see R.styleable.AndroidManifestApplication_allowClearUserDataOnFailedRestore
     */
    val isClearUserDataOnFailedRestoreAllowed: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING
     *
     * @see R.styleable.AndroidManifestApplication_allowNativeHeapPointerTagging
     */
    val isAllowNativeHeapPointerTagging: Boolean

    /**
     * @see ApplicationInfo.FLAG_ALLOW_TASK_REPARENTING
     *
     * @see R.styleable.AndroidManifestApplication_allowTaskReparenting
     */
    val isTaskReparentingAllowed: Boolean

    /**
     * If omitted from manifest, returns true if [.getTargetSdkVersion] >= [ ][android.os.Build.VERSION_CODES.DONUT].
     *
     * @see R.styleable.AndroidManifestSupportsScreens_anyDensity
     *
     * @see ApplicationInfo.FLAG_SUPPORTS_SCREEN_DENSITIES
     */
    val isAnyDensity: Boolean

    /**
     * @see ApplicationInfo.areAttributionsUserVisible
     * @see R.styleable.AndroidManifestApplication_attributionsAreUserVisible
     */
    val isAttributionsUserVisible: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_BACKUP_IN_FOREGROUND
     *
     * @see R.styleable.AndroidManifestApplication_backupInForeground
     */
    val isBackupInForeground: Boolean

    /**
     * @see ApplicationInfo.FLAG_HARDWARE_ACCELERATED
     *
     * @see R.styleable.AndroidManifestApplication_hardwareAccelerated
     */
    val isHardwareAccelerated: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_CANT_SAVE_STATE
     *
     * @see R.styleable.AndroidManifestApplication_cantSaveState
     */
    val isSaveStateDisallowed: Boolean

    /**
     * @see PackageInfo.coreApp
     */
    val isCoreApp: Boolean

    /**
     * @see ApplicationInfo.crossProfile
     *
     * @see R.styleable.AndroidManifestApplication_crossProfile
     */
    val isCrossProfile: Boolean

    /**
     * @see ApplicationInfo.FLAG_DEBUGGABLE
     *
     * @see R.styleable.AndroidManifestApplication_debuggable
     */
    val isDebuggable: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE
     *
     * @see R.styleable.AndroidManifestApplication_defaultToDeviceProtectedStorage
     */
    val isDefaultToDeviceProtectedStorage: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_DIRECT_BOOT_AWARE
     *
     * @see R.styleable.AndroidManifestApplication_directBootAware
     */
    val isDirectBootAware: Boolean

    /**
     * @see ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS
     *
     * @see R.styleable.AndroidManifestApplication_extractNativeLibs
     */
    val isExtractNativeLibrariesRequested: Boolean

    /**
     * @see ApplicationInfo.FLAG_FACTORY_TEST
     */
    val isFactoryTest: Boolean

    /**
     * @see R.styleable.AndroidManifestApplication_forceQueryable
     */
    val isForceQueryable: Boolean

    /**
     * @see ApplicationInfo.FLAG_FULL_BACKUP_ONLY
     *
     * @see R.styleable.AndroidManifestApplication_fullBackupOnly
     */
    val isFullBackupOnly: Boolean

    /**
     * @see ApplicationInfo.FLAG_HAS_CODE
     *
     * @see R.styleable.AndroidManifestApplication_hasCode
     */
    val isDeclaredHavingCode: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_HAS_FRAGILE_USER_DATA
     *
     * @see R.styleable.AndroidManifestApplication_hasFragileUserData
     */
    val isUserDataFragile: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_ISOLATED_SPLIT_LOADING
     *
     * @see R.styleable.AndroidManifest_isolatedSplits
     */
    val isIsolatedSplitLoading: Boolean

    /**s
     * @see ApplicationInfo.FLAG_KILL_AFTER_RESTORE
     *
     * @see R.styleable.AndroidManifestApplication_killAfterRestore
     */
    val isKillAfterRestoreAllowed: Boolean

    /**
     * @see ApplicationInfo.FLAG_LARGE_HEAP
     *
     * @see R.styleable.AndroidManifestApplication_largeHeap
     */
    val isLargeHeap: Boolean

    /**
     * Returns true if R.styleable#AndroidManifest_sharedUserMaxSdkVersion is set to a value
     * smaller than the current SDK version, indicating the package wants to leave its declared
     * [.getSharedUserId]. This only occurs on new installs, pretending the app never
     * declared one.
     *
     * @see R.styleable.AndroidManifest_sharedUserMaxSdkVersion
     */
    val isLeavingSharedUser: Boolean

    /**
     * @see ApplicationInfo.FLAG_MULTIARCH
     *
     * @see R.styleable.AndroidManifestApplication_multiArch
     */
    val isMultiArch: Boolean

    /**
     * @see ApplicationInfo.nativeLibraryRootRequiresIsa
     */
    val isNativeLibraryRootRequiresIsa: Boolean

    /**
     * @see R.styleable.AndroidManifestApplication_enableOnBackInvokedCallback
     */
    val isOnBackInvokedCallbackEnabled: Boolean

    /**
     * @see ApplicationInfo.FLAG_PERSISTENT
     *
     * @see R.styleable.AndroidManifestApplication_persistent
     */
    val isPersistent: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_EXT_PROFILEABLE
     *
     * @see R.styleable.AndroidManifestProfileable
     */
    val isProfileable: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_PROFILEABLE_BY_SHELL
     *
     * @see R.styleable.AndroidManifestProfileable_shell
     */
    val isProfileableByShell: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE
     *
     * @see R.styleable.AndroidManifestApplication_requestLegacyExternalStorage
     */
    val isRequestLegacyExternalStorage: Boolean

    /**
     * @see PackageInfo.requiredForAllUsers
     *
     * @see R.styleable.AndroidManifestApplication_requiredForAllUsers
     */
    val isRequiredForAllUsers: Boolean

    /**
     * Whether the enabled settings of components in the application should be reset to the default,
     * when the application's user data is cleared.
     *
     * @see R.styleable.AndroidManifestApplication_resetEnabledSettingsOnAppDataCleared
     */
    val isResetEnabledSettingsOnAppDataCleared: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_IS_RESOURCE_OVERLAY
     *
     * @see ApplicationInfo.isResourceOverlay
     * @see R.styleable.AndroidManifestResourceOverlay
     */
    val isResourceOverlay: Boolean

    /**
     * @see ApplicationInfo.FLAG_RESTORE_ANY_VERSION
     *
     * @see R.styleable.AndroidManifestApplication_restoreAnyVersion
     */
    val isRestoreAnyVersion: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY
     */
    val isSignedWithPlatformKey: Boolean

    /**
     * If omitted from manifest, returns true if [.getTargetSdkVersion] >= [ ][android.os.Build.VERSION_CODES.GINGERBREAD].
     *
     * @see R.styleable.AndroidManifestSupportsScreens_xlargeScreens
     *
     * @see ApplicationInfo.FLAG_SUPPORTS_XLARGE_SCREENS
     */
    val isExtraLargeScreensSupported: Boolean

    /**
     * If omitted from manifest, returns true if [.getTargetSdkVersion] >= [ ][android.os.Build.VERSION_CODES.DONUT].
     *
     * @see R.styleable.AndroidManifestSupportsScreens_largeScreens
     *
     * @see ApplicationInfo.FLAG_SUPPORTS_LARGE_SCREENS
     */
    val isLargeScreensSupported: Boolean

    /**
     * If omitted from manifest, returns true.
     *
     * @see R.styleable.AndroidManifestSupportsScreens_normalScreens
     *
     * @see ApplicationInfo.FLAG_SUPPORTS_NORMAL_SCREENS
     */
    val isNormalScreensSupported: Boolean

    /**
     * @see ApplicationInfo.FLAG_SUPPORTS_RTL
     *
     * @see R.styleable.AndroidManifestApplication_supportsRtl
     */
    val isRtlSupported: Boolean

    /**
     * If omitted from manifest, returns true if [.getTargetSdkVersion] >= [ ][android.os.Build.VERSION_CODES.DONUT].
     *
     * @see R.styleable.AndroidManifestSupportsScreens_smallScreens
     *
     * @see ApplicationInfo.FLAG_SUPPORTS_SMALL_SCREENS
     */
    val isSmallScreensSupported: Boolean

    /**
     * @see ApplicationInfo.FLAG_TEST_ONLY
     *
     * @see R.styleable.AndroidManifestApplication_testOnly
     */
    val isTestOnly: Boolean

    /**
     * The install time abi override to choose 32bit abi's when multiple abi's are present. This is
     * only meaningful for multiarch applications. The use32bitAbi attribute is ignored if
     * cpuAbiOverride is also set.
     *
     * @see R.attr.use32bitAbi
     */
    fun is32BitAbiPreferred(): Boolean

    /**
     * @see ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
     *
     * @see R.styleable.AndroidManifestApplication_usesCleartextTraffic
     */
    val isCleartextTrafficAllowed: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_USE_EMBEDDED_DEX
     *
     * @see R.styleable.AndroidManifestApplication_useEmbeddedDex
     */
    val isUseEmbeddedDex: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_USES_NON_SDK_API
     *
     * @see R.styleable.AndroidManifestApplication_usesNonSdkApi
     */
    val isNonSdkApiRequested: Boolean

    /**
     * @see ApplicationInfo.FLAG_VM_SAFE_MODE
     *
     * @see R.styleable.AndroidManifestApplication_vmSafeMode
     */
    val isVmSafeMode: Boolean

    // Methods below this comment are not yet exposed as API
    val activities: List<Any?>

    /**
     * The names of packages to adopt ownership of permissions from, parsed under [ ][ParsingPackageUtils.TAG_ADOPT_PERMISSIONS].
     *
     * @see R.styleable.AndroidManifestOriginalPackage_name
     *
     * @hide
     */
    val adoptPermissions: List<String?>

    val apexSystemServices: List<Any?>

    val attributions: List<Any?>

    /**
     * @see ApplicationInfo.AUTO_REVOKE_ALLOWED
     *
     * @see ApplicationInfo.AUTO_REVOKE_DISCOURAGED
     *
     * @see ApplicationInfo.AUTO_REVOKE_DISALLOWED
     *
     * @see R.styleable.AndroidManifestApplication_autoRevokePermissions
     *
     * @hide
     */
    val autoRevokePermissions: Int

    val baseApkPath: String

    val compileSdkVersion: Int

    val compileSdkVersionCodeName: String?

    val configPreferences: List<ConfigurationInfo?>

    val featureGroups: List<FeatureGroupInfo?>

    val implicitPermissions: Set<String?>

    val installLocation: Int

    val instrumentations: List<Any?>

    val keySetMapping: Map<String?, Any?>

    val knownActivityEmbeddingCerts: Set<String?>

    val manageSpaceActivityName: String?

    val manifestPackageName: String

    val maxSdkVersion: Int

    // @get:ApplicationInfo.MemtagMode
    val memtagMode: Int

    // @get:ApplicationInfo.PageSizeAppCompatFlags
    val pageSizeAppCompatFlags: Int

    val metaData: Bundle?

    val mimeGroups: Set<String?>?

    val minExtensionVersions: SparseIntArray?

    val minSdkVersion: Int

    val nativeLibraryDir: String?

    val nativeLibraryRootDir: String?

    val nonLocalizedLabel: CharSequence?

    val originalPackages: List<String?>

    val overlayCategory: String?

    val overlayPriority: Int

    val overlayTarget: String?

    val overlayTargetOverlayableName: String?

    val overlayables: Map<String?, String?>

    val packageName: String?

    val path: String

    val permission: String?

    val permissionGroups: List<Any?>

    val permissions: List<Any?>

    val preferredActivityFilters: List<Pair<String?, Any?>?>

    val processName: String

    val processes: Map<String?, Any?>

    val properties: Map<String?, PackageManager.Property?>

    val protectedBroadcasts: List<String?>

    val providers: List<Any?>

    val queriesIntents: List<Intent?>

    val queriesPackages: List<String?>

    val queriesProviders: Set<String?>

    val receivers: List<Any?>

    val requestedFeatures: List<FeatureInfo?>

    val requestedPermissions: Set<String?>

    val resizeableActivity: Boolean?

    val restrictUpdateHash: ByteArray?

    val sdkLibVersionMajor: Int

    val secondaryNativeLibraryDir: String?

    val services: List<Any?>

    // val signingDetails: SigningDetails

    val splitClassLoaderNames: Array<String?>?

    val splitCodePaths: Array<String?>

    val splitDependencies: SparseArray<IntArray?>

    val splitFlags: IntArray?

    val splitNames: Array<String?>

    val splitRevisionCodes: IntArray

    /**
     * @see ApplicationInfo.targetSandboxVersion
     *
     * @see R.styleable.AndroidManifest_targetSandboxVersion
     *
     * @hide
     */
    val targetSandboxVersion: Int

    val taskAffinity: String?

    @get:Deprecated(
        """Use {@link PackageState#getAppId()} instead.
      """
    )
    val uid: Int

    /**
     * For use with [com.android.server.pm.KeySetManagerService]. Parsed in [ ][ParsingPackageUtils.TAG_KEY_SETS].
     *
     * @see R.styleable.AndroidManifestUpgradeKeySet
     *
     * @hide
     */
    val upgradeKeySets: Set<String?>

    /**
     * @see R.styleable.AndroidManifestUsesLibrary
     *
     * @hide
     */
    val usesLibraries: List<String?>

    /**
     * @see R.styleable.AndroidManifestUsesNativeLibrary
     *
     * @hide
     */
    val usesNativeLibraries: List<String?>

    /**
     * Like [.getUsesLibraries], but marked optional by setting [ ][R.styleable.AndroidManifestUsesLibrary_required] to false . Application is expected to handle
     * absence manually.
     *
     * @see R.styleable.AndroidManifestUsesLibrary
     *
     * @hide
     */
    val usesOptionalLibraries: List<String?>

    /**
     * Like [.getUsesNativeLibraries], but marked optional by setting [ ][R.styleable.AndroidManifestUsesNativeLibrary_required] to false . Application is expected to
     * handle absence manually.
     *
     * @see R.styleable.AndroidManifestUsesNativeLibrary
     *
     * @hide
     */
    val usesOptionalNativeLibraries: List<String?>

    val usesPermissions: List<Any?>

    /**
     * TODO(b/135203078): Move SDK library stuff to an inner data class
     *
     * @see R.styleable.AndroidManifestUsesSdkLibrary
     *
     * @hide
     */
    val usesSdkLibraries: List<String?>

    val usesSdkLibrariesCertDigests: Array<Array<String?>?>?

    val usesSdkLibrariesVersionsMajor: LongArray?


    val usesSdkLibrariesOptional: BooleanArray?

    /**
     * TODO(b/135203078): Move static library stuff to an inner data class
     *
     * @see R.styleable.AndroidManifestUsesStaticLibrary
     *
     * @hide
     */
    val usesStaticLibraries: List<String?>

    val usesStaticLibrariesCertDigests: Array<Array<String?>?>?

    val usesStaticLibrariesVersions: LongArray?

    val volumeUuid: String?

    /** @hide
     */
    fun hasPreserveLegacyExternalStorage(): Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_EXT_REQUEST_FOREGROUND_SERVICE_EXEMPTION
     *
     * @see R.styleable.AndroidManifestApplication_requestForegroundServiceExemption
     *
     * @hide
     */
    fun hasRequestForegroundServiceExemption(): Boolean

    /**
     * @see ApplicationInfo.getRequestRawExternalStorageAccess
     * @see R.styleable.AndroidManifestApplication_requestRawExternalStorageAccess
     *
     * @hide
     */
    fun hasRequestRawExternalStorageAccess(): Boolean?

    /** @hide
     */
    val isApex: Boolean


    /**
     * @see R.styleable.AndroidManifestApplication_updatableSystem
     *
     * @hide
     */
    val isUpdatableSystem: Boolean

    /**
     * @see ApplicationInfo.enabled
     *
     * @see R.styleable.AndroidManifestApplication_enabled
     *
     * @hide
     */
    val isEnabled: Boolean

    /**
     * @see ApplicationInfo.FLAG_EXTERNAL_STORAGE
     *
     * @hide
     */
    val isExternalStorage: Boolean

    @get:Deprecated("")
    val isGame: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_HAS_DOMAIN_URLS
     *
     * @see R.styleable.AndroidManifestIntentFilter
     *
     * @hide
     */
    val isHasDomainUrls: Boolean

    /**
     * @see PackageInfo.mOverlayIsStatic
     *
     * @hide
     */
    val isOverlayIsStatic: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE
     *
     * @see R.styleable.AndroidManifestActivity_directBootAware
     *
     * @see R.styleable.AndroidManifestProvider_directBootAware
     *
     * @see R.styleable.AndroidManifestReceiver_directBootAware
     *
     * @see R.styleable.AndroidManifestService_directBootAware
     *
     * @hide
     */
    val isPartiallyDirectBootAware: Boolean

    /**
     * If omitted from manifest, returns true if [.getTargetSdkVersion] >= [ ][android.os.Build.VERSION_CODES.DONUT].
     *
     * @see R.styleable.AndroidManifestSupportsScreens_resizeable
     *
     * @see ApplicationInfo.FLAG_RESIZEABLE_FOR_SCREENS
     *
     * @hide
     */
    val isResizeable: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION
     *
     * @see R.styleable.AppWidgetProviderInfo_resizeMode
     *
     * @hide
     */
    val isResizeableActivityViaSdkVersion: Boolean

    /**
     * True means that this package/app contains an SDK library.
     * @see R.styleable.AndroidManifestSdkLibrary
     *
     * @hide
     */
    val isSdkLibrary: Boolean

    /**
     * @see ApplicationInfo.PRIVATE_FLAG_STATIC_SHARED_LIBRARY
     *
     * @see R.styleable.AndroidManifestStaticLibrary
     *
     * @hide
     */
    val isStaticSharedLibrary: Boolean

    /**
     * @see PackageInfo.isStub
     *
     * @hide
     */
    val isStub: Boolean

    /**
     * Set if the any of components are visible to instant applications.
     *
     * @see R.styleable.AndroidManifestActivity_visibleToInstantApps
     *
     * @see R.styleable.AndroidManifestProvider_visibleToInstantApps
     *
     * @see R.styleable.AndroidManifestService_visibleToInstantApps
     *
     * @hide
     */
    val isVisibleToInstantApps: Boolean

    /**
     * @see ApplicationInfo.allowCrossUidActivitySwitchFromBelow
     *
     * @see R.styleable.AndroidManifestApplication_allowCrossUidActivitySwitchFromBelow
     *
     * @hide
     */
    val isAllowCrossUidActivitySwitchFromBelow: Boolean

    /**
     * Returns the intent matching flags.
     * @hide
     */
    val intentMatchingFlags: Int
}