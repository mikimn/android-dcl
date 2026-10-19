package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.junit.rules.TemporaryFolder

class ApkPolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val assets = setOf("calculator.apk", "simple.apk")

    private fun allowed(name: String, installed: Set<String> = emptySet(), debuggable: Boolean = false) =
        ApkPolicy.isAllowed(name, debuggable, assets, installed)

    @Test fun aBundledAssetIsAllowed() {
        assertThat(allowed("calculator.apk")).isTrue()
    }

    @Test fun anAssetThatIsNotBundledIsRefused() {
        assertThat(allowed("evil.apk")).isFalse()
    }

    @Test fun anInstalledPackagesApkIsAllowedByCanonicalPath() {
        val apk = tmp.newFile("base.apk")
        val installed = setOf(apk.canonicalPath)
        assertThat(allowed(apk.path, installed)).isTrue()
    }

    @Test fun anArbitraryReadableFileIsRefused() {
        val other = tmp.newFile("download.apk")
        val installed = setOf(tmp.newFile("base.apk").canonicalPath)
        assertThat(allowed(other.path, installed)).isFalse()
        assertThat(allowed("/sdcard/Download/evil.apk", installed)).isFalse()
    }

    @Test fun dotDotAndSymlinksCannotSteerIntoTheAllowedSet() {
        val dir = tmp.newFolder("app")
        val base = File(dir, "base.apk").apply { writeText("x") }
        val other = tmp.newFile("evil.apk")
        val installed = setOf(base.canonicalPath)
        // a path that textually starts in the allowed directory but resolves elsewhere
        assertThat(allowed("${dir.path}/../evil.apk", installed)).isFalse()
        // a symlink inside the allowed directory pointing at a file that is not allowed
        val link = File(dir, "link.apk")
        java.nio.file.Files.createSymbolicLink(link.toPath(), other.toPath())
        assertThat(allowed(link.path, installed)).isFalse()
        // and the allowed file itself is still reachable through a harmless `..`
        assertThat(allowed("${dir.path}/../app/base.apk", installed)).isTrue()
    }

    @Test fun aRelativeNameThatIsNotAnAssetIsRefused() {
        assertThat(allowed("../../data/evil.apk")).isFalse()
        assertThat(ApkPolicy.canonical("relative.apk")).isNull()
    }

    @Test fun aDebuggableBuildAllowsAnything() {
        assertThat(allowed("/sdcard/anything.apk", debuggable = true)).isTrue()
    }
}
