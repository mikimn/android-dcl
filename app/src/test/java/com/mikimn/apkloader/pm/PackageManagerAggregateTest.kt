package com.mikimn.apkloader.pm

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A plugin that knows exactly one package/component and says "not found" for everything else. */
class FakePlugin(private val pkg: String, private val label: String) : PackageManagerPlugin {
    val calls = mutableListOf<String>()
    private fun nf(what: String): Nothing { calls += what; throw PackageManager.NameNotFoundException(what) }

    override fun getActivityInfo(component: ComponentName, flags: Int): ActivityInfo =
        if (component.packageName == pkg) ActivityInfo().apply { name = label } else nf("activity")
    override fun getReceiverInfo(component: ComponentName, flags: Int): ActivityInfo =
        if (component.packageName == pkg) ActivityInfo().apply { name = label } else nf("receiver")
    override fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo =
        if (component.packageName == pkg) ServiceInfo().apply { name = label } else nf("service")
    override fun getProviderInfo(component: ComponentName, flags: Int): ProviderInfo =
        if (component.packageName == pkg) ProviderInfo().apply { name = label } else nf("provider")
    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo =
        if (packageName == pkg) ApplicationInfo().apply { name = label } else nf("app")
    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo =
        if (packageName == pkg) PackageInfo().apply { this.packageName = label } else nf("package")
    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? =
        if (intent.`package` == pkg) ResolveInfo().apply { resolvePackageName = label } else { calls += "resolve"; null }
}

