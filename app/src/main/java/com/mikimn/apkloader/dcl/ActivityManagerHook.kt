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

    // ActivityManager.INTENT_SENDER_* (hidden constants): the first argument of getIntentSender*
    private const val INTENT_SENDER_BROADCAST = 1
    private const val INTENT_SENDER_ACTIVITY = 2
    private const val INTENT_SENDER_ACTIVITY_RESULT = 3
    private const val INTENT_SENDER_SERVICE = 4
    private const val INTENT_SENDER_FOREGROUND_SERVICE = 5
    private var installed = false
    private val warnedComponentBroadcasts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
            for (i in args.indices) {
                val intent = args[i] as? Intent ?: continue
                routeService(intent)?.let {
                    // The caller's own Intent stays untouched; the system gets the rewritten copy.
                    @Suppress("UNCHECKED_CAST")
                    (args as Array<Any?>)[i] = it
                }
            }
        }

        /** The copy of a service [intent] retargeted at a host proxy slot, or null if it needs none. */
        private fun routeService(intent: Intent): Intent? {
            val slots = ServiceSlotsHolder.get() ?: return null
            return when (val route = ServiceRouting.route(
                intent, hostPackageName, slots,
                apkNameOfClass = { loader.ownerOf(it)?.name },
                resolveByPackage = { resolveServiceByPackage(it) }
            )) {
                is ServiceRouting.Route.Rewritten -> {
                    Log.i(TAG, "[Rewrite] service ${route.className} -> ${route.intent.component?.className}")
                    route.intent
                }
                is ServiceRouting.Route.NoFreeSlot -> {
                    Log.e(TAG, "No free service proxy slot for ${route.className}; it will not start")
                    null
                }
                ServiceRouting.Route.None -> null
            }
        }

        // PendingIntents (notification taps, alarms, shortcuts) are resolved by the system later, from outside
        // this process: their target must already name something the host manifest declares. Same rewriting as
        // an in-process start, applied when the PendingIntent is created (IActivityManager.getIntentSender*).
        private fun rewriteIntentSender(args: Array<out Any?>) {
            val type = args.firstOrNull { it is Int } as? Int ?: return
            val at = args.indexOfFirst { it is Array<*> && it.javaClass.componentType == Intent::class.java }
            if (at < 0) return
            @Suppress("UNCHECKED_CAST")
            val intents = args[at] as Array<Intent?>
            val rewritten = Array<Intent?>(intents.size) { n ->
                val intent = intents[n] ?: return@Array null
                routePendingIntent(type, intent) ?: intent
            }
            @Suppress("UNCHECKED_CAST")
            (args as Array<Any?>)[at] = rewritten
        }

        private fun routePendingIntent(type: Int, intent: Intent): Intent? = when (type) {
            INTENT_SENDER_ACTIVITY, INTENT_SENDER_ACTIVITY_RESULT ->
                Intent(intent).takeIf { ActivityRouting.rewrite(it, loader, hostPackageName) }
            INTENT_SENDER_SERVICE, INTENT_SENDER_FOREGROUND_SERVICE -> routeService(intent)
            INTENT_SENDER_BROADCAST -> {
                val route = ReceiverRouting.route(
                    intent, hostPackageName,
                    apkNameOfClass = { loader.ownerOf(it)?.name },
                    isLoadedPackage = loader::isLoadedPackage
                )
                when (route) {
                    is ReceiverRouting.Route.RestrictedToHost -> route.intent
                    is ReceiverRouting.Route.Dispatch -> {
                        // The system fires a PendingIntent itself: there is no in-process dispatch to hook, and the
                        // host cannot declare a receiver (docs: CLAUDE.md). Only action-based broadcasts work.
                        // Once per distinct target, not once per PendingIntent creation (apps re-create them constantly).
                        if (warnedComponentBroadcasts.add("${route.className}|${intent.action}")) {
                            Log.w(TAG, "A PendingIntent broadcast to ${route.className} (action=${intent.action}) by component cannot be delivered; use an action")
                        }
                        null
                    }
                    ReceiverRouting.Route.None -> null
                }
            }
            else -> null
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
            if (args != null && method.name.startsWith("getIntentSender")) rewriteIntentSender(args)
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
