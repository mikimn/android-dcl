package com.mikimn.apkloader.apk

import android.app.Application
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import com.mikimn.apkloader.reflection.tryGetMethod
import com.mikimn.apkloader.utils.Zip
import dalvik.system.InMemoryDexClassLoader
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile

class LoadedApk(val name: String, private val baseClassLoader: ClassLoader) {
    var loader: ClassLoader? = null
    var resourcesProvider: ResourcesProvider? = null
    var manifestReader: AndroidManifestReader? = null

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
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp", ".apk")
            tempFile.writeBytes(data)
            tempFile.setReadOnly()

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
            var extractedApkDirectory = tempFile.parentFile!!
            extractedApkDirectory = File(extractedApkDirectory, cacheDirName)
            // Clear any previous extraction for this name first: a re-test of the same
            // `name` (e.g. a bundled sample rebuilt with different content) must not leave
            // stale files from an older version of the APK lying around.
            extractedApkDirectory.deleteRecursively()
            extractedApkDirectory.mkdirs()

            // Zip.unzip(ZipInputStream(tempFile.inputStream()), extractedApkDirectory)
            Zip.unzip(ZipFile(tempFile), extractedApkDirectory)

            val splitApks = apkInstallDir?.let { discoverSplitApks(it) } ?: emptyList()
            val splitApkNativeDirs = mutableListOf<File>()
            val splitApkOutputDirs = mutableListOf<File>()
            for (splitApk in splitApks) {
                val outputDir = File(extractedApkDirectory, splitApk.name)
                Zip.unzip(ZipFile(splitApk), outputDir)
                splitApkNativeDirs.add(File(outputDir, "lib"))
                splitApkOutputDirs.add(outputDir)
            }

            loader = buildClassLoader(extractedApkDirectory, splitApkNativeDirs + listOf(nativeLibsUncompressedDir), baseClassLoader)

            // TODO Move inside buildClassLoader
            // Should fix `Module with the Main dispatcher is missing. Add dependency providing the Main dispatcher, e.g. 'kotlinx-coroutines-android ...`
            //    This is because of the way ServiceLoaded uses Class.classLoader explicitly, which is provided when the class is first initiated, and the
            //    way META-INF directory is resolved from the BaseDexClassLoader
            val addDexPath = loader!!::class.java.tryGetMethod("addDexPath", String::class.java)
            addDexPath?.isAccessible = true
            addDexPath?.invoke(loader!!, extractedApkDirectory.absolutePath)

            resourcesProvider = buildResourceProvider(tempFile)
            val manifestFile = extractedApkDirectory
                .listFiles { f -> f.name == "AndroidManifest.xml" }
                ?.firstOrNull()

            resources.addLoaders(ResourcesLoader().apply {
                addProvider(buildResourceProvider(tempFile))
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

        } finally {
            tempFile?.delete()
        }
    }

    private fun buildClassLoader(
        extractedApkDirectory: File,
        nativeLibsUncompressedDirs: List<File?>,
        baseClassLoader: ClassLoader
    ): ClassLoader {
        val dexFiles = extractedApkDirectory.listFiles { f -> f.extension == "dex" }

        if (dexFiles == null) {
            throw IllegalStateException("Could not list files from extracted APK at ${extractedApkDirectory.path}")
        }

        val dexBuffers = dexFiles.map { dexFile ->
            dexFile.setReadOnly()
            ByteBuffer.wrap(dexFile.readBytes())
        }.toTypedArray()

        val allLibPaths = nativeLibsUncompressedDirs.mapNotNull { file ->
            file?.absolutePath + File.pathSeparator + (file?.listFiles()
                ?.joinToString(File.pathSeparator) { it.absolutePath } ?: "")
        }.joinToString(File.pathSeparator)

        return InMemoryDexClassLoader(dexBuffers, allLibPaths, baseClassLoader)
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
