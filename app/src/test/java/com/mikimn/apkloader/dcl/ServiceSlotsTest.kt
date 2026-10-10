package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ServiceSlots.Assignment
import org.junit.Test

class ServiceSlotsTest {
    private class MemoryStore(initial: List<Assignment> = emptyList()) : ServiceSlots.Store {
        val data = initial.toMutableList()
        override fun all(): List<Assignment> = data.toList()
        override fun put(assignment: Assignment) { data.removeAll { it.className == assignment.className }; data.add(assignment) }
    }

    @Test fun eachClassGetsItsOwnSlotAndKeepsIt() {
        val slots = ServiceSlots(MemoryStore(), size = 4)
        val a = slots.slotFor("a.A", "/apk/one")!!
        val b = slots.slotFor("b.B", "/apk/one")!!
        assertThat(a).isNotEqualTo(b)
        assertThat(slots.slotFor("a.A", "/apk/one")).isEqualTo(a)
        assertThat(slots.slotFor("b.B", "/apk/one")).isEqualTo(b)
    }

    @Test fun twoClassesNeverShareASlotAndAFullPoolReturnsNull() {
        val slots = ServiceSlots(MemoryStore(), size = 3)
        val taken = listOf("a.A", "b.B", "c.C").map { slots.slotFor(it, "/apk")!! }
        assertThat(taken.toSet()).hasSize(3)
        assertThat(slots.slotFor("d.D", "/apk")).isNull()
        assertThat(slots.slotFor("a.A", "/apk")).isEqualTo(taken[0]) // existing assignments are unaffected
    }

    // The system creates (and restarts) a service with no intent: the slot alone must say what it hosts.
    @Test fun aSlotKnowsWhichLoadedServiceAndApkItHosts() {
        val slots = ServiceSlots(MemoryStore(), size = 4)
        val slot = slots.slotFor("a.A", "/data/app/x/base.apk")!!
        assertThat(slots.assignmentOf(slot)).isEqualTo(Assignment(slot, "a.A", "/data/app/x/base.apk"))
        assertThat(slots.assignmentOf((slot + 1) % 4)).isNull()
    }

    // The system restarts a service under the slot it was recorded with, after the process died.
    @Test fun assignmentsSurviveAProcessRestartThroughTheStore() {
        val store = MemoryStore()
        val first = ServiceSlots(store, size = 4)
        val a = first.slotFor("a.A", "/apk/a")!!
        val b = first.slotFor("b.B", "/apk/b")!!

        val afterRestart = ServiceSlots(store, size = 4) // a new process, same persisted store
        assertThat(afterRestart.slotFor("b.B", "/apk/b")).isEqualTo(b)
        assertThat(afterRestart.assignmentOf(a)).isEqualTo(Assignment(a, "a.A", "/apk/a")) // incl. the APK, with no intent
        // a new class must not take a slot that is already recorded for another class
        assertThat(afterRestart.slotFor("c.C", "/apk/c")).isNotIn(listOf(a, b))
    }

    @Test fun poolNamesAreDeclaredInTheManifest() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        for (name in DCLServiceProxyPool.allClassNames()) {
            assertThat(manifest).contains("android:name=\"${name.removePrefix("com.mikimn.apkloader")}\"")
        }
        assertThat(DCLServiceProxyPool.allClassNames()).hasSize(DCLServiceProxyPool.SIZE)
        assertThat(manifest).doesNotContain(".dcl.DCLServiceProxy${DCLServiceProxyPool.SIZE}\"")
    }

    @Test fun recognizesOnlyProxyNamesAndReadsTheirSlot() {
        assertThat(DCLServiceProxyPool.isProxyClassName(DCLServiceProxyPool.className(3))).isTrue()
        assertThat(DCLServiceProxyPool.slotOf(DCLServiceProxyPool.className(7))).isEqualTo(7)
        assertThat(DCLServiceProxyPool.slotOf("com.example.MyService")).isNull()
        assertThat(DCLServiceProxyPool.isProxyClassName("com.mikimn.apkloader.dcl.DCLService")).isFalse()
    }
}
