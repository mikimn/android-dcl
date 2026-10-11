package com.mikimn.apkloader.dcl

import android.content.SharedPreferences

/**
 * Manifest-declared placeholder `<service>` entries (`DCLServiceProxy0..SIZE-1`, see
 * AndroidManifest.xml). A loaded app's services aren't in the host manifest, so each loaded service
 * class is hosted by one slot: [DCLAppComponentFactory.instantiateService] turns any slot name into
 * a [DCLService] that drives the real loaded `Service`. As with the activity pool, the names need
 * manifest entries only, no Kotlin classes.
 */
object DCLServiceProxyPool {
    const val SIZE = 16
    private const val CLASS_PREFIX = "com.mikimn.apkloader.dcl.DCLServiceProxy"

    fun className(index: Int): String = "$CLASS_PREFIX$index"
    fun allClassNames(): List<String> = (0 until SIZE).map(::className)
    fun isProxyClassName(className: String): Boolean = className.startsWith(CLASS_PREFIX)

    /** The slot index of a proxy class name (`...DCLServiceProxy7` -> 7), or null. */
    fun slotOf(className: String): Int? =
        if (isProxyClassName(className)) className.removePrefix(CLASS_PREFIX).toIntOrNull() else null
}

/**
 * Which proxy slot hosts which loaded service class (and from which APK). Unlike the activity pool
 * this must be **stable across processes**: the system records a started service under its slot's
 * component and, after the process dies, restarts it under that same slot, so the class must map to
 * the same slot next time or one service would run in two slots. And since the system creates the
 * service with *no intent* (`instantiateService(..., null)`; also on a restart), the slot is the only
 * thing that tells [DCLService] which loaded class to host, so the APK is persisted with it.
 * Assignments go through [Store] (see [PrefsStore]).
 *
 * An assignment is keyed by **(APK, class)**: library services share class names across apps
 * (WorkManager's `SystemJobService`, GMS/Firebase services), and an updated installed app has a new
 * APK path, which is simply a new key. One slot is one service instance, so slots are never shared
 * between two live services; but they are **reclaimed**: when every slot is taken, the
 * least-recently-used assignment whose service is not running in this process ([markRunning] /
 * [markStopped], called by [DCLService]) is evicted. [slotFor] returns null only when all
 * [size] slots host a running service.
 */
class ServiceSlots(private val store: Store, private val size: Int = DCLServiceProxyPool.SIZE) {
    data class Assignment(val slot: Int, val className: String, val apkName: String)

    interface Store {
        fun all(): List<Assignment>
        fun put(assignment: Assignment)
        fun remove(assignment: Assignment)
    }

    // Insertion order is recency order: a hit re-inserts, so the first entry is the least recently used.
    private val assigned = LinkedHashMap<String, Assignment>().also { map -> store.all().forEach { map[keyOf(it.apkName, it.className)] = it } }
    private val running = HashSet<Int>()

    private fun keyOf(apkName: String, className: String) = "$apkName|$className"

    @Synchronized
    fun slotFor(className: String, apkName: String): Int? {
        val key = keyOf(apkName, className)
        assigned.remove(key)?.let { assigned[key] = it; return it.slot }
        val taken = assigned.values.mapTo(HashSet()) { it.slot }
        var slot = (0 until size).firstOrNull { it !in taken }
        if (slot == null) {
            val victim = assigned.entries.firstOrNull { it.value.slot !in running } ?: return null
            assigned.remove(victim.key)
            store.remove(victim.value)
            slot = victim.value.slot
        }
        val assignment = Assignment(slot, className, apkName)
        assigned[key] = assignment
        store.put(assignment)
        return slot
    }

    /** The loaded service a slot hosts, if it has been assigned. */
    @Synchronized
    fun assignmentOf(slot: Int): Assignment? = assigned.values.firstOrNull { it.slot == slot }

    /** Drops [assignment] (e.g. its APK is gone), freeing the slot. */
    @Synchronized
    fun release(assignment: Assignment) {
        if (assigned.remove(keyOf(assignment.apkName, assignment.className)) != null) store.remove(assignment)
        running.remove(assignment.slot)
    }

    @Synchronized fun markRunning(slot: Int) { running.add(slot) }
    @Synchronized fun markStopped(slot: Int) { running.remove(slot) }
}

/** [ServiceSlots.Store] over a plain `SharedPreferences`: `"<apk>|<class>"` -> slot. */
class PrefsStore(private val prefs: SharedPreferences) : ServiceSlots.Store {
    override fun all(): List<ServiceSlots.Assignment> = prefs.all.mapNotNull { (key, v) ->
        val slot = (v as? String)?.toIntOrNull() ?: return@mapNotNull null
        val split = key.lastIndexOf('|')
        if (split <= 0) return@mapNotNull null
        ServiceSlots.Assignment(slot, key.substring(split + 1), key.substring(0, split))
    }

    override fun put(assignment: ServiceSlots.Assignment) {
        prefs.edit().putString("${assignment.apkName}|${assignment.className}", assignment.slot.toString()).apply()
    }

    override fun remove(assignment: ServiceSlots.Assignment) {
        prefs.edit().remove("${assignment.apkName}|${assignment.className}").apply()
    }
}
