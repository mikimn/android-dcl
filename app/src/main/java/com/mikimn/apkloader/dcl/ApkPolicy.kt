package com.mikimn.apkloader.dcl

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Which APKs the loader will run. Everything the loader runs executes in this process with the host's uid and
 * every permission it holds, and the name of the APK arrives in places other apps can reach (intent extras of an
 * exported entry point, a `PendingIntent` another app holds and can `fillIn`, a persisted job). So the *value* is
 * checked once, at the choke point every loading path goes through ([ApkLoading.load]), instead of per caller:
 *
 * - a bundled asset (an `.apk` in `assets`), or
 * - the base/split APK of a package the system reports as installed (everything the user can already launch from
 *   the host's own grid), compared by canonical path so `..` and symlinks cannot steer it, or
 * - anything, in a **debuggable** build (tests, `scripts/test-apk.sh` and manual work push APKs anywhere).
 *
 * Pure on its inputs, so the rules are unit-testable; [allowed] gathers them from a [Context].
 */
object ApkPolicy {
    fun isAllowed(
        apkName: String,
        debuggable: Boolean,
        bundledAssets: Set<String>,
        installedApks: Set<String>
    ): Boolean {
        if (debuggable) return true
        if (apkName in bundledAssets) return true
        // Anything that names a file (rather than an asset) must be an installed package's own APK.
        return canonical(apkName)?.let { it in installedApks } == true
    }

    /** [isAllowed] with the facts read from [context] (assets, installed packages, debuggable flag). */
    fun allowed(context: Context, apkName: String): Boolean {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return debuggable || allowedInRelease(context, apkName)
    }

    /** The rules of a release build, whatever this build is: what [allowed] applies when not debuggable. */
    fun allowedInRelease(context: Context, apkName: String): Boolean {
        val assets = context.assets.list("").orEmpty().filter { it.endsWith(".apk") }.toSet()
        val installed = HashSet<String>()
        @Suppress("DEPRECATION")
        for (info in context.packageManager.getInstalledApplications(0)) {
            listOfNotNull(info.sourceDir, info.publicSourceDir).plus(info.splitSourceDirs.orEmpty())
                .mapNotNullTo(installed) { canonical(it) }
        }
        return isAllowed(apkName, debuggable = false, bundledAssets = assets, installedApks = installed)
    }

    /** The canonical form of a file path, or null if [path] is not an absolute path. */
    fun canonical(path: String): String? =
        if (path.startsWith("/")) try { File(path).canonicalPath } catch (_: java.io.IOException) { null } else null
}
