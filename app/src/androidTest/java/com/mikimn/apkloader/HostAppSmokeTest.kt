package com.mikimn.apkloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    // Compared by name on purpose: the host app's classes are defined by the FileTrackingClassLoader
    // that DCLAppComponentFactory installs, while this test APK has its own loader, so
    // `isInstanceOf(DCLApplication::class.java)` fails with "expected DCLApplication but was
    // DCLApplication". See "Two copies of host classes" in docs/TESTING.md.
    @Test
    fun hostApplicationIsDclApplication() {
        assertThat(targetContext.applicationContext.javaClass.name)
            .isEqualTo("com.mikimn.apkloader.dcl.DCLApplication")
    }

    @Test
    fun hostClassLoaderIsFileTrackingClassLoader() {
        assertThat(targetContext.classLoader.javaClass.name)
            .isEqualTo("com.mikimn.apkloader.dcl.FileTrackingClassLoader")
    }
}
