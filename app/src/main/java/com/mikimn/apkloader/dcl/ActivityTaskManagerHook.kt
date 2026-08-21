package com.mikimn.apkloader.dcl

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * Replaces the process's cached `IActivityTaskManager` binder client with a
 * [Proxy] so every outgoing `startActivity`-family call can be observed (and,
 * eventually, rewritten) before it reaches ActivityManagerService.
 *
 * None of the types involved (`ActivityTaskManager`, `IActivityTaskManager`,
 * `android.util.Singleton`) are in the public SDK, so everything here goes
 * through [Class.forName] + reflection rather than compile-time references -
 * `Instrumentation.execStartActivity`, the interception point every other
 * DCL-style framework overrides directly, isn't in the compileSdk 34/35 stub
 * jars either, so a normal `override fun` isn't an option here.
 *
 * The two internal fields are located structurally (by type, not by name):
 * the exact field name backing `ActivityTaskManager`'s singleton has moved
 * before across AOSP versions, but "the static field whose type is
 * android.util.Singleton" and "Singleton's one instance field" have not.
 */
object ActivityTaskManagerHook {
    private const val TAG = "ATMHook"
    private var installed = false

    fun install(loader: FileTrackingClassLoader, hostPackageName: String) {
        if (installed) return

        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val singletonClass = Class.forName("android.util.Singleton")

            val singletonField = atmClass.declaredFields.firstOrNull { it.type == singletonClass }
            if (singletonField == null) {
                Log.e(TAG, "No Singleton<IActivityTaskManager> field found on ActivityTaskManager")
                return
            }
            singletonField.isAccessible = true
            val singletonInstance = singletonField.get(null)
            if (singletonInstance == null) {
                Log.e(TAG, "ActivityTaskManager.$singletonField is null")
                return
            }

            val instanceField = singletonClass.declaredFields.firstOrNull { !Modifier.isStatic(it.modifiers) }
            if (instanceField == null) {
                Log.e(TAG, "Singleton has no instance field")
                return
            }
            instanceField.isAccessible = true

            // Force lazy creation via the real get() before we swap the field.
            val getMethod = singletonClass.getDeclaredMethod("get")
            getMethod.isAccessible = true
            val real = getMethod.invoke(singletonInstance)
            if (real == null) {
                Log.e(TAG, "Singleton.get() returned null")
                return
            }

            val iatmClass = Class.forName("android.app.IActivityTaskManager")
            val proxy = Proxy.newProxyInstance(
                iatmClass.classLoader,
                arrayOf(iatmClass),
                RewritingInvocationHandler(real, loader, hostPackageName)
            )

            instanceField.set(singletonInstance, proxy)
            installed = true
            Log.i(TAG, "Installed IActivityTaskManager proxy over $real")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install ActivityTaskManager hook", e)
        }
    }

    /**
     * Rewrites outgoing explicit-component intents that target a class belonging to a
     * loaded APK, so AMS resolves them against a manifest-declared DCLActivityProxyPool
     * slot instead of rejecting them outright - see DCLActivity.forActivityClass for the
     * equivalent trick applied manually, via a hardcoded whitelist, before this hook
     * existed.
     */
    private class RewritingInvocationHandler(
        private val real: Any,
        private val loader: FileTrackingClassLoader,
        private val hostPackageName: String
    ) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            if (method.name.startsWith("startActivit") && args != null) {
                for (arg in args) {
                    if (arg is Intent) {
                        rewriteIfNeeded(arg)
                    }
                }
            }

            return try {
                method.invoke(real, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        private fun rewriteIfNeeded(intent: Intent) {
            val targetClassName = intent.component?.className ?: return
            if (DCLActivityProxyPool.isProxyClassName(targetClassName)) return

            val owningApk = loader.ownerOf(targetClassName) ?: return
            val proxyClassName = DCLActivityProxyPool.nextClassName()

            Log.i(TAG, "[Rewrite] $targetClassName (apk=${owningApk.name}) -> $proxyClassName")

            intent.putExtra(DCLActivity.KEY_ACTIVITY_CLASS, targetClassName)
            intent.putExtra(DCLActivity.KEY_LOADED_APK_NAME, owningApk.name)
            intent.component = ComponentName(hostPackageName, proxyClassName)
        }
    }
}
