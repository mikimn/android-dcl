package com.mikimn.apkloader.dcl

import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import com.mikimn.apkloader.apk.LoadedApk
import java.io.IOException
import java.net.URL
import java.util.Enumeration

/**
 * A [ClassLoader] implementation that allows dynamically adding code from APK files
 * using [addApkFile].
 */
class FileTrackingClassLoader(val baseClassLoader: ClassLoader) : ClassLoader() {
    private val loadedAPKFiles: MutableMap<String, LoadedApk> = mutableMapOf()
    private var lastLoadedApk: LoadedApk? = null

    val resourcesLoader = ResourcesLoader()

    fun addApkFile(name: String, data: ByteArray, resources: Resources): LoadedApk {
        if (name in loadedAPKFiles) {
            return loadedAPKFiles[name]!!
        }

        // TODO(@mikimn): baseClassLoader or baseClassLoader.parent ??
        val loadedApk = LoadedApk(name, baseClassLoader.parent)
        loadedApk.load(data, resources)
        loadedAPKFiles[name] = loadedApk

        loadedApk.resourcesProvider?.let { resourcesLoader.addProvider(it) }
        lastLoadedApk = loadedApk

        return loadedApk
    }

    fun apkFile(name: String): LoadedApk? = loadedAPKFiles.getOrDefault(name, null)

    fun clearLast() {
        lastLoadedApk = null
    }

    val last: LoadedApk?
        get() = lastLoadedApk

    override fun loadClass(name: String): Class<*> {
//        try {
//            return baseClassLoader.loadClass(name)
//        } catch (_: ClassNotFoundException) {
//            // Left blank intentionally
//        }

        for (loadedApk in loadedAPKFiles.values) {
            try {
                return loadedApk.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // Left blank intentionally
            }
        }

        // best effort, don't really need to call baseClassLoader because
        // all APK loaders are expected to inherit from it
        return baseClassLoader.loadClass(name)
    }
}