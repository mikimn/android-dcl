package com.mikimn.apkloader.loader

interface ApkLoader {
    val tempPath: String

    val loader: ClassLoader

    fun loadClass(name: String): Class<*>
}