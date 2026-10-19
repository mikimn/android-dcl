package com.mikimn.apkloader.dcl

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.reflection.tryGetValue
import com.mikimn.apkloader.shadow.ShadowApplication
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Creates the loaded app's real `Service` object on behalf of a host service ([DCLService], [DCLJobService]): loads
 * the APK by name, instantiates the class, and `attach`es it with the **host service's own** token, thread and
 * `IActivityManager`, so `stopSelf()`, `startForeground()` and friends act on the host service the system knows.
 */
object HostedServices {
    class Hosted(val service: Service, val component: ComponentName)

    fun create(host: Service, className: String, apkName: String): Hosted {
        val app = host.applicationContext
        val loader = FileTrackingClassLoader.process ?: error("Host class loader is not a FileTrackingClassLoader")
        val apk = ApkLoading.load(app, loader, apkName)
        val pkg = apk.manifestReader!!.getApplicationInfo().packageName

        val service = apk.loadClass(className).getDeclaredConstructor().newInstance() as Service
        val context = DCLContext.forLoadedApk(app, apk, pkg)
        // Service.attach(Context, ActivityThread, String className, IBinder token, Application, Object am)
        // (hidden). The host's own token and className make stopSelf()/startForeground() act on the host service.
        HiddenApiBypass.invoke(
            Service::class.java, service, "attach",
            context, host.tryGetValue<Any>("mThread"), host.tryGetValue<String>("mClassName"),
            host.tryGetValue<IBinder>("mToken"), shadowApplication(host, loader, apk, context),
            host.tryGetValue<Any>("mActivityManager")
        )
        return Hosted(service, ComponentName(pkg, className))
    }

    /** The loaded app's own Application (created once per APK, as DCLActivity does), else the host's. */
    private fun shadowApplication(host: Service, loader: FileTrackingClassLoader, apk: LoadedApk, context: Context): Application {
        apk.shadowApplication?.let { return it }
        val className = apk.manifestReader?.getApplicationInfo()?.name ?: return host.application
        return ShadowApplication.createShadowApplication(loader, className, host.application, context).also {
            apk.shadowApplication = it
            (context as? DCLContext)?.setShadowApplication(it)
            ShadowApplication.onCreate(it)
        }
    }
}
