package com.mikimn.apkloader.dcl

import java.util.concurrent.atomic.AtomicInteger

/**
 * A fixed pool of manifest-declared, launchMode="standard" placeholder
 * `<activity>` entries (see AndroidManifest.xml) used to give each
 * in-app-navigation target its own back-stack entry - unlike DCLActivity's
 * own manifest entry, which stays singleTask for the existing entry-point
 * flow (DCLActivity.intentForAPK).
 *
 * DCLAppComponentFactory.instantiateActivity already redirects any activity
 * class name it doesn't recognize to a real DCLActivity instance (see the
 * external ACTIVITY_WHITELIST classes it already handles, e.g.
 * com.dotgears.GameActivity, whose manifest entries don't correspond to real
 * compiled classes either) - so these names need matching manifest entries
 * only, no matching Kotlin classes.
 */
object DCLActivityProxyPool {
    const val SIZE = 8
    private const val CLASS_PREFIX = "com.mikimn.apkloader.dcl.DCLActivityProxy"

    private val next = AtomicInteger(0)

    fun className(index: Int): String = "$CLASS_PREFIX$index"

    fun isProxyClassName(className: String): Boolean = className.startsWith(CLASS_PREFIX)

    /** Round-robin slot allocation - fine for a small, short-lived navigation depth. */
    fun nextClassName(): String = className(next.getAndIncrement().mod(SIZE))
}
