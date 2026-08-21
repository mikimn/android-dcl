package com.mikimn.apkloader.utils

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


object Zip {
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
                    val d = File(outputDir, entry.name)
                    if (!d.exists()) d.mkdirs()
                } else {
                    val f = File(outputDir, entry.name)
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
                    val d = File(outputDir, zipEntry.name)
                    if (!d.exists()) d.mkdirs()
                } else {
                    val f = File(outputDir, zipEntry.name)
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}