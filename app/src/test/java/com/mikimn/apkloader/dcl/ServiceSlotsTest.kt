package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ServiceSlots.Assignment
import org.junit.Test

class ServiceSlotsTest {
    private var clock = 0L
    private fun slotsOf(store: ServiceSlots.Store, size: Int) = ServiceSlots(store, size) { clock }
    private fun later() { clock += ServiceSlots.PENDING_MS }

    private class MemoryStore(initial: List<Assignment> = emptyList()) : ServiceSlots.Store {
        val data = initial.toMutableList()
        override fun all(): List<Assignment> = data.toList()
        override fun put(assignment: Assignment) { data.removeAll { it.className == assignment.className && it.apkName == assignment.apkName }; data.add(assignment) }
        override fun remove(assignment: Assignment) { data.remove(assignment) }
    }

    @Test fun eachClassGetsItsOwnSlotAndKeepsIt() {
        val slots = ServiceSlots(MemoryStore(), size = 4)
        val a = slots.slotFor("a.A", "/apk/one")!!
        val b = slots.slotFor("b.B", "/apk/one")!!
        assertThat(a).isNotEqualTo(b)
        assertThat(slots.slotFor("a.A", "/apk/one")).isEqualTo(a)
        assertThat(slots.slotFor("b.B", "/apk/one")).isEqualTo(b)
    }

    @Test fun twoLiveServicesNeverShareASlotAndAPoolOfRunningServicesReturnsNull() {
        val slots = ServiceSlots(MemoryStore(), size = 3)
        val taken = listOf("a.A", "b.B", "c.C").map { slots.slotFor(it, "/apk")!!.also(slots::markRunning) }
        assertThat(taken.toSet()).hasSize(3)
        assertThat(slots.slotFor("d.D", "/apk")).isNull()
        assertThat(slots.slotFor("a.A", "/apk")).isEqualTo(taken[0]) // existing assignments are unaffected
    }

    // Library services share class names across apps: they must not be routed to the first app's APK.
    @Test fun theSameClassNameInTwoApksGetsTwoAssignments() {
        val slots = ServiceSlots(MemoryStore(), size = 4)
        val a = slots.slotFor("androidx.work.impl.background.systemjob.SystemJobService", "/apk/a")!!
        val b = slots.slotFor("androidx.work.impl.background.systemjob.SystemJobService", "/apk/b")!!
        assertThat(a).isNotEqualTo(b)
        assertThat(slots.assignmentOf(a)!!.apkName).isEqualTo("/apk/a")
        assertThat(slots.assignmentOf(b)!!.apkName).isEqualTo("/apk/b")
    }

    @Test fun whenFullTheLeastRecentlyUsedStoppedAssignmentIsReclaimed() {
        val store = MemoryStore()
        val slots = slotsOf(store, 3)
        val a = slots.slotFor("a.A", "/apk")!!
        val b = slots.slotFor("b.B", "/apk")!!
        val c = slots.slotFor("c.C", "/apk")!!
        slots.markRunning(a) // a is the oldest but running; b is the oldest stopped one
        slots.slotFor("c.C", "/apk") // touching c keeps it recent
        later()
        val d = slots.slotFor("d.D", "/apk")
        assertThat(d).isEqualTo(b)
        assertThat(slots.assignmentOf(a)!!.className).isEqualTo("a.A")
        assertThat(slots.assignmentOf(c)!!.className).isEqualTo("c.C")
        assertThat(store.all().map { it.className }).containsExactly("a.A", "c.C", "d.D")
    }

    @Test fun stoppedServicesBecomeReclaimableAgain() {
        val slots = slotsOf(MemoryStore(), 1)
        val a = slots.slotFor("a.A", "/apk")!!
        slots.markRunning(a)
        assertThat(slots.slotFor("b.B", "/apk")).isNull()
        slots.markStopped(a)
        later()
        assertThat(slots.slotFor("b.B", "/apk")).isEqualTo(a)
    }

    // A slot is only "running" once the system created the service: until then a fresh assignment is pending.
    @Test fun aFreshAssignmentIsNotEvictedBeforeItsServiceHadTimeToStart() {
        val slots = slotsOf(MemoryStore(), 1)
        val a = slots.slotFor("a.A", "/apk")!!
        assertThat(slots.slotFor("b.B", "/apk")).isNull() // a's service has not been created yet
        later()
        assertThat(slots.slotFor("b.B", "/apk")).isEqualTo(a)
    }

    // After a process restart nothing is running, but the system may be about to restart a sticky service.
    @Test fun assignmentsLoadedAtProcessStartAreProtectedForTheStartupWindow() {
        val store = MemoryStore(listOf(Assignment(0, "a.A", "/apk")))
        val slots = slotsOf(store, 1)
        assertThat(slots.slotFor("b.B", "/apk")).isNull()
        later()
        assertThat(slots.slotFor("b.B", "/apk")).isEqualTo(0)
    }

    @Test fun releasingAnAssignmentFreesItsSlotAndTheStore() {
        val store = MemoryStore()
        val slots = ServiceSlots(store, size = 1)
        val slot = slots.slotFor("a.A", "/gone.apk")!!
        slots.release(slots.assignmentOf(slot)!!)
        assertThat(slots.assignmentOf(slot)).isNull()
        assertThat(store.all()).isEmpty()
        assertThat(slots.slotFor("b.B", "/apk")).isEqualTo(slot)
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
