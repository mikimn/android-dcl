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

    companion object {
        /**
         * The process's loader, installed by [DCLAppComponentFactory]. Code that has only some app's Context
         * must not ask it for the loader: a hosted app's context returns the app's *own* loader (#22).
         */
        @Volatile var process: FileTrackingClassLoader? = null
    }

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

    /** Every APK loaded in this process. */
    fun loadedApks(): List<LoadedApk> = loadedAPKFiles.values.toList()

    /** Whether [packageName] is the (manifest) package of an APK loaded in this process. */
    fun isLoadedPackage(packageName: String): Boolean =
        loadedAPKFiles.values.any { it.manifestReader?.getApplicationInfo()?.packageName == packageName }

    /** Which loaded APK (if any) a class name belongs to, e.g. for retargeting an intent. */
    fun ownerOf(className: String): LoadedApk? {
        for (loadedApk in loadedAPKFiles.values) {
            try {
                val resolved = loadedApk.loadClass(className)
                // loadClass() delegates to the parent classloader first (standard Java
                // parent-delegation), so it happily "resolves" platform/JDK classes too
                // (e.g. dalvik.system.VMStack) via the shared boot classloader - that's
                // not this APK's own code. Only count it as owned if this APK's own
                // dex is what actually defined the class.
                if (resolved.classLoader === loadedApk.loader) {
                    return loadedApk
                }
            } catch (_: ClassNotFoundException) {
                // Left blank intentionally
            }
        }
        return null
    }

    private val liveActivities = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** A hosted activity of [apk] was created: it is now the preferred app, and counts as live. */
    fun activityCreated(apk: LoadedApk) {
        liveActivities.merge(apk.name, 1, Int::plus)
        preferred = apk
    }

    /**
     * A hosted activity of [apk] was destroyed. [preferred] is forgotten only when it was the APK's *last* live
     * activity: finishing activity A after navigating to B of the same app must not clear it while B is in front.
     */
    fun activityDestroyed(apk: LoadedApk) {
        val left = liveActivities.compute(apk.name) { _, n -> if (n == null || n <= 1) null else n - 1 }
        if (left == null && preferred === apk) preferred = null
    }

    fun clearLast() {
        lastLoadedApk = null
    }

    val last: LoadedApk?
        get() = lastLoadedApk

    /**
     * The APK of the app currently in the foreground. The framework resolves classes by name through this
     * loader without saying for whom (LayoutInflater, fragment/state restore), and two loaded apps may both
     * bundle a library (androidx, OkHttp, ...): the foreground app's copy wins, instead of whichever was
     * loaded first. Only callers that go through the process/host loader depend on it (framework code given the
     * host's context or `Thread.contextClassLoader`; a hosted app's own context returns its own loader and
     * does not). Best effort: a background thread of another app still gets the foreground app's copy.
     * Set when a hosted activity is created or resumed, and cleared by [activityDestroyed] when the app's last
     * live activity is destroyed (then lookups fall back to first-match).
     */
    @Volatile var preferred: LoadedApk? = null

    override fun loadClass(name: String): Class<*> {
        preferred?.let {
            try {
                return it.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // not this app's: fall through to the others
            }
        }
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