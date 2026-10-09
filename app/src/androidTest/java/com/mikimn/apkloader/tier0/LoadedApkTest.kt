package com.mikimn.apkloader.tier0

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@Tier0
@RunWith(AndroidJUnit4::class)
class LoadedApkTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val helloActivity = "com.mikimn.fixture.hello.HelloActivity"

    private fun cacheDir(name: String) =
        File(System.getProperty("java.io.tmpdir")!!, "cache-" + name.replace(File.separatorChar, '_'))

    // ---- class loading ----------------------------------------------------------------------

    @Test fun loadClassBeforeLoadFails() {
        val apk = fx.loadedApk("never-loaded.apk")
        assertThrows(IllegalStateException::class.java) { apk.loadClass(helloActivity) }
    }

    @Test fun loadsFixtureClassDefinedByTheApksOwnLoader() {
        val apk = fx.load("fx-hello.apk")
        val cls = apk.loadClass(helloActivity)
        assertThat(cls.classLoader).isSameInstanceAs(apk.loader)
        assertThat(cls.superclass!!.name).isEqualTo("android.app.Activity")
    }

    @Test fun loadsClassesFromEveryDexInAMultiDexApk() {
        // The debug fixtures are split over several classes*.dex files (a class and its helper
        // may land in different ones); everything must be reachable.
        val apk = fx.load("fx-hello.apk")
        for (name in listOf(helloActivity, "com.mikimn.fixture.common.Probe")) {
            assertThat(apk.loadClass(name).classLoader).isSameInstanceAs(apk.loader)
        }
    }

    @Test fun unknownClassIsClassNotFound() {
        val apk = fx.load("fx-hello.apk")
        assertThrows(ClassNotFoundException::class.java) { apk.loadClass("com.example.DoesNotExist") }
    }

    @Test fun frameworkClassesResolveThroughTheParent() {
        val apk = fx.load("fx-hello.apk")
        assertThat(apk.loadClass("android.app.Activity")).isSameInstanceAs(android.app.Activity::class.java)
    }

    // The loaded code must not see the host's (or test's) classes: they are separate code.
    // This documents the isolation the whole design relies on (baseClassLoader.parent).
    @Test fun loadedApkCannotSeeHostOrTestClasses() {
        val apk = fx.load("fx-hello.apk")
        assertThrows(ClassNotFoundException::class.java) { apk.loadClass("com.mikimn.apkloader.dcl.DCLActivity") }
        assertThrows(ClassNotFoundException::class.java) { apk.loadClass("com.mikimn.apkloader.testing.FixtureLoader") }
    }

    // ---- extraction & cache dir -------------------------------------------------------------

    @Test fun extractsApkIntoPerApkCacheDir() {
        fx.load("fx-hello.apk")
        val dir = cacheDir("fx-hello.apk")
        assertThat(dir.isDirectory).isTrue()
        assertThat(dir.list()!!.toList()).containsAtLeast("AndroidManifest.xml", "classes.dex")
    }

    @Test fun absoluteDevicePathGetsOneFlatCacheDirNotANestedTree() {
        val path = FixtureApks.install("fx-hello.apk")
        fx.loadFromPath(path)
        val dir = cacheDir(path.path)
        assertThat(dir.isDirectory).isTrue()
        assertThat(dir.name).doesNotContain(File.separator)
        assertThat(dir.list()!!.toList()).contains("AndroidManifest.xml")
        // regression: the path used to be treated as nested dirs under a shared top-level "cache-"
        assertThat(File(System.getProperty("java.io.tmpdir")!!, "cache-").exists()).isFalse()
    }

    @Test fun reloadingSameNameClearsStaleFiles() {
        fx.load("fx-hello.apk")
        val stale = File(cacheDir("fx-hello.apk"), "stale-from-older-build.txt").apply { writeText("old") }
        assertThat(stale.exists()).isTrue()

        // a new LoadedApk for the same name (e.g. after a rebuild / new process) re-extracts clean
        fx.loadedApk("fx-hello.apk").load(FixtureApks.install("fx-hello.apk").readBytes(), fx.resources)
        assertThat(stale.exists()).isFalse()
        assertThat(File(cacheDir("fx-hello.apk"), "AndroidManifest.xml").exists()).isTrue()
    }

    @Test fun differentApksGetDifferentCacheDirs() {
        fx.load("fx-hello.apk")
        fx.load("fx-resources.apk")
        assertThat(cacheDir("fx-hello.apk").list()!!.toList()).doesNotContain("assets")
        assertThat(cacheDir("fx-resources.apk").list()!!.toList()).contains("assets")
    }

    // ---- resources --------------------------------------------------------------------------

    @Test fun loadedResourcesResolveByNameWithTheAppsOwnPackageId() {
        fx.load("fx-resources.apk")
        val pkg = "com.mikimn.fixture.resources"
        val titleId = fx.id(pkg, "string", "title")
        assertThat(titleId).isNotEqualTo(0)
        assertThat(titleId ushr 24).isEqualTo(0x7f)
        assertThat(fx.resources.getString(titleId)).isEqualTo("fx-resources title")
    }

    @Test fun hostAndLoadedPackageIdsDoNotCollide() {
        fx.load("fx-resources.apk")
        val hostId = fx.id(fx.targetContext.packageName, "string", "app_name")
        val loadedId = fx.id("com.mikimn.fixture.resources", "string", "title")
        assertThat(hostId ushr 24).isEqualTo(0x8f)
        assertThat(loadedId ushr 24).isEqualTo(0x7f)
        // and the host's own resources still resolve after the loader is added
        assertThat(fx.resources.getString(hostId)).isNotEmpty()
    }

    @Test fun loadedApkOtherResourceKindsResolve() {
        fx.load("fx-resources.apk")
        val pkg = "com.mikimn.fixture.resources"
        assertThat(fx.resources.getColor(fx.id(pkg, "color", "accent"), null)).isEqualTo(0xFF112233.toInt())
        assertThat(fx.resources.getDimensionPixelSize(fx.id(pkg, "dimen", "gap"))).isGreaterThan(0)
        assertThat(fx.resources.getQuantityString(fx.id(pkg, "plurals", "items"), 3, 3)).isEqualTo("3 items")
        val raw = fx.resources.openRawResource(fx.id(pkg, "raw", "payload")).bufferedReader().readLine()
        assertThat(raw).isEqualTo("raw-payload")
    }

    @Test fun loadedApkAssetsAreReadable() {
        fx.load("fx-resources.apk")
        val text = fx.resources.assets.open("asset.txt").bufferedReader().readLine()
        assertThat(text).isEqualTo("asset-payload")
    }

    @Test fun resourcesProviderIsCreated() {
        assertThat(fx.load("fx-hello.apk").resourcesProvider).isNotNull()
    }

    @Test fun manifestReaderIsCreated() {
        assertThat(fx.load("fx-hello.apk").manifestReader).isNotNull()
    }

    // ---- split APKs -------------------------------------------------------------------------

    private fun installDir(name: String) = File(fx.targetContext.filesDir, "install-$name").apply {
        deleteRecursively(); mkdirs()
    }

    @Test fun splitConfigSiblingsAreExtractedAndTheirResourcesLoaded() {
        val dir = installDir("with-split")
        val base = File(dir, "base.apk").apply { writeBytes(FixtureApks.install("fx-hello.apk").readBytes()) }
        // any valid APK works as a stand-in split; fx-resources carries resources we can look up
        File(dir, "split_config.xxhdpi.apk").writeBytes(FixtureApks.install("fx-resources.apk").readBytes())

        fx.loadFromPath(base)

        val splitOut = File(cacheDir(base.path), "split_config.xxhdpi.apk")
        assertThat(splitOut.isDirectory).isTrue()
        assertThat(splitOut.list()!!.toList()).contains("AndroidManifest.xml")
        assertThat(fx.id("com.mikimn.fixture.resources", "string", "title")).isNotEqualTo(0)
    }

    @Test fun nonSplitSiblingsAreIgnored() {
        val dir = installDir("with-decoys")
        val base = File(dir, "base.apk").apply { writeBytes(FixtureApks.install("fx-hello.apk").readBytes()) }
        File(dir, "other.apk").writeBytes(FixtureApks.install("fx-resources.apk").readBytes())
        File(dir, "split_not_config.apk").writeBytes(FixtureApks.install("fx-resources.apk").readBytes())

        fx.loadFromPath(base)

        assertThat(File(cacheDir(base.path), "other.apk").exists()).isFalse()
        assertThat(File(cacheDir(base.path), "split_not_config.apk").exists()).isFalse()
        assertThat(fx.id("com.mikimn.fixture.resources", "string", "title")).isEqualTo(0)
    }

    @Test fun bareNameHasNoSiblingsToDiscover() {
        // asset-style names have no parent dir; load must not throw or look elsewhere
        fx.load("fx-hello.apk")
        assertThat(cacheDir("fx-hello.apk").list()!!.none { it.startsWith("split_config") }).isTrue()
    }

    // ---- hostile input (zip-slip fix, end to end) -------------------------------------------

    @Test fun apkWithPathTraversalEntryFailsLoudly() {
        val evil = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../../escaped.txt")); zip.write(1); zip.closeEntry()
            }
        }.toByteArray()
        assertThrows(SecurityException::class.java) {
            fx.loadedApk("evil.apk").load(evil, fx.resources)
        }
        assertThat(File(System.getProperty("java.io.tmpdir")!!, "../escaped.txt").exists()).isFalse()
    }
}
