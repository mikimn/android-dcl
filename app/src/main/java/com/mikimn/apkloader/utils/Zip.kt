package com.mikimn.apkloader.utils

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


object Zip {
    /**
     * Resolves a zip entry name against [outputDir], refusing names that escape it ("zip-slip":
     * `../../x`). Names like `a/../b` that stay inside are fine and are normalized.
     *
     * @throws SecurityException if the resolved path is outside [outputDir]. Deliberately not
     * swallowed by [unzip]'s generic error handling: a hostile archive must fail loudly instead
     * of loading half-extracted.
     */
    private fun resolveEntry(outputDir: File, entryName: String): File {
        val target = File(outputDir, entryName)
        val root = outputDir.canonicalPath
        val resolved = target.canonicalPath
        if (resolved != root && !resolved.startsWith(root + File.separator)) {
            throw SecurityException("Zip entry escapes the output directory: $entryName")
        }
        // Hand back the normalized path: `out/a/../b.txt` can't be opened when `a` doesn't exist.
        return File(resolved)
    }

    /**
     * Extract a zip file into any directory
     *
     * @param zipFile src zip file
     * @param extractTo directory to extract into.
     * There will be new folder with the zip's name inside [extractTo] directory.
     * @param extractHere no extra folder will be created and will be extracted
     * directly inside [extractTo] folder.
     *
     * @return the extracted directory i.e, [extractTo] folder if [extractHere] is `true`
     * and [extractTo]\zipFile\ folder otherwise.
     */
    // TODO(@mikimn): Doesnt seem to work for SimpleAPK. Not all files are extracted properly.
    fun unzip(
        zipFile: ZipInputStream,
        outputDir: File
    ): File? {
        return try {
            val buffer = ByteArray(2048)

            zipFile.closeEntry()
            var zipEntry: ZipEntry? = zipFile.nextEntry
            while (zipEntry != null) {
                val entry = zipEntry
                if (entry.isDirectory) {
                    val d = resolveEntry(outputDir, entry.name)
                    if (!d.exists()) d.mkdirs()
                } else {
                    val f = resolveEntry(outputDir, entry.name)
                    if (f.parentFile?.exists() != true) f.parentFile?.mkdirs()

                    f.delete()
                    f.outputStream().use { output ->
                        var len: Int
                        while ((zipFile.read(buffer).also { len = it }) > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }

                zipFile.closeEntry()
                zipEntry = zipFile.nextEntry
            }

            outputDir
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun unzip(zipFile: ZipFile, outputDir: File): File? {
        return try {
            val buffer = ByteArray(2048)


            for (zipEntry in zipFile.stream()) {
                if (zipEntry.isDirectory) {
                    val d = resolveEntry(outputDir, zipEntry.name)
                    if (!d.exists()) d.mkdirs()
                } else {
                    val f = resolveEntry(outputDir, zipEntry.name)
                    if (f.parentFile?.exists() != true) f.parentFile?.mkdirs()

                    f.delete()
                    f.outputStream().use { output ->
                        val inputStream = zipFile.getInputStream(zipEntry)
                        var len: Int
                        while ((inputStream.read(buffer).also { len = it }) != -1) {
                            output.write(buffer, 0, len)
                        }
                    }
                }
            }

            outputDir
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}