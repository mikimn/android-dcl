package com.mikimn.apkloader.testing

import android.content.Context
import java.io.File

/**
 * How a test observes what code inside a loaded (fixture) APK did.
 *
 * Loaded APKs run in the host process and share its data directory and uid, so a fixture can
 * report back through plain files, with no dependency on the host or test classes.
 *
 * **Protocol (fixture side)**: append one line per event to
 * `<host filesDir>/probe/<channel>.log`, e.g. `onCreate`, `lifecycle:RESUMED`, `key=value`.
 * A fixture can find the directory from any `Context` with
 * `File(context.filesDir, "probe/<channel>.log")`. Because `filesDir` is shared, this works no
 * matter which package name the loaded code sees.
 *
 * **Test side**: this class. Always construct it with the *target* (host app) context, e.g.
 * `InstrumentationRegistry.getInstrumentation().targetContext`: the test APK's own context has a
 * different `filesDir`, so a probe built from it would never see the fixture's events. Use [clear] in `@Before`; each test runs in a fresh process under
 * the orchestrator, so this is only needed when a test reuses a channel across phases.
 */
class ProbeChannel(context: Context, val name: String, loadedPackage: String? = null) {
    // A fixture running through DCLActivity gets per-package storage (docs/TESTING.md, "Per-package
    // storage"): its filesDir is <host dataDir>/virtual/<package>/files. Pass [loadedPackage] to
    // read what such a fixture wrote; leave it null for code running with the host's own context.
    private val file = File(File(filesDirOf(context, loadedPackage), DIR), "$name.log")

    /** All events recorded so far, in order. Empty if the fixture hasn't written anything. */
    fun events(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotEmpty() } else emptyList()

    fun count(event: String): Int = events().count { it == event }

    fun clear() {
        file.delete()
    }

    /** Test-side writer, used to unit-test the channel itself and to simulate a fixture. */
    fun append(event: String) {
        file.parentFile!!.mkdirs()
        file.appendText(event + "\n")
    }

    /** Waits until [event] has been recorded at least once. */
    fun awaitEvent(event: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        waitFor("probe '$name' to record '$event' (saw ${events()})", timeoutMs) {
            event in events()
        }
    }

    /** Waits until the recorded events satisfy [predicate] and returns them. */
    fun awaitEvents(
        description: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        predicate: (List<String>) -> Boolean,
    ): List<String> = waitForNotNull("probe '$name': $description", timeoutMs) {
        events().takeIf(predicate)
    }

    /** Value of the latest `key=value` event for [key], or null. */
    fun valueOf(key: String): String? =
        events().lastOrNull { it.startsWith("$key=") }?.substringAfter('=')

    companion object {
        const val DIR = "probe"

        private fun filesDirOf(context: Context, loadedPackage: String?): File =
            if (loadedPackage == null) context.filesDir
            else File(File(File(context.dataDir, "virtual"), loadedPackage), "files")
    }
}
