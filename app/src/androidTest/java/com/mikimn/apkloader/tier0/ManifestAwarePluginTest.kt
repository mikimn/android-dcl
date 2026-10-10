package com.mikimn.apkloader.tier0

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.apk.ManifestAwarePlugin
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Tier0
@RunWith(AndroidJUnit4::class)
class ManifestAwarePluginTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val pkg = "com.mikimn.fixture.manifest"
    private val plugin by lazy { ManifestAwarePlugin(fx.load("fx-manifest.apk").manifestReader!!) }

    @Test fun activityInfoForDeclaredActivity() {
        assertThat(plugin.getActivityInfo(ComponentName(pkg, "$pkg.MainActivity"), 0).name)
            .isEqualTo("$pkg.MainActivity")
    }

    @Test fun serviceInfoForDeclaredServiceAndUnknown() {
        assertThat(plugin.getServiceInfo(ComponentName(pkg, "$pkg.ExportedService"), 0).name)
            .isEqualTo("$pkg.ExportedService")
        assertThrows(NameNotFoundException::class.java) {
            plugin.getServiceInfo(ComponentName(pkg, "$pkg.Nope"), 0)
        }
    }

    @Test fun providerInfoForDeclaredProviderAndUnknown() {
        assertThat(plugin.getProviderInfo(ComponentName(pkg, "$pkg.FxProvider"), 0).authority)
            .isEqualTo("$pkg.provider")
        assertThrows(NameNotFoundException::class.java) {
            plugin.getProviderInfo(ComponentName(pkg, "$pkg.Nope"), 0)
        }
    }

    // Documents a known gap (R12): broadcast receivers are never answered, even declared ones.
    @Test fun receiverInfoIsAlwaysNotFoundEvenForDeclaredReceiver() {
        assertThrows(NameNotFoundException::class.java) {
            plugin.getReceiverInfo(ComponentName(pkg, "$pkg.FxReceiver"), 0)
        }
    }

    @Test fun applicationInfoReturnsTheLoadedAppRegardlessOfRequestedPackage() {
        // Current behavior: the package name argument is ignored.
        assertThat(plugin.getApplicationInfo(pkg, 0).packageName).isEqualTo(pkg)
        assertThat(plugin.getApplicationInfo("some.other.pkg", 0).packageName).isEqualTo(pkg)
    }

    @Test fun packageInfoOnlyForTheLoadedPackage() {
        assertThat(plugin.getPackageInfo(pkg, 0).applicationInfo!!.packageName).isEqualTo(pkg)
        assertThrows(NameNotFoundException::class.java) { plugin.getPackageInfo("some.other.pkg", 0) }
    }

    @Test fun resolveActivityMatchesByActionAlone() {
        val resolved = plugin.resolveActivity(Intent("fx.action.VIEW_ME"), 0)
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.activityInfo.name).isEqualTo("$pkg.MainActivity")
        assertThat(resolved.filter.hasAction("fx.action.VIEW_ME")).isTrue()
    }

    @Test fun resolveActivityReturnsNullWhenNoFilterMatches() {
        assertThat(plugin.resolveActivity(Intent("fx.action.UNKNOWN"), 0)).isNull()
    }

    // Documents the "minimal action-only" resolution: explicit components and data are ignored.
    @Test fun resolveActivityIgnoresExplicitComponent() {
        val explicit = Intent().setComponent(ComponentName(pkg, "$pkg.SecondActivity"))
        assertThat(plugin.resolveActivity(explicit, 0)).isNull()
    }
}
