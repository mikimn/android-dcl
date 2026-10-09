package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
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
}
