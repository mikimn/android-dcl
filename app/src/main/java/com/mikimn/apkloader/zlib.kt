package com.mikimn.apkloader

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Compress a string using ZLIB.
 *
 * @return a compressed byte array.
 */
fun ByteArray.zlibCompress(): ByteArray {
    val input = this

    // Compress the bytes
    // 1 to 4 bytes/char for UTF-8
    val output = ByteArray(input.size * 4)
    val compressor = Deflater().apply {
        setInput(input)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    return output.copyOfRange(0, compressedDataLength)
}

/**
 * Decompress a byte array using ZLIB.
 *
 * @return a decompressed ByteArray
 */
fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater(true)
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}