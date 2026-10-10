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
 * Out of scope (see `docs/ROADMAP.md`, R12/R13): delivery while the process is dead. A dynamic
 * registration lives only as long as the process; that needs PendingIntent rewriting.
 */
object ReceiverRegistry {
    private const val TAG = "ReceiverRegistry"

    // Context.RECEIVER_EXPORTED / RECEIVER_NOT_EXPORTED (API 33); mandatory for non-system
    // broadcasts when targeting 34+, ignored below 33.
    private const val RECEIVER_EXPORTED = 0x2
    private const val RECEIVER_NOT_EXPORTED = 0x4

    /** @return how many (receiver, filter) registrations were made. */
    fun register(context: Context, apk: LoadedApk): Int {
        val app = context.applicationContext
        val receivers = apk.manifestReader?.parseReceivers() ?: return 0
        var count = 0
        for ((info, filters) in receivers) {
            if (!info.enabled) continue
            for (filter in filters) {
                try {
                    registerOne(app, DCLReceiverProxy(info.name, apk.name), IntentFilter(filter), info.permission, info.exported)
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
