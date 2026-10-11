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
 * A hosted app starts, binds, unbinds and stops its own service (#23) through the real hook chain:
 * `fx-hello`'s activity talks to its `HelloService`, which the system can't resolve for a loaded app.
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedServicesTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    // A hosted fixture writes under its own per-package storage (docs/TESTING.md, "Per-package storage").
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }

    @Test fun serviceIsStartedBoundUnboundAndStoppedWithItsFullLifecycle() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        probe.awaitEvent("service.onDestroy")
        Thread.sleep(500) // a duplicate would show up by now

        val lifecycle = probe.events().filter { it.startsWith("service.on") }
        // created exactly once (start and bind share one instance), bound, unbound, destroyed last
        assertThat(lifecycle).containsExactly("service.onCreate", "service.onBind", "service.onUnbind", "service.onDestroy").inOrder()

        // onStartCommand got the sender's intent for the *loaded* class, none of the loader's routing extras
        assertThat(probe.valueOf("service.start")).startsWith("start:")
        assertThat(probe.valueOf("service.start.component")).isEqualTo("com.mikimn.fixture.hello.HelloService")
        assertThat(probe.valueOf("service.start.routingExtras")).isEqualTo("false")

        // the client got the loaded service's own Binder, usable in-process
        assertThat(probe.valueOf("service.bound.answer")).isEqualTo("42")
    }

    // stopSelf() builds the ComponentName from the service's own context and must reach the slot's record.
    @Test fun aServiceThatCallsStopSelfIsActuallyStopped() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path)
                .putExtra("fx.stopself", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        probe.awaitEvent("service.stopSelf")
        probe.awaitEvent("service.onDestroy")
        assertThat(probe.events().filter { it.startsWith("service.on") }).containsExactly("service.onCreate", "service.onDestroy").inOrder()
    }
}
