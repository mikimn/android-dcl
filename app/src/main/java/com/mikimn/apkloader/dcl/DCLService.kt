package com.mikimn.apkloader.dcl

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.util.Log
import com.mikimn.apkloader.apk.LoadedApk
import com.mikimn.apkloader.reflection.tryGetValue
import com.mikimn.apkloader.shadow.ShadowApplication
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The host-side `Service` behind every [DCLServiceProxyPool] slot. The system creates it (through
 * [DCLAppComponentFactory.instantiateService]), it finds out which loaded service its slot hosts from
 * [ServiceSlots], and it hosts an instance of the loaded app's real `Service` class: `attach`ed with the host service's
 * own token and `IActivityManager`, so `stopSelf()`, `startForeground()` and friends act on this
 * host service, and every lifecycle callback is forwarded.
 *
 * Unlike receivers, a system-created service in the host works: `handleCreateService` builds its own
 * context and does not cast the Application's base context (see `ReceiverRouting` for the receiver
 * case that does).
 *
 * Known differences from a real service: a client's `ServiceConnection.onServiceConnected` gets the
 * proxy slot's `ComponentName`, not the loaded class's; foreground-service types must be declared on
 * the host's slot entries.
 */
class DCLService : Service() {
    // Which loaded service this host slot hosts: read back from the persisted slot assignment, not from
    // an intent (the system creates a service with none, and restarts it with none).
    private var target: ServiceSlots.Assignment? = null

    private var shadow: Service? = null
    private var shadowComponent: ComponentName? = null

    override fun onCreate() {
        super.onCreate()
        val slot = tryGetValue<String>("mClassName")?.let { DCLServiceProxyPool.slotOf(it) }
        val t = slot?.let { ServiceSlotsHolder.get()?.assignmentOf(it) }.also { target = it }
        if (t == null) {
            Log.e(TAG, "Service slot ${tryGetValue<String>("mClassName")} has no loaded service assigned; stopping")
            stopSelf()
            return
        }
        // An installed app that was updated or removed since the assignment was recorded: its APK path is gone.
        if (t.apkName.startsWith("/") && !java.io.File(t.apkName).exists()) {
            Log.w(TAG, "APK ${t.apkName} of service ${t.className} no longer exists; dropping its slot")
            ServiceSlotsHolder.get()?.release(t)
            stopSelf()
            return
        }
        ServiceSlotsHolder.get()?.markRunning(t.slot)
        try {
            shadow = createShadow(t).also { it.onCreate() }
        } catch (e: Throwable) {
            // A loaded service that can't be created must not take the host process down.
            // (Service.attach is hidden and positional: a signature mismatch on this Android version, SDK
            // ${android.os.Build.VERSION.SDK_INT}, lands here too; verified so far on API 30 and 36.)
            Log.e(TAG, "Loaded service ${t.className} failed to start", e)
            shadow = null
            stopSelf()
        }
    }

    private fun createShadow(t: ServiceSlots.Assignment): Service {
        val app = applicationContext
        val loader = app.classLoader as? FileTrackingClassLoader ?: error("Host class loader is not a FileTrackingClassLoader")
        val apk = ApkLoading.load(app, loader, t.apkName)
        val pkg = apk.manifestReader!!.getApplicationInfo().packageName
        shadowComponent = ComponentName(pkg, t.className)

        val service = apk.loadClass(t.className).getDeclaredConstructor().newInstance() as Service
        val context = DCLContext.forLoadedApk(app, apk, pkg)
        // Service.attach(Context, ActivityThread, String className, IBinder token, Application, Object am)
        // (hidden). The host's own token and className make stopSelf()/startForeground() act on this slot.
        HiddenApiBypass.invoke(
            Service::class.java, service, "attach",
            context, tryGetValue<Any>("mThread"), tryGetValue<String>("mClassName"),
            tryGetValue<IBinder>("mToken"), shadowApplication(loader, apk, context), tryGetValue<Any>("mActivityManager")
        )
        return service
    }

    /** The loaded app's own Application (created once per APK, as DCLActivity does), else the host's. */
    private fun shadowApplication(loader: FileTrackingClassLoader, apk: LoadedApk, context: android.content.Context): Application {
        apk.shadowApplication?.let { return it }
        val className = apk.manifestReader?.getApplicationInfo()?.name ?: return application
        return ShadowApplication.createShadowApplication(loader, className, application, context).also {
            apk.shadowApplication = it
            (context as? DCLContext)?.setShadowApplication(it)
            ShadowApplication.onCreate(it)
        }
    }

    /** What the loaded service sees: its own component, none of the loader's routing extras. */
    private fun unwrap(intent: Intent?): Intent? = intent?.let {
        Intent(it).apply {
            shadowComponent?.let { c -> component = c }
            removeExtra(ServiceRouting.KEY_SERVICE_CLASS)
            removeExtra(DCLActivity.KEY_LOADED_APK_NAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        shadow?.onStartCommand(unwrap(intent), flags, startId) ?: START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = shadow?.onBind(unwrap(intent))

    override fun onUnbind(intent: Intent?): Boolean = shadow?.onUnbind(unwrap(intent)) ?: false

    override fun onRebind(intent: Intent?) {
        shadow?.onRebind(unwrap(intent))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shadow?.onTaskRemoved(unwrap(rootIntent))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        shadow?.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        shadow?.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        shadow?.onTrimMemory(level)
    }

    override fun onDestroy() {
        try {
            shadow?.onDestroy()
        } catch (e: Throwable) {
            Log.e(TAG, "Loaded service ${shadowComponent?.className} failed in onDestroy", e)
        }
        shadow = null
        target?.let { ServiceSlotsHolder.get()?.markStopped(it.slot) }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DCLService"
    }
}
