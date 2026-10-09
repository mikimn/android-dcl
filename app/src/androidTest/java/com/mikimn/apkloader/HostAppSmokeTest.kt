package com.mikimn.apkloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikimn.apkloader.dcl.DCLApplication
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanity checks that the instrumentation runs inside the *real* host process, i.e. that the hook
 * chain (`DCLAppComponentFactory` + `DCLApplication`) is active under test. Every later
 * instrumented test relies on this.
 */
@RunWith(AndroidJUnit4::class)
class HostAppSmokeTest {
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun targetPackageIsHost() {
        assertThat(targetContext.packageName).isEqualTo("com.mikimn.apkloader")
    }

    @Test
    fun hostApplicationIsDclApplication() {
        assertThat(targetContext.applicationContext).isInstanceOf(DCLApplication::class.java)
    }
}
