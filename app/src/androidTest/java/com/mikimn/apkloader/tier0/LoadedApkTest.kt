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

    // ---- on-disk APK, file-backed loader, ApplicationInfo paths -------------------------------

    @Test fun apkIsKeptOnDiskReadOnlyAndMatchesTheInput() {
        val apk = fx.load("fx-hello.apk")
        val onDisk = apk.paths!!.apk
        assertThat(onDisk.isFile).isTrue()
        assertThat(onDisk.canWrite()).isFalse()
        assertThat(onDisk.readBytes()).isEqualTo(FixtureApks.install("fx-hello.apk").readBytes())
        assertThat(onDisk.parentFile).isEqualTo(File(System.getProperty("java.io.tmpdir")!!))
    }

    @Test fun classesAreLoadedByAFileBackedDexClassLoader() {
        val apk = fx.load("fx-hello.apk")
        assertThat(apk.loader).isInstanceOf(dalvik.system.DexClassLoader::class.java)
        assertThat(apk.loader.toString()).contains(apk.paths!!.apk.path)
    }

    @Test fun applicationInfoPointsAtTheLoadedApk() {
        val apk = fx.load("fx-hello.apk")
        val info = apk.manifestReader!!.getApplicationInfo()
        assertThat(info.sourceDir).isEqualTo(apk.paths!!.apk.path)
        assertThat(info.publicSourceDir).isEqualTo(apk.paths!!.apk.path)
        assertThat(info.splitSourceDirs).isNull() // loaded from assets: no installed splits
        // ...and it is a real, reopenable APK (what a library that reopens itself relies on).
        java.util.zip.ZipFile(info.sourceDir).use { assertThat(it.getEntry("AndroidManifest.xml")).isNotNull() }
        assertThat(info.sourceDir).isNotEqualTo(fx.targetContext.applicationInfo.sourceDir)
    }

    @Test fun reloadingTheSameNameDoesNotBreakAnEarlierLoader() {
        val first = fx.load("fx-hello.apk")
        // Replaces the on-disk file (by rename) while `first`'s loader still reads the old one.
        fx.loadedApk("fx-hello.apk").load(FixtureApks.install("fx-hello.apk").readBytes(), fx.resources)
        assertThat(first.loadClass("com.mikimn.fixture.common.Probe").classLoader).isSameInstanceAs(first.loader)
    }

    @Test fun failedLoadLeavesNoApkBehind() {
        val bad = fx.loadedApk("not-an-apk.apk")
        assertThrows(Exception::class.java) { bad.load("definitely not a zip".toByteArray(), fx.resources) }
        assertThat(bad.paths).isNull()
        assertThat(copiesOf("not-an-apk.apk")).isEmpty()
    }

    private val tmpDir get() = File(System.getProperty("java.io.tmpdir")!!)
    private fun copiesOf(name: String) =
        tmpDir.listFiles { f -> f.isFile && f.name.startsWith("cache-$name.") && f.name.endsWith(".apk") }!!.toList()

    @Test fun eachLoadGetsItsOwnApkFile() {
        val first = fx.load("fx-hello.apk")
        val second = fx.loadedApk("fx-hello.apk").also { it.load(FixtureApks.install("fx-hello.apk").readBytes(), fx.resources) }
        assertThat(second.paths!!.apk).isNotEqualTo(first.paths!!.apk)
        assertThat(first.paths!!.apk.exists()).isTrue() // an earlier loader's file is never replaced
        assertThat(copiesOf("fx-hello.apk")).containsAtLeast(first.paths!!.apk, second.paths!!.apk)
    }

    @Test fun aFailedReloadKeepsTheEarlierLoadedApksFile() {
        val first = fx.load("fx-hello.apk")
        val bad = fx.loadedApk("fx-hello.apk")
        assertThrows(Exception::class.java) { bad.load("not a zip".toByteArray(), fx.resources) }
        assertThat(first.paths!!.apk.exists()).isTrue()
        assertThat(first.manifestReader!!.getApplicationInfo().sourceDir).isEqualTo(first.paths!!.apk.path)
        assertThat(first.loadClass(helloActivity).classLoader).isSameInstanceAs(first.loader)
        assertThat(copiesOf("fx-hello.apk")).containsExactly(first.paths!!.apk)
    }

    // Leftovers of earlier processes (this test runs in a fresh process, so none of the files
    // below were written by this one) are removed on the first load; extraction dirs are not.
    @Test fun staleApkCopiesAndPartFilesAreSweptOnTheFirstLoad() {
        val staleApk = File(tmpDir, "cache-stale-one.apk.3.apk").apply { writeText("x") }
        val stalePart = File(tmpDir, "cache-stale-one.apk.3.apk123.part").apply { writeText("x") }
        val unrelated = File(tmpDir, "unrelated-file.apk").apply { writeText("x") }
        val extractionDir = File(tmpDir, "cache-stale-dir.apk").apply { mkdirs() }
        try {
            val first = fx.load("fx-hello.apk")
            assertThat(staleApk.exists()).isFalse()
            assertThat(stalePart.exists()).isFalse()
            assertThat(unrelated.exists()).isTrue()
            assertThat(extractionDir.isDirectory).isTrue()
            assertThat(first.paths!!.apk.exists()).isTrue()
            // only the first load of a process sweeps: a later leftover survives
            val late = File(tmpDir, "cache-late.apk.1.apk").apply { writeText("x") }
            fx.load("fx-resources.apk")
            assertThat(late.exists()).isTrue()
            late.delete()
        } finally {
            unrelated.delete(); extractionDir.delete()
        }
    }

    // ---- resources --------------------------------------------------------------------------

    @Test fun loadedResourcesResolveByNameWithTheAppsOwnPackageId() {
        fx.load("fx-resources.apk")
        val pkg = "com.mikimn.fixture.resources"
        val titleId = fx.id(pkg, "string", "title")
        assertThat(titleId).isNotEqualTo(0)
        assertThat(titleId ushr 24).isEqualTo(0x7f)
        // The fixture ships a values-night variant of this string, so the right answer depends on the
        // device's current dark-mode setting (resource qualifiers resolve for the loaded APK too).
        val night = fx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        assertThat(fx.resources.getString(titleId))
            .isEqualTo(if (night) "fx-resources title (night)" else "fx-resources title")
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

    private fun zipWithEntry(name: String): ByteArray = java.io.ByteArrayOutputStream().also { bytes ->
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(1); zip.closeEntry()
        }
    }.toByteArray()

    // A hostile archive is rejected either by our own zip-slip guard (SecurityException) or, on newer
    // Android (observed on API 36), already by ZipFile while it opens the archive (ZipException:
    // invalid entry path). Which one depends on the OS; what matters is that load() fails loudly
    // and, in the tests below, that nothing is written outside, registered, or left behind.
    private fun assertHostileRejected(block: () -> Unit) {
        val e = assertThrows(Exception::class.java) { block() }
        assertThat(e is SecurityException || e is java.util.zip.ZipException).isTrue()
    }

    @Test fun apkWithPathTraversalEntryFailsLoudlyAndWritesNothingOutside() {
        val evil = zipWithEntry("../../escaped.txt")
        // The traversal is resolved against the per-APK cache dir (<tmpdir>/cache-evil.apk), so
        // "../../escaped.txt" lands in the directory *above* java.io.tmpdir. Built lexically
        // because the cache dir does not exist yet (the OS can't resolve ".." through it).
        // Positive control: that directory is writable by this app, so without the fix the file
        // really would have been created there (the check is not vacuous).
        val cache = cacheDir("evil.apk")
        val escapeDir = cache.absoluteFile.parentFile!!.parentFile!!
        val escapeTarget = File(escapeDir, "escaped.txt")
        assertThat(escapeDir.canWrite()).isTrue()
        escapeTarget.delete()

        assertHostileRejected {
            fx.loadedApk("evil.apk").load(evil, fx.resources)
        }

        val escaped = escapeTarget.exists()
        escapeTarget.delete() // never leave it behind, even if the assertion below fails
        assertThat(escaped).isFalse()
    }

    @Test fun hostileApkLeavesNoHalfExtractedCacheDir() {
        assertHostileRejected {
            fx.loadedApk("evil.apk").load(zipWithEntry("../../escaped.txt"), fx.resources)
        }
        assertThat(cacheDir("evil.apk").exists()).isFalse()
    }

    @Test fun fileEntryResolvingToTheExtractionDirIsRejectedAndCleanedUp() {
        // "a/.." is a *file* entry that resolves to the extraction dir itself
        assertHostileRejected {
            fx.loadedApk("evil.apk").load(zipWithEntry("a/.."), fx.resources)
        }
        assertThat(cacheDir("evil.apk").exists()).isFalse()
    }

    @Test fun hostileApkIsNeverRegisteredWithTheClassLoader() {
        assertHostileRejected {
            fx.loader.addApkFile("evil.apk", zipWithEntry("../../escaped.txt"), fx.resources)
        }
        assertThat(fx.loader.apkFile("evil.apk")).isNull()
        assertThat(fx.loader.last).isNull()
        assertThat(fx.loader.ownerOf("com.example.Anything")).isNull()
        assertThat(fx.loader.resourcesLoader.providers).isEmpty()
    }

    @Test fun failedLoadLeavesTheLoadedApkUnusable() {
        val apk = fx.loadedApk("evil.apk")
        assertHostileRejected { apk.load(zipWithEntry("../../escaped.txt"), fx.resources) }
        assertThat(apk.loader).isNull()
        assertThrows(IllegalStateException::class.java) { apk.loadClass("com.example.Anything") }
    }
}
