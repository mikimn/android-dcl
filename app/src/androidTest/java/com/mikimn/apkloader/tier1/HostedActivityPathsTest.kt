package com.mikimn.apkloader.tier1

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.ProbeChannel
import com.mikimn.apkloader.testing.Tier1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/** A hosted app sees its *own* APK as its code path, not the host's (#19). */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedActivityPathsTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val loadedPackage = "com.mikimn.fixture.hello"
    // A hosted fixture writes under its own per-package storage (docs/TESTING.md, "Per-package storage").
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = loadedPackage).also { it.clear() }

    @Test fun codeResourceAndApplicationInfoPathsAreTheLoadedApksNotTheHosts() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        probe.awaitEvent("onResume")

        val code = probe.valueOf("path.code")!!
        assertThat(probe.valueOf("path.resource")).isEqualTo(code)
        assertThat(probe.valueOf("path.sourceDir")).isEqualTo(code)
        assertThat(probe.valueOf("path.publicSourceDir")).isEqualTo(code)

        // not the host's own APK...
        assertThat(code).isNotEqualTo(target.packageCodePath)
        assertThat(code).isNotEqualTo(target.applicationInfo.sourceDir)
        // ...but a real, reopenable APK that is the fixture (what a library that reopens itself relies on)
        assertThat(File(code).isFile).isTrue()
        ZipFile(code).use {
            assertThat(it.getEntry("AndroidManifest.xml")).isNotNull()
            assertThat(it.getEntry("classes.dex")).isNotNull()
        }
        assertThat(File(code).readBytes()).isEqualTo(FixtureApks.install("fx-hello.apk").readBytes())

        // and the data dir reported with it is the per-package one (#18), patched together with the code paths
        assertThat(probe.valueOf("path.dataDir")).isEqualTo(File(File(target.dataDir, "virtual"), loadedPackage).path)
    }
}
