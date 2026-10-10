package com.mikimn.apkloader.apk

/**
 * The platform's rule for a component's `exported` when the manifest does not say: a component with an
 * intent filter was exported by default before API 31; since 31 `android:exported` is mandatory for
 * those, and without a filter a component is never exported by default. An absent `targetSdkVersion`
 * (0) is "legacy", like the platform.
 */
fun defaultExported(declared: Boolean?, hasIntentFilter: Boolean, targetSdkVersion: Int): Boolean =
    declared ?: (hasIntentFilter && targetSdkVersion < 31)
