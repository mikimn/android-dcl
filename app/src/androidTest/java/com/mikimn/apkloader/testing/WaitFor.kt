package com.mikimn.apkloader.testing

import android.os.SystemClock

/**
 * Polls [condition] until it returns a non-null / true result or [timeoutMs] elapses.
 * Prefer this to `Thread.sleep`: loaded activities reach their states asynchronously, and a
 * fixed sleep is both slow when things work and flaky when they don't.
 */
fun <T : Any> waitForNotNull(
    description: String,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    pollMs: Long = DEFAULT_POLL_MS,
    condition: () -> T?,
): T {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    var lastError: Throwable? = null
    while (true) {
        try {
            condition()?.let { return it }
            lastError = null
        } catch (t: Throwable) {
            lastError = t
        }
        if (SystemClock.elapsedRealtime() >= deadline) {
            throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $description", lastError)
        }
        SystemClock.sleep(pollMs)
    }
}

fun waitFor(
    description: String,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    pollMs: Long = DEFAULT_POLL_MS,
    condition: () -> Boolean,
) {
    waitForNotNull(description, timeoutMs, pollMs) { if (condition()) true else null }
}

/**
 * Asserts [condition] keeps holding for [durationMs] (a bounded negative wait: "this does NOT happen"),
 * failing as soon as it stops. Prefer this to a bare sleep followed by one check.
 */
fun assertHoldsFor(
    description: String,
    durationMs: Long,
    pollMs: Long = DEFAULT_POLL_MS,
    condition: () -> Boolean,
) {
    val deadline = SystemClock.elapsedRealtime() + durationMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (!condition()) throw AssertionError("Stopped holding within ${durationMs}ms: $description")
        SystemClock.sleep(pollMs)
    }
}

const val DEFAULT_TIMEOUT_MS = 10_000L
const val DEFAULT_POLL_MS = 50L
