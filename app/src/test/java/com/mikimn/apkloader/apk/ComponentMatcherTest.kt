package com.mikimn.apkloader.apk

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ComponentMatcherTest {
    private val pkg = "loaded.pkg"

    private fun activity(name: String, enabled: Boolean = true) =
        ActivityInfo().also { it.name = "$pkg.$name"; it.packageName = pkg; it.enabled = enabled }

    private fun filter(action: String, priority: Int = 0, vararg categories: String, build: IntentFilter.() -> Unit = {}) =
        IntentFilter(action).also {
            it.priority = priority
            categories.forEach(it::addCategory)
            it.build()
        }

    private fun resolve(
        components: List<Pair<ActivityInfo, List<IntentFilter>>>,
        intent: Intent,
        flags: Int = 0
    ): List<String> =
        ComponentMatcher.resolve(pkg, components, intent, flags) { ResolveInfo().apply { activityInfo = it } }
            .map { it.activityInfo.name.removePrefix("$pkg.") }

    @Test fun higherFilterPriorityComesFirstThenManifestOrder() {
        val components = listOf(
            activity("Low") to listOf(filter("a", priority = 0)),
            activity("High") to listOf(filter("a", priority = 10)),
            activity("AlsoLow") to listOf(filter("a", priority = 0)),
        )
        assertThat(resolve(components, Intent("a"))).containsExactly("High", "Low", "AlsoLow").inOrder()
    }

    @Test fun aComponentIsListedOnceUsingItsBestFilter() {
        val c = activity("Multi") to listOf(filter("a", priority = 1), filter("a", priority = 7))
        val result = ComponentMatcher.resolve(pkg, listOf(c), Intent("a"), 0) { ResolveInfo().apply { activityInfo = it } }
        assertThat(result).hasSize(1)
        assertThat(result.single().priority).isEqualTo(7)
    }

    @Test fun intentCategoriesMustBeDeclaredByTheFilter() {
        val c = listOf(activity("A") to listOf(filter("a", 0, Intent.CATEGORY_DEFAULT)))
        assertThat(resolve(c, Intent("a").addCategory(Intent.CATEGORY_DEFAULT))).containsExactly("A")
        assertThat(resolve(c, Intent("a").addCategory(Intent.CATEGORY_BROWSABLE))).isEmpty()
    }

    @Test fun dataSchemeAndPathPrefixAreHonored() {
        val f = filter("a") { addDataScheme("https"); addDataAuthority("h.com", null); addDataPath("/p", android.os.PatternMatcher.PATTERN_PREFIX) }
        val c = listOf(activity("A") to listOf(f))
        assertThat(resolve(c, Intent("a", Uri.parse("https://h.com/p/1")))).containsExactly("A")
        assertThat(resolve(c, Intent("a", Uri.parse("https://h.com/q")))).isEmpty()
        assertThat(resolve(c, Intent("a", Uri.parse("ftp://h.com/p/1")))).isEmpty()
        assertThat(resolve(c, Intent("a"))).isEmpty()
    }

    @Test fun explicitComponentBypassesFiltersButMustBelongToThePackage() {
        val c = listOf(activity("NoFilters") to emptyList<IntentFilter>())
        assertThat(resolve(c, Intent().setComponent(ComponentName(pkg, "$pkg.NoFilters")))).containsExactly("NoFilters")
        assertThat(resolve(c, Intent().setComponent(ComponentName("x", "$pkg.NoFilters")))).isEmpty()
        assertThat(resolve(c, Intent().setComponent(ComponentName(pkg, "$pkg.Missing")))).isEmpty()
    }

    @Test fun explicitPackageMustMatch() {
        val c = listOf(activity("A") to listOf(filter("a")))
        assertThat(resolve(c, Intent("a").setPackage(pkg))).containsExactly("A")
        assertThat(resolve(c, Intent("a").setPackage("other"))).isEmpty()
    }

    @Test fun disabledComponentsAreSkippedUnlessRequested() {
        val c = listOf(
            activity("On") to listOf(filter("a")),
            activity("Off", enabled = false) to listOf(filter("a")),
        )
        assertThat(resolve(c, Intent("a"))).containsExactly("On")
        assertThat(resolve(c, Intent("a"), PackageManager.MATCH_DISABLED_COMPONENTS)).containsExactly("On", "Off")
        // ...and an explicit intent to a disabled component doesn't resolve either.
        assertThat(resolve(c, Intent().setComponent(ComponentName(pkg, "$pkg.Off")))).isEmpty()
    }

    @Test fun matchDefaultOnlyDropsFiltersWithoutTheDefaultCategory() {
        val c = listOf(
            activity("Def") to listOf(filter("a", 0, Intent.CATEGORY_DEFAULT)),
            activity("NoDef") to listOf(filter("a")),
        )
        assertThat(resolve(c, Intent("a"))).containsExactly("Def", "NoDef")
        assertThat(resolve(c, Intent("a"), PackageManager.MATCH_DEFAULT_ONLY)).containsExactly("Def")
    }
}
