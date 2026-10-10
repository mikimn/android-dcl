package com.mikimn.apkloader.dcl

import android.content.ContentProvider
import android.content.pm.ApplicationInfo
import android.content.pm.ProviderInfo
import android.os.Process
import android.util.Log
import com.mikimn.apkloader.reflection.tryGetValue
import org.lsposed.hiddenapibypass.HiddenApiBypass

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

    /** `UserHandle.PER_USER_RANGE` (hidden): uid = userId * 100000 + appId. */
    private const val PER_USER_RANGE = 100000

    private val registered = HashSet<String>()

    /**
     * Registers an already-attached [provider] under every authority in [info].
     *
     * Skipped (returns false, with a log) when
     * - this provider (`package/class`) was already registered: only the first instance per class
     *   is kept, so navigation hops that re-instantiate providers don't swap the live instance; or
     * - **any** of its authorities is already served in this process. The runtime's own
     *   registration would silently overwrite the entry, letting a loaded APK take over a system
     *   authority, the host's, or another loaded app's.
     *
     * Relies on hidden `ActivityThread`/`ContentProviderHolder` internals (verified on API 30
     * only); any failure is logged and leaves the provider unregistered.
     *
     * @return true if the provider was registered by this call.
     */
    @Synchronized
    fun register(provider: ContentProvider, info: ProviderInfo): Boolean {
        val authorities = info.authority?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        if (authorities.isEmpty()) return false
        val id = "${info.applicationInfo?.packageName}/${info.name}"
        if (id in registered) return false
        return try {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/app/ContentProviderHolder;",
                "Landroid/app/ActivityThread\$ProviderKey;"
            )

            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                ?: error("No ActivityThread")
            val providerMap = activityThread.tryGetValue<Map<Any, Any>>("mProviderMap")
                ?: error("ActivityThread.mProviderMap not found")

            val keyClass = Class.forName("android.app.ActivityThread\$ProviderKey")
            val userId = Process.myUid() / PER_USER_RANGE
            val taken = synchronized(providerMap) {
                authorities.firstOrNull { providerMap.containsKey(HiddenApiBypass.newInstance(keyClass, it, userId)) }
            }
            if (taken != null) {
                Log.w(TAG, "Not registering ${info.name}: authority $taken is already served in this process")
                return false
            }

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
            registered.add(id)
            Log.i(TAG, "Registered ${info.name} for authorities ${info.authority}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register provider ${info.name}", e)
            false
        }
    }
}
