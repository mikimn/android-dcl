package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * DCLActivity runs whatever APK its intent names inside the host (#55), so the shipped (main) manifest must not
 * export it, nor anything else that reaches the loader: the only exported component is the launcher. A debug-only
 * overlay re-exports DCLActivity for `am start`, guarded by a permission that the shell holds and apps do not.
 * Reads the source manifests, like the pool tests.
 */
class DCLActivityExportTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val toolsNs = "http://schemas.android.com/tools"

    private fun components(path: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val app = factory.newDocumentBuilder().parse(File(path)).documentElement
            .getElementsByTagName("application").item(0) as Element
        val kinds = setOf("activity", "activity-alias", "service", "receiver", "provider")
        return (0 until app.childNodes.length).map { app.childNodes.item(it) }
            .filterIsInstance<Element>().filter { it.tagName in kinds }
            .filter { it.getAttributeNS(toolsNs, "node") != "remove" }
    }

    private fun Element.name() = getAttributeNS(androidNs, "name")
    private fun Element.exportedAttr(): String? = getAttributeNS(androidNs, "exported").ifEmpty { null }
    private fun Element.isExported() =
        exportedAttr() == "true" || (exportedAttr() == null && getElementsByTagName("intent-filter").length > 0)

    @Test fun theOnlyExportedComponentOfTheMainManifestIsTheLauncher() {
        val exported = components("src/main/AndroidManifest.xml").filter { it.isExported() }.map { it.name() }
        assertThat(exported).containsExactly(".MainActivity")
    }

    @Test fun dclActivityAndTheProxySlotsArePrivateInMain() {
        val byName = components("src/main/AndroidManifest.xml").associateBy { it.name() }
        assertThat(byName.getValue(".dcl.DCLActivity").exportedAttr()).isEqualTo("false")
        for (name in DCLActivityProxyPool.allClassNames()) {
            assertThat(byName.getValue(name.removePrefix("com.mikimn.apkloader")).exportedAttr()).isEqualTo("false")
        }
    }

    @Test fun onlyTheDebugOverlayExportsDclActivityAndOnlyBehindDump() {
        val debug = components("src/debug/AndroidManifest.xml").single()
        assertThat(debug.name()).isEqualTo(".dcl.DCLActivity")
        assertThat(debug.exportedAttr()).isEqualTo("true")
        assertThat(debug.getAttributeNS(androidNs, "permission")).isEqualTo("android.permission.DUMP")
        assertThat(File("src/release/AndroidManifest.xml").exists()).isFalse()
    }
}
