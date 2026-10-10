package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.ServiceRouting.Route
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ServiceRoutingTest {
    private val host = "com.mikimn.apkloader"
    private val loadedPkg = "com.example.loaded"
    private val apk = "/data/app/loaded/base.apk"

    private class MemoryStore : ServiceSlots.Store {
        val data = mutableListOf<ServiceSlots.Assignment>()
        override fun all(): List<ServiceSlots.Assignment> = data.toList()
        override fun put(assignment: ServiceSlots.Assignment) { data.add(assignment) }
    }

    private fun route(
        intent: Intent,
        slots: ServiceSlots = ServiceSlots(MemoryStore()),
        byPackage: (Intent) -> Pair<String, String>? = { null }
    ) = ServiceRouting.route(
        intent, host, slots,
        apkNameOfClass = { if (it.startsWith("com.example.loaded.")) apk else null },
        resolveByPackage = byPackage
    )

    @Test fun explicitLoadedServiceIsRetargetedAtItsSlotAndTheSendersIntentIsKept() {
        val slots = ServiceSlots(MemoryStore())
        val intent = Intent("some.ACTION").setComponent(ComponentName(loadedPkg, "com.example.loaded.Sync")).putExtra("keep", 1)
        val result = route(intent, slots)

        val slot = slots.slotFor("com.example.loaded.Sync", apk)!!
        assertThat(result).isEqualTo(Route.Rewritten("com.example.loaded.Sync", slot))
        assertThat(intent.component).isEqualTo(ComponentName(host, DCLServiceProxyPool.className(slot)))
        assertThat(intent.getStringExtra(ServiceRouting.KEY_SERVICE_CLASS)).isEqualTo("com.example.loaded.Sync")
        assertThat(intent.getStringExtra(DCLActivity.KEY_LOADED_APK_NAME)).isEqualTo(apk)
        assertThat(intent.action).isEqualTo("some.ACTION")
        assertThat(intent.getIntExtra("keep", 0)).isEqualTo(1)
        // the slot knows what it hosts, which is how DCLService finds out (the system gives it no intent)
        assertThat(slots.assignmentOf(slot)?.apkName).isEqualTo(apk)
    }

    @Test fun theSameServiceAlwaysGoesToTheSameSlotAndDifferentOnesToDifferentSlots() {
        val slots = ServiceSlots(MemoryStore())
        fun slotOf(cls: String) = (route(Intent().setComponent(ComponentName(loadedPkg, cls)), slots) as Route.Rewritten).slot
        assertThat(slotOf("com.example.loaded.A")).isEqualTo(slotOf("com.example.loaded.A"))
        assertThat(slotOf("com.example.loaded.A")).isNotEqualTo(slotOf("com.example.loaded.B"))
    }

    @Test fun otherServicesAreLeftAlone() {
        val other = Intent().setComponent(ComponentName("com.google.android.gms", "com.google.android.gms.Svc"))
        assertThat(route(other)).isEqualTo(Route.None)
        assertThat(other.component).isEqualTo(ComponentName("com.google.android.gms", "com.google.android.gms.Svc"))
        assertThat(other.hasExtra(ServiceRouting.KEY_SERVICE_CLASS)).isFalse()
    }

    @Test fun anIntentAlreadyAimedAtASlotIsNotRewrittenAgain() {
        val intent = Intent().setComponent(ComponentName(host, DCLServiceProxyPool.className(2)))
        assertThat(route(intent)).isEqualTo(Route.None)
        assertThat(intent.hasExtra(ServiceRouting.KEY_SERVICE_CLASS)).isFalse()
    }

    @Test fun aPackageRestrictedActionIsResolvedAgainstTheLoadedManifest() {
        val intent = Intent("com.example.SYNC").setPackage(loadedPkg)
        assertThat(route(intent) { "com.example.loaded.Sync" to apk }).isInstanceOf(Route.Rewritten::class.java)
        assertThat(intent.component!!.packageName).isEqualTo(host)
        assertThat(intent.getStringExtra(ServiceRouting.KEY_SERVICE_CLASS)).isEqualTo("com.example.loaded.Sync")
        assertThat(intent.action).isEqualTo("com.example.SYNC")

        val unresolved = Intent("com.example.NOPE").setPackage(loadedPkg)
        assertThat(route(unresolved)).isEqualTo(Route.None)
        assertThat(unresolved.component).isNull()
    }

    @Test fun whenEverySlotIsTakenTheIntentIsLeftAloneAndReported() {
        val slots = ServiceSlots(MemoryStore(), size = 1)
        route(Intent().setComponent(ComponentName(loadedPkg, "com.example.loaded.A")), slots)
        val b = Intent().setComponent(ComponentName(loadedPkg, "com.example.loaded.B"))
        assertThat(route(b, slots)).isEqualTo(Route.NoFreeSlot("com.example.loaded.B"))
        assertThat(b.component).isEqualTo(ComponentName(loadedPkg, "com.example.loaded.B"))
    }
}
