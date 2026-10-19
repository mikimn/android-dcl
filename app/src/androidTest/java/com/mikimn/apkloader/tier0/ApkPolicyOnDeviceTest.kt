package com.mikimn.apkloader.tier0

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ApkPolicy
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The release-build rules of [ApkPolicy] (#55) against what this device really reports: a debug build allows
 * anything, so the interesting branch is run directly. Needs the host to be able to list installed packages.
 */
@Tier0
@RunWith(AndroidJUnit4::class)
class ApkPolicyOnDeviceTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun anInstalledPackagesApkAndABundledAssetAreAllowed() {
        // the host's own installed APK stands for "any package the user can launch from the grid"
        assertThat(ApkPolicy.allowedInRelease(context, context.applicationInfo.sourceDir)).isTrue()
        val asset = context.assets.list("").orEmpty().first { it.endsWith(".apk") }
        assertThat(ApkPolicy.allowedInRelease(context, asset)).isTrue()
    }

    @Test fun aFileThatIsNotAnInstalledPackagesApkIsRefused() {
        // exists and is readable by the host, but nobody installed it: exactly the attacker's file
        val fixture = FixtureApks.install("fx-hello.apk")
        assertThat(ApkPolicy.allowedInRelease(context, fixture.path)).isFalse()
        assertThat(ApkPolicy.allowedInRelease(context, "/sdcard/Download/evil.apk")).isFalse()
        assertThat(ApkPolicy.allowedInRelease(context, "evil.apk")).isFalse()
    }
}
