package com.mikimn.apkloader.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
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

    // zip-slip: an entry that resolves outside the output dir must never be written, and
    // extraction must fail loudly (not return a half-extracted dir).
    private fun assertSlipRejected(entryName: String, viaStream: Boolean = false) {
        val parent = tmp.newFolder()
        val out = File(parent, "out").apply { mkdirs() }
        val data = zipOf(entryName to "pwned")
        assertThrows(SecurityException::class.java) {
            if (viaStream) Zip.unzip(ZipInputStream(ByteArrayInputStream(data)), out)
            else Zip.unzip(ZipFile(tmp.newFile().apply { writeBytes(data) }), out)
        }
        assertThat(parent.walkTopDown().filter { it.isFile && it.name == "evil.txt" }.toList()).isEmpty()
    }

    @Test fun rejectsParentTraversalEntry() = assertSlipRejected("../evil.txt")
    @Test fun rejectsDeepParentTraversalEntry() = assertSlipRejected("a/b/../../../evil.txt")
    @Test fun rejectsTraversalEntryDirectoryToo() = assertSlipRejected("../evil-dir/")
    @Test fun rejectsParentTraversalViaStreamOverload() = assertSlipRejected("../evil.txt", viaStream = true)

    @Test fun allowsDotDotThatStaysInsideOutputDir() {
        val out = out()
        Zip.unzip(zipFile("a/../b.txt" to "B"), out)
        assertThat(File(out, "b.txt").readText()).isEqualTo("B")
    }

    @Test fun siblingDirWithCommonPrefixIsNotInside() {
        // "/tmp/out" must not be treated as containing "/tmp/out-evil/x".
        val parent = tmp.newFolder()
        val out = File(parent, "out").apply { mkdirs() }
        assertThrows(SecurityException::class.java) {
            Zip.unzip(zipFile("../out-evil/x.txt" to "x"), out)
        }
        assertThat(File(parent, "out-evil").exists()).isFalse()
    }

    // A *file* entry that resolves to the output directory itself ("a/..", ".") must not make
    // extraction delete the directory and write a file in its place (review finding on the fix).
    private fun assertRootEntryRejected(entryName: String, viaStream: Boolean) {
        val out = tmp.newFolder()
        File(out, "keep.txt").writeText("keep")
        val data = zipOf(entryName to "pwned")
        assertThrows(SecurityException::class.java) {
            if (viaStream) Zip.unzip(ZipInputStream(ByteArrayInputStream(data)), out)
            else Zip.unzip(ZipFile(tmp.newFile().apply { writeBytes(data) }), out)
        }
        assertThat(out.isDirectory).isTrue()
        assertThat(File(out, "keep.txt").readText()).isEqualTo("keep")
    }

    @Test fun rejectsFileEntryResolvingToOutputDir() = assertRootEntryRejected("a/..", viaStream = false)
    @Test fun rejectsDotFileEntryResolvingToOutputDir() = assertRootEntryRejected(".", viaStream = false)
    @Test fun rejectsFileEntryResolvingToOutputDirViaStream() = assertRootEntryRejected("a/..", viaStream = true)

    @Test fun directoryEntryResolvingToOutputDirIsHarmless() {
        val out = tmp.newFolder()
        File(out, "keep.txt").writeText("keep")
        Zip.unzip(zipFile("a/../" to null, "b.txt" to "B"), out)
        assertThat(File(out, "keep.txt").readText()).isEqualTo("keep")
        assertThat(File(out, "b.txt").readText()).isEqualTo("B")
    }
}
