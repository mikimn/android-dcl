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

/**
 * A hosted app's own `sendBroadcast` calls reach its manifest receivers (#24), through the real
 * hook chain: `fx-hello`'s activity sends an explicit, an implicit and a package-restricted
 * broadcast to its own `HelloReceiver`.
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedReceiversTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    // A hosted fixture writes under its own per-package storage (docs/TESTING.md, "Per-package storage").
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }

    @Test fun explicitImplicitAndPackageRestrictedBroadcastsAllReachTheLoadedReceiver() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (via in listOf("explicit", "implicit", "package")) probe.awaitEvent("receiver.$via")
        Thread.sleep(1500) // a duplicate delivery would show up by now
        for (via in listOf("explicit", "implicit", "package")) {
            assertThat(probe.count("receiver.$via")).isEqualTo(1)
            // The loaded receiver sees its own component and none of the loader's routing extras...
            assertThat(probe.valueOf("receiver.component.$via")).isEqualTo("com.mikimn.fixture.hello/com.mikimn.fixture.hello.HelloReceiver")
            assertThat(probe.valueOf("receiver.routingExtras.$via")).isEqualTo("false")
            // The receiver sees the package its sender asked for: the loaded one for the package-restricted
            // broadcast (the system was sent a host-restricted copy), none for the others.
            assertThat(probe.valueOf("receiver.package.$via"))
                .isEqualTo(if (via == "package") "com.mikimn.fixture.hello" else "null")
            // ...and runs with the loaded package's own context (per-package storage), not the host's.
            assertThat(probe.valueOf("receiver.contextClass.$via")).isEqualTo("com.mikimn.apkloader.dcl.DCLContext")
        }
    }
}
