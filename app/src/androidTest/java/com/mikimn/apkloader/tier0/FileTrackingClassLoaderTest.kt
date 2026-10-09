package com.mikimn.apkloader.tier0

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.testing.FixtureLoader
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Tier0
@RunWith(AndroidJUnit4::class)
class FileTrackingClassLoaderTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val fx = FixtureLoader()
    private val loader get() = fx.loader

    private val hello = "com.mikimn.fixture.hello.HelloActivity"
    private val resources = "com.mikimn.fixture.resources.ResourcesActivity"
    private val sharedProbe = "com.mikimn.fixture.common.Probe" // compiled into every fixture

    @Test fun startsEmpty() {
        assertThat(loader.last).isNull()
        assertThat(loader.apkFile("fx-hello.apk")).isNull()
        assertThat(loader.ownerOf(hello)).isNull()
    }

    @Test fun addApkFileIsIdempotentPerName() {
        val first = fx.load("fx-hello.apk")
        val second = fx.load("fx-hello.apk")
        assertThat(second).isSameInstanceAs(first)
        assertThat(loader.apkFile("fx-hello.apk")).isSameInstanceAs(first)
    }

    @Test fun trackedByNameAndLastAndClearLast() {
        val hello = fx.load("fx-hello.apk")
        assertThat(loader.last).isSameInstanceAs(hello)
        val res = fx.load("fx-resources.apk")
        assertThat(loader.last).isSameInstanceAs(res)
        loader.clearLast()
        assertThat(loader.last).isNull()
        // clearing "last" must not forget the APKs themselves
        assertThat(loader.apkFile("fx-hello.apk")).isSameInstanceAs(hello)
    }

    @Test fun loadClassDelegatesToTheApkThatOwnsIt() {
        val helloApk = fx.load("fx-hello.apk")
        val resApk = fx.load("fx-resources.apk")
        assertThat(loader.loadClass(hello).classLoader).isSameInstanceAs(helloApk.loader)
        assertThat(loader.loadClass(resources).classLoader).isSameInstanceAs(resApk.loader)
    }

    @Test fun loadClassFallsBackToTheBaseLoader() {
        fx.load("fx-hello.apk")
        // a class only the base (test) loader knows
        val own = loader.loadClass("com.mikimn.apkloader.testing.FixtureLoader")
        assertThat(own).isSameInstanceAs(FixtureLoader::class.java)
        assertThat(loader.loadClass("java.lang.String")).isSameInstanceAs(String::class.java)
    }

    @Test fun unknownClassIsClassNotFound() {
        fx.load("fx-hello.apk")
        assertThrows(ClassNotFoundException::class.java) { loader.loadClass("com.example.DoesNotExist") }
    }

    // Two APKs defining the same class name: the first-loaded APK wins. Documents current behavior
    // (R10: multiple concurrent APKs).
    @Test fun duplicateClassNameResolvesToTheFirstLoadedApk() {
        val first = fx.load("fx-hello.apk")
        fx.load("fx-resources.apk")
        assertThat(loader.loadClass(sharedProbe).classLoader).isSameInstanceAs(first.loader)
        assertThat(loader.ownerOf(sharedProbe)).isSameInstanceAs(first)
    }

    @Test fun ownerOfReturnsTheDefiningApk() {
        val helloApk = fx.load("fx-hello.apk")
        val resApk = fx.load("fx-resources.apk")
        assertThat(loader.ownerOf(hello)).isSameInstanceAs(helloApk)
        assertThat(loader.ownerOf(resources)).isSameInstanceAs(resApk)
    }

    // ownerOf is what ActivityTaskManagerHook uses to decide "retarget this intent?". Platform
    // classes resolve through the parent loader but are NOT an APK's own code.
    @Test fun ownerOfIgnoresFrameworkAndUnknownClasses() {
        fx.load("fx-hello.apk")
        assertThat(loader.ownerOf("android.app.Activity")).isNull()
        assertThat(loader.ownerOf("java.lang.String")).isNull()
        assertThat(loader.ownerOf("com.example.DoesNotExist")).isNull()
    }

    @Test fun ownerOfDoesNotClaimTheHostOrTestClasses() {
        fx.load("fx-hello.apk")
        assertThat(loader.ownerOf("com.mikimn.apkloader.testing.FixtureLoader")).isNull()
        assertThat(loader.ownerOf("com.mikimn.apkloader.dcl.DCLActivity")).isNull()
    }

    @Test fun eachLoadedApkContributesOneResourcesProvider() {
        assertThat(loader.resourcesLoader.providers).isEmpty()
        fx.load("fx-hello.apk")
        assertThat(loader.resourcesLoader.providers).hasSize(1)
        fx.load("fx-resources.apk")
        assertThat(loader.resourcesLoader.providers).hasSize(2)
        fx.load("fx-resources.apk") // idempotent: no third provider
        assertThat(loader.resourcesLoader.providers).hasSize(2)
    }
}
