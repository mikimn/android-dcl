package com.mikimn.apkloader.dcl

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RoutingMacTest {
    private val secret = ByteArray(32) { it.toByte() }

    private fun routed(cls: String = "com.app.Inner", apk: String = "/data/app/x/base.apk") =
        Intent().putExtra(DCLActivity.KEY_ACTIVITY_CLASS, cls).putExtra(DCLActivity.KEY_LOADED_APK_NAME, apk)
            .also { RoutingMac.sign(secret, it) }

    @Test fun aSignedIntentVerifies() {
        assertThat(RoutingMac.verify(secret, routed())).isTrue()
    }

    // what a mutable PendingIntent's holder can do: fillIn never replaces an extra that is set, but adds missing ones
    @Test fun addingAnApkExtraThatWinsPrecedenceBreaksTheSignature() {
        val intent = routed() // carries loadedApkName only
        intent.fillIn(Intent().putExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME, "/sdcard/evil.apk"), 0)
        // fillIn added it, and ApkLoading.apkNameOf would now prefer it over the signed loadedApkName
        assertThat(ApkLoading.apkNameOf(intent)).isEqualTo("/sdcard/evil.apk")
        assertThat(RoutingMac.verify(secret, intent)).isFalse()
    }

    @Test fun fillInDoesNotReplaceTheSignedExtrasAndTheSignatureStillHolds() {
        val intent = routed()
        intent.fillIn(Intent().putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.app.InternalOnly"), 0)
        assertThat(intent.getStringExtra(DCLActivity.KEY_ACTIVITY_CLASS)).isEqualTo("com.app.Inner")
        assertThat(RoutingMac.verify(secret, intent)).isTrue()
    }

    @Test fun addingAClassToAnIntentThatHadNoneBreaksTheSignature() {
        val intent = Intent().putExtra(DCLActivity.KEY_LOADED_APK_NAME, "/data/app/x/base.apk").also { RoutingMac.sign(secret, it) }
        intent.fillIn(Intent().putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.app.InternalOnly"), 0)
        assertThat(RoutingMac.verify(secret, intent)).isFalse()
    }

    @Test fun rewritingTheApkInPlaceBreaksTheSignature() {
        val intent = routed()
        intent.putExtra(DCLActivity.KEY_LOADED_APK_NAME, "/sdcard/evil.apk")
        assertThat(RoutingMac.verify(secret, intent)).isFalse()
    }

    @Test fun anUnsignedIntentIsRefused() {
        val intent = Intent().putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.app.Inner")
        assertThat(RoutingMac.verify(secret, intent)).isFalse()
        assertThat(RoutingMac.verify(secret, null)).isFalse()
    }

    @Test fun aSignatureMadeWithAnotherSecretIsRefused() {
        val other = ByteArray(32) { (it + 1).toByte() }
        val intent = routed()
        assertThat(RoutingMac.verify(other, intent)).isFalse()
    }

    @Test fun aLaunchOfAnApkWithoutAClassSignsOverTheApkAlone() {
        val intent = Intent().putExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME, "/data/app/y/base.apk").also { RoutingMac.sign(secret, it) }
        assertThat(RoutingMac.verify(secret, intent)).isTrue()
        intent.putExtra(DCLActivity.KEY_ACTIVITY_CLASS, "com.app.Added")
        assertThat(RoutingMac.verify(secret, intent)).isFalse()
    }
}
