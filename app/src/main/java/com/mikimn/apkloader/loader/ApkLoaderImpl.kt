package com.mikimn.apkloader.loader

import android.util.Log
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.util.DexUtil
import java.io.File
import java.nio.ByteBuffer
import java.util.jar.JarEntry
import java.util.jar.JarInputStream


class ApkLoaderImpl(data: ByteArray, classLoader: ClassLoader) : ApkLoader {
    private val tempFile: File
    override val loader: DexClassLoader

    companion object {
        private val TAG = this::class.java.simpleName
    }

    init {
        Log.d(TAG, "Load APK of size: ${data.size}")
        Log.d(TAG, "Header: ${data.copyOfRange(0, 1000).toList()}")

        tempFile = File.createTempFile("temp", ".apk")
        tempFile.writeBytes(data)
        tempFile.setReadOnly()

        loader = DexClassLoader(tempFile.absolutePath, tempFile.parent, null, classLoader)
    }

    override val tempPath: String
        get() = tempFile.absolutePath

    override fun loadClass(name: String): Class<*> {
        return loader.loadClass(name)
    }
}