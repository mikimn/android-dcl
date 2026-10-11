package com.mikimn.apkloader.tier0

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.testing.Tier0
import org.junit.Test
import org.junit.runner.RunWith

@Tier0
@RunWith(AndroidJUnit4::class)
class HostManifestTest {
    // On Android 11 ActivityThread.handleReceiver casts the Application's base context to ContextImpl, which
    // DCLApplication replaces with a DCLContext: the system delivering to *any* host <receiver> kills the
    // process (#62). A dependency that merges one in must be removed with tools:node="remove".
    @Test fun theHostDeclaresNoManifestReceivers() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val info = target.packageManager.getPackageInfo(target.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_RECEIVERS.toLong()))
        assertThat(info.receivers?.map { it.name } ?: emptyList<String>()).isEmpty()
    }
}
