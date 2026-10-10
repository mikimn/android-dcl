package com.mikimn.apkloader.plugins

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// manifest = NONE: Robolectric cannot parse the app's resource APK (custom --package-id 0x8f).
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PlayServicesBlockingPackageManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pm = PlayServicesBlockingPackageManager(context.packageManager)

    @Test fun pretendsPlayServicesIsNotInstalled() {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            pm.getPackageInfo("com.google.android.gms", 0)
        }
    }

    @Test fun otherPackagesStillResolve() {
        assertThat(pm.getPackageInfo(context.packageName, 0).packageName).isEqualTo(context.packageName)
    }

    @Test fun whitelistedActivityResolvesToEmptyInfoWithoutTouchingBase() {
        val ad = "com.google.ads.AdActivity"
        assertThat(pm.getActivityInfo(ComponentName("any.pkg", ad), 0)).isNotNull()
        val resolved = pm.resolveActivity(Intent().setComponent(ComponentName("any.pkg", ad)), 0)
        assertThat(resolved).isNotNull()
    }

    @Test fun nonWhitelistedActivityIsNotFabricated() {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            pm.getActivityInfo(ComponentName("any.pkg", "any.pkg.NotWhitelisted"), 0)
        }
    }
}
