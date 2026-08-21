package com.mikimn.apkloader.plugins

import android.content.pm.PackageManager
import com.mikimn.apkloader.pm.DefaultPackageManagerPlugin
import com.mikimn.apkloader.pm.PackageManagerAggregate

class DefaultPluginProvider : ContextPluginProvider {
    companion object {
        private var instance: PackageManager? = null
    }

    override fun providePackageManager(base: PackageManager): PackageManager {
        if (instance == null) {
            instance = PackageManagerAggregate(
                PlayServicesBlockingPackageManager(base), arrayOf(
                    DefaultPackageManagerPlugin(base)
                )
            )
        }
        return instance!!
    }
}