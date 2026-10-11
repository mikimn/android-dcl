package com.mikimn.apkloader.apk

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The dex reader on hand-built dex files: the optimized (memory-mapped) path, and hostile/corrupt input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class HiddenApiExemptionsDexTest {
    @get:Rule val tmp = TemporaryFolder()

    /**
     * A minimal but structurally valid dex: header, string_ids, type_ids, class_defs, string data.
     * [typeStrings] are the descriptor of each type; [definedTypes] the type indices with a class_def.
     */
    private fun dex(typeStrings: List<String>, definedTypes: List<Int>): ByteArray {
        val headerSize = 0x70
        val stringIdsOff = headerSize
        val typeIdsOff = stringIdsOff + 4 * typeStrings.size
        val classDefsOff = typeIdsOff + 4 * typeStrings.size
        val dataOff = classDefsOff + 32 * definedTypes.size
        val data = java.io.ByteArrayOutputStream()
        val stringOffsets = typeStrings.map { str ->
            val off = dataOff + data.size()
            val bytes = str.toByteArray()
            require(bytes.size < 128)
            data.write(bytes.size); data.write(bytes); data.write(0)
            off
        }
        val buf = ByteBuffer.allocate(dataOff + data.size()).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), '0'.code.toByte(), '3'.code.toByte(), '5'.code.toByte(), 0))
        buf.putInt(0x38, typeStrings.size); buf.putInt(0x3C, stringIdsOff)
        buf.putInt(0x40, typeStrings.size); buf.putInt(0x44, typeIdsOff)
        buf.putInt(0x60, definedTypes.size); buf.putInt(0x64, classDefsOff)
        stringOffsets.forEachIndexed { i, off -> buf.putInt(stringIdsOff + 4 * i, off) }
        typeStrings.indices.forEach { buf.putInt(typeIdsOff + 4 * it, it) } // type i -> string i
        definedTypes.forEachIndexed { i, t -> buf.putInt(classDefsOff + 32 * i, t) }
        buf.position(dataOff); buf.put(data.toByteArray())
        return buf.array()
    }

    private fun file(bytes: ByteArray): File = tmp.newFile().also { it.writeBytes(bytes) }

    private val valid = dex(listOf("Lcom/example/Mine;", "Lcom/vendor/inner/Ext;", "I"), definedTypes = listOf(0))

    @Test fun readsTheTypeTablesOfAHandBuiltDex() {
        val types = HiddenApiExemptions.dexTypes(valid)
        assertThat(types.referenced).containsExactly("Lcom/example/Mine;", "Lcom/vendor/inner/Ext;", "I").inOrder()
        assertThat(types.defined).containsExactly("Lcom/example/Mine;")
    }

    @Test fun theMappedReaderReturnsOnlyWhatTheDexReferencesButDoesNotDefine() {
        assertThat(HiddenApiExemptions.externalTypes(file(valid))).containsExactly("Lcom/vendor/inner/Ext;") // primitives and platform types are skipped on the raw bytes
    }

    @Test fun truncatedHeaderIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(valid.copyOf(0x20)) }
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(ByteArray(0)) }
    }

    @Test fun aTableOffsetOutsideTheFileIsRejectedBeforeItIsFollowed() {
        val bad = valid.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x44, Int.MAX_VALUE - 8) } // type_ids offset
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(bad) }
        val hugeCount = valid.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x40, 1_000_000_000) } // type_ids count
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(hugeCount) }
        val negative = valid.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x40, -1) }
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(negative) }
    }

    @Test fun aClassDefOrTypeEntryPointingOutsideItsTableIsRejected() {
        val badClassDef = dex(listOf("Lcom/example/Mine;"), definedTypes = listOf(0)).also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70 + 4 + 4, 99) // class_def 0 -> type 99 of 1
        }
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(badClassDef) }
        val badType = valid.copyOf().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0x70 + 4 * 3, 77) // type 0 -> string 77 of 3
        }
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(badType) }
    }

    @Test fun anUnterminatedStringIsRejectedNotReadPastTheFile() {
        val cut = valid.copyOf(valid.size - 3) // chops the last string's terminator
        assertThrows(IllegalArgumentException::class.java) { HiddenApiExemptions.dexTypes(cut) }
    }

    // Fails closed: a corrupt dex yields no exemptions and never throws out of applyFor.
    @Test fun applyForReturnsNothingForCorruptDexFilesAndDoesNotThrow() {
        val files = listOf(file(valid.copyOf(0x20)), file(ByteArray(0)), file("plain text, not a dex".toByteArray()))
        assertThat(HiddenApiExemptions.applyFor(files)).isEmpty()
    }

    @Test fun aGoodDexThatReferencesNoBootVendorClassNeedsNoExemption() {
        // com.vendor.inner.Ext does not exist on the JVM's boot path, so nothing is exempted
        assertThat(HiddenApiExemptions.applyFor(listOf(file(valid)))).isEmpty()
    }
}
