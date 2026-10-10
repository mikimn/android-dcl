package com.mikimn.apkloader.testing

import android.content.Context
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.dcl.FileTrackingClassLoader
import java.io.File

/**
 * Loads fixture APKs through the real loader classes, without going through DCLActivity.
 *
 * Builds its **own** [FileTrackingClassLoader] rather than using the host's, so a test never
 * touches host statics (see "Two copies of host classes" in docs/TESTING.md). The base loader is
 * the test APK's loader, so - as in production, where the base is the app's PathClassLoader - the
 * loaded APK's parent is the boot loader and it cannot see host or test classes.
 */
class FixtureLoader {
    val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val resources: Resources get() = targetContext.resources
    val baseClassLoader: ClassLoader = InstrumentationRegistry.getInstrumentation().context.classLoader
    val loader = FileTrackingClassLoader(baseClassLoader)

    /** Loads `fixtures/<fixture>` under the bare asset-style name `<fixture>` (no parent dir). */
    fun load(fixture: String): LoadedApk =
        loader.addApkFile(fixture, FixtureApks.install(fixture).readBytes(), resources)

    /** Loads a fixture from an absolute path, like an installed app's `base.apk`. */
    fun loadFromPath(apk: File): LoadedApk =
        loader.addApkFile(apk.path, apk.readBytes(), resources)

    /**
     * A bare [LoadedApk] (no [FileTrackingClassLoader]) against the same base loader.
     *
     * The parent is deliberately `baseClassLoader.parent`, not the base itself: this is what
     * [FileTrackingClassLoader.addApkFile] passes in production (where the base is the app's
     * PathClassLoader and its parent is the boot loader), so the loaded code sees framework
     * classes but not the host's or the test's. Passing the base would let loaded code see them.
     */
    fun loadedApk(name: String): LoadedApk = LoadedApk(name, baseClassLoader.parent)

    fun id(pkg: String, type: String, name: String): Int = resources.getIdentifier(name, type, pkg)

    /**
     * What `fx-resources`'s `title` string resolves to on *this device right now*: the fixture
     * ships a `values-night` variant, so the answer follows the device's dark-mode setting. Tests
     * must not assume light mode (they would fail on a phone that happens to be in dark mode).
     */
    fun expectedFxResourcesTitle(resources: Resources = this.resources): String {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (night) "fx-resources title (night)" else "fx-resources title"
    }
}
