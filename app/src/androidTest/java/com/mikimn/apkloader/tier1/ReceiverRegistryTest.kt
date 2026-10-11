package com.mikimn.apkloader.tier1

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ReceiverRegistry
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Dynamic registration of a loaded APK's manifest receiver filters. */
@Tier1
@RunWith(AndroidJUnit4::class)
class ReceiverRegistryTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    // Delivery itself (implicit, explicit and package-restricted) is covered end to end by
    // HostedReceiversTest. It can't be driven from here: code run through this test APK's own copy
    // of the host classes can't see the host's FileTrackingClassLoader (docs/TESTING.md, "Two
    // copies of host classes").
    @Test fun oneRegistrationIsMadePerReceiverFilter() {
        val apk = fx.loadFromPath(FixtureApks.install("fx-manifest.apk"))
        assertThat(ReceiverRegistry.register(target, apk)).isEqualTo(1) // FxReceiver has one filter
    }

    @Test fun anApkWithoutReceiverFiltersRegistersNothing() {
        assertThat(ReceiverRegistry.register(target, fx.load("fx-resources.apk"))).isEqualTo(0)
    }

    // Non-exported receivers are protected below API 33 by a permission only this app holds.
    @Test fun theInternalBroadcastPermissionIsSignatureLevelAndHeldByTheHostOnly() {
        val perm = ReceiverRegistry.internalBroadcastPermission(target)
        val info = target.packageManager.getPermissionInfo(perm, 0)
        assertThat(info.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE)
            .isEqualTo(android.content.pm.PermissionInfo.PROTECTION_SIGNATURE)
        // our own process passes the sender check (so the host's/loaded app's own broadcasts arrive)...
        assertThat(target.checkSelfPermission(perm)).isEqualTo(android.content.pm.PackageManager.PERMISSION_GRANTED)
        // ...while another app's (different signature) does not
        assertThat(target.packageManager.checkPermission(perm, "com.android.settings"))
            .isEqualTo(android.content.pm.PackageManager.PERMISSION_DENIED)
    }
}
