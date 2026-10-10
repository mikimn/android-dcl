package com.mikimn.apkloader.dcl

import android.content.pm.ActivityInfo
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLActivityProxyPool.Kind
import org.junit.Test

class DCLActivityProxyPoolTest {
    @Test fun classNameFormat() {
        assertThat(DCLActivityProxyPool.className(0)).isEqualTo("com.mikimn.apkloader.dcl.DCLActivityProxy0")
        assertThat(DCLActivityProxyPool.className(7)).isEqualTo("com.mikimn.apkloader.dcl.DCLActivityProxy7")
    }

    @Test fun recognizesOnlyProxyNames() {
        assertThat(DCLActivityProxyPool.isProxyClassName(DCLActivityProxyPool.className(3))).isTrue()
        assertThat(DCLActivityProxyPool.isProxyClassName("com.mikimn.apkloader.dcl.DCLActivity")).isFalse()
        assertThat(DCLActivityProxyPool.isProxyClassName("com.example.Main")).isFalse()
    }

    @Test fun roundRobinCoversEverySlotAndWraps() {
        val names = List(DCLActivityProxyPool.SIZE * 2) { DCLActivityProxyPool.nextClassName() }
        val firstLap = names.take(DCLActivityProxyPool.SIZE)
        assertThat(firstLap.toSet()).hasSize(DCLActivityProxyPool.SIZE)
        assertThat(names.drop(DCLActivityProxyPool.SIZE)).containsExactlyElementsIn(firstLap).inOrder()
    }

