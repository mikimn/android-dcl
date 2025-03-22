package com.mikimn.apkloader

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException


class AssetReader(context: Context) {
    companion object {
        private val TAG = this::class.java.simpleName
    }
    val manager: AssetManager = context.assets

    // TODO: Doesn't handle the case where the file is too big
    fun readFile(name: String): ByteArray {
        return try {
            val stream = manager.open(name)
            val bytes = ByteArray(stream.available())
            val dataInputStream = DataInputStream(stream)
            dataInputStream.readFully(bytes)
            bytes
        } catch (exception: IOException) {
            Log.e(TAG, exception.toString())
            throw exception
        } catch (exception: EOFException) {
            Log.e(TAG, exception.toString())
            throw exception
        }
    }
}