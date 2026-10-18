package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * DCLActivity runs whatever APK its intent names inside the host (#55), so the shipped (main) manifest must not
 * export it; only the debug overlay does, for `am start`. Reads the source manifests, like the pool tests.
 */
class DCLActivityExportTest {
    private fun exportedOf(manifest: String, name: String): String? =
        Regex("""<activity\b[^>]*?android:name="$name"[^>]*?>""", RegexOption.DOT_MATCHES_ALL).find(manifest)?.value
            ?.let { Regex("""android:exported="(\w+)"""").find(it)?.groupValues?.get(1) }

    @Test fun theMainManifestDoesNotExportDclActivity() {
        val main = File("src/main/AndroidManifest.xml").readText()
        assertThat(exportedOf(main, "\\.dcl\\.DCLActivity")).isEqualTo("false")
    }

    @Test fun onlyTheDebugOverlayExportsIt() {
        val debug = File("src/debug/AndroidManifest.xml").readText()
        assertThat(exportedOf(debug, "\\.dcl\\.DCLActivity")).isEqualTo("true")
        assertThat(File("src/release/AndroidManifest.xml").exists()).isFalse()
    }

    @Test fun theProxySlotsStayPrivate() {
        val main = File("src/main/AndroidManifest.xml").readText()
        for (name in DCLActivityProxyPool.allClassNames()) {
            assertThat(exportedOf(main, name.removePrefix("com.mikimn.apkloader"))).isEqualTo("false")
        }
    }
}
