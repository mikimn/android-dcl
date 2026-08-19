package com.mikimn.apkloader.plugins

import android.content.pm.PackageManager

interface ContextPluginProvider {
    fun providePackageManager(base: PackageManager): PackageManager
}