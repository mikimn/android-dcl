package com.mikimn.apkloader.tier2

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.ProbeChannel
import com.mikimn.apkloader.testing.Tier2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real hook chain: a hosted activity navigates (ActivityTaskManagerHook
 * retargets to a proxy slot) and the *system's* own launch-mode logic must apply to the loaded
 * activity's declared `launchMode`. `fx-manifest`: MainActivity -> SecondActivity (`singleTop`),
 * which starts itself once more.
 */
@Tier2
@RunWith(AndroidJUnit4::class)
class NavigationLaunchModeTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    // A hosted fixture writes under its own per-package storage (docs/TESTING.md, "Per-package storage").
    private val probe = ProbeChannel(target, "nav", loadedPackage = "com.mikimn.fixture.manifest").also { it.clear() }

    @Test fun singleTopActivityReceivesOnNewIntentInsteadOfASecondInstance() {
        // A plain startActivity, not ActivityScenario: DCLActivity doesn't report the lifecycle
        // transitions ActivityScenario waits for (it drives the shadow activity's lifecycle itself),
        // and `DCLActivity.intentForAPK` only builds an Intent, which is safe (docs/TESTING.md).
        val intent = DCLActivity.intentForAPK(target, FixtureApks.install("fx-manifest.apk").path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        target.startActivity(intent)

        probe.awaitEvent("second.onNewIntent")
        // Give a wrongly created second instance time to show up before asserting its absence.
        Thread.sleep(1500)
        assertThat(probe.count("main.onCreate")).isEqualTo(1)
        assertThat(probe.count("second.onCreate")).isEqualTo(1)
        assertThat(probe.count("second.onNewIntent")).isEqualTo(1)
    }
}
