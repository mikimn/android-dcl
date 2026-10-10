package com.mikimn.apkloader.dcl

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * Replaces the process's cached `IActivityManager` binder client with a [Proxy] so outgoing
 * `broadcastIntent*` calls can be routed by [ReceiverRouting] before they reach the system.
 * The same technique, and the same structural field lookup (by type, not by name, since the
 * singleton's name has moved between AOSP versions), as [ActivityTaskManagerHook].
 */
object ActivityManagerHook {
    private const val TAG = "AMHook"
    private const val BROADCAST_SUCCESS = 0 // ActivityManager.BROADCAST_SUCCESS
    private var installed = false

    fun install(loader: FileTrackingClassLoader, hostPackageName: String) {
        if (installed) return
        try {
            val amClass = Class.forName("android.app.ActivityManager")
            val singletonClass = Class.forName("android.util.Singleton")

            val singletonField = amClass.declaredFields.firstOrNull { it.type == singletonClass }
            if (singletonField == null) {
                Log.e(TAG, "No Singleton<IActivityManager> field found on ActivityManager")
                return
            }
            singletonField.isAccessible = true
            val singletonInstance = singletonField.get(null) ?: run {
                Log.e(TAG, "ActivityManager.$singletonField is null")
                return
            }
            val instanceField = singletonClass.declaredFields.firstOrNull { !Modifier.isStatic(it.modifiers) } ?: run {
                Log.e(TAG, "Singleton has no instance field")
                return
            }
            instanceField.isAccessible = true

            // Force lazy creation via the real get() before swapping the field.
            val getMethod = singletonClass.getDeclaredMethod("get").apply { isAccessible = true }
            val real = getMethod.invoke(singletonInstance) ?: run {
                Log.e(TAG, "Singleton.get() returned null")
                return
            }

            val iamClass = Class.forName("android.app.IActivityManager")
            val proxy = Proxy.newProxyInstance(
                iamClass.classLoader, arrayOf(iamClass), BroadcastRewritingHandler(real, loader, hostPackageName)
            )
            instanceField.set(singletonInstance, proxy)
            installed = true
            Log.i(TAG, "Installed IActivityManager proxy over $real")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install ActivityManager hook", e)
        }
    }

    // Calls whose Intent names the target service (startForegroundService is startService + a flag).
    private val SERVICE_METHODS = listOf("startService", "bindService", "bindIsolatedService", "stopService", "peekService")

    private class BroadcastRewritingHandler(
        private val real: Any,
        private val loader: FileTrackingClassLoader,
        private val hostPackageName: String
    ) : InvocationHandler {
        private val main = Handler(Looper.getMainLooper())
        private val resultReceiverClass: Class<*>? = runCatching { Class.forName("android.content.IIntentReceiver") }.getOrNull()

        private fun rewriteServiceIntents(args: Array<out Any?>) {
            val slots = ServiceSlotsHolder.get() ?: return
            for (intent in args.filterIsInstance<Intent>()) {
                when (val route = ServiceRouting.route(
                    intent, hostPackageName, slots,
                    apkNameOfClass = { loader.ownerOf(it)?.name },
                    resolveByPackage = { resolveServiceByPackage(it) }
                )) {
                    is ServiceRouting.Route.Rewritten ->
                        Log.i(TAG, "[Rewrite] service ${route.className} -> ${intent.component?.className}")
                    is ServiceRouting.Route.NoFreeSlot ->
                        Log.e(TAG, "No free service proxy slot for ${route.className}; it will not start")
                    ServiceRouting.Route.None -> Unit
                }
            }
        }

        // A service intent restricted to a loaded package by action: the loaded service whose filter matches.
        private fun resolveServiceByPackage(intent: Intent): Pair<String, String>? {
            val pkg = intent.`package` ?: return null
            if (!loader.isLoadedPackage(pkg)) return null
            for (apk in loader.loadedApks()) {
                val reader = apk.manifestReader ?: continue
                if (reader.getApplicationInfo().packageName != pkg) continue
                val match = com.mikimn.apkloader.apk.ComponentMatcher.resolve(pkg, reader.parseServices(), intent, 0) {
                    android.content.pm.ResolveInfo().apply { serviceInfo = it }
                }.firstOrNull()?.serviceInfo ?: continue
                return match.name to apk.name
            }
            return null
        }

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            if (args != null && SERVICE_METHODS.any { method.name.startsWith(it) }) rewriteServiceIntents(args)
            // broadcastIntent / broadcastIntentWithFeature (the signature grew across releases)
            if (method.name.startsWith("broadcastIntent") && args != null) {
                for (i in args.indices) {
                    val intent = args[i] as? Intent ?: continue
                    val route = ReceiverRouting.route(
                        intent, hostPackageName,
                        apkNameOfClass = { loader.ownerOf(it)?.name },
                        isLoadedPackage = loader::isLoadedPackage
                    )
                    when (route) {
                        is ReceiverRouting.Route.Dispatch -> {
                            // The system can't resolve this receiver: deliver it here, asynchronously on
                            // the main thread like a real broadcast, and don't send it at all.
                            Log.i(TAG, "[Dispatch] ${route.className} (apk=${route.apkName}) for ${intent.action}")
                            if (args.any { resultReceiverClass?.isInstance(it) == true }) {
                                Log.w(TAG, "Dropping the result receiver of an ordered broadcast to ${route.className}")
                            }
                            val app = ApkLoading.currentApplication()
                            if (app != null) {
                                val copy = Intent(intent)
                                main.post { DCLReceiverProxy.deliver(app, route.className, route.apkName, copy) }
                                return BROADCAST_SUCCESS
                            }
                        }
                        is ReceiverRouting.Route.RestrictedToHost -> {
                            // Send the restricted copy; the app's own Intent keeps its original package.
                            @Suppress("UNCHECKED_CAST")
                            (args as Array<Any?>)[i] = route.intent
                            Log.i(TAG, "[Rewrite] ${intent.action}: package restricted to the host")
                        }
                        ReceiverRouting.Route.None -> Unit
                    }
                }
            }
            return try {
                method.invoke(real, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }
}

/** The process-wide [ServiceSlots], backed by the host's own SharedPreferences (stable across restarts). */
object ServiceSlotsHolder {
    @Volatile private var slots: ServiceSlots? = null

    fun get(): ServiceSlots? = slots ?: synchronized(this) {
        slots ?: ApkLoading.currentApplication()?.let {
            ServiceSlots(PrefsStore(it.getSharedPreferences("dcl_service_slots", android.content.Context.MODE_PRIVATE)))
        }?.also { slots = it }
    }
}
