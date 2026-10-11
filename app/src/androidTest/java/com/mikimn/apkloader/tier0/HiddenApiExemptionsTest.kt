package com.mikimn.apkloader.tier0

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.apk.HiddenApiExemptions
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@Tier0
@RunWith(AndroidJUnit4::class)
class HiddenApiExemptionsTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()

    private fun dexBytes(apk: File): List<ByteArray> = ZipFile(apk).use { zip ->
        zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }
            .map { zip.getInputStream(it).readBytes() }.toList()
    }

    @Test fun readsDefinedAndReferencedTypesFromARealDex() {
        val types = dexBytes(com.mikimn.apkloader.testing.FixtureApks.install("fx-hello.apk")).map { HiddenApiExemptions.dexTypes(it) }
        val referenced = types.flatMap { it.referenced }.toSet()
        val defined = types.flatMap { it.defined }.toSet()

        assertThat(defined).contains("Lcom/mikimn/fixture/hello/HelloActivity;")
        assertThat(referenced).containsAtLeast("Landroid/app/Activity;", "Ljava/lang/String;")
        // Framework classes are referenced, not defined, by the APK.
        assertThat(defined).doesNotContain("Landroid/app/Activity;")
        assertThat(referenced).containsAtLeastElementsIn(defined)
    }

    @Test fun anAppThatLinksOnlyAgainstAospNeedsNoExemptions() {
        fx.load("fx-hello.apk") // extracts the APK, dex files included
        val extracted = File(System.getProperty("java.io.tmpdir")!!, "cache-fx-hello.apk")
        val dex = extracted.listFiles { f -> f.extension == "dex" }!!.toList()
        assertThat(dex).isNotEmpty()
        assertThat(HiddenApiExemptions.applyFor(dex)).isEmpty()
    }
}
