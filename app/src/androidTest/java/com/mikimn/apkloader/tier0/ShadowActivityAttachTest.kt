package com.mikimn.apkloader.tier0

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.shadow.AttachArguments.Role
import com.mikimn.apkloader.shadow.ShadowActivity
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hidden `Activity.attach` of the device this runs on, resolved for real (#27): every parameter must map to a
 * role we can fill, or this fails with the device's whole signature in the message - which is how a new Android
 * release that adds a parameter is meant to show up.
 */
@Tier0
@RunWith(AndroidJUnit4::class)
class ShadowActivityAttachTest {
    @get:Rule val crashRule = LogcatCrashRule()

    @Test fun theDevicesRealAttachSignatureIsFullyRecognized() {
        val target = ShadowActivity.resolveAttach() // throws UnrecognizedParameter naming the signature otherwise
        assertThat(target.roles).containsAtLeast(
            Role.CONTEXT, Role.MAIN_THREAD, Role.INSTRUMENTATION, Role.TOKEN, Role.APPLICATION, Role.INTENT, Role.ACTIVITY_INFO
        )
        assertThat(target.roles.count { it == Role.TOKEN }).isEqualTo(1)
        // every parameter got a role (none was silently skipped)
        assertThat(target.roles).hasSize(target.method.parameterCount)
    }
}
