package com.mikimn.apkloader.dcl

import android.content.ContentProvider
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.os.Process
import android.util.Log
import com.mikimn.apkloader.reflection.tryGetValue
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes a loaded APK's [ContentProvider]s reachable through the normal `ContentResolver` path.
 *
 * `ContentResolver` -> `ActivityThread.acquireProvider(authority)` first consults the process-local
 * `mProviderMap`, and only on a miss asks the system (AMS), which knows nothing about a
 * loaded-but-not-installed APK. Instantiating a provider is therefore not enough: without
 * registering it here, `insert/query/...` on the loaded app's own authority returns null/fails
 * (or worse, reaches the real installed copy of the app, if there is one). This registers the
 * instance exactly the way `ActivityThread.installProvider` does for a locally-hosted provider.
 */
object ProviderRegistry {
    private const val TAG = "ProviderRegistry"

    private val registered = ConcurrentHashMap.newKeySet<String>()

    /**
     * Registers an already-attached [provider] under every authority in [info]. Only the first
     * instance per provider class is registered; later calls for the same class are ignored so
     * navigation hops (which re-instantiate providers) don't swap the live instance.
     *
     * @return true if the provider was registered by this call.
     */
    fun register(provider: ContentProvider, info: ProviderInfo): Boolean {
        if (info.authority.isNullOrBlank() || !registered.add(info.name)) return false
        return try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ContentProviderHolder;")

            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                ?: error("No ActivityThread")
            val providerMap = activityThread.tryGetValue<Any>("mProviderMap")
                ?: error("ActivityThread.mProviderMap not found")

            // The manifest-derived ApplicationInfo has no uid; installProviderAuthoritiesLocked
            // derives the user id from it, and the resolver looks it up under the host's user.
            val hostedInfo = ProviderInfo(info).apply {
                applicationInfo = ApplicationInfo(info.applicationInfo).also { it.uid = Process.myUid() }
            }

            val binder = HiddenApiBypass.invoke(ContentProvider::class.java, provider, "getIContentProvider")
            val holderClass = Class.forName("android.app.ContentProviderHolder")
            val holder = HiddenApiBypass.newInstance(holderClass, hostedInfo)
            holderClass.getField("provider").set(holder, binder)
            holderClass.getField("noReleaseNeeded").setBoolean(holder, true)

            synchronized(providerMap) {
                HiddenApiBypass.invoke(
                    activityThreadClass, activityThread, "installProviderAuthoritiesLocked",
                    binder, provider, holder
                )
            }
            Log.i(TAG, "Registered ${info.name} for authorities ${info.authority}")
            true
        } catch (e: Throwable) {
            registered.remove(info.name)
            Log.e(TAG, "Failed to register provider ${info.name}", e)
            false
        }
    }
}
