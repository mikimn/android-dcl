package com.mikimn.apkloader.tier0

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ProviderRegistry
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.ProbeChannel
import com.mikimn.apkloader.testing.Tier0
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A test-side provider that just counts inserts, to tell instances apart. */
private class CountingProvider : ContentProvider() {
    var inserts = 0
    override fun onCreate() = true
    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? { inserts++; return Uri.withAppendedPath(uri, "x") }
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}

/**
 * [ProviderRegistry] makes a loaded APK's providers reachable through the real `ContentResolver`.
 * Each test method runs in a fresh process (orchestrator), so the registry and the process's
 * provider map start empty.
 */
@Tier0
@RunWith(AndroidJUnit4::class)
class ProviderRegistryTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val pkg = "com.mikimn.fixture.manifest"
    private val authority = "$pkg.provider"
    private val uri = Uri.parse("content://$authority/thing")
    private val probe = ProbeChannel(fx.targetContext, "provider")

    @Before fun clearProbe() = probe.clear()

    private val info: ProviderInfo by lazy {
        fx.load("fx-manifest.apk").manifestReader!!.getProviders().first { it.name == "$pkg.FxProvider" }
    }

    private fun fixtureProvider(): ContentProvider =
        (fx.loader.loadClass(info.name).getDeclaredConstructor().newInstance() as ContentProvider)
            .also { it.attachInfo(fx.targetContext, info) }

    private fun insert() = fx.targetContext.contentResolver.insert(uri, ContentValues())

    @Test fun registeredProviderIsReachableThroughContentResolver() {
        assertThat(ProviderRegistry.register(fixtureProvider(), info)).isTrue()

        assertThat(insert()).isEqualTo(Uri.withAppendedPath(uri, "1"))
        assertThat(probe.events()).containsExactly("insert:$uri")
    }

    @Test fun secondRegistrationOfTheSameProviderIsIgnored() {
        assertThat(ProviderRegistry.register(fixtureProvider(), info)).isTrue()
        assertThat(ProviderRegistry.register(fixtureProvider(), info)).isFalse()
    }

    @Test fun anAuthorityAlreadyServedIsNeverTakenOver() {
        assertThat(ProviderRegistry.register(fixtureProvider(), info)).isTrue()

        val intruder = CountingProvider()
        val hijack = ProviderInfo(info).apply { name = "evil.Provider" }
        intruder.attachInfo(fx.targetContext, hijack)
        assertThat(ProviderRegistry.register(intruder, hijack)).isFalse()

        insert()
        assertThat(intruder.inserts).isEqualTo(0)
        assertThat(probe.events()).containsExactly("insert:$uri")
    }

    @Test fun sameClassNameFromAnotherPackageIsRegisteredSeparately() {
        assertThat(ProviderRegistry.register(fixtureProvider(), info)).isTrue()

        val other = ProviderInfo(info).apply {
            authority = "other.pkg.provider"
            applicationInfo = ApplicationInfo(info.applicationInfo).also { it.packageName = "other.pkg" }
        }
        val second = CountingProvider().also { it.attachInfo(fx.targetContext, other) }
        assertThat(ProviderRegistry.register(second, other)).isTrue()

        fx.targetContext.contentResolver.insert(Uri.parse("content://other.pkg.provider/x"), ContentValues())
        assertThat(second.inserts).isEqualTo(1)
    }
}
