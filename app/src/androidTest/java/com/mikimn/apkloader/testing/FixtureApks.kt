package com.mikimn.apkloader.testing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException

/**
 * Access to APKs packaged into the *androidTest* assets (under `fixtures/`).
 *
 * `DCLActivity` resolves a bare name against the **host's** assets, which does not contain the
 * test assets, so tests load fixtures by absolute path: [install] copies the APK into the
 * host's private files dir and returns that path (readable by the host, no storage permission).
 */
object FixtureApks {
    private const val ASSET_DIR = "fixtures"

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun available(): List<String> =
        testContext.assets.list(ASSET_DIR)?.filter { it.endsWith(".apk") }?.sorted() ?: emptyList()

    /** @param fileName e.g. `"fx-hello.apk"` */
    fun install(fileName: String): File {
        val out = File(File(targetContext.filesDir, "fixture-apks").apply { mkdirs() }, fileName)
        val bytes = try {
            testContext.assets.open("$ASSET_DIR/$fileName").use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            throw AssertionError(
                "Fixture '$fileName' is not packaged in the androidTest assets. Available: ${available()}",
                e,
            )
        }
        out.writeBytes(bytes)
        return out
    }
}
