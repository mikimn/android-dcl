package com.mikimn.apkloader.dcl

import android.content.ComponentName
import android.content.Intent
import android.util.Log

/**
 * Retargets an explicit-component `Intent` at a loaded APK's activity onto a manifest-declared
 * [DCLActivityProxyPool] slot (chosen by the target's own launch mode and config handling), carrying the real
 * class and APK as extras. Shared by the `startActivity` hook ([ActivityTaskManagerHook]) and by activity
 * `PendingIntent`s ([PendingIntentRouting]), which the system fires later and from outside the process.
 */
object ActivityRouting {
    private const val TAG = "ATMHook"

    /** Rewrites [intent] **in place**; returns whether it named an activity of a loaded APK. */
    fun rewrite(intent: Intent, loader: FileTrackingClassLoader, hostPackageName: String): Boolean {
        val targetClassName = intent.component?.className ?: return false
        if (DCLActivityProxyPool.isProxyClassName(targetClassName)) return false

        val owningApk = loader.ownerOf(targetClassName) ?: return false
        // The target's own manifest attributes pick the pool (launch mode, config handling);
        // an activity the manifest doesn't list (e.g. an alias) falls back to the defaults.
        val targetInfo = owningApk.manifestReader?.parseActivities()
            ?.firstOrNull { it.first.name == targetClassName }?.first
        val proxyClassName = DCLActivityProxyPool.classNameFor(
            targetClassName, targetInfo?.launchMode ?: 0, targetInfo?.configChanges ?: 0
        )

        Log.i(TAG, "[Rewrite] $targetClassName (apk=${owningApk.name}) -> $proxyClassName")

        intent.putExtra(DCLActivity.KEY_ACTIVITY_CLASS, targetClassName)
        intent.putExtra(DCLActivity.KEY_LOADED_APK_NAME, owningApk.name)
        intent.component = ComponentName(hostPackageName, proxyClassName)
        return true
    }
}
