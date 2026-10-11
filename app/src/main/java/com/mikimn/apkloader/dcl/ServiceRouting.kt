package com.mikimn.apkloader.dcl

import android.content.ComponentName
import android.content.Intent

/**
 * Retargets an outgoing service `Intent` (`startService`, `bindService`, `stopService`) at a host
 * proxy slot (on a copy: see [Route.Rewritten]) when it names a service of a loaded APK, which the system could not resolve (it is not
 * in the host manifest). Pure, so it is unit-testable. The class/APK pair is recorded with the slot
 * ([ServiceSlots]); [DCLService] reads it back from there, since the system creates the service
 * with no intent. The extras also carry the real class and APK, for diagnostics.
 */
object ServiceRouting {
    const val KEY_SERVICE_CLASS = "dclServiceClass"

    sealed class Route {
        /** Not a loaded app's service: leave the intent alone. */
        object None : Route()

        /** Retargeted at [slot]: use [intent], a copy; the caller's own Intent is never modified. */
        data class Rewritten(val className: String, val slot: Int, val intent: Intent) : Route()

        /** A loaded service, but every proxy slot is taken: the intent is left alone and will fail as before. */
        data class NoFreeSlot(val className: String) : Route()
    }

    /**
     * @param apkNameOfClass the name of the loaded APK that defines a class, or null if none does
     * @param resolveByPackage for an intent restricted to a loaded package without a component
     * (`setPackage` + action), the (class, apk) of the loaded service whose filter matches, if any
     */
    fun route(
        intent: Intent,
        hostPackage: String,
        slots: ServiceSlots,
        apkNameOfClass: (String) -> String?,
        resolveByPackage: (Intent) -> Pair<String, String>?
    ): Route {
        var className = intent.component?.className
        var apkName: String? = null
        if (className != null) {
            if (DCLServiceProxyPool.isProxyClassName(className)) return Route.None
            apkName = apkNameOfClass(className) ?: return Route.None
        } else {
            // Service intents must be explicit; a package-restricted action is the usual stand-in.
            val (cls, apk) = resolveByPackage(intent) ?: return Route.None
            className = cls
            apkName = apk
        }
        val slot = slots.slotFor(className, apkName) ?: return Route.NoFreeSlot(className)
        val rewritten = Intent(intent)
            .putExtra(KEY_SERVICE_CLASS, className)
            .putExtra(DCLActivity.KEY_LOADED_APK_NAME, apkName)
            .setComponent(ComponentName(hostPackage, DCLServiceProxyPool.className(slot)))
        return Route.Rewritten(className, slot, rewritten)
    }
}
