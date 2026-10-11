package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.content.res.Resources
import android.util.Log
import com.mikimn.apkloader.MyContextWrapper
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.plugins.DefaultPluginProvider
import com.mikimn.apkloader.plugins.ContextPluginProvider
import com.mikimn.apkloader.pm.PackageManagerAggregate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * @param virtualPackage when set, this context belongs to that loaded package (the shadow
 * Application/Activity and the loaded app's providers get one), and every private-storage API is
 * redirected to its own [VirtualDataDirs]. The host's own contexts leave it null and are untouched.
 * @param loadedPaths where that package's APK, splits and native libs are on disk. Reported by
 * `getApplicationInfo()` and `getPackageCodePath()`/`getPackageResourcePath()` instead of the host's
 * own APK, because libraries reopen their own APK by path (crash reporters, asset-bundle loaders,
 * split-install helpers, ...).
 */
class DCLContext(
    base: Context,
    private val pluginProvider: ContextPluginProvider = DefaultPluginProvider(),
    private val virtualPackage: String? = null,
    private val loadedPaths: LoadedApk.Paths? = null
) : ContextWrapper(base) {
    init {
        // The paths only take effect together with the per-package storage (getApplicationInfo() is
        // patched only for a virtual package), so a context given paths alone would silently ignore them.
        require(loadedPaths == null || virtualPackage != null) { "loadedPaths requires virtualPackage" }
    }

    companion object {
        private var shadowPackageName: String? = null
        var shadowApp: Application? = null

        // Process-lifetime state, deliberately not an instance: PackageManagerAggregate is a
        // process-wide singleton, so a resolver that captured a DCLContext (which wraps an
        // Activity/Application base) would keep every context it was last handed alive.
        @Volatile private var trackingLoader: FileTrackingClassLoader? = null

        /**
         * A context for code of [apk] running outside a hosted activity (e.g. a receiver): the host's
         * base services, with that package's own storage. [context] may be the host's
         * Application or any wrapper of it.
         */
        @Suppress("UNUSED_PARAMETER") // [apk] is where its code paths come from once the context reports them
        fun forLoadedApk(context: Context, apk: LoadedApk, packageName: String): Context {
            var base = context
            while (base is ContextWrapper && base !is DCLContext) base = base.baseContext
            if (base is DCLContext) base = base.baseContext
            return DCLContext(base, virtualPackage = packageName)
        }

        /** The loaded package's name if (and only if) the real caller is that APK's own code. */
        fun loadedPackageForCaller(): String? {
            val shadow = shadowPackageName ?: return null
            val loader = trackingLoader ?: return null
            val callerClassName = CallerClassResolver.findRealCallerClassName() ?: return null
            return if (loader.ownerOf(callerClassName) != null) shadow else null
        }
    }

    override fun getPackageManager(): PackageManager {
        Log.e("DCLContext", "INVOKE getPackageManager")
        val base = super.getPackageManager()
        (classLoader as? FileTrackingClassLoader)?.let { trackingLoader = it }
        return pluginProvider.providePackageManager(base).also {
            (it as? PackageManagerAggregate)?.ownUidPackageResolver = Companion::loadedPackageForCaller
        }
    }

    // Starting a service that is not a loaded app's own stays the legacy no-op (it can't be resolved in the
    // host and used to be silently ignored); a loaded app's own service goes to the system, where
    // ActivityManagerHook retargets it at a host proxy slot (see DCLService).
    override fun startService(service: Intent?): ComponentName? {
        val loader = trackingLoader
        val isLoaded = service != null && loader != null && (
            service.component?.className?.let { loader.ownerOf(it) != null } == true ||
                service.`package`?.let { loader.isLoadedPackage(it) } == true
            )
        return if (isLoaded) super.startService(service) else service?.component
    }

    // Only spoof the package name for callers that are the loaded APK's own code - real
    // OS-level subsystems (WebView among them, confirmed on-device) rely on getPackageName()
    // returning the host's actual, installed identity and break if lied to unconditionally.
    // See docs/apk-test-log.md's "Meme Generator" research notes for how this was derived.
    override fun getPackageName(): String {
        (classLoader as? FileTrackingClassLoader)?.let { trackingLoader = it }
        return loadedPackageForCaller() ?: super.getPackageName()
    }

    private var cachedResources: Resources? = null

    // A Context's getResources() is expected to return a stable identity across calls
    // (real Android Contexts cache it) - this used to build a brand-new wrapper on
    // every call, silently discarding any ResourcesLoader anyone had attached to a
    // previous call's result (e.g. DCLActivity.initResourceLoader's
    // baseContext.resources.addLoaders(...)), since native code frequently re-fetches
    // .resources rather than holding onto one reference.
    override fun getResources(): Resources {
        cachedResources?.let { return it }

        val originalResources = super.getResources()
        val wrapped = object : Resources(
            originalResources.assets, originalResources.displayMetrics, originalResources.configuration
        ) {
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
                val defaultRes = super.getIdentifier(name, defType, defPackage)

                return shadowPackageName?.let {
                    if (defaultRes == 0) {
                        super.getIdentifier(name, defType, it)
                    } else {
                        defaultRes
                    }
                } ?: defaultRes
            }
        }
        cachedResources = wrapped
        return wrapped
    }

    override fun getApplicationContext(): Context {
        return shadowApp ?: super.getApplicationContext()
    }

    fun setShadowPackageName(packageName: String) {
        shadowPackageName = packageName
    }

    fun setShadowApplication(shadowApp: Application) {
        DCLContext.shadowApp = shadowApp
    }

    // --- per-package private storage (see VirtualDataDirs) ---

    private val dirs: VirtualDataDirs? by lazy {
        virtualPackage?.let { VirtualDataDirs(baseContext.dataDir, it) }
    }

    private fun File.ensureDir(): File = also { mkdirs() }

    override fun getDataDir(): File = dirs?.dataDir?.ensureDir() ?: super.getDataDir()
    override fun getFilesDir(): File = dirs?.files?.ensureDir() ?: super.getFilesDir()
    override fun getCacheDir(): File = dirs?.cache?.ensureDir() ?: super.getCacheDir()
    override fun getCodeCacheDir(): File = dirs?.codeCache?.ensureDir() ?: super.getCodeCacheDir()
    override fun getNoBackupFilesDir(): File = dirs?.noBackup?.ensureDir() ?: super.getNoBackupFilesDir()
    override fun getDir(name: String, mode: Int): File = dirs?.dir(name)?.ensureDir() ?: super.getDir(name, mode)

    // The platform's file APIs resolve names against ContextImpl's own private directories rather
    // than calling getFilesDir() on the wrapper, so each has to be redirected explicitly.
    override fun getFileStreamPath(name: String): File = dirs?.file(name) ?: super.getFileStreamPath(name)

    override fun openFileInput(name: String): FileInputStream =
        dirs?.let { FileInputStream(it.file(name)) } ?: super.openFileInput(name)

    override fun openFileOutput(name: String, mode: Int): FileOutputStream =
        dirs?.let { d ->
            d.files.ensureDir()
            FileOutputStream(d.file(name), mode and MODE_APPEND != 0)
        } ?: super.openFileOutput(name, mode)

    override fun deleteFile(name: String): Boolean = dirs?.file(name)?.delete() ?: super.deleteFile(name)

    override fun fileList(): Array<String> =
        dirs?.let { it.files.list() ?: emptyArray() } ?: super.fileList()

    override fun getDatabasePath(name: String): File =
        dirs?.database(name)?.also { it.parentFile?.mkdirs() } ?: super.getDatabasePath(name)

    // ContextImpl treats an absolute path as the database file itself.
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        dirs?.let { super.openOrCreateDatabase(getDatabasePath(name).path, mode, factory) }
            ?: super.openOrCreateDatabase(name, mode, factory)

    override fun openOrCreateDatabase(
        name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase =
        dirs?.let { super.openOrCreateDatabase(getDatabasePath(name).path, mode, factory, errorHandler) }
            ?: super.openOrCreateDatabase(name, mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean =
        dirs?.let { super.deleteDatabase(getDatabasePath(name).path) } ?: super.deleteDatabase(name)

    override fun databaseList(): Array<String> =
        dirs?.let { it.databases.list() ?: emptyArray() } ?: super.databaseList()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        dirs?.let { super.getSharedPreferences(it.prefsName(name), mode) } ?: super.getSharedPreferences(name, mode)

    override fun deleteSharedPreferences(name: String?): Boolean =
        dirs?.let { super.deleteSharedPreferences(it.prefsName(name)) } ?: super.deleteSharedPreferences(name)

    override fun getExternalFilesDir(type: String?): File? =
        dirs?.let { d -> super.getExternalFilesDir(null)?.let { d.external(it, type).ensureDir() } }
            ?: super.getExternalFilesDir(type)

    override fun getExternalFilesDirs(type: String?): Array<File?> =
        dirs?.let { d -> super.getExternalFilesDirs(null).map { b -> b?.let { d.external(it, type).ensureDir() } }.toTypedArray() }
            ?: super.getExternalFilesDirs(type)

    override fun getExternalCacheDir(): File? =
        dirs?.let { d -> super.getExternalCacheDir()?.let { d.external(it).ensureDir() } } ?: super.getExternalCacheDir()

    override fun getExternalCacheDirs(): Array<File?> =
        dirs?.let { d -> super.getExternalCacheDirs().map { b -> b?.let { d.external(it).ensureDir() } }.toTypedArray() }
            ?: super.getExternalCacheDirs()

    // Apps read ApplicationInfo.dataDir directly (e.g. to build their own paths).
    private val patchedApplicationInfo: ApplicationInfo by lazy {
        ApplicationInfo(super.getApplicationInfo()).also { info ->
            dirs?.let { info.dataDir = it.dataDir.path }
            loadedPaths?.let { paths ->
                info.sourceDir = paths.apk.path
                info.publicSourceDir = paths.apk.path
                info.splitSourceDirs = paths.splits.map { it.path }.toTypedArray().takeIf { it.isNotEmpty() }
                info.splitPublicSourceDirs = info.splitSourceDirs?.clone() // a copy: callers may mutate one
                paths.nativeLibraryDir?.let { info.nativeLibraryDir = it.path }
            }
        }
    }

    override fun getApplicationInfo(): ApplicationInfo =
        if (dirs != null) patchedApplicationInfo else super.getApplicationInfo()

    override fun getPackageCodePath(): String = loadedPaths?.apk?.path ?: super.getPackageCodePath()

    override fun getPackageResourcePath(): String = loadedPaths?.apk?.path ?: super.getPackageResourcePath()
}
