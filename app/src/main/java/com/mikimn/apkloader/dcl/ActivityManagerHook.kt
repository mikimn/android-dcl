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

    private class BroadcastRewritingHandler(
        private val real: Any,
        private val loader: FileTrackingClassLoader,
        private val hostPackageName: String
    ) : InvocationHandler {
        private val main = Handler(Looper.getMainLooper())

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            // broadcastIntent / broadcastIntentWithFeature (the signature grew across releases)
            if (method.name.startsWith("broadcastIntent") && args != null) {
                for (intent in args.filterIsInstance<Intent>()) {
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
                            val app = ApkLoading.currentApplication()
                            if (app != null) {
                                val copy = Intent(intent)
                                main.post { DCLReceiverProxy.deliver(app, route.className, route.apkName, copy) }
                                return BROADCAST_SUCCESS
                            }
                        }
                        ReceiverRouting.Route.RestrictedToHost ->
                            Log.i(TAG, "[Rewrite] ${intent.action}: package restricted to the host")
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
