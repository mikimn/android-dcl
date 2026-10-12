package com.mikimn.apkloader.tier1

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.testing.FixtureApks
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.ProbeChannel
import com.mikimn.apkloader.testing.Tier1
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two loaded APKs in one process (#22): each must see its own Application and
 * application context, regardless of which was loaded first or last.
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class MultipleLoadedApksTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val hello = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }
    private val app = ProbeChannel(target, "fx-application", loadedPackage = "com.mikimn.fixture.application").also { it.clear() }

    private fun launch(apk: String) = target.startActivity(
        DCLActivity.intentForAPK(target, FixtureApks.install(apk).path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    @Test fun twoAppsLoadedInOneProcessEachKeepTheirOwnIdentity() {
        launch("fx-application.apk")
        app.awaitEvent("activity.onCreate")
        launch("fx-hello.apk")
        hello.awaitEvent("onCreate")
        // launching the earlier app again resumes it (DCLActivity is singleTask): no second Application or activity
        launch("fx-application.apk")
        Thread.sleep(1500)
        assertThat(app.count("activity.onCreate")).isEqualTo(1)

        assertThat(app.valueOf("activity.applicationClass")).isEqualTo("com.mikimn.fixture.application.FxApplication")
        assertThat(app.valueOf("activity.sameApplication")).isEqualTo("true")
        assertThat(app.count("app.onCreate")).isEqualTo(1)

        // fx-hello declares no Application of its own: it gets the host's, never fx-application's
        // a class both apps bundle: each sees its own copy, directly and by name through its context's class
        // loader (the process-wide loader can only ever answer one of them)
        assertThat(app.valueOf("shared.direct")).isEqualTo("fx-application")
        assertThat(hello.valueOf("shared.direct")).isEqualTo("fx-hello")
        assertThat(app.valueOf("shared.byName")).isEqualTo("fx-application")
        assertThat(hello.valueOf("shared.byName")).isEqualTo("fx-hello")
        assertThat(hello.valueOf("applicationClass")).isEqualTo("com.mikimn.apkloader.dcl.DCLApplication")
    }
}
