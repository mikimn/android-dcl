package com.mikimn.apkloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Placeholder proving the JVM test source set and Truth are wired; real unit tests land in PR 3. */
class JvmTestSetupTest {
    @Test
    fun truthIsAvailable() {
        assertThat(listOf(1, 2, 3)).containsExactly(1, 2, 3).inOrder()
    }
}
