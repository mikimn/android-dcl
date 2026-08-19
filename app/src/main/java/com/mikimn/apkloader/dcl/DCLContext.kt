package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import com.mikimn.apkloader.MyContextWrapper
import com.mikimn.apkloader.plugins.DefaultPluginProvider
import com.mikimn.apkloader.plugins.ContextPluginProvider

class DCLContext(base: Context, private val pluginProvider: ContextPluginProvider = DefaultPluginProvider()) : ContextWrapper(base) {
    companion object {
        private var shadowPackageName: String? = null
        var shadowApp: Application? = null
    }

    override fun getPackageManager(): PackageManager {
        Log.e("DCLContext", "INVOKE getPackageManager")
        val base = super.getPackageManager()
        return pluginProvider.providePackageManager(base)
    }

    override fun startService(service: Intent?): ComponentName? {
        return service?.component
    }

    //    override fun getPackageName(): String {
//        return shadowPackageName ?: super.getPackageName()
//    }

    // TODO Provide injected resources here
    override fun getResources(): Resources {
        val originalResources = super.getResources()

        return object: Resources(originalResources.assets, originalResources.displayMetrics, originalResources
            .configuration) {
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
                val defaultRes = super.getIdentifier(name, defType, defPackage)

                return shadowPackageName?.let {
                    if (defaultRes == 0) {
                        super.getIdentifier(name, defType, it)
                    } else {
                        defaultRes
                    }
                } ?: defaultRes
            }
        }
    }

    override fun getApplicationContext(): Context {
        return shadowApp ?: super.getApplicationContext()
    }

    fun setShadowPackageName(packageName: String) {
        shadowPackageName = packageName
    }

    fun setShadowApplication(shadowApp: Application) {
        DCLContext.shadowApp = shadowApp
    }
}
