package com.mikimn.apkloader.dcl

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.mikimn.apkloader.apk.LoadedApk

/**
 * Registers a loaded APK's manifest-declared receivers that have intent filters, so implicit
 * broadcasts reach them while the process is alive. Each (receiver, filter) pair gets its own
 * [DCLReceiverProxy], registered with the host's Application context, honoring the receiver's
 * `android:permission` (required of senders) and `android:exported`.
 *
 * Explicit broadcasts don't use these registrations: [ActivityManagerHook] delivers them in-process.
 *
 * A receiver with several intent filters gets one registration per filter, so a broadcast matching
 * more than one of them is delivered once per matching filter, where the platform delivers once per
 * receiver. (De-duplicating needs an identity for "the same broadcast" that the system doesn't give
 * dynamic receivers; a known difference.)
 *
 * Out of scope (see `docs/ROADMAP.md`, R12/R13): delivery while the process is dead. A dynamic
 * registration lives only as long as the process; that needs PendingIntent rewriting.
 */
object ReceiverRegistry {
    private const val TAG = "ReceiverRegistry"

    // Context.RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED (API 33); mandatory for non-system
    // broadcasts when targeting 34+, ignored below 33.
    private const val RECEIVER_EXPORTED = 0x2
    private const val RECEIVER_NOT_EXPORTED = 0x4

    /**
     * The permission a sender must hold to reach a dynamically registered receiver. A receiver's own
     * `android:permission` applies to an exported one. A **non-exported** receiver must be reachable only
     * by its own app (and the system, which always passes): below API 33 a plain `registerReceiver` is
     * visible to every app (the `RECEIVER_NOT_EXPORTED` flag only exists from 33), so it requires the
     * host's own signature-level permission, which no other app holds (declared in the host manifest).
     */
    fun senderPermission(context: Context, declaredPermission: String?, exported: Boolean): String? =
        if (exported) declaredPermission else internalBroadcastPermission(context)

    /** The host's signature-level permission that only its own process holds (see AndroidManifest.xml). */
    fun internalBroadcastPermission(context: Context): String = "${context.packageName}.permission.INTERNAL_BROADCAST"

    /** @return how many (receiver, filter) registrations were made. */
    fun register(context: Context, apk: LoadedApk): Int {
        val app = context.applicationContext
        val receivers = apk.manifestReader?.parseReceivers() ?: return 0
        var count = 0
        for ((info, filters) in receivers) {
            if (!info.enabled) continue
            for (filter in filters) {
                try {
                    registerOne(app, DCLReceiverProxy(info.name, apk.name), IntentFilter(filter), senderPermission(app, info.permission, info.exported), info.exported)
                    count++
                } catch (e: Throwable) {
                    // e.g. a protected/unsupported action: one bad filter must not block the others
                    Log.w(TAG, "Could not register ${info.name} for ${filter.actionsIterator().asSequence().toList()}", e)
                }
            }
        }
        if (count > 0) Log.i(TAG, "Registered $count receiver filter(s) of ${apk.name}")
        return count
    }

    private fun registerOne(app: Context, proxy: DCLReceiverProxy, filter: IntentFilter, permission: String?, exported: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(proxy, filter, permission, null, if (exported) RECEIVER_EXPORTED else RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(proxy, filter, permission, null)
        }
    }
}