    // Every slot handed out must have a matching <activity> in the host manifest.
    @Test fun everySlotIsDeclaredInTheManifest() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        for (i in 0 until DCLActivityProxyPool.SIZE) {
            assertThat(manifest).contains(".dcl.DCLActivityProxy$i")
        }
        assertThat(manifest).doesNotContain(".dcl.DCLActivityProxy${DCLActivityProxyPool.SIZE}\"")
    }

    // ---- launch-mode / config pools ---------------------------------------------------------

    private fun pool(mode: Int, cfg: Int = 0) = DCLActivityProxyPool.poolFor(mode, cfg)

    @Test fun poolFollowsLaunchModeAndConfigHandling() {
        assertThat(pool(ActivityInfo.LAUNCH_MULTIPLE).kind).isEqualTo(Kind.STANDARD)
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_TOP).kind).isEqualTo(Kind.SINGLE_TOP)
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_TASK).kind).isEqualTo(Kind.SINGLE_TASK)
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_INSTANCE).kind).isEqualTo(Kind.SINGLE_INSTANCE)
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_INSTANCE_PER_TASK).kind).isEqualTo(Kind.SINGLE_TASK)
        assertThat(pool(999).kind).isEqualTo(Kind.STANDARD)
        assertThat(pool(0, 0).handlesConfigChanges).isFalse()
        assertThat(pool(0, ActivityInfo.CONFIG_ORIENTATION).handlesConfigChanges).isTrue()
    }

    @Test fun legacyStandardNamesAreUnchangedAndOtherPoolsAreDistinct() {
        assertThat(pool(0).className(3)).isEqualTo("com.mikimn.apkloader.dcl.DCLActivityProxy3")
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_TOP).className(1)).endsWith("DCLActivityProxySingleTop1")
        assertThat(pool(ActivityInfo.LAUNCH_SINGLE_TASK, 1).className(0)).endsWith("DCLActivityProxySingleTaskCfg0")
        assertThat(pool(0, 1).className(2)).endsWith("DCLActivityProxyCfg2")
        val all = DCLActivityProxyPool.allClassNames()
        assertThat(all).containsNoDuplicates()
        assertThat(all.all { DCLActivityProxyPool.isProxyClassName(it) }).isTrue()
        assertThat(all).hasSize(2 * (8 + 4 + 4 + 4))
    }

    @Test fun theSameTargetAlwaysGetsTheSameSlot() {
        val first = DCLActivityProxyPool.classNameFor("t.SameClass", ActivityInfo.LAUNCH_SINGLE_TOP, 0)
        repeat(5) { assertThat(DCLActivityProxyPool.classNameFor("t.SameClass", ActivityInfo.LAUNCH_SINGLE_TOP, 0)).isEqualTo(first) }
        // ...but a different launch mode / config handling is a different pool.
        assertThat(DCLActivityProxyPool.classNameFor("t.SameClass", ActivityInfo.LAUNCH_SINGLE_TASK, 0)).isNotEqualTo(first)
        assertThat(DCLActivityProxyPool.classNameFor("t.SameClass", ActivityInfo.LAUNCH_SINGLE_TOP, 1)).isNotEqualTo(first)
    }

    private fun <T> capturingWarnings(block: (List<String>) -> T): T {
        val warnings = mutableListOf<String>()
        val original = DCLActivityProxyPool.warn
        DCLActivityProxyPool.warn = { warnings.add(it) }
        try { return block(warnings) } finally { DCLActivityProxyPool.warn = original }
    }

    @Test fun distinctTargetsGetDistinctSlotsInALaunchModePool() {
        val slots = DCLActivityProxyPool.pools.first { it.kind == Kind.SINGLE_INSTANCE && !it.handlesConfigChanges }.slots
        capturingWarnings { warnings ->
            val names = (0 until slots).map { DCLActivityProxyPool.classNameFor("t.Distinct$it", ActivityInfo.LAUNCH_SINGLE_INSTANCE, 0) }
            assertThat(names.toSet()).hasSize(slots)
            assertThat(warnings).isEmpty()
        }
    }

    // A class that doesn't fit must never share another class's launch-mode slot (the system would
    // hand its intent to the other class's instance): it falls back to the standard pool, loudly.
    @Test fun aFullLaunchModePoolOverflowsIntoTheStandardPoolWithAWarning() {
        capturingWarnings { warnings ->
            val slots = DCLActivityProxyPool.pools.first { it.kind == Kind.SINGLE_TASK && it.handlesConfigChanges }.slots
            val taken = (0 until slots).map { DCLActivityProxyPool.classNameFor("t.Full$it", ActivityInfo.LAUNCH_SINGLE_TASK, 1) }
            val overflow = DCLActivityProxyPool.classNameFor("t.FullExtra", ActivityInfo.LAUNCH_SINGLE_TASK, 1)

            assertThat(overflow).isNotIn(taken)
            assertThat(overflow).matches("com\\.mikimn\\.apkloader\\.dcl\\.DCLActivityProxyCfg\\d")
            assertThat(DCLActivityProxyPool.allClassNames()).contains(overflow)
            assertThat(warnings).hasSize(1)
            assertThat(warnings.single()).contains("t.FullExtra")

            // sticky and quiet afterwards; classes that already own a slot are unaffected
            assertThat(DCLActivityProxyPool.classNameFor("t.FullExtra", ActivityInfo.LAUNCH_SINGLE_TASK, 1)).isEqualTo(overflow)
            assertThat(DCLActivityProxyPool.classNameFor("t.Full0", ActivityInfo.LAUNCH_SINGLE_TASK, 1)).isEqualTo(taken[0])
            assertThat(warnings).hasSize(1)
        }
    }

    @Test fun aFullStandardPoolWrapsOntoDeclaredSlots() {
        capturingWarnings { warnings ->
            val names = (0..DCLActivityProxyPool.SIZE).map { DCLActivityProxyPool.classNameFor("t.Std$it", ActivityInfo.LAUNCH_MULTIPLE, 0) }
            assertThat(DCLActivityProxyPool.allClassNames()).containsAtLeastElementsIn(names.toSet())
            assertThat(warnings.single()).contains("STANDARD pool is full")
        }
    }

    @Test fun changesThatCanNeverRelaunchAreIgnored() {
        val assetsPaths = 0x80000000.toInt() // Configuration.diff reports it; no app can declare it
        val windowConfig = 0x20000000
        assertThat(DCLActivityProxyPool.needsRecreate(assetsPaths, 0)).isFalse()
        assertThat(DCLActivityProxyPool.needsRecreate(windowConfig, 0)).isFalse()
        assertThat(DCLActivityProxyPool.needsRecreate(windowConfig or ActivityInfo.CONFIG_LOCALE, 0)).isTrue()
    }

    @Test fun legacyAppsHandleScreenLayoutAndSmallestScreenSizeImplicitly() {
        val declared = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE
        val legacy = DCLActivityProxyPool.appHandledConfigChanges(declared, 25)
        assertThat(legacy and ActivityInfo.CONFIG_SCREEN_LAYOUT).isNotEqualTo(0)
        assertThat(legacy and ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE).isNotEqualTo(0)
        assertThat(DCLActivityProxyPool.appHandledConfigChanges(declared, 0)).isEqualTo(legacy) // absent = legacy
        assertThat(DCLActivityProxyPool.appHandledConfigChanges(declared, 26)).isEqualTo(declared)
        assertThat(DCLActivityProxyPool.appHandledConfigChanges(declared, 34)).isEqualTo(declared)
        // a rotation-like change (orientation, screenSize, smallestScreenSize, screenLayout) does not recreate a legacy app...
        val rotation = declared or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_SCREEN_LAYOUT
        assertThat(DCLActivityProxyPool.needsRecreate(rotation, legacy)).isFalse()
        // ...but does recreate a modern one that didn't declare them
        assertThat(DCLActivityProxyPool.needsRecreate(rotation, DCLActivityProxyPool.appHandledConfigChanges(declared, 34))).isTrue()
    }

    @Test fun recreateOnlyForChangesTheAppDidNotDeclare() {
        val handled = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE
        assertThat(DCLActivityProxyPool.needsRecreate(ActivityInfo.CONFIG_ORIENTATION, handled)).isFalse()
        assertThat(DCLActivityProxyPool.needsRecreate(handled, handled)).isFalse()
        assertThat(DCLActivityProxyPool.needsRecreate(ActivityInfo.CONFIG_LOCALE, handled)).isTrue()
        assertThat(DCLActivityProxyPool.needsRecreate(ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_LOCALE, handled)).isTrue()
        assertThat(DCLActivityProxyPool.needsRecreate(0, 0)).isFalse()
        assertThat(DCLActivityProxyPool.needsRecreate(ActivityInfo.CONFIG_ORIENTATION, 0)).isTrue()
    }

    // The manifest must declare exactly the pools the code hands out, with matching attributes.
    @Test fun manifestDeclaresEveryPoolWithTheRightAttributes() {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.File("src/main/AndroidManifest.xml"))
        val nodes = doc.getElementsByTagName("activity")
        val declared = (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("android:name").nodeValue.startsWith(".dcl.DCLActivityProxy") }
            .associateBy { "com.mikimn.apkloader" + it.attributes.getNamedItem("android:name").nodeValue }

        assertThat(declared.keys).containsExactlyElementsIn(DCLActivityProxyPool.allClassNames())
        for (pool in DCLActivityProxyPool.pools) {
            for (i in 0 until pool.slots) {
                val node = declared.getValue(pool.className(i))
                fun attr(n: String) = node.attributes.getNamedItem("android:$n")?.nodeValue
                assertThat(attr("launchMode")).isEqualTo(pool.kind.manifestMode)
                assertThat(attr("exported")).isEqualTo("false")
                if (pool.handlesConfigChanges) {
                    // every config the platform lets an app handle (at least the common ones)
                    assertThat(attr("configChanges")!!.split('|')).containsAtLeast(
                        "orientation", "screenSize", "smallestScreenSize", "screenLayout", "keyboardHidden", "uiMode", "locale"
                    )
                } else {
                    assertThat(attr("configChanges")).isNull()
                }
            }
        }
    }
}
