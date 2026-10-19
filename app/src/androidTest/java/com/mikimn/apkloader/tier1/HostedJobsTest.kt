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
 * A hosted app schedules jobs with `JobScheduler` (#23; this is what WorkManager does underneath). The system
 * validates a job's service against the host manifest, so the job is retargeted at the host's `DCLJobService`
 * and the loaded class travels in the job's extras.
 */
@Tier1
@RunWith(AndroidJUnit4::class)
class HostedJobsTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val probe = ProbeChannel(target, "fx-hello", loadedPackage = "com.mikimn.fixture.hello").also { it.clear() }

    @Test fun aLoadedJobServiceRunsAndFinishesAndPendingJobsLookUnchanged() {
        runLoadedJobs()
    }

    // cancelAll() on a loaded app's scheduler is the app's own jobs only: the host's job of the same uid survives it
    @Test fun cancelAllCancelsTheAppsOwnJobsButNotTheHosts() {
        val hostScheduler = target.getSystemService(android.app.job.JobScheduler::class.java)
        val hostJobId = 900
        hostScheduler.schedule(
            android.app.job.JobInfo.Builder(hostJobId, android.content.ComponentName(target, com.mikimn.apkloader.dcl.DCLJobService::class.java))
                .setMinimumLatency(60L * 60 * 1000).build()
        )
        try {
            target.startActivity(
                DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path)
                    .putExtra("fx.job.cancelAll", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            probe.awaitEvent("job.cancelAll.done")
            assertThat(probe.valueOf("job.error")).isNull()
            assertThat(probe.valueOf("job.cancelAll.before")).isEqualTo("true")
            assertThat(probe.valueOf("job.cancelAll.own")).isEqualTo("true")
            assertThat(hostScheduler.getPendingJob(hostJobId)).isNotNull()
        } finally {
            hostScheduler.cancel(hostJobId)
        }
    }

    private fun runLoadedJobs() {
        target.startActivity(
            DCLActivity.intentForAPK(target, FixtureApks.install("fx-hello.apk").path)
                .putExtra("fx.job", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        probe.awaitEvent("job.finished")

        assertThat(probe.valueOf("job.error")).isNull()
        // the job ran in the loaded class, with the extras the app gave it and none of the loader's
        assertThat(probe.valueOf("job.start")).isEqualTo("77:now")
        assertThat(probe.valueOf("job.routingExtras")).isEqualTo("false")
        // jobFinished() reached the system through the host's engine; the service is torn down with the host's
        assertThat(probe.count("job.onCreate")).isEqualTo(1)

        // the app lists its pending jobs as it scheduled them (WorkManager filters by its own service class)
        assertThat(probe.valueOf("job.pending.service")).isEqualTo("com.mikimn.fixture.hello.HelloJobService")
        assertThat(probe.valueOf("job.pending.extras")).doesNotContain("dclJob")
        assertThat(probe.valueOf("job.pending.listed")).isEqualTo("true")
        assertThat(probe.valueOf("job.pending.afterCancel")).isEqualTo("true")
    }
}
