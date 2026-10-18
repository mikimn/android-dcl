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
 * PendingIntents created by a hosted app (#25) - the kind a notification, alarm or shortcut hands to the system
 * to fire later, from outside the process. The system can only resolve what the host manifest declares, so the
 * target is rewritten when the PendingIntent is created (`IActivityManager.getIntentSender`).
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedPendingIntentsTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }

    @Test fun activityServiceAndBroadcastPendingIntentsReachTheLoadedApp() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path)
                .putExtra("fx.pending", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        // the system fired each PendingIntent: a second HelloActivity instance, the service, the receiver
        probe.awaitEvent("receiver.pending")
        probe.awaitEvent("service.onCreate")
        probe.awaitEvents("a second activity launched by the PendingIntent") { events -> events.count { it == "onCreate" } >= 2 }

        assertThat(probe.valueOf("pending.error")).isNull()
        assertThat(probe.valueOf("intent.extra")).isEqualTo("pending-activity")
        assertThat(probe.valueOf("service.start")).startsWith("pending:")
        assertThat(probe.valueOf("service.start.routingExtras")).isEqualTo("false")
    }
}
