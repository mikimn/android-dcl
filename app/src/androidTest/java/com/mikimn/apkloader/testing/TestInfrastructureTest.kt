package com.mikimn.apkloader.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import kotlin.concurrent.thread

/** Tests for the test utilities themselves, so a broken helper can't masquerade as a loader bug. */
@RunWith(AndroidJUnit4::class)
class TestInfrastructureTest {
    @get:Rule val crashRule = LogcatCrashRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var probe: ProbeChannel

    @Before fun setUp() {
        probe = ProbeChannel(context, "infra-test").also { it.clear() }
    }

    @Test fun probeStartsEmpty() {
        assertThat(probe.events()).isEmpty()
    }

    @Test fun probeRecordsEventsInOrder() {
        probe.append("a"); probe.append("b"); probe.append("a")
        assertThat(probe.events()).containsExactly("a", "b", "a").inOrder()
        assertThat(probe.count("a")).isEqualTo(2)
    }

    @Test fun probeValueOfReturnsLatest() {
        probe.append("k=1"); probe.append("k=2")
        assertThat(probe.valueOf("k")).isEqualTo("2")
        assertThat(probe.valueOf("missing")).isNull()
    }

    @Test fun probeAwaitSeesLateEvent() {
        thread { Thread.sleep(200); probe.append("late") }
        probe.awaitEvent("late")
    }

    @Test fun probeAwaitTimesOutWithEventsInMessage() {
        probe.append("present")
        val e = assertThrows(AssertionError::class.java) { probe.awaitEvent("absent", timeoutMs = 200) }
        assertThat(e).hasMessageThat().contains("absent")
        assertThat(e).hasMessageThat().contains("present")
    }

    @Test fun waitForReportsLastConditionError() {
        val e = assertThrows(AssertionError::class.java) {
            waitFor("never", timeoutMs = 150) { error("boom") }
        }
        assertThat(e.cause).hasMessageThat().isEqualTo("boom")
    }

    @Test fun missingFixtureGivesHelpfulError() {
        val e = assertThrows(AssertionError::class.java) { FixtureApks.install("does-not-exist.apk") }
        assertThat(e).hasMessageThat().contains("does-not-exist.apk")
    }

    // ---- LogcatCrashRule -------------------------------------------------------------------

    private fun runWithCrashRule(body: () -> Unit) {
        val statement = object : Statement() { override fun evaluate() = body() }
        LogcatCrashRule().apply(statement, Description.createTestDescription("fake", "fake")).evaluate()
    }

    @Test fun crashRuleFailsWhenAFatalExceptionWasLogged() {
        val e = assertThrows(AssertionError::class.java) {
            runWithCrashRule { android.util.Log.e("AndroidRuntime", "FATAL EXCEPTION: fake-thread") }
        }
        assertThat(e).hasMessageThat().contains("FATAL EXCEPTION")
    }

    @Test fun crashRulePassesWhenNothingFatalWasLogged() {
        runWithCrashRule { android.util.Log.e("SomeOtherTag", "not fatal") }
    }

    @Test fun crashRuleIgnoresFatalLinesLoggedBeforeTheTestStarted() {
        android.util.Log.e("AndroidRuntime", "FATAL EXCEPTION: from-before")
        Thread.sleep(50)
        runWithCrashRule { }
    }
}
