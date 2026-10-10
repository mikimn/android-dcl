package com.mikimn.apkloader.apk

import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Works out which hidden-API exemptions a loaded APK needs, instead of hardcoding vendors.
 *
 * Why exemptions at all: the runtime enforces the hidden-API list on *bytecode linking*, not just
 * reflection, so a loaded app whose own dex links against an OEM framework wrapper on the boot
 * classpath (e.g. `com.oneplus.inner.os.UserHandleWrapper`, in `oneplus_framework_wrapper.jar`)
 * dies with `NoSuchFieldError`/`NoSuchMethodError` even though the member exists. The real app is
 * exempt, the host is not, and `StrictMode.permitNonSdkApiUsage()` only silences detection.
 *
 * Why not exempt everything: `addHiddenApiExemptions("L")` was tried and breaks the platform's
 * own `ActivityThread`. So exemptions must stay narrow.
 *
 * What is derived: every type descriptor in the APK's dex files that resolves on the **boot**
 * class path but isn't AOSP-owned is a vendor class; its vendor namespace (the first two package
 * segments, e.g. `com/oneplus/`) is exempted. Classes only reached by reflection by *name* have no
 * type-table entry and are not covered.
 */
object HiddenApiExemptions {
    private const val TAG = "HiddenApiExemptions"

    /** Package roots owned by AOSP / the Java runtime; never vendor wrappers. */
    private val PLATFORM_PREFIXES = listOf(
        "java/", "javax/", "android/", "androidx/", "dalvik/", "libcore/", "sun/", "kotlin/", "kotlinx/",
        "com/android/", "com/google/android/", "org/apache/harmony/", "org/apache/http/", "org/json/",
        "org/w3c/", "org/xml/", "org/xmlpull/", "org/conscrypt/", "org/bouncycastle/", "org/kxml2/",
        "junit/", "jdk/",
    )

    private val applied: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Derives the exemption prefixes for [dexFiles] and applies those not yet applied. Safe to call
     * for every loaded APK, before any of its classes are linked. Failures are logged, never thrown:
     * without exemptions a vendor-linking app fails the way it always did.
     *
     * @return the prefixes newly applied by this call.
     */
    fun applyFor(dexFiles: List<File>): Set<String> = try {
        val dexes = dexFiles.map { dexTypes(it.readBytes()) }
        // Classes some dex of this APK defines are the app's own, whichever dex references them.
        val defined = dexes.flatMapTo(HashSet()) { it.defined }
        val descriptors = dexes.flatMap { it.referenced }.filter { it !in defined }
        val prefixes = vendorExemptions(descriptors, ::isBootClass) - applied
        if (prefixes.isNotEmpty()) {
            HiddenApiBypass.addHiddenApiExemptions(*prefixes.toTypedArray())
            applied.addAll(prefixes)
            Log.i(TAG, "Exempted vendor namespaces referenced by the APK: $prefixes")
        }
        prefixes
    } catch (e: Throwable) {
        Log.e(TAG, "Could not derive hidden-API exemptions", e)
        emptySet()
    }

    /**
     * The exemption prefixes (`Lcom/vendor/`) for the vendor classes among [descriptors]
     * (`Lcom/vendor/Foo;`, arrays allowed). [isBootClass] says whether a binary name
     * (`com.vendor.Foo`) loads from the boot class path. Pure, so it is unit-testable.
     */
    fun vendorExemptions(descriptors: Collection<String>, isBootClass: (String) -> Boolean): Set<String> {
        val result = linkedSetOf<String>()
        for (raw in descriptors) {
            val descriptor = raw.trimStart('[')
            if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) continue // primitive
            val internalName = descriptor.substring(1, descriptor.length - 1)
            if (PLATFORM_PREFIXES.any { internalName.startsWith(it) }) continue
            val segments = internalName.split('/')
            if (segments.size < 3) continue // a vendor namespace needs at least vendor + package + class
            val root = "${segments[0]}/${segments[1]}/"
            if (result.contains("L$root")) continue // already covered, skip the class lookup
            if (isBootClass(internalName.replace('/', '.'))) result.add("L$root")
        }
        return result
    }

    // Loaded by the boot class loader alone, i.e. a framework/vendor jar on BOOTCLASSPATH, not the app.
    private fun isBootClass(binaryName: String): Boolean = try {
        Class.forName(binaryName, false, Any::class.java.classLoader)
        true
    } catch (_: Throwable) {
        false
    }

    class DexTypes(
        /** Every type in the type table: classes the dex defines *or* references (`Lfoo/Bar;`, `[I`, ...). */
        val referenced: List<String>,
        /** The subset it defines (its `class_defs`). */
        val defined: Set<String>,
    )

    /** Reads the header, `string_ids`, `type_ids` and `class_defs` of a dex file. */
    fun dexTypes(dex: ByteArray): DexTypes {
        require(dex.size >= 0x70 && dex[0] == 'd'.code.toByte() && dex[1] == 'e'.code.toByte() && dex[2] == 'x'.code.toByte()) {
            "Not a dex file"
        }
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsOff = buf.getInt(0x3C)
        val typeIdsSize = buf.getInt(0x40)
        val typeIdsOff = buf.getInt(0x44)
        val classDefsSize = buf.getInt(0x60)
        val classDefsOff = buf.getInt(0x64)

        val types = (0 until typeIdsSize).map { type ->
            val stringIdx = buf.getInt(typeIdsOff + type * 4)
            readString(buf, buf.getInt(stringIdsOff + stringIdx * 4))
        }
        // class_def_item is 32 bytes; its first field is the type index of the class.
        val defined = (0 until classDefsSize).mapTo(HashSet()) { types[buf.getInt(classDefsOff + it * 32)] }
        return DexTypes(types, defined)
    }

    // string_data_item: uleb128 utf16 length, then NUL-terminated (modified) UTF-8.
    private fun readString(buf: ByteBuffer, offset: Int): String {
        var pos = offset
        while (buf.get(pos).toInt() and 0x80 != 0) pos++ // skip the uleb128 length
        pos++
        val start = pos
        while (buf.get(pos).toInt() != 0) pos++
        val bytes = ByteArray(pos - start)
        for (i in bytes.indices) bytes[i] = buf.get(start + i)
        return String(bytes, Charsets.UTF_8)
    }
}
