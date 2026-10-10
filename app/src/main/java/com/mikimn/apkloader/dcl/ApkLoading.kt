package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.apk.ManifestAwarePlugin
import com.mikimn.apkloader.pm.PackageManagerAggregate
import com.mikimn.apkloader.utils.AssetReader
import java.io.File
import java.io.FileInputStream

/**
 * Loads an APK into the process's [FileTrackingClassLoader]. Usable from any [Context], so it can
 * run before a hosting activity exists.
 */
object ApkLoading {
    private const val TAG = "ApkLoading"

    /**
     * Loads [apkName] (an absolute device path, else a bundled asset name) unless it already is,
     * and registers its manifest with the package-manager plugins. Idempotent.
     */
    fun load(context: Context, loader: FileTrackingClassLoader, apkName: String): LoadedApk {
        loader.apkFile(apkName)?.let { return it }
        // This archive was already rejected in this process: don't extract it again.
        rejected[apkName]?.let { throw it }

        val reader = AssetReader(context)
        val apkFile = File(apkName)
        val apkData = if (apkFile.exists()) {
            FileInputStream(apkFile).use { reader.readStream(it) }
        } else {
            reader.readFile(apkName)
        }

        val loadedApk = loader.addApkFile(apkName, apkData, context.resources)

        (context.packageManager as? PackageManagerAggregate)
            ?.addPlugin(ManifestAwarePlugin(loadedApk.manifestReader!!))

        // Implicit broadcasts for the app's manifest receivers (explicit ones are rewritten by
        // ActivityManagerHook). Best effort: never fail the load over a receiver.
        try {
            ReceiverRegistry.register(context, loadedApk)
        } catch (e: Throwable) {
            Log.w(TAG, "Receiver registration failed for $apkName", e)
        }

        return loadedApk
    }

    // Archives rejected as hostile or malformed, so a second activity instance (or DCLActivity.onCreate
    // after a failed preload) reports the same failure instead of extracting the APK again. That is
    // our own zip-slip guard (SecurityException) or, on newer Android, ZipFile itself refusing a
    // traversal entry while opening the archive (ZipException).
    private val rejected = java.util.concurrent.ConcurrentHashMap<String, Exception>()

    /**
     * The APK a hosted-activity [intent] refers to (an explicit launch, or a navigation hop), if any.
     * Same precedence as `DCLActivity.onCreate`.
     */
    fun apkNameOf(intent: Intent?): String? =
        intent?.getStringExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME)
            ?: intent?.getStringExtra(DCLActivity.KEY_LOADED_APK_NAME)

    /**
     * Loads the APK [intent] refers to as early as possible. Needed when Android restores a hosted
     * activity into a fresh process: the platform unmarshals the saved-state Bundle (which holds the
     * loaded app's own Parcelables) with this loader *before* [DCLActivity.onCreate] runs, so the APK
     * must already be registered by then or the restore dies in `BadParcelableException`.
     *
     * Best effort: a failure is logged and left for [DCLActivity.onCreate] to report as before.
     *
     * @return the loaded APK, or null when the intent names none or loading failed.
     */
    fun preload(context: Context, loader: ClassLoader, intent: Intent?): LoadedApk? {
        val apkName = apkNameOf(intent) ?: return null
        val tracking = loader as? FileTrackingClassLoader ?: return null
        return try {
            load(context, tracking, apkName)
        } catch (e: Throwable) {
            if (e is SecurityException || e is java.util.zip.ZipException) rejected[apkName] = e as Exception
            Log.e(TAG, "Preload of $apkName failed; DCLActivity.onCreate will report it", e)
            null
        }
    }

    /** The process's `Application`, which exists before any activity is instantiated. */
    @Suppress("DiscouragedPrivateApi")
    fun currentApplication(): Application? = try {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    } catch (e: Throwable) {
        Log.e(TAG, "No current Application", e)
        null
    }
}
