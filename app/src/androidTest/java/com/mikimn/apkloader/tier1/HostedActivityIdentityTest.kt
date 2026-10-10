package com.mikimn.apkloader.tier1

import android.content.Intent
import android.content.pm.PackageManager
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
import java.security.MessageDigest

/**
 * What a hosted app sees when it checks its own identity (#17): its own signing certificate, and
 * that it was not installed by anyone (rather than an exception, or the host's identity).
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedActivityIdentityTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    // A hosted fixture writes under its own per-package storage (docs/TESTING.md, "Per-package storage").
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }

    @Test fun hostedAppSeesItsOwnSigningCertificateAndNoInstaller() {
        val apk = FixtureApks.install("fx-hello.apk")
        target.startActivity(DCLActivity.intentForAPK(target, apk.path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        probe.awaitEvent("onResume")

        assertThat(probe.valueOf("identity.error")).isNull()

        // what the platform itself reads from the APK file: the fixture's own signer, not the host's
        val signer = target.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)!!
            .signingInfo!!.apkContentsSigners.single()
        val expected = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
        assertThat(probe.valueOf("sig.count")).isEqualTo("1")
        assertThat(probe.valueOf("sig.sha256")).isEqualTo(expected)

        // "not installed by anyone", not a NameNotFoundException and never a store
        assertThat(probe.valueOf("installer")).isEqualTo("null")
    }
}
