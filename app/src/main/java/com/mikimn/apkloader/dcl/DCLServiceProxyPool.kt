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
 * Assignments go through [Store] (see [PrefsStore]). Slots are never shared between two classes,
 * since one slot is one service instance; when all are taken [slotFor] returns null.
 */
class ServiceSlots(private val store: Store, private val size: Int = DCLServiceProxyPool.SIZE) {
    data class Assignment(val slot: Int, val className: String, val apkName: String)

    interface Store {
        fun all(): List<Assignment>
        fun put(assignment: Assignment)
    }

    private val assigned = LinkedHashMap<String, Assignment>().also { map -> store.all().forEach { map[it.className] = it } }

    @Synchronized
    fun slotFor(className: String, apkName: String): Int? {
        assigned[className]?.let { return it.slot }
        val free = (0 until size).firstOrNull { slot -> assigned.values.none { it.slot == slot } } ?: return null
        val assignment = Assignment(free, className, apkName)
        assigned[className] = assignment
        store.put(assignment)
        return free
    }

    /** The loaded service a slot hosts, if it has been assigned. */
    @Synchronized
    fun assignmentOf(slot: Int): Assignment? = assigned.values.firstOrNull { it.slot == slot }
}

/** [ServiceSlots.Store] over a plain `SharedPreferences`: class name -> `"<slot>:<apk>"`. */
class PrefsStore(private val prefs: SharedPreferences) : ServiceSlots.Store {
    override fun all(): List<ServiceSlots.Assignment> = prefs.all.mapNotNull { (cls, v) ->
        val text = v as? String ?: return@mapNotNull null
        val slot = text.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
        ServiceSlots.Assignment(slot, cls, text.substringAfter(':'))
    }

    override fun put(assignment: ServiceSlots.Assignment) {
        prefs.edit().putString(assignment.className, "${assignment.slot}:${assignment.apkName}").apply()
    }
}
