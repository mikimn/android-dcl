package com.mikimn.apkloader.tier0

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
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

    @Test fun receiverInfoForDeclaredReceiverAndUnknown() {
        val info = plugin.getReceiverInfo(ComponentName(pkg, "$pkg.FxReceiver"), 0)
        assertThat(info.name).isEqualTo("$pkg.FxReceiver")
        assertThat(info.exported).isFalse()
        assertThat(info.metaData.getString("rcv.key")).isEqualTo("rcv-value")
        assertThrows(NameNotFoundException::class.java) {
            plugin.getReceiverInfo(ComponentName(pkg, "$pkg.Nope"), 0)
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

    // ---- PackageInfo ------------------------------------------------------------------------

    @Test fun packageInfoCarriesPackageNameAndVersion() {
        val info = plugin.getPackageInfo(pkg, 0)
        assertThat(info.packageName).isEqualTo(pkg)
        assertThat(info.versionName).isNotEmpty() // injected by the fixture's Gradle build
        assertThat(info.longVersionCode).isGreaterThan(0L)
    }

    @Test fun packageInfoComponentAndPermissionArraysFollowFlags() {
        val bare = plugin.getPackageInfo(pkg, 0)
        assertThat(bare.requestedPermissions).isNull()
        assertThat(bare.activities).isNull()

        val full = plugin.getPackageInfo(
            pkg,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        )
        assertThat(full.requestedPermissions.toList()).containsAtLeast(
            "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"
        )
        assertThat(full.activities.map { it.name }).containsExactly(
            "$pkg.MainActivity", "$pkg.SecondActivity", "$pkg.ViewActivity"
        )
        assertThat(full.services.map { it.name }).containsExactly("$pkg.ExportedService", "$pkg.PrivateService")
        assertThat(full.receivers.map { it.name }).containsExactly("$pkg.FxReceiver")
        assertThat(full.providers.map { it.name }).containsExactly("$pkg.FxProvider")
    }

    // ---- intent resolution ------------------------------------------------------------------

    @Test fun resolveActivityMatchesByAction() {
        val resolved = plugin.resolveActivity(Intent("fx.action.VIEW_ME"), 0)
        assertThat(resolved).isNotNull()
        assertThat(resolved!!.activityInfo.name).isEqualTo("$pkg.MainActivity")
        assertThat(resolved.filter.hasAction("fx.action.VIEW_ME")).isTrue()
    }

    @Test fun resolveActivityReturnsNullWhenNoFilterMatches() {
        assertThat(plugin.resolveActivity(Intent("fx.action.UNKNOWN"), 0)).isNull()
    }

    @Test fun resolveActivityHonorsExplicitComponentAndPackage() {
        val explicit = Intent().setComponent(ComponentName(pkg, "$pkg.SecondActivity"))
        assertThat(plugin.resolveActivity(explicit, 0)!!.activityInfo.name).isEqualTo("$pkg.SecondActivity")

        assertThat(plugin.resolveActivity(Intent().setComponent(ComponentName(pkg, "$pkg.Nope")), 0)).isNull()
        assertThat(plugin.resolveActivity(Intent().setComponent(ComponentName("other.pkg", "$pkg.SecondActivity")), 0)).isNull()
        assertThat(plugin.resolveActivity(Intent("fx.action.VIEW_ME").setPackage("other.pkg"), 0)).isNull()
        assertThat(plugin.resolveActivity(Intent("fx.action.VIEW_ME").setPackage(pkg), 0)).isNotNull()
    }

    @Test fun resolveActivityMatchesDataSchemeHostPathAndType() {
        val ok = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://fx.example.com/items/7"), "text/plain")
        assertThat(plugin.resolveActivity(ok, 0)!!.activityInfo.name).isEqualTo("$pkg.ViewActivity")

        for (bad in listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://fx.example.com/items/7"), "text/plain"),
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://evil.example.com/items/7"), "text/plain"),
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://fx.example.com/other"), "text/plain"),
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://fx.example.com/items/7"), "image/png"),
        )) {
            assertThat(plugin.resolveActivity(bad, 0)).isNull()
        }
    }

    @Test fun launcherIsOnlyReachableThroughTheEnabledAlias() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = plugin.queryIntentActivities(launcher, 0)
        assertThat(results.map { it.activityInfo.name }).containsExactly("$pkg.LauncherAlias")
        assertThat(results.single().activityInfo.targetActivity).isEqualTo("$pkg.MainActivity")
        // The disabled decoy alias shows up only when asked for.
        assertThat(plugin.queryIntentActivities(launcher, PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.name }).containsExactly("$pkg.LauncherAlias", "$pkg.DisabledAlias")
    }

    @Test fun matchDefaultOnlyRequiresTheDefaultCategory() {
        // ViewActivity's filter is DEFAULT; the launcher alias's filter is not.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        assertThat(plugin.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)).isEmpty()
        assertThat(plugin.queryIntentActivities(Intent("fx.action.VIEW_ME"), PackageManager.MATCH_DEFAULT_ONLY)).hasSize(1)
    }

    @Test fun queryServicesAndReceiversByAction() {
        assertThat(plugin.queryIntentServices(Intent("fx.action.SERVE"), 0).single().serviceInfo.name)
            .isEqualTo("$pkg.ExportedService")
        assertThat(plugin.queryBroadcastReceivers(Intent("fx.action.PING"), 0).single().activityInfo.name)
            .isEqualTo("$pkg.FxReceiver")
        assertThat(plugin.queryIntentServices(Intent("fx.action.PING"), 0)).isEmpty()
    }

    @Test fun launchIntentForPackage() {
        val intent = plugin.getLaunchIntentForPackage(pkg)!!
        assertThat(intent.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(intent.categories).contains(Intent.CATEGORY_LAUNCHER)
        assertThat(intent.component).isEqualTo(ComponentName(pkg, "$pkg.MainActivity"))
        assertThat(plugin.getLaunchIntentForPackage("other.pkg")).isNull()
    }
}
