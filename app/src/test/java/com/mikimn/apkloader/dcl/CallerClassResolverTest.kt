package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import fakeapp.FakeLoadedApp
import org.junit.Test

class CallerClassResolverTest {
    // The caller *of the host code* is what matters: a non-host class that calls in is reported.
    @Test fun reportsFirstNonHostCaller() {
        assertThat(FakeLoadedApp.whoCalled()).isEqualTo("fakeapp.FakeLoadedApp")
    }

    @Test fun neverReportsHostOrVmInternalFrames() {
        val caller = CallerClassResolver.findRealCallerClassName()
        assertThat(caller).isNotNull()
        assertThat(caller).doesNotMatch("com\\.mikimn\\.apkloader\\..*")
        assertThat(caller).isNotEqualTo("java.lang.Thread")
        assertThat(caller).isNotEqualTo("dalvik.system.VMStack")
    }
}
