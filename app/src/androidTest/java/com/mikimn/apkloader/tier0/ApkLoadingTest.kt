package com.mikimn.apkloader.tier0

import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ApkLoading
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.dcl.FileTrackingClassLoader
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Tier0
@RunWith(AndroidJUnit4::class)
class ApkLoadingTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val context = fx.targetContext
    private val apkPath get() = FixtureApks.install("fx-hello.apk").path
    private val payloadClass = "com.mikimn.fixture.hello.FxPayload"

    private fun freshLoader() = FileTrackingClassLoader(fx.baseClassLoader)
    private fun intentFor(key: String, value: String) = Intent().putExtra(key, value)

    @Test fun apkNameComesFromEitherExtraAndNavigationHopWins() {
        assertThat(ApkLoading.apkNameOf(null)).isNull()
        assertThat(ApkLoading.apkNameOf(Intent())).isNull()
        assertThat(ApkLoading.apkNameOf(intentFor(DCLActivity.KEY_APK_ASSET_FILE_NAME, "a.apk"))).isEqualTo("a.apk")
        assertThat(ApkLoading.apkNameOf(intentFor(DCLActivity.KEY_LOADED_APK_NAME, "b.apk"))).isEqualTo("b.apk")
        val both = Intent().putExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME, "a.apk").putExtra(DCLActivity.KEY_LOADED_APK_NAME, "b.apk")
        assertThat(ApkLoading.apkNameOf(both)).isEqualTo("b.apk")
    }

    @Test fun loadRegistersTheApkAndIsIdempotent() {
        val loader = freshLoader()
        val first = ApkLoading.load(context, loader, apkPath)
        assertThat(loader.apkFile(apkPath)).isSameInstanceAs(first)
        assertThat(ApkLoading.load(context, loader, apkPath)).isSameInstanceAs(first)
    }

    @Test fun preloadLoadsTheApkNamedByTheIntent() {
        val loader = freshLoader()
        val loaded = ApkLoading.preload(context, loader, intentFor(DCLActivity.KEY_LOADED_APK_NAME, apkPath))
        assertThat(loaded).isNotNull()
        assertThat(loader.apkFile(apkPath)).isSameInstanceAs(loaded)
    }

    @Test fun preloadDoesNothingWithoutAnApkOrWithAForeignLoader() {
        val loader = freshLoader()
        assertThat(ApkLoading.preload(context, loader, Intent())).isNull()
        assertThat(ApkLoading.preload(context, loader, null)).isNull()
        assertThat(ApkLoading.preload(context, fx.baseClassLoader, intentFor(DCLActivity.KEY_LOADED_APK_NAME, apkPath))).isNull()
    }

    @Test fun preloadSwallowsFailuresSoOnCreateCanReportThem() {
        val loader = freshLoader()
        assertThat(ApkLoading.preload(context, loader, intentFor(DCLActivity.KEY_LOADED_APK_NAME, "/no/such/file.apk"))).isNull()
        assertThat(loader.apkFile("/no/such/file.apk")).isNull()
    }

    // The process-death bug: saved state holding the loaded app's own Parcelable can only be read
    // once the APK is registered with the loader the platform uses to unmarshal it.
    @Test fun savedStateWithALoadedAppsParcelableOnlyReadsOnceTheApkIsLoaded() {
        val writer = freshLoader()
        val payload = ApkLoading.load(context, writer, apkPath).loadClass(payloadClass)
            .getConstructor(String::class.java).newInstance("restored") as Parcelable
        val parcel = Parcel.obtain()
        parcel.writeBundle(Bundle().apply { putParcelable("state", payload) })
        val bytes = parcel.marshall()
        parcel.recycle()

        fun readBack(loader: ClassLoader): Parcelable? {
            val p = Parcel.obtain()
            p.unmarshall(bytes, 0, bytes.size)
            p.setDataPosition(0)
            return try {
                @Suppress("DEPRECATION")
                p.readBundle(loader)!!.getParcelable("state")
            } finally {
                p.recycle()
            }
        }

        // A fresh process: nothing registered yet.
        val restored = freshLoader()
        assertThrows(BadParcelableException::class.java) { readBack(restored) }

        // What the component factory now does before the platform reads the Bundle.
        ApkLoading.preload(context, restored, intentFor(DCLActivity.KEY_LOADED_APK_NAME, apkPath))
        val value = readBack(restored)!!
        assertThat(value.javaClass.name).isEqualTo(payloadClass)
        assertThat(value.javaClass.getField("value").get(value)).isEqualTo("restored")
    }
}
