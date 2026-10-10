package com.mikimn.apkloader.apk

import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
 *
 * **Trust.** The dex is untrusted input and exemptions are process-global and irreversible. A hostile
 * APK can therefore exempt any boot-class-path *vendor* namespace it references (by naming a class
 * there), which is exactly what a legitimate vendor app does. What it cannot do is touch anything
 * AOSP-owned: [PLATFORM_PREFIXES] is the allow-list that keeps `java/`, `android/`, `com/android/`,
 * `dalvik/`, ... out, and a unit test pins that for every prefix. A corrupt or truncated dex fails
 * closed: it is rejected before its tables are trusted, and no exemption is applied for it.
 */
object HiddenApiExemptions {
    private const val TAG = "HiddenApiExemptions"

    /** Package roots owned by AOSP / the Java runtime; never vendor wrappers. */
    internal val PLATFORM_PREFIXES = listOf(
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
        val started = System.nanoTime()
        // Per dex: only the types it references but does not itself define (memory-mapped, so a 100 MB
        // APK's dex never lands on the heap). A type defined by a *different* dex of the same APK looks
        // external here; vendorExemptions' per-package negative cache keeps that from costing a lookup each.
        val descriptors = dexFiles.flatMap { externalTypes(it) }
        val prefixes = vendorExemptions(descriptors, ::isBootClass) - applied
        if (prefixes.isNotEmpty()) {
            HiddenApiBypass.addHiddenApiExemptions(*prefixes.toTypedArray())
            applied.addAll(prefixes)
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i(
            TAG, "Exempted vendor namespaces referenced by the APK: $prefixes " +
                "(${dexFiles.size} dex, ${descriptors.size} external types, $ms ms)"
        )
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
        // A package either holds boot-path classes or it doesn't: after a few misses in one package the
        // rest are the app's own (typically cross-dex references), so stop paying a failed lookup each.
        val misses = HashMap<String, Int>()
        for (raw in descriptors) {
            val descriptor = raw.trimStart('[')
            if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) continue // primitive
            val internalName = descriptor.substring(1, descriptor.length - 1)
            if (PLATFORM_PREFIXES.any { internalName.startsWith(it) }) continue
            val segments = internalName.split('/')
            if (segments.size < 3) continue // a vendor namespace needs at least vendor + package + class
            val root = "${segments[0]}/${segments[1]}/"
            if (result.contains("L$root")) continue // already covered, skip the class lookup
            val pkg = internalName.substringBeforeLast('/')
            if ((misses[pkg] ?: 0) >= MAX_MISSES_PER_PACKAGE) continue
            if (isBootClass(internalName.replace('/', '.'))) result.add("L$root") else misses.merge(pkg, 1, Int::plus)
        }
        return result
    }

