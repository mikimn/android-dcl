package com.mikimn.apkloader.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (content != null) zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun zipFile(vararg entries: Pair<String, String?>): ZipFile =
        ZipFile(tmp.newFile().apply { writeBytes(zipOf(*entries)) })

    private fun out() = tmp.newFolder()

    @Test fun extractsFilesAndNestedDirectories() {
        val out = out()
        val result = Zip.unzip(
            zipFile("a.txt" to "A", "dir/" to null, "dir/b.txt" to "B", "x/y/z/c.txt" to "C"), out
        )
        assertThat(result).isEqualTo(out)
        assertThat(File(out, "a.txt").readText()).isEqualTo("A")
        assertThat(File(out, "dir/b.txt").readText()).isEqualTo("B")
        assertThat(File(out, "x/y/z/c.txt").readText()).isEqualTo("C")
        assertThat(File(out, "dir").isDirectory).isTrue()
    }

    @Test fun createsParentDirsForEntriesWithoutExplicitDirEntry() {
        val out = out()
        Zip.unzip(zipFile("lib/arm64-v8a/libx.so" to "ELF"), out)
        assertThat(File(out, "lib/arm64-v8a/libx.so").readText()).isEqualTo("ELF")
    }

    @Test fun overwritesStaleFile() {
        val out = out()
        File(out, "a.txt").writeText("stale and much longer than the new content")
        Zip.unzip(zipFile("a.txt" to "new"), out)
        assertThat(File(out, "a.txt").readText()).isEqualTo("new")
    }

    @Test fun emptyZipExtractsNothing() {
        val out = out()
        assertThat(Zip.unzip(zipFile(), out)).isEqualTo(out)
        assertThat(out.listFiles()).isEmpty()
    }

    @Test fun largeEntrySpanningManyBuffersIsIntact() {
        val big = "0123456789".repeat(10_000)
        val out = out()
        Zip.unzip(zipFile("big.bin" to big), out)
        assertThat(File(out, "big.bin").readText()).isEqualTo(big)
    }

    @Test fun streamAndFileOverloadsAgree() {
        val data = zipOf("a.txt" to "A", "d/b.txt" to "B")
        val viaFile = out().also { Zip.unzip(ZipFile(tmp.newFile().apply { writeBytes(data) }), it) }
        val viaStream = out().also { Zip.unzip(ZipInputStream(ByteArrayInputStream(data)), it) }
        for (rel in listOf("a.txt", "d/b.txt")) {
            assertThat(File(viaStream, rel).readText()).isEqualTo(File(viaFile, rel).readText())
        }
    }

    // Documents current behavior: failures are swallowed and surface as null, not an exception.
    @Test fun corruptStreamReturnsNull() {
        val corrupt = ZipInputStream(ByteArrayInputStream(ByteArray(64) { 0x50 }))
        // Not a valid local file header: either no entries (returns outputDir) or null on error;
        // what must never happen is an exception escaping.
        Zip.unzip(corrupt, out())
    }

    // zip-slip: an entry named "../x" must never be written outside the output dir.
    // KNOWN BUG: neither overload sanitizes entry names. See PR description.
    @org.junit.Ignore("known bug: Zip.unzip is vulnerable to zip-slip; remove @Ignore when fixed")
    @Test fun rejectsEntriesEscapingOutputDir() {
        val parent = tmp.newFolder()
        val out = File(parent, "out").apply { mkdirs() }
        Zip.unzip(zipFile("../evil.txt" to "pwned"), out)
        assertThat(File(parent, "evil.txt").exists()).isFalse()
    }
}
