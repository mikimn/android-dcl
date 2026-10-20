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
 * The routing secret must be the same whichever Application is "current" (#25 review): a loaded app with its own
 * Application class makes the current one its shadow Application, whose context redirects preferences into that
 * app's own storage. If signing and verifying read different secrets, every launch after a process restart would be
 * refused. `fx-application` has such an Application class.
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class RoutingMacSecretTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbeChannel(target, "fx-application", loadedPackage = "com.mikimn.fixture.application").also { it.clear() }

    // Host classes are reached through the running app's class loader: calling them directly would touch the test APK's
    // own copy, with its own (empty) static state (docs/TESTING.md, "Two copies of host classes").
    private val loader = target.classLoader
    private fun host(name: String): Any = loader.loadClass("com.mikimn.apkloader.dcl.$name").getField("INSTANCE").get(null)!!
    private val macs get() = host("RoutingMac")
    private fun hostApplication(): android.app.Application =
        host("HostApplication").let { it.javaClass.getMethod("getInstance").invoke(it) as android.app.Application }
    private fun currentApplication(): android.app.Application =
        host("ApkLoading").let { it.javaClass.getMethod("currentApplication").invoke(it) as android.app.Application }
    private fun secretOf(context: android.content.Context): ByteArray =
        macs.javaClass.getMethod("secret", android.content.Context::class.java).invoke(macs, context) as ByteArray
    private fun sign(intent: Intent) { macs.javaClass.getMethod("sign", Intent::class.java).invoke(macs, intent) }
    private fun verify(intent: Intent): Boolean = macs.javaClass.getMethod("verify", Intent::class.java).invoke(macs, intent) as Boolean

    @Test fun theSecretIsTheHostsWhateverApplicationIsCurrent() {
        val hostApp = hostApplication()
        val before = secretOf(hostApp)
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-application.apk").path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        probe.awaitEvent("app.onCreate")

        // the scenario of the bug: the process's "current" Application is now the loaded app's shadow Application
        val current = currentApplication()
        assertThat(current).isNotSameInstanceAs(hostApp)
        assertThat(current.javaClass.name).isEqualTo("com.mikimn.fixture.application.FxApplication")

        // signing and verifying (what the rewrite and the factory do) agree, and use the same persisted secret
        val intent = Intent().putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.example.Target").putExtra(DCLActivity.KEY_LOADED_APK_NAME, "/x/base.apk")
        sign(intent)
        assertThat(verify(intent)).isTrue()
        // the signature was made with the HOST's persisted secret: exactly what a restarted process (whose current
        // Application is the host's again) would verify it against. With the old per-"current Application" lookup this
        // was the shadow Application's secret and a restart refused every signed launch.
        val expected = macs.javaClass.getMethod("mac", ByteArray::class.java, Intent::class.java).invoke(macs, before, intent) as String
        assertThat(intent.getStringExtra("dclRoutingMac")).isEqualTo(expected)
        assertThat(secretOf(hostApp)).isEqualTo(before)
        // the shadow Application's own context would have read another preferences file entirely
        assertThat(secretOf(current)).isNotEqualTo(before)
        // persisted in the host's real preferences file, i.e. what a restarted process reads back
        val persisted = target.getSharedPreferences("dcl_routing", android.content.Context.MODE_PRIVATE).getString("secret", null)
        assertThat(persisted).isNotNull()
        assertThat(android.util.Base64.decode(persisted, android.util.Base64.NO_WRAP)).isEqualTo(before)
    }
}