    private const val MAX_MISSES_PER_PACKAGE = 3

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
        val view = DexView(ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN))
        val types = (0 until view.typeCount).map { view.descriptor(it) }
        return DexTypes(types, view.definedTypeIndices().mapTo(HashSet()) { types[it] })
    }

    /**
     * The type descriptors [file] references but does not define. Memory-mapped and decoded only for
     * the external types, so a huge dex costs one pass over two integer tables, not a heap copy and a
     * `String` per class.
     */
    fun externalTypes(file: File): List<String> = RandomAccessFile(file, "r").use { raf ->
        val view = DexView(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN))
        val defined = view.definedTypeIndices()
        val isDefined = BooleanArray(view.typeCount).also { flags -> defined.forEach { flags[it] = true } }
        // Most external types are java/android/kotlin/androidx...: skip those on the raw bytes, before a
        // String is allocated (vendorExemptions would drop them anyway, but only after the decode).
        (0 until view.typeCount).mapNotNull { if (isDefined[it]) null else view.descriptorUnlessPlatform(it, PLATFORM_PREFIX_BYTES) }
    }

    private val PLATFORM_PREFIX_BYTES: List<ByteArray> =
        PLATFORM_PREFIXES.filter { it.endsWith("/") }.map { it.toByteArray() }

    /**
     * A dex file's tables, **validated before they are trusted**: the dex is untrusted input, so a
     * truncated header, or an offset or count that points outside the file, is rejected up front
     * (`IllegalArgumentException`) rather than followed.
     */
    private class DexView(private val buf: ByteBuffer) {
        private val limit = buf.limit().toLong()
        private val stringIdsOff: Int
        private val stringIdsSize: Int
        private val typeIdsOff: Int
        val typeCount: Int
        private val classDefsOff: Int
        private val classDefsSize: Int

        init {
            require(limit >= HEADER_SIZE) { "Not a dex file (too short for a header: $limit bytes)" }
            require(buf.get(0) == 'd'.code.toByte() && buf.get(1) == 'e'.code.toByte() && buf.get(2) == 'x'.code.toByte()) {
                "Not a dex file"
            }
            stringIdsSize = buf.getInt(0x38)
            stringIdsOff = buf.getInt(0x3C)
            typeCount = buf.getInt(0x40)
            typeIdsOff = buf.getInt(0x44)
            classDefsSize = buf.getInt(0x60)
            classDefsOff = buf.getInt(0x64)
            requireTable("string_ids", stringIdsOff, stringIdsSize, 4)
            requireTable("type_ids", typeIdsOff, typeCount, 4)
            requireTable("class_defs", classDefsOff, classDefsSize, 32)
        }

        private fun requireTable(name: String, offset: Int, count: Int, entrySize: Int) {
            require(count >= 0 && offset >= 0 && offset.toLong() + count.toLong() * entrySize <= limit) {
                "Corrupt dex: $name table ($count x $entrySize bytes at $offset) lies outside the $limit-byte file"
            }
        }

        fun descriptor(typeIdx: Int): String {
            val stringIdx = buf.getInt(typeIdsOff + typeIdx * 4)
            require(stringIdx in 0 until stringIdsSize) { "Corrupt dex: type $typeIdx names string $stringIdx of $stringIdsSize" }
            return readString(buf.getInt(stringIdsOff + stringIdx * 4))
        }

        /**
         * The descriptor of [typeIdx], or null if it names a class under one of the [platformPrefixes]
         * (`java/`, `android/`, ...; arrays of them too) or is a primitive: decided on the raw bytes so
         * the common case allocates nothing.
         */
        fun descriptorUnlessPlatform(typeIdx: Int, platformPrefixes: List<ByteArray>): String? {
            val stringIdx = buf.getInt(typeIdsOff + typeIdx * 4)
            require(stringIdx in 0 until stringIdsSize) { "Corrupt dex: type $typeIdx names string $stringIdx of $stringIdsSize" }
            val offset = buf.getInt(stringIdsOff + stringIdx * 4)
            require(offset in 0 until limit) { "Corrupt dex: string data at $offset outside the $limit-byte file" }
            var pos = offset
            while (pos < limit && buf.get(pos).toInt() and 0x80 != 0) pos++ // skip the uleb128 length
            pos++
            while (pos < limit && buf.get(pos) == '['.code.toByte()) pos++ // arrays: judge the element type
            if (pos + 1 >= limit || buf.get(pos) != 'L'.code.toByte()) return null // primitive (or malformed): nothing to exempt
            val start = pos + 1
            for (prefix in platformPrefixes) {
                if (start + prefix.size > limit) continue
                var match = true
                for (i in prefix.indices) if (buf.get(start + i) != prefix[i]) { match = false; break }
                if (match) return null
            }
            return readString(offset)
        }

        /** The type indices of the classes this dex defines (the first field of each `class_def_item`). */
        fun definedTypeIndices(): IntArray = IntArray(classDefsSize) {
            val idx = buf.getInt(classDefsOff + it * 32)
            require(idx in 0 until typeCount) { "Corrupt dex: class_def $it has type $idx of $typeCount" }
            idx
        }

        // string_data_item: uleb128 utf16 length, then NUL-terminated (modified) UTF-8.
        private fun readString(offset: Int): String {
            require(offset in 0 until limit) { "Corrupt dex: string data at $offset outside the $limit-byte file" }
            var pos = offset
            while (pos < limit && buf.get(pos).toInt() and 0x80 != 0) pos++ // skip the uleb128 length
            pos++
            val start = pos
            while (pos < limit && buf.get(pos).toInt() != 0) pos++
            require(pos < limit) { "Corrupt dex: unterminated string at $offset" }
            val bytes = ByteArray(pos - start)
            for (i in bytes.indices) bytes[i] = buf.get(start + i)
            return String(bytes, Charsets.UTF_8)
        }

        private companion object {
            const val HEADER_SIZE = 0x70
        }
    }
}
