package com.mikimn.apkloader

import android.app.Application
import android.content.Context
import com.mikimn.apkloader.loader.ApkLoader
import com.mikimn.apkloader.loader.ApkLoaderImpl

class DCLApplication : Application() {
    private lateinit var reader: AssetReader
    private lateinit var loader: ApkLoader

    override fun onCreate() {
        super.onCreate()
        reader = AssetReader(this)
        loader = ApkLoaderImpl(reader.readFile("calculator.apk"), classLoader)

        overrideClassLoader(baseContext)

        val fPackageInfo = baseContext.javaClass.tryGetField("mPackageInfo")
        fPackageInfo?.isAccessible = true
        val packageInfo = fPackageInfo?.get(baseContext)

        packageInfo?.let {
            overrideClassLoader(it)
        }
    }

    private fun overrideClassLoader(baseContext: Any) {
        val fClassLoader = baseContext.javaClass.tryGetField("mClassLoader")
        fClassLoader?.isAccessible = true
        fClassLoader?.set(baseContext, loader.loader)
    }
}