package com.mikimn.apkloader.apk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HiddenApiExemptionsTest {
    private val boot = setOf(
        "com.oneplus.inner.os.UserHandleWrapper",
        "com.samsung.android.os.SemFoo",
        "net.oneplus.odm.Thing",
        "com.android.internal.os.Zygote", // AOSP: never a vendor class even though it is on the boot path
        "android.os.Bundle",
    )

    private fun exemptions(vararg descriptors: String, lookups: MutableList<String>? = null) =
        HiddenApiExemptions.vendorExemptions(descriptors.toList()) { name ->
            lookups?.add(name)
            name in boot
        }

    @Test fun vendorClassOnTheBootPathExemptsItsVendorNamespace() {
        assertThat(exemptions("Lcom/oneplus/inner/os/UserHandleWrapper;")).containsExactly("Lcom/oneplus/")
        assertThat(exemptions("Lcom/samsung/android/os/SemFoo;")).containsExactly("Lcom/samsung/")
        assertThat(exemptions("Lnet/oneplus/odm/Thing;")).containsExactly("Lnet/oneplus/")
    }

    @Test fun anAppsOwnClassesAreNotVendorClassesEvenInTheSameNamespace() {
        // com.oneplus.note.* is the *app's*; only a boot-path class makes the namespace a vendor one.
        assertThat(exemptions("Lcom/oneplus/note/ui/MainActivity;", "Lcom/example/app/Main;")).isEmpty()
        assertThat(exemptions("Lcom/oneplus/note/ui/MainActivity;", "Lcom/oneplus/inner/os/UserHandleWrapper;"))
            .containsExactly("Lcom/oneplus/")
    }

    @Test fun platformAndLanguageClassesAreNeverConsidered() {
        val lookups = mutableListOf<String>()
        val result = exemptions(
            "Landroid/os/Bundle;", "Ljava/lang/String;", "Ljavax/net/Foo;", "Lcom/android/internal/os/Zygote;",
            "Ldalvik/system/VMRuntime;", "Lkotlin/Unit;", "Landroidx/core/Foo;", "Lorg/json/JSONObject;",
            lookups = lookups
        )
        assertThat(result).isEmpty()
        assertThat(lookups).isEmpty() // filtered by prefix before any class lookup
    }

    @Test fun arraysAreUnwrappedAndPrimitivesIgnored() {
        assertThat(exemptions("[Lcom/samsung/android/os/SemFoo;", "[[Lcom/samsung/android/os/SemFoo;")).containsExactly("Lcom/samsung/")
        assertThat(exemptions("I", "[I", "Z", "[[J", "V")).isEmpty()
    }

    @Test fun degenerateNamesDoNotCrashOrMatch() {
        assertThat(exemptions("", "L;", "Lfoo;", "Lfoo/Bar;", "Lcom/Foo;")).isEmpty()
    }

    @Test fun eachNamespaceIsExemptedOnceAndLookedUpOnlyUntilItIsCovered() {
        val lookups = mutableListOf<String>()
        val result = exemptions(
            "Lcom/oneplus/inner/os/UserHandleWrapper;",
            "Lcom/oneplus/inner/os/Other;",
            "Lcom/oneplus/other/Pkg;",
            lookups = lookups
        )
        assertThat(result).containsExactly("Lcom/oneplus/")
        assertThat(lookups).containsExactly("com.oneplus.inner.os.UserHandleWrapper") // covered after the first hit
    }

    @Test fun severalVendorsYieldSeveralPrefixes() {
        assertThat(exemptions("Lcom/oneplus/inner/os/UserHandleWrapper;", "Lcom/samsung/android/os/SemFoo;", "Lnet/oneplus/odm/Thing;"))
            .containsExactly("Lcom/oneplus/", "Lcom/samsung/", "Lnet/oneplus/")
    }

    @Test fun nonDexInputIsRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HiddenApiExemptions.dexTypes("not a dex file, just text".toByteArray())
        }
    }

    // The dex is untrusted: whatever it references, nothing AOSP-owned can ever be exempted.
    @Test fun noPlatformPrefixIsEverExemptedOrEvenLookedUp() {
        for (prefix in HiddenApiExemptions.PLATFORM_PREFIXES.filter { it.endsWith("/") }) {
            val lookups = mutableListOf<String>()
            val result = HiddenApiExemptions.vendorExemptions(listOf("L${prefix}vendor/pkg/Foo;")) { lookups.add(it); true }
            assertThat(result).isEmpty()
            assertThat(lookups).isEmpty()
        }
    }

    @Test fun aPackageOfTheAppsOwnClassesStopsCostingLookupsAfterAFewMisses() {
        val lookups = mutableListOf<String>()
        val own = (1..50).map { "Lcom/example/app/ui/Screen$it;" }
        val result = HiddenApiExemptions.vendorExemptions(own) { lookups.add(it); false }
        assertThat(result).isEmpty()
        assertThat(lookups.size).isAtMost(3)
    }

    @Test fun theNegativeCacheIsPerPackageSoAVendorPackageElsewhereIsStillFound() {
        val own = (1..10).map { "Lcom/example/app/Own$it;" }
        val result = HiddenApiExemptions.vendorExemptions(own + "Lcom/vendor/inner/Wrapper;") { it == "com.vendor.inner.Wrapper" }
        assertThat(result).containsExactly("Lcom/vendor/")
    }
}
