package com.mikimn.apkloader.tier0

import android.content.ComponentName
import android.content.Intent
import android.view.WindowManager
import android.content.pm.ActivityInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.apk.AndroidManifestReader
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parses the real binary AndroidManifest.xml of each fixture with the real [AndroidManifestReader]
 * (it needs a live `Resources` to resolve `@resource` references, which is why this is not a JVM test).
 */
@Tier0
@RunWith(AndroidJUnit4::class)
class AndroidManifestReaderTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val pkg = "com.mikimn.fixture.manifest"

    private fun reader(fixture: String): AndroidManifestReader = fx.load(fixture).manifestReader!!

    // ---- application ------------------------------------------------------------------------

    @Test fun applicationInfoPackageAndClassName() {
        val info = reader("fx-manifest.apk").getApplicationInfo()
        assertThat(info.packageName).isEqualTo(pkg)
        assertThat(info.name).isEqualTo("$pkg.ManifestApplication")
    }

    @Test fun applicationWithoutCustomClassHasNoName() {
        val info = reader("fx-hello.apk").getApplicationInfo()
        assertThat(info.packageName).isEqualTo("com.mikimn.fixture.hello")
        assertThat(info.name).isNull()
    }

    @Test fun applicationInfoIsCached() {
        val r = reader("fx-manifest.apk")
        assertThat(r.getApplicationInfo()).isSameInstanceAs(r.getApplicationInfo())
    }

    @Test fun applicationThemeIsTheResolvedStyleId() {
        val info = reader("fx-manifest.apk").getApplicationInfo()
        assertThat(info.theme).isEqualTo(fx.id(pkg, "style", "FxTheme"))
        assertThat(info.theme ushr 24).isEqualTo(0x7f)
    }

    // ---- application meta-data --------------------------------------------------------------

    @Test fun metaDataLiteralsKeepTheirTypes() {
        val md = reader("fx-manifest.apk").getApplicationInfo().metaData
        assertThat(md.getString("fx.string")).isEqualTo("hello")
        assertThat(md.getInt("fx.int")).isEqualTo(42)
        assertThat(md.get("fx.int")).isInstanceOf(Integer::class.java)
        assertThat(md.getFloat("fx.float")).isEqualTo(1.5f)
        assertThat(md.getBoolean("fx.bool")).isTrue()
    }

    // aapt2 cannot encode an integer literal beyond int32 in binary XML, so android:value="5000000000"
    // reaches the reader as a float attribute (the platform's own parser sees the same). The
    // reader's Long branch is therefore unreachable for compiled manifests; assert what is stored.
    @Test fun metaDataIntegerBeyondInt32ArrivesAsFloat() {
        val md = reader("fx-manifest.apk").getApplicationInfo().metaData
        assertThat(md.get("fx.long")).isInstanceOf(java.lang.Float::class.java)
        assertThat(md.getFloat("fx.long")).isEqualTo(5.0e9f)
    }

    @Test fun metaDataResourceReferencesAreResolved() {
        val md = reader("fx-manifest.apk").getApplicationInfo().metaData
        assertThat(md.getString("fx.ref.string")).isEqualTo("resolved-string")
        assertThat(md.getInt("fx.ref.int")).isEqualTo(7)
        assertThat(md.getBoolean("fx.ref.bool")).isTrue()
    }

    @Test fun absentMetaDataIsAnEmptyBundleNotNull() {
        val md = reader("fx-hello.apk").getApplicationInfo().metaData
        assertThat(md).isNotNull()
        assertThat(md.isEmpty).isTrue()
    }

    // ---- activities -------------------------------------------------------------------------

    @Test fun parsesAllActivitiesButNotAliases() {
        val names = reader("fx-manifest.apk").parseActivities().map { it.first.name }
        assertThat(names).containsExactly("$pkg.MainActivity", "$pkg.SecondActivity", "$pkg.ViewActivity")
    }

    @Test fun activityIntentFilterIsParsed() {
        val main = reader("fx-manifest.apk").parseActivities().first { it.first.name == "$pkg.MainActivity" }
        assertThat(main.second).hasSize(1)
        assertThat(main.second[0].hasAction("fx.action.VIEW_ME")).isTrue()
        assertThat(main.second[0].hasCategory(Intent.CATEGORY_DEFAULT)).isTrue()
    }

    @Test fun activityWithoutFilterHasNone() {
        val second = reader("fx-manifest.apk").parseActivities().first { it.first.name == "$pkg.SecondActivity" }
        assertThat(second.second).isEmpty()
    }

    @Test fun activityWindowAndTaskAttributesAreParsed() {
        val second = reader("fx-manifest.apk").parseActivities().first { it.first.name == "$pkg.SecondActivity" }.first
        assertThat(second.launchMode).isEqualTo(ActivityInfo.LAUNCH_SINGLE_TOP)
        assertThat(second.configChanges).isEqualTo(ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE)
        assertThat(second.screenOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(second.softInputMode)
            .isEqualTo(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        assertThat(second.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS).isNotEqualTo(0)
        assertThat(second.taskAffinity).isEqualTo(".second")
        assertThat(second.exported).isFalse()
    }

    @Test fun activityWithoutThoseAttributesKeepsPlatformDefaults() {
        val main = reader("fx-manifest.apk").parseActivities().first { it.first.name == "$pkg.MainActivity" }.first
        assertThat(main.launchMode).isEqualTo(ActivityInfo.LAUNCH_MULTIPLE)
        assertThat(main.configChanges).isEqualTo(0)
        assertThat(main.screenOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(main.softInputMode).isEqualTo(0)
        assertThat(main.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS).isEqualTo(0)
    }

    @Test fun activityThemeIsParsedAndAppInfoAttached() {
        val main = reader("fx-manifest.apk").parseActivities().first { it.first.name == "$pkg.MainActivity" }.first
        assertThat(main.theme).isEqualTo(fx.id(pkg, "style", "FxTheme"))
        assertThat(main.applicationInfo.packageName).isEqualTo(pkg)
    }

    @Test fun getActivityInfoFindsByClassName() {
        val info = reader("fx-manifest.apk").getActivityInfo(ComponentName(pkg, "$pkg.SecondActivity"), 0)
        assertThat(info.name).isEqualTo("$pkg.SecondActivity")
    }

    // Documents current behavior: unlike PackageManager, the reader throws IllegalArgumentException,
    // which ManifestAwarePlugin does NOT translate to NameNotFoundException.
    @Test fun getActivityInfoForUnknownComponentThrowsIllegalArgument() {
        val r = reader("fx-manifest.apk")
        assertThrows(IllegalArgumentException::class.java) {
            r.getActivityInfo(ComponentName(pkg, "$pkg.Nope"), 0)
        }
    }

    // ---- launcher resolution ----------------------------------------------------------------

    @Test fun launcherIsTheActivityWithMainAction() {
        assertThat(reader("fx-hello.apk").getLauncherActivity()!!.name)
            .isEqualTo("com.mikimn.fixture.hello.HelloActivity")
    }

    @Test fun launcherThroughEnabledAliasResolvesToTargetActivity() {
        // no <activity> has MAIN; only an enabled alias -> MainActivity, with a disabled decoy
        // alias -> SecondActivity that must be ignored
        assertThat(reader("fx-manifest.apk").getLauncherActivity()!!.name).isEqualTo("$pkg.MainActivity")
    }

    // ---- services / providers ---------------------------------------------------------------

    @Test fun servicesAreParsedWithExportedAndMetaData() {
        val services = reader("fx-manifest.apk").getServices().associateBy { it.name }
        assertThat(services.keys).containsExactly("$pkg.ExportedService", "$pkg.PrivateService")
        assertThat(services.getValue("$pkg.ExportedService").exported).isTrue()
        assertThat(services.getValue("$pkg.ExportedService").metaData.getString("svc.key")).isEqualTo("svc-value")
        assertThat(services.getValue("$pkg.PrivateService").exported).isFalse()
    }

    @Test fun providersAreParsedWithAuthorityAndGrantUriPermissions() {
        val providers = reader("fx-manifest.apk").getProviders()
        assertThat(providers).hasSize(1)
        assertThat(providers[0].name).isEqualTo("$pkg.FxProvider")
        assertThat(providers[0].authority).isEqualTo("$pkg.provider")
        assertThat(providers[0].grantUriPermissions).isTrue()
    }

    @Test fun noServicesOrProvidersGivesEmptyLists() {
        val r = reader("fx-hello.apk")
        assertThat(r.getServices()).isEmpty()
        assertThat(r.getProviders()).isEmpty()
    }
}
