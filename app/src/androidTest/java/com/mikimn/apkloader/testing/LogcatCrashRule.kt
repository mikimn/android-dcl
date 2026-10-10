package com.mikimn.apkloader.testing

import android.os.Process
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fails a test if this process logged a `FATAL EXCEPTION` while it ran.
 *
 * A truly fatal crash kills the test process and the orchestrator reports it anyway. This rule
 * catches the case that is easy to miss: an exception that was logged as fatal but did not take
 * the process down, e.g. one swallowed by `DCLApplication`'s uncaught-exception guard or thrown
 * on a thread the loaded app owns. Mirrors the `FATAL EXCEPTION` check in `scripts/test-apk.sh`.
 *
 * Runs `logcat` from the app's own process (an app may read its own log lines) filtered by pid
 * and by the time the test started. It deliberately does **not** use `UiAutomation`: under the
 * orchestrator a fresh process per test races the previous one's UiAutomation connection and
 * fails with "UiAutomationService ... already registered", and `logcat -c` would clear the
 * device-wide buffer.
 */
class LogcatCrashRule : TestWatcher() {
    private var startedAtMs = 0L

    override fun starting(description: Description) {
        startedAtMs = System.currentTimeMillis()
    }

    override fun succeeded(description: Description) {
        // logcat -T takes "MM-DD HH:MM:SS.mmm" in the device's local time
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(startedAtMs))
        val log = logcat("-d", "-T", since, "--pid=${Process.myPid()}", "-s", "AndroidRuntime:E")
        val idx = log.indexOf("FATAL EXCEPTION")
        if (idx >= 0) {
            throw AssertionError("Process logged a FATAL EXCEPTION during the test:\n" + log.substring(idx).take(2000))
        }
    }

    private fun logcat(vararg args: String): String {
        val proc = ProcessBuilder(listOf("logcat") + args).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        return out
    }
}
