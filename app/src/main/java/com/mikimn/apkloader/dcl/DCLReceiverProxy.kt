package com.mikimn.apkloader.dcl

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Host-side stand-in for a loaded app's `BroadcastReceiver`, **registered dynamically** (see
 * [ReceiverRegistry]) once per manifest intent filter, with the receiver class and APK fixed at
 * construction, so implicit broadcasts reach the loaded receiver. It is never declared in the host
 * manifest: see [ReceiverRouting] for why the system can't instantiate receivers in the host.
 *
 * Like the platform for manifest receivers, every delivery uses a **new instance** of the loaded
 * receiver class.
 */
class DCLReceiverProxy(private val receiverClass: String, private val apkName: String) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The system gave *this* instance a PendingResult (goAsync / ordered results); hand it on.
        deliver(context, receiverClass, apkName, intent, pendingResultOf(this))
    }

    companion object {
        private const val TAG = "DCLReceiverProxy"

        /**
         * Runs the loaded receiver [className] of APK [apkName] for [intent]. A failure of the loaded
         * receiver (or a class that cannot be loaded) is contained and logged, like
         * `ShadowApplication.onCreate`: it must not take the host process down.
         *
         * @param pendingResult what `goAsync()`/`setResultCode()` act on, when the caller has one
         */
        fun deliver(context: Context, className: String, apkName: String, intent: Intent, pendingResult: Any? = null) {
            try {
                val app = context.applicationContext
                val loader = app.classLoader as? FileTrackingClassLoader
                    ?: error("Host class loader is not a FileTrackingClassLoader")
                val apk = ApkLoading.load(app, loader, apkName)
                val pkg = apk.manifestReader!!.getApplicationInfo().packageName

                val receiver = apk.loadClass(className).getDeclaredConstructor().newInstance() as BroadcastReceiver
                if (pendingResult != null) setPendingResult(receiver, pendingResult)

                // What the loaded receiver sees: its own component.
                val delivered = Intent(intent).apply {
                    component = ComponentName(pkg, className)
                    // ActivityManagerHook sent a copy restricted to the host for a setPackage(<loaded>)
                    // broadcast; the receiver should see the package its sender asked for.
                    if (`package` == app.packageName) setPackage(pkg)
                }
                receiver.onReceive(DCLContext.forLoadedApk(app, apk, pkg), delivered)
            } catch (e: Throwable) {
                Log.e(TAG, "Loaded receiver $className failed for ${intent.action}", e)
            }
        }

        // goAsync() / setResultCode() / abortBroadcast() act on the receiver's PendingResult, which
        // the system only sets on the instance it runs; the accessors are hidden API. Best effort.
        private fun pendingResultOf(receiver: BroadcastReceiver): Any? = try {
            HiddenApiBypass.invoke(BroadcastReceiver::class.java, receiver, "getPendingResult")
        } catch (e: Throwable) {
            Log.w(TAG, "Could not read the PendingResult", e)
            null
        }

        private fun setPendingResult(receiver: BroadcastReceiver, pending: Any) {
            try {
                HiddenApiBypass.invoke(BroadcastReceiver::class.java, receiver, "setPendingResult", pending)
            } catch (e: Throwable) {
                Log.w(TAG, "Could not share the PendingResult with the loaded receiver", e)
            }
        }
    }
}
