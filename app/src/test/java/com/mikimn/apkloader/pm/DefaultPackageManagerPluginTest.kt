package com.mikimn.apkloader.pm

import android.app.Application
import android.content.ComponentName
import android.content.Context
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
class DefaultPackageManagerPluginTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val plugin = DefaultPackageManagerPlugin(context.packageManager)
    private val discovery = "com.google.firebase.components.ComponentDiscoveryService"

    @Test fun synthesizesFirebaseComponentDiscoveryMetadata() {
        val info = plugin.getServiceInfo(ComponentName("any.pkg", discovery), 0)
        assertThat(info.name).isEqualTo(discovery)
        val keys = info.metaData.keySet()
        assertThat(keys).isNotEmpty()
        assertThat(keys).contains(
            "com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar"
        )
        // every registrar entry maps to the ComponentRegistrar marker value Firebase looks for
        assertThat(keys.map { info.metaData.getString(it) }.toSet())
            .containsExactly("com.google.firebase.components.ComponentRegistrar")
    }

    @Test fun otherServicesDelegateToBase() {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            plugin.getServiceInfo(ComponentName("any.pkg", "any.pkg.OtherService"), 0)
        }
    }

    @Test fun packageInfoDelegatesToBase() {
        assertThat(plugin.getPackageInfo(context.packageName, 0).packageName).isEqualTo(context.packageName)
    }
}
