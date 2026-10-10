package com.mikimn.apkloader.apk

import android.app.Application
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.os.Process
import android.util.Log
import com.mikimn.apkloader.reflection.tryGetMethod
import com.mikimn.apkloader.utils.Zip
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

class LoadedApk(val name: String, private val baseClassLoader: ClassLoader) {
    var loader: ClassLoader? = null
    var resourcesProvider: ResourcesProvider? = null
    var manifestReader: AndroidManifestReader? = null

    /**
     * Where this APK lives on disk once loaded. Shadow `ApplicationInfo`/`Context` paths report
     * these instead of the host's own APK, because libraries reopen their own APK by path
     * (crash reporters, asset-bundle loaders, split-install helpers, ...).
     */
    data class Paths(val apk: File, val splits: List<File>, val nativeLibraryDir: File?)

    var paths: Paths? = null
        private set

    // A real Android process has exactly one Application instance for its whole
    // lifetime, shared across every Activity. DCLActivity.onCreate() runs once per
    // proxy-pool slot (i.e. once per in-app navigation hop), so without caching it
    // here, each hop would spin up a brand-new shadow Application - and call its
    // onCreate() again. Apps that initialize a singleton resource keyed by an
    // on-disk path in Application.onCreate() (e.g. a Jetpack DataStore) then throw,
    // since two "active" instances for the same file trip the library's own
    // duplicate-instance safety check.
    var shadowApplication: Application? = null

    private fun discoverSplitApks(baseDir: File): List<File> {
        return baseDir.listFiles { dir, name ->
            name.startsWith("split_config") && name.endsWith("apk")
        }
            ?.toList() ?: emptyList()
    }

    // TODO support load from assets
    fun load(data: ByteArray, resources: Resources) {
        var apkFile: File? = null
        var extractionDir: File? = null
        var loaded = false
        try {

            // Only present when `name` is an absolute on-device path (e.g. an installed
            // app's APK); bare asset names like "calculator.apk" have no parent directory
            // to look for split APKs / uncompressed native libs alongside.
            val apkInstallDir = File(name).parentFile

            val nativeLibsUncompressedDir = apkInstallDir
                ?.listFiles { file, _ -> file.isDirectory }
                ?.filter { it.name == "lib" }
                ?.firstOrNull()

            // name is often a full device path (e.g. /data/app/~~.../base.apk) rather than a
            // bare filename - File(parent, "cache-$name") would silently treat its embedded "/"
            // as nested subdirectories instead of one flat cache dir per APK (confirmed
            // on-device: this produced a shared top-level "cache-" directory containing a
            // "data/app/~~.../..." tree, with every device-path-loaded APK's cache nested
            // inside it rather than each getting its own directory).
            val cacheDirName = "cache-" + name.replace(File.separatorChar, '_')
            val cacheRoot = File(System.getProperty("java.io.tmpdir")!!)
            // Kept on disk for the life of the process (it used to be a deleted temp file): the
            // class loader reads dex from it, and loaded code can reopen it by path.
            apkFile = persistApk(data, File(cacheRoot, "$cacheDirName.apk"))
            var extractedApkDirectory = File(cacheRoot, cacheDirName)
            // Clear any previous extraction for this name first: a re-test of the same
            // `name` (e.g. a bundled sample rebuilt with different content) must not leave
            // stale files from an older version of the APK lying around.
            extractedApkDirectory.deleteRecursively()
            extractedApkDirectory.mkdirs()
            extractionDir = extractedApkDirectory

            // Zip.unzip(ZipInputStream(tempFile.inputStream()), extractedApkDirectory)
            Zip.unzip(ZipFile(apkFile), extractedApkDirectory)

            val splitApks = apkInstallDir?.let { discoverSplitApks(it) } ?: emptyList()
            val splitApkNativeDirs = mutableListOf<File>()
            val splitApkOutputDirs = mutableListOf<File>()
            for (splitApk in splitApks) {
                val outputDir = File(extractedApkDirectory, splitApk.name)
                Zip.unzip(ZipFile(splitApk), outputDir)
                splitApkNativeDirs.add(File(outputDir, "lib"))
                splitApkOutputDirs.add(outputDir)
            }

            // The base APK's own lib/<abi>/*.so, as just extracted above. The install-dir lib/
            // folder only exists for installed apps whose libs the package manager extracted;
            // an APK loaded from assets (or one built with extractNativeLibs=false and no ABI
            // split) carries its libs only inside the APK itself.
            val baseApkNativeDir = primaryAbiDir(File(extractedApkDirectory, "lib"))

            loader = buildClassLoader(
                apkFile,
                splitApkNativeDirs + listOf(nativeLibsUncompressedDir),
                listOfNotNull(baseApkNativeDir),
                baseClassLoader
            )

            // TODO Move inside buildClassLoader
            // Should fix `Module with the Main dispatcher is missing. Add dependency providing the Main dispatcher, e.g. 'kotlinx-coroutines-android ...`
            //    This is because of the way ServiceLoaded uses Class.classLoader explicitly, which is provided when the class is first initiated, and the
            //    way META-INF directory is resolved from the BaseDexClassLoader
            val addDexPath = loader!!::class.java.tryGetMethod("addDexPath", String::class.java)
            addDexPath?.isAccessible = true
            addDexPath?.invoke(loader!!, extractedApkDirectory.absolutePath)

            resourcesProvider = buildResourceProvider(apkFile)
            val manifestFile = extractedApkDirectory
                .listFiles { f -> f.name == "AndroidManifest.xml" }
                ?.firstOrNull()

            resources.addLoaders(ResourcesLoader().apply {
                addProvider(buildResourceProvider(apkFile))
                addProvider(buildResourceProviderFromDir(extractedApkDirectory))
                // Config splits (e.g. split_config.xxxhdpi.apk) carry density/language/ABI-
                // specific resources - such as bitmap drawables referenced from a base-APK
                // drawable XML - that don't exist anywhere in the base APK itself. Without
                // these, resolving such a resource ID throws Resources.NotFoundException.
                for ((splitApk, outputDir) in splitApks.zip(splitApkOutputDirs)) {
                    addProvider(buildResourceProvider(splitApk))
                    addProvider(buildResourceProviderFromDir(outputDir))
                }
            })

            manifestReader = manifestFile?.let { AndroidManifestReader(it.parentFile!!, FileInputStream(it), resources) }

            paths = Paths(apkFile, splitApks, baseApkNativeDir ?: nativeLibsUncompressedDir)
            manifestReader?.getApplicationInfo()?.let { info ->
                info.sourceDir = apkFile.path
                info.publicSourceDir = apkFile.path
                info.splitSourceDirs = splitApks.map { it.path }.toTypedArray().takeIf { it.isNotEmpty() }
                info.splitPublicSourceDirs = info.splitSourceDirs
                paths!!.nativeLibraryDir?.let { info.nativeLibraryDir = it.path }
            }
            loaded = true

        } catch (e: SecurityException) {
            // A hostile archive (zip-slip): don't leave its half-extracted, attacker-controlled
            // files in the cache. `loader` was never built, so nothing can class-load from them,
            // and FileTrackingClassLoader.addApkFile never registers an APK whose load() threw.
            extractionDir?.deleteRecursively()
            throw e
        } finally {
            // A failed load must not leave a half-trusted APK lying around; a successful one
            // keeps it, since the class loader and loaded code use it.
            if (!loaded) apkFile?.delete()
        }
    }

