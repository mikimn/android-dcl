package com.mikimn.apkloader.dcl

import android.content.Intent

/**
 * What to do with an outgoing broadcast `Intent` so a loaded app's own receivers get it. A loaded
 * app's receivers are not in the host's manifest, so the system cannot resolve an explicit
 * component or a `setPackage(<loaded package>)` for them. Pure, so it is unit-testable.
 *
 * Why explicit broadcasts are dispatched in-process rather than retargeted at a manifest-declared
 * host receiver: on Android 11 `ActivityThread.handleReceiver` casts the Application's base context
 * to `ContextImpl`, and `DCLApplication` deliberately wraps it in `DCLContext`, so *any* receiver
 * the system instantiates in the host crashes the process (`ClassCastException`). Dynamically
 * registered receivers don't go through that path.
 */
object ReceiverRouting {
    sealed class Route {
        /** Leave the broadcast as it is. */
        object None : Route()

        /** An explicit broadcast to a receiver class of a loaded APK: deliver it in-process, don't send it. */
        data class Dispatch(val className: String, val apkName: String) : Route()

        /** An implicit broadcast restricted to a loaded package: now restricted to the host (already applied). */
        object RestrictedToHost : Route()
    }

    /**
     * Decides how to route [intent]; for [Route.RestrictedToHost] the intent has been modified in place.
     *
     * @param apkNameOfClass the name of the loaded APK that defines a class, or null if none does
     * @param isLoadedPackage whether a package name is one of the loaded apps' own
     */
    fun route(
        intent: Intent,
        hostPackage: String,
        apkNameOfClass: (String) -> String?,
        isLoadedPackage: (String) -> Boolean
    ): Route {
        val component = intent.component
        if (component != null) {
            val apkName = apkNameOfClass(component.className) ?: return Route.None
            return Route.Dispatch(component.className, apkName)
        }
        // An implicit broadcast limited to the loaded package matches nothing at the system; limited
        // to the host it reaches the dynamically registered proxies (see ReceiverRegistry).
        val pkg = intent.`package`
        if (pkg != null && pkg != hostPackage && isLoadedPackage(pkg)) {
            intent.setPackage(hostPackage)
            return Route.RestrictedToHost
        }
        return Route.None
    }
}
