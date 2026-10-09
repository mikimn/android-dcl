package com.mikimn.apkloader.testing

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Fails a test if this process logged a `FATAL EXCEPTION` while it ran.
 *
 * A truly fatal crash kills the test process and the orchestrator reports it anyway. This rule
 * catches the case that is easy to miss: an exception that was logged as fatal but did not take
 * the process down, e.g. one swallowed by `DCLApplication`'s uncaught-exception guard or thrown
 * on a thread the loaded app owns. Mirrors the `FATAL EXCEPTION` check in `scripts/test-apk.sh`.
 */
class LogcatCrashRule : TestWatcher() {
    override fun starting(description: Description) {
        shell("logcat -c")
    }

    override fun succeeded(description: Description) {
        val log = shell("logcat -d --pid=${Process.myPid()} -s AndroidRuntime:E")
        val idx = log.indexOf("FATAL EXCEPTION")
        if (idx >= 0) {
            throw AssertionError("Process logged a FATAL EXCEPTION during the test:\n" + log.substring(idx).take(2000))
        }
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)))
            .use { it.readText() }
    }
}
