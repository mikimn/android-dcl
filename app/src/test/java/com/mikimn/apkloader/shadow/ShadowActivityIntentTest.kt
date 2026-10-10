package com.mikimn.apkloader.shadow

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ShadowActivityIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hostOnly = DCLActivity.HOST_ONLY_EXTRAS

    private fun hostIntent() = Intent("fx.ACTION", Uri.parse("https://example.com/x"))
        .setComponent(ComponentName(context, "com.mikimn.apkloader.dcl.DCLActivityProxy3"))
        .putExtra("note_id", 42)
        .putExtra("title", "hello")
        .putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.example.Editor")
        .putExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME, "/data/app/x/base.apk")
        .putExtra(DCLActivity.KEY_LOADED_APK_NAME, "/data/app/x/base.apk")
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun shadow(host: Intent?) =
        ShadowActivity.shadowIntent(host, context, String::class.java, hostOnly)

    @Test fun carriesWhatTheLauncherPassed() {
        val intent = shadow(hostIntent())
        assertThat(intent.action).isEqualTo("fx.ACTION")
        assertThat(intent.dataString).isEqualTo("https://example.com/x")
        assertThat(intent.getIntExtra("note_id", -1)).isEqualTo(42)
        assertThat(intent.getStringExtra("title")).isEqualTo("hello")
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotEqualTo(0)
    }

    @Test fun isPointedAtTheShadowClassNotTheProxySlot() {
        val intent = shadow(hostIntent())
        assertThat(intent.component).isEqualTo(ComponentName(context, String::class.java))
    }

    @Test fun hidesTheLoadersOwnRoutingExtras() {
        val intent = shadow(hostIntent())
        for (key in hostOnly) assertThat(intent.hasExtra(key)).isFalse()
        assertThat(intent.extras!!.keySet()).containsExactly("note_id", "title")
    }

    @Test fun doesNotModifyTheHostIntent() {
        val host = hostIntent()
        shadow(host).putExtra("added-by-shadow", true)
        for (key in hostOnly) assertThat(host.hasExtra(key)).isTrue()
        assertThat(host.hasExtra("added-by-shadow")).isFalse()
        assertThat(host.component!!.className).endsWith("DCLActivityProxy3")
    }

    @Test fun withoutAHostIntentItIsJustTheComponent() {
        val intent = shadow(null)
        assertThat(intent.component).isEqualTo(ComponentName(context, String::class.java))
        assertThat(intent.action).isNull()
        assertThat(intent.extras).isNull()
    }
}