@RunWith(RobolectricTestRunner::class)
// manifest = NONE: Robolectric cannot parse the app's resource APK (custom --package-id 0x8f).
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class PackageManagerAggregateTest {
    private val base: PackageManager = ApplicationProvider.getApplicationContext<Context>().packageManager
    private val hostPkg: String = ApplicationProvider.getApplicationContext<Context>().packageName

    private fun aggregate(vararg plugins: PackageManagerPlugin) = PackageManagerAggregate(base, arrayOf(*plugins))
    private fun cn(pkg: String) = ComponentName(pkg, "$pkg.Thing")

    @Test fun pluginAnswersForItsOwnPackage() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"))
        assertThat(agg.getActivityInfo(cn("a.pkg"), 0).name).isEqualTo("from-a")
        assertThat(agg.getServiceInfo(cn("a.pkg"), 0).name).isEqualTo("from-a")
        assertThat(agg.getProviderInfo(cn("a.pkg"), 0).name).isEqualTo("from-a")
        assertThat(agg.getReceiverInfo(cn("a.pkg"), 0).name).isEqualTo("from-a")
        assertThat(agg.getApplicationInfo("a.pkg", 0).name).isEqualTo("from-a")
        assertThat(agg.getPackageInfo("a.pkg", 0).packageName).isEqualTo("from-a")
    }

    @Test fun fallsThroughToNextPluginThenBase() {
        val a = FakePlugin("a.pkg", "from-a")
        val b = FakePlugin("b.pkg", "from-b")
        val agg = aggregate(a, b)
        assertThat(agg.getPackageInfo("b.pkg", 0).packageName).isEqualTo("from-b")
        assertThat(a.calls).containsExactly("package")
        // Neither plugin knows the host package: answered by the real base PackageManager.
        assertThat(agg.getPackageInfo(hostPkg, 0).packageName).isEqualTo(hostPkg)
    }

    @Test fun unknownEverywhereThrowsNameNotFound() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"))
        assertThrows(PackageManager.NameNotFoundException::class.java) { agg.getPackageInfo("nope.nope", 0) }
        assertThrows(PackageManager.NameNotFoundException::class.java) { agg.getActivityInfo(cn("nope.nope"), 0) }
    }

    @Test fun firstMatchingPluginWinsOverLaterOnes() {
        val agg = aggregate(FakePlugin("same.pkg", "first"), FakePlugin("same.pkg", "second"))
        assertThat(agg.getPackageInfo("same.pkg", 0).packageName).isEqualTo("first")
    }

    @Test fun addPluginInsertsAtFront() {
        val agg = aggregate(FakePlugin("same.pkg", "old"))
        agg.addPlugin(FakePlugin("same.pkg", "new"))
        assertThat(agg.getPackageInfo("same.pkg", 0).packageName).isEqualTo("new")
    }

    @Test fun removePluginStopsItAnswering() {
        val plugin = FakePlugin("a.pkg", "from-a")
        val agg = aggregate(plugin)
        agg.removePlugin(plugin)
        assertThrows(PackageManager.NameNotFoundException::class.java) { agg.getPackageInfo("a.pkg", 0) }
    }

    @Test fun resolveActivityNullFallsThroughToNextPlugin() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"), FakePlugin("b.pkg", "from-b"))
        val resolved = agg.resolveActivity(Intent().setPackage("b.pkg"), 0)
        assertThat(resolved?.resolvePackageName).isEqualTo("from-b")
    }

    @Test fun resolveActivityNoPluginMatchGoesToBaseAndMayBeNull() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"))
        assertThat(agg.resolveActivity(Intent("some.unhandled.ACTION"), 0)).isNull()
    }

    // ---- API 33 `*Flags` overloads must also go through the plugins ---------------------------

    @Test fun flagsOverloadsConsultPluginsToo() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"))
        val component = PackageManager.ComponentInfoFlags.of(0)
        assertThat(agg.getActivityInfo(cn("a.pkg"), component).name).isEqualTo("from-a")
        assertThat(agg.getReceiverInfo(cn("a.pkg"), component).name).isEqualTo("from-a")
        assertThat(agg.getServiceInfo(cn("a.pkg"), component).name).isEqualTo("from-a")
        assertThat(agg.getProviderInfo(cn("a.pkg"), component).name).isEqualTo("from-a")
        assertThat(agg.getApplicationInfo("a.pkg", PackageManager.ApplicationInfoFlags.of(0)).name).isEqualTo("from-a")
        assertThat(agg.getPackageInfo("a.pkg", PackageManager.PackageInfoFlags.of(0)).packageName).isEqualTo("from-a")
        val resolved = agg.resolveActivity(Intent().setPackage("a.pkg"), PackageManager.ResolveInfoFlags.of(0))
        assertThat(resolved?.resolvePackageName).isEqualTo("from-a")
    }

    @Test fun flagsOverloadsFallThroughToBase() {
        val agg = aggregate(FakePlugin("a.pkg", "from-a"))
        assertThat(agg.getPackageInfo(hostPkg, PackageManager.PackageInfoFlags.of(0)).packageName).isEqualTo(hostPkg)
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            agg.getPackageInfo("nope.nope", PackageManager.PackageInfoFlags.of(0))
        }
    }

    @Test fun flagsValueIsForwardedToThePlugin() {
        var seen = -1
        val plugin = object : PackageManagerPlugin by FakePlugin("a.pkg", "from-a") {
            override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
                seen = flags
                return PackageInfo().apply { this.packageName = "from-a" }
            }
        }
        aggregate(plugin).getPackageInfo("a.pkg", PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        assertThat(seen).isEqualTo(PackageManager.GET_ACTIVITIES)
    }

    // --- own-uid identity (loaded APK asking "who am I?" / "who is calling me?") ---

    private val myUid = android.os.Process.myUid()

    @Test fun ownUidResolvesToLoadedPackageWhenResolverAnswers() {
        val agg = aggregate()
        agg.ownUidPackageResolver = { "loaded.pkg" }
        assertThat(agg.getNameForUid(myUid)).isEqualTo("loaded.pkg")
        assertThat(agg.getPackagesForUid(myUid)).asList().containsExactly("loaded.pkg")
    }

    @Test fun ownUidFallsThroughWhenResolverReturnsNull() {
        val agg = aggregate()
        agg.ownUidPackageResolver = { null }
        assertThat(agg.getNameForUid(myUid)).isEqualTo(base.getNameForUid(myUid))
        assertThat(agg.getPackagesForUid(myUid)).isEqualTo(base.getPackagesForUid(myUid))
    }

    @Test fun ownUidFallsThroughWhenNoResolverIsSet() {
        val agg = aggregate()
        assertThat(agg.getNameForUid(myUid)).isEqualTo(base.getNameForUid(myUid))
        assertThat(agg.getPackagesForUid(myUid)).isEqualTo(base.getPackagesForUid(myUid))
    }

    @Test fun otherUidsNeverConsultTheResolver() {
        val agg = aggregate()
        var consulted = false
        agg.ownUidPackageResolver = { consulted = true; "loaded.pkg" }
        val other = myUid + 1
        assertThat(agg.getNameForUid(other)).isEqualTo(base.getNameForUid(other))
        assertThat(agg.getPackagesForUid(other)).isEqualTo(base.getPackagesForUid(other))
        assertThat(consulted).isFalse()
    }
}
