package com.mikimn.apkloader.pkg

/**
 * Methods used for mutation after direct package parsing, mostly done inside
 * [com.android.server.pm.PackageManagerService].
 *
 * Java disallows defining this as an inner interface, so this must be a separate file.
 *
 * TODO: Remove extending AndroidPackage, should be an isolated interface with only the methods
 * necessary to parse and install
 *
 * @hide
 */
interface ParsedPackage : AndroidPackage {
    // fun hideAsFinal(): AndroidPackageInternal?

    fun addUsesLibrary(index: Int, libraryName: String?): ParsedPackage?

    fun addUsesOptionalLibrary(index: Int, libraryName: String?): ParsedPackage?

    fun capPermissionPriorities(): ParsedPackage?

    fun clearAdoptPermissions(): ParsedPackage?

    fun clearOriginalPackages(): ParsedPackage?

    fun clearProtectedBroadcasts(): ParsedPackage?

    fun setBaseApkPath(baseApkPath: String?): ParsedPackage?

    fun setPath(path: String?): ParsedPackage?

    fun setNativeLibraryDir(nativeLibraryDir: String?): ParsedPackage?

    fun setNativeLibraryRootDir(nativeLibraryRootDir: String?): ParsedPackage?

    fun setPackageName(packageName: String?): ParsedPackage?

    fun setPrimaryCpuAbi(primaryCpuAbi: String?): ParsedPackage?

    fun setSecondaryCpuAbi(secondaryCpuAbi: String?): ParsedPackage?

    // fun setSigningDetails(signingDetails: SigningDetails?): ParsedPackage?

    fun setSplitCodePaths(splitCodePaths: Array<String?>?): ParsedPackage?

    fun setNativeLibraryRootRequiresIsa(nativeLibraryRootRequiresIsa: Boolean): ParsedPackage?

    fun setAllComponentsDirectBootAware(allComponentsDirectBootAware: Boolean): ParsedPackage?

    fun setFactoryTest(factoryTest: Boolean): ParsedPackage?

    fun setApex(isApex: Boolean): ParsedPackage?

    fun setUpdatableSystem(value: Boolean): ParsedPackage?

    /**
     * Sets a system app that is allowed to update another system app
     */
    fun setEmergencyInstaller(emergencyInstaller: String?): ParsedPackage?

    fun markNotActivitiesAsNotExportedIfSingleUser(): ParsedPackage?

    fun setOdm(odm: Boolean): ParsedPackage?

    fun setOem(oem: Boolean): ParsedPackage?

    fun setPrivileged(privileged: Boolean): ParsedPackage?

    fun setProduct(product: Boolean): ParsedPackage?

    fun setSignedWithPlatformKey(signedWithPlatformKey: Boolean): ParsedPackage?

    fun setSystem(system: Boolean): ParsedPackage?

    fun setSystemExt(systemExt: Boolean): ParsedPackage?

    fun setVendor(vendor: Boolean): ParsedPackage?

    fun removePermission(index: Int): ParsedPackage?

    fun removeUsesLibrary(libraryName: String?): ParsedPackage?

    fun removeUsesOptionalLibrary(libraryName: String?): ParsedPackage?

    fun setCoreApp(coreApp: Boolean): ParsedPackage?

    fun setStub(isStub: Boolean): ParsedPackage?

    fun setRestrictUpdateHash(restrictUpdateHash: ByteArray?): ParsedPackage?

    fun setSecondaryNativeLibraryDir(secondaryNativeLibraryDir: String?): ParsedPackage?

    /**
     * This is an appId, the uid if the userId is == USER_SYSTEM
     */
    fun setUid(uid: Int): ParsedPackage?

    fun setVersionCode(versionCode: Int): ParsedPackage?

    fun setVersionCodeMajor(versionCodeMajor: Int): ParsedPackage?

    // TODO(b/135203078): Move logic earlier in parse chain so nothing needs to be reverted
    fun setDefaultToDeviceProtectedStorage(defaultToDeviceProtectedStorage: Boolean): ParsedPackage?

    fun setDirectBootAware(directBootAware: Boolean): ParsedPackage?

    fun setPersistent(persistent: Boolean): ParsedPackage?
}