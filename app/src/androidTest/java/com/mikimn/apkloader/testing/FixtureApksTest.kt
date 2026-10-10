package com.mikimn.apkloader.testing

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

/**
 * Validates the fixture APKs themselves, using the framework's own parser rather than the
 * loader under test. If a later tier fails, this tells you whether the fixture or the loader is
 * to blame.
 */
@RunWith(AndroidJUnit4::class)
class FixtureApksTest {
    private data class Expected(val file: String, val pkg: String, val launcher: String)

    private val expected = listOf(
        Expected("fx-hello.apk", "com.mikimn.fixture.hello", "com.mikimn.fixture.hello.HelloActivity"),
        Expected("fx-resources.apk", "com.mikimn.fixture.resources", "com.mikimn.fixture.resources.ResourcesActivity"),
        Expected("fx-application.apk", "com.mikimn.fixture.application", "com.mikimn.fixture.application.AppActivity"),
        Expected("fx-manifest.apk", "com.mikimn.fixture.manifest", "com.mikimn.fixture.manifest.MainActivity"),
    )

    @Test fun allFixturesArePackaged() {
        assertThat(FixtureApks.available()).containsAtLeastElementsIn(expected.map { it.file })
    }

    @Test fun fixturesAreValidApksWithExpectedPackageAndLauncher() {
        val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        for (e in expected) {
            val apk = FixtureApks.install(e.file)
            val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_ACTIVITIES)
            assertThat(info).isNotNull()
            assertThat(info!!.packageName).isEqualTo(e.pkg)
            assertThat(info.activities.map { it.name }).contains(e.launcher)
        }
    }

    @Test fun fixturesAreNeverInstalledOnTheDevice() {
        // The whole point of the loader is running APKs without installing them; a fixture that
        // happened to be installed would make later tests pass for the wrong reason.
        val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        for (e in expected) {
            try {
                pm.getPackageInfo(e.pkg, 0)
                throw AssertionError("${e.pkg} is installed on the device; uninstall it so tests prove the no-install path")
            } catch (_: PackageManager.NameNotFoundException) {
                // expected
            }
        }
    }

    @Test fun resourcesFixtureCarriesItsAssetsAndResources() {
        ZipFile(FixtureApks.install("fx-resources.apk")).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertThat(names).containsAtLeast("AndroidManifest.xml", "resources.arsc", "assets/asset.txt", "res/raw/payload.txt")
            assertThat(names.any { it.endsWith(".dex") }).isTrue()
        }
    }
}