    /**
     * Writes [data] to [target] read-only and atomically replaces any previous copy: an earlier
     * LoadedApk for the same name (same process) may still have its class loader reading the old
     * file, which stays valid because it is replaced by rename, never rewritten in place.
     */
    private fun persistApk(data: ByteArray, target: File): File {
        val part = File.createTempFile(target.name, ".part", target.parentFile)
        try {
            part.writeBytes(data)
            part.setReadOnly()
            if (!part.renameTo(target)) throw java.io.IOException("Could not move $part to $target")
        } finally {
            part.delete() // no-op after a successful rename
        }
        return target
    }

    /**
     * The `lib/<abi>` subdirectory for this process's most-preferred ABI that the APK ships, if
     * any. The loaded code runs in the *host's* process, so only ABIs matching its bitness can
     * work (a 64-bit process can't dlopen a 32-bit `.so`, even on a device that supports
     * 32-bit apps), and only one ABI may be used: mixing them on the search path lets the
     * linker pick whichever directory comes first.
     */
    private fun primaryAbiDir(libDir: File): File? {
        val processAbis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        val match = processAbis
            .map { File(libDir, it) }
            .firstOrNull { it.isDirectory }

        if (match == null) {
            val shippedAbis = libDir.listFiles { f -> f.isDirectory }?.map { it.name }.orEmpty()
            if (shippedAbis.isNotEmpty()) {
                Log.w(
                    "LoadedApk",
                    "$name ships native libs for $shippedAbis, none loadable in this " +
                        "${if (Process.is64Bit()) "64" else "32"}-bit process (${processAbis.toList()})"
                )
            }
        }
        return match
    }

    private fun buildClassLoader(
        apkFile: File,
        nativeLibsUncompressedDirs: List<File?>,
        nativeLibAbiDirs: List<File>,
        baseClassLoader: ClassLoader
    ): ClassLoader {
        // filterNotNull first: a null dir (no install-dir lib/, e.g. any asset-loaded APK) used
        // to stringify into a literal "null:" search path entry.
        val allLibPaths = nativeLibsUncompressedDirs.filterNotNull().map { file ->
            file.absolutePath + File.pathSeparator + (file.listFiles()
                ?.joinToString(File.pathSeparator) { it.absolutePath } ?: "")
        }.plus(nativeLibAbiDirs.map { it.absolutePath }).joinToString(File.pathSeparator)

        // File-backed (not InMemoryDexClassLoader), so ART can compile/verify the dex ahead of
        // time and share it between processes instead of re-interpreting it from memory on every
        // start. Every classes*.dex in the APK is picked up. The optimized directory is ignored
        // since API 26.
        return DexClassLoader(apkFile.absolutePath, null, allLibPaths, baseClassLoader)
    }

    private fun buildResourceProvider(apkFile: File): ResourcesProvider {
        val pfd = ParcelFileDescriptor.open(apkFile, MODE_READ_ONLY)
        val rp = ResourcesProvider.loadFromApk(pfd)
        return rp
    }

    private fun buildResourceProviderFromDir(extractedApkDirectory: File): ResourcesProvider {
        return ResourcesProvider.loadFromDirectory(extractedApkDirectory.absolutePath, null)
    }

    fun loadClass(name: String): Class<*> {
        return loader?.loadClass(name) ?: throw IllegalStateException("Call load(ByteArray) before using LoadedApk")
    }
}
