package com.mikimn.apkloader.dcl

import android.content.pm.ActivityInfo
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed pools of manifest-declared placeholder `<activity>` entries (see AndroidManifest.xml) that
 * give each in-app-navigation target its own back-stack entry. A loaded activity can't have its own
 * manifest entry, so the entry's *attributes* are chosen by picking the right pool:
 *
 * - **launch mode**: `standard`, `singleTop`, `singleTask`, `singleInstance` - so the system's own
 *   task logic applies. This only works if the same loaded class keeps landing on the same slot
 *   (the system compares the proxy component), hence [classNameFor] assigns slots **per class**,
 *   not round-robin.
 * - **config handling**: a `Cfg` variant of each declares `configChanges` for everything, so a
 *   rotation doesn't recreate the host out from under an app that handles it itself
 *   ([DCLActivity] recreates on demand for changes the app did not opt into).
 *
 * DCLAppComponentFactory.instantiateActivity redirects any activity class name it doesn't
 * recognize to a real DCLActivity instance, so these names need matching manifest entries only,
 * no matching Kotlin classes. Every name returned by [allClassNames] must be declared (a unit
 * test checks the manifest). Slots are finite: with more distinct classes than slots in a pool,
 * the oldest assignments wrap and two classes share a slot.
 */
object DCLActivityProxyPool {
    /** Slots in the legacy `standard` pool (`DCLActivityProxy0..7`). */
    const val SIZE = 8
    private const val CLASS_PREFIX = "com.mikimn.apkloader.dcl.DCLActivityProxy"

    /** One pool: a launch mode plus whether the host handles every config change itself. */
    enum class Kind(val launchMode: Int, val manifestMode: String, val infix: String, val slots: Int) {
        STANDARD(ActivityInfo.LAUNCH_MULTIPLE, "standard", "", SIZE),
        SINGLE_TOP(ActivityInfo.LAUNCH_SINGLE_TOP, "singleTop", "SingleTop", 4),
        SINGLE_TASK(ActivityInfo.LAUNCH_SINGLE_TASK, "singleTask", "SingleTask", 4),
        SINGLE_INSTANCE(ActivityInfo.LAUNCH_SINGLE_INSTANCE, "singleInstance", "SingleInstance", 4);
    }

    data class Pool(val kind: Kind, val handlesConfigChanges: Boolean) {
        val slots: Int get() = kind.slots
        fun className(index: Int): String =
            "$CLASS_PREFIX${kind.infix}${if (handlesConfigChanges) "Cfg" else ""}$index"
    }

    val pools: List<Pool> = Kind.values().flatMap { listOf(Pool(it, false), Pool(it, true)) }

    fun allClassNames(): List<String> = pools.flatMap { pool -> (0 until pool.slots).map { pool.className(it) } }

    /** The legacy `standard`, non-config pool: `DCLActivityProxy<index>`. */
    fun className(index: Int): String = Pool(Kind.STANDARD, false).className(index)

    fun isProxyClassName(className: String): Boolean = className.startsWith(CLASS_PREFIX)

    // Legacy round-robin over the standard pool. Not used for routing any more (it can never
    // match a launch mode or clear-top against an earlier instance); kept for its callers/tests.
    private val next = AtomicInteger(0)
    fun nextClassName(): String = className(next.getAndIncrement().mod(SIZE))

    private val nextSlot = ConcurrentHashMap<Pool, AtomicInteger>()
    private val assigned = ConcurrentHashMap<Pair<Pool, String>, Int>()
    /** Classes that didn't fit their launch-mode pool and live in the standard pool instead. */
    private val overflowed = ConcurrentHashMap.newKeySet<Pair<Pool, String>>()

    /** Where exhaustion warnings go; tests swap it (android.util.Log isn't available on the JVM). */
    @Volatile var warn: (String) -> Unit = { Log.w("DCLProxyPool", it) }

    /** Which pool a loaded activity with these manifest attributes belongs to. */
    fun poolFor(launchMode: Int, configChanges: Int): Pool {
        val kind = when (launchMode) {
            ActivityInfo.LAUNCH_SINGLE_TOP -> Kind.SINGLE_TOP
            ActivityInfo.LAUNCH_SINGLE_TASK -> Kind.SINGLE_TASK
            ActivityInfo.LAUNCH_SINGLE_INSTANCE -> Kind.SINGLE_INSTANCE
            // singleInstancePerTask (API 31) is closest to singleTask; unknown values act like standard.
            ActivityInfo.LAUNCH_SINGLE_INSTANCE_PER_TASK -> Kind.SINGLE_TASK
            else -> Kind.STANDARD
        }
        return Pool(kind, handlesConfigChanges = configChanges != 0)
    }

    /**
     * The proxy slot for [targetClassName]. The same class always gets the same slot (within its
     * pool), which is what lets the system apply singleTop/singleTask/singleInstance and
     * FLAG_ACTIVITY_CLEAR_TOP / SINGLE_TOP against the real target.
     *
     * Slots are finite. A class that doesn't fit in a full launch-mode pool is **not** squeezed onto
     * another class's slot (the system would then deliver its intent to that class's instance); it
     * falls back to the `standard` pool of the same config handling and loses its launch-mode
     * semantics, with a warning. Only a full `standard` pool wraps and shares slots, which is
     * harmless for standard mode apart from clear-top matching.
     */
    fun classNameFor(targetClassName: String, launchMode: Int, configChanges: Int): String {
        var pool = poolFor(launchMode, configChanges)
        val key = pool to targetClassName
        if (pool.kind != Kind.STANDARD && !assigned.containsKey(key) &&
            (key in overflowed || nextSlot[pool]?.get().let { it != null && it >= pool.slots })
        ) {
            if (overflowed.add(key)) {
                warn(
                    "${pool.kind} pool (config=${pool.handlesConfigChanges}) is full; $targetClassName falls " +
                        "back to a standard slot and loses its launch mode"
                )
            }
            pool = Pool(Kind.STANDARD, pool.handlesConfigChanges)
        }
        val slot = assigned.getOrPut(pool to targetClassName) {
            val n = nextSlot.getOrPut(pool) { AtomicInteger(0) }.getAndIncrement()
            if (n == pool.slots) warn("${pool.kind} pool is full; further classes will share slots")
            n.mod(pool.slots)
        }
        return pool.className(slot)
    }

    /**
     * Whether [changedBits] (as reported by `Configuration.diff`, i.e. `ActivityInfo.CONFIG_*`)
     * include a change the loaded app did not declare in its own `configChanges`, meaning the
     * real system would have recreated it.
     */
    fun needsRecreate(changedBits: Int, appHandledBits: Int): Boolean =
        changedBits and appHandledBits.inv() and DECLARABLE_CONFIG_CHANGES != 0

    /**
     * Config bits an app can declare in `configChanges`. `CONFIG_ASSETS_PATHS` and
     * `CONFIG_WINDOW_CONFIGURATION` (hidden) are reported by `Configuration.diff` but never cause a
     * relaunch.
     */
    private const val DECLARABLE_CONFIG_CHANGES = 0x7fffffff and 0x20000000.inv()

    /**
     * The config changes the real system would treat as handled by an app: what it declares, plus -
     * for `targetSdkVersion < 26` - `screenLayout` and `smallestScreenSize` (`ActivityInfo.getRealConfigChanged`).
     * An absent `targetSdkVersion` (0) means "legacy", as on the platform.
     */
    fun appHandledConfigChanges(declared: Int, targetSdkVersion: Int): Int =
        if (targetSdkVersion < 26) declared or ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
        else declared
}
