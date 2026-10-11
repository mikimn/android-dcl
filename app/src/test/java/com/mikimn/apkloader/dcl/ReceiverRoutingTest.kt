package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ReceiverRouting.Route
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ReceiverRoutingTest {
    private val host = "com.mikimn.apkloader"
    private val loadedPkg = "com.example.loaded"
    private val apk = "/data/app/loaded/base.apk"

    private fun route(intent: Intent) = ReceiverRouting.route(
        intent, host,
        apkNameOfClass = { if (it.startsWith("com.example.loaded.")) apk else null },
        isLoadedPackage = { it == loadedPkg }
    )

    @Test fun explicitReceiverOfALoadedApkIsDispatchedLocallyAndTheIntentIsLeftUntouched() {
        val intent = Intent("some.ACTION").setComponent(ComponentName(loadedPkg, "com.example.loaded.MyReceiver")).putExtra("keep", 1)
        assertThat(route(intent)).isEqualTo(Route.Dispatch("com.example.loaded.MyReceiver", apk))
        // the sender's intent is what the receiver will see; nothing was added or changed
        assertThat(intent.component).isEqualTo(ComponentName(loadedPkg, "com.example.loaded.MyReceiver"))
        assertThat(intent.action).isEqualTo("some.ACTION")
        assertThat(intent.extras!!.keySet()).containsExactly("keep")
    }

    @Test fun explicitIntentForAnythingElseIsLeftAlone() {
        val other = Intent().setComponent(ComponentName("com.other", "com.other.Receiver"))
        assertThat(route(other)).isEqualTo(Route.None)
        assertThat(other.component).isEqualTo(ComponentName("com.other", "com.other.Receiver"))
        // the host's own receivers are not a loaded app's
        assertThat(route(Intent().setComponent(ComponentName(host, "$host.SomeHostReceiver")))).isEqualTo(Route.None)
    }

    @Test fun implicitIntentRestrictedToALoadedPackageGetsARestrictedCopyAndTheOriginalIsUntouched() {
        val intent = Intent("fx.PING").setPackage(loadedPkg).putExtra("keep", 1)
        val result = route(intent)

        assertThat(result).isInstanceOf(Route.RestrictedToHost::class.java)
        val copy = (result as Route.RestrictedToHost).intent
        assertThat(copy.`package`).isEqualTo(host)
        assertThat(copy.action).isEqualTo("fx.PING")
        assertThat(copy.getIntExtra("keep", 0)).isEqualTo(1)
        // sendBroadcast doesn't change the app's own Intent, so neither do we
        assertThat(intent.`package`).isEqualTo(loadedPkg)
        assertThat(copy).isNotSameInstanceAs(intent)
    }

    @Test fun otherImplicitIntentsAreLeftAlone() {
        for (intent in listOf(
            Intent("fx.PING"),                          // no restriction
            Intent("fx.PING").setPackage(host),         // already the host
            Intent("fx.PING").setPackage("com.other"),  // someone else's app
        )) {
            val before = intent.`package`
            assertThat(route(intent)).isEqualTo(Route.None)
            assertThat(intent.`package`).isEqualTo(before)
        }
    }
}
