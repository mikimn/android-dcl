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

    // Only spoof the package name for callers that are the loaded APK's own code - real
    // OS-level subsystems (WebView among them, confirmed on-device) rely on getPackageName()
    // returning the host's actual, installed identity and break if lied to unconditionally.
    // See docs/apk-test-log.md's "Meme Generator" research notes for how this was derived.
    override fun getPackageName(): String {
        val shadow = shadowPackageName ?: return super.getPackageName()
        val loader = classLoader as? FileTrackingClassLoader ?: return super.getPackageName()
        val callerClassName = CallerClassResolver.findRealCallerClassName() ?: return super.getPackageName()
        return if (loader.ownerOf(callerClassName) != null) shadow else super.getPackageName()
    }

    private var cachedResources: Resources? = null

    // A Context's getResources() is expected to return a stable identity across calls
    // (real Android Contexts cache it) - this used to build a brand-new wrapper on
    // every call, silently discarding any ResourcesLoader anyone had attached to a
    // previous call's result (e.g. DCLActivity.initResourceLoader's
    // baseContext.resources.addLoaders(...)), since native code frequently re-fetches
    // .resources rather than holding onto one reference.
    override fun getResources(): Resources {
        cachedResources?.let { return it }

        val originalResources = super.getResources()
        val wrapped = object : Resources(
            originalResources.assets, originalResources.displayMetrics, originalResources.configuration
        ) {
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
        cachedResources = wrapped
        return wrapped
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
