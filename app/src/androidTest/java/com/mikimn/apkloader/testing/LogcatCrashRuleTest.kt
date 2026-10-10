package com.mikimn.apkloader.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Tests for [LogcatCrashRule]. Deliberately has no `@Rule LogcatCrashRule` of its own: these tests
 * log fake `FATAL EXCEPTION` lines on purpose, which an outer rule would (correctly) flag.
 */
@RunWith(AndroidJUnit4::class)
class LogcatCrashRuleTest {
    private fun runWithCrashRule(body: () -> Unit) {
        val statement = object : Statement() { override fun evaluate() = body() }
        LogcatCrashRule().apply(statement, Description.createTestDescription("fake", "fake")).evaluate()
    }

    @Test fun failsWhenAFatalExceptionWasLogged() {
        val e = assertThrows(AssertionError::class.java) {
            runWithCrashRule { android.util.Log.e("AndroidRuntime", "FATAL EXCEPTION: fake-thread") }
        }
        assertThat(e).hasMessageThat().contains("FATAL EXCEPTION")
    }

    @Test fun passesWhenNothingFatalWasLogged() {
        runWithCrashRule { android.util.Log.e("SomeOtherTag", "not fatal") }
    }

    @Test fun ignoresFatalLinesLoggedBeforeTheTestStarted() {
        android.util.Log.e("AndroidRuntime", "FATAL EXCEPTION: from-before")
        Thread.sleep(50)
        runWithCrashRule { }
    }
}
