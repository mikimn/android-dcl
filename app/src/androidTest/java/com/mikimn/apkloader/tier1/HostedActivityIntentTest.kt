package com.mikimn.apkloader.tier1

import android.content.Intent
import android.net.Uri
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

/** A hosted activity reads what its launcher passed, through the real DCLActivity hosting path. */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedActivityIntentTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbeChannel(target, "fx-hello").also { it.clear() }

    @Test fun shadowActivityGetsExtrasActionAndDataButNotTheLoadersOwnExtras() {
        val intent = DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path)
            .setAction("fx.ACTION")
            .setData(Uri.parse("https://example.com/item/7"))
            .putExtra("fx.extra", "from-launcher")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        target.startActivity(intent)

        probe.awaitEvent("onResume")
        assertThat(probe.valueOf("intent.action")).isEqualTo("fx.ACTION")
        assertThat(probe.valueOf("intent.data")).isEqualTo("https://example.com/item/7")
        assertThat(probe.valueOf("intent.extra")).isEqualTo("from-launcher")
        assertThat(probe.valueOf("intent.component")).isEqualTo("com.mikimn.fixture.hello.HelloActivity")
        assertThat(probe.valueOf("intent.hostExtras")).isEqualTo("false")
    }
}
