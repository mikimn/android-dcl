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

    @Test fun apkNameComesFromEitherExtraAndTheExplicitLaunchWinsAsInOnCreate() {
        assertThat(ApkLoading.apkNameOf(null)).isNull()
        assertThat(ApkLoading.apkNameOf(Intent())).isNull()
        assertThat(ApkLoading.apkNameOf(intentFor(DCLActivity.KEY_APK_ASSET_FILE_NAME, "a.apk"))).isEqualTo("a.apk")
        assertThat(ApkLoading.apkNameOf(intentFor(DCLActivity.KEY_LOADED_APK_NAME, "b.apk"))).isEqualTo("b.apk")
        val both = Intent().putExtra(DCLActivity.KEY_APK_ASSET_FILE_NAME, "a.apk").putExtra(DCLActivity.KEY_LOADED_APK_NAME, "b.apk")
        assertThat(ApkLoading.apkNameOf(both)).isEqualTo("a.apk") // same precedence as DCLActivity.onCreate
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

    // preload() runs against the Application's Resources; DCLActivity.onCreate still calls
    // initResourceLoader, which attaches the process-wide loader to the *activity's* Resources.
    // Prove an unrelated Resources object (as an activity has) resolves a preloaded APK's resources.
    @Test fun preloadedApkResourcesReachAnotherResourcesObjectThroughTheSharedLoader() {
        val loader = freshLoader()
        val pkg = "com.mikimn.fixture.resources"
        ApkLoading.preload(context, loader, intentFor(DCLActivity.KEY_LOADED_APK_NAME, FixtureApks.install("fx-resources.apk").path))

        val activityLikeResources = context.createConfigurationContext(android.content.res.Configuration()).resources
        assertThat(activityLikeResources).isNotSameInstanceAs(context.resources)
        // (A Resources derived from the application's may already see the APK, since loaders attached
        // by LoadedApk.load are shared; attaching the process-wide loader is what makes it certain.)
        activityLikeResources.addLoaders(loader.resourcesLoader) // what DCLActivity.initResourceLoader does
        val id = activityLikeResources.getIdentifier("title", "string", pkg)
        assertThat(id).isNotEqualTo(0)
        // The fixture ships a values-night variant, so the expected string follows the device's dark mode.
        val night = activityLikeResources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        assertThat(activityLikeResources.getString(id))
            .isEqualTo(if (night) "fx-resources title (night)" else "fx-resources title")
    }

    @Test fun aHostileArchiveIsRejectedOnceAndNotExtractedAgain() {
        val evil = java.io.File(context.filesDir, "evil-slip.apk")
        java.util.zip.ZipOutputStream(evil.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("../../escape.txt")); it.write("x".toByteArray()); it.closeEntry()
        }
        val loader = freshLoader()
        val intent = intentFor(DCLActivity.KEY_LOADED_APK_NAME, evil.path)
        assertThat(ApkLoading.preload(context, loader, intent)).isNull()

        // Our zip-slip guard (SecurityException) or, on newer Android, ZipFile itself (ZipException).
        fun rejection() = assertThrows(Exception::class.java) { ApkLoading.load(context, loader, evil.path) }.also {
            assertThat(it is SecurityException || it is java.util.zip.ZipException).isTrue()
        }
        val first = rejection()
        val second = rejection()
        assertThat(second).isSameInstanceAs(first) // remembered, not re-extracted
        assertThat(loader.apkFile(evil.path)).isNull()
        evil.delete()
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
