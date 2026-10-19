package com.mikimn.apkloader.dcl

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * What a loaded app gets for `getSystemService(JOB_SCHEDULER_SERVICE)`: the real scheduler, with jobs that name a
 * service of a loaded APK retargeted at [DCLJobService] on the way in ([JobRouting.rewrite]) and given back as the
 * app scheduled them on the way out (WorkManager lists pending jobs and filters them by its own service class).
 *
 * `JobScheduler` has concrete methods whose default body throws (`UnsupportedOperationException`, as the
 * `PackageManager` ones do), so **every** public method of the compile SDK is overridden and delegated; a reflection
 * test (`DCLJobSchedulerCompletenessTest`) fails when one is missing. Methods added after the compile SDK (API 36:
 * `getPendingJobReasons`, `getPendingJobReasonsHistory`, `getPendingJobReasonStats`) cannot be overridden yet and hit the
 * base stub on those releases until `compileSdk` is raised.
 *
 * Job ids are per *uid*, which loaded apps share with the host and with each other, so one app's `schedule` can
 * replace another's job with the same id; what is scoped here is what an app can *see and cancel*: only its own
 * routed jobs (`KEY_APK`) and jobs that were never routed.
 *
 * @param ownApk the name of the APK this scheduler belongs to (null if it cannot be told)
 */
class DCLJobScheduler(
    private val real: JobScheduler,
    private val hostPackage: String,
    private val loader: () -> FileTrackingClassLoader?,
    private val ownApk: () -> String?
) : JobScheduler() {
    private fun route(job: JobInfo): JobInfo {
        val className = job.service.className
        val apk = loader()?.ownerOf(className) ?: return job
        Log.i(TAG, "[Rewrite] job ${job.id} for $className (apk=${apk.name}) -> DCLJobService")
        // Remember the package the app itself named (it builds ComponentName(context, Class), whatever
        // context.getPackageName() said), so the job can be handed back exactly as it was scheduled.
        return JobRouting.rewrite(job, hostPackage, className, apk.name, job.service.packageName)
    }

    /** Whether the app this scheduler belongs to may see [job]: its own routed jobs, and jobs never routed. */
    private fun visible(job: JobInfo): Boolean {
        val owner = job.extras.getString(JobRouting.KEY_APK) ?: return true
        return owner == ownApk()
    }

    private fun mine(jobs: List<JobInfo>): MutableList<JobInfo> =
        jobs.filter(::visible).mapTo(ArrayList()) { JobRouting.original(it) }

    override fun schedule(job: JobInfo): Int = real.schedule(route(job))

    override fun enqueue(job: JobInfo, work: JobWorkItem): Int = real.enqueue(route(job), work)

    override fun cancel(jobId: Int) {
        val pending = real.getPendingJob(jobId)
        if (pending != null && !visible(pending)) {
            Log.w(TAG, "Ignoring cancel($jobId): that job belongs to another loaded app")
            return
        }
        real.cancel(jobId)
    }

    override fun cancelAll() {
        val own = ownApk()
        if (own == null) {
            Log.w(TAG, "cancelAll() ignored: cannot tell which jobs belong to this app")
            return
        }
        // Not the real cancelAll(): that cancels every job of the uid, the host's and other loaded apps' included.
        real.allPendingJobs.filter { it.extras.getString(JobRouting.KEY_APK) == own }.forEach { real.cancel(it.id) }
    }

    override fun getAllPendingJobs(): MutableList<JobInfo> = mine(real.allPendingJobs)

    override fun getPendingJob(jobId: Int): JobInfo? =
        real.getPendingJob(jobId)?.takeIf(::visible)?.let { JobRouting.original(it) }

    // --- API 34+ ---

    @RequiresApi(34)
    override fun forNamespace(namespace: String): JobScheduler =
        DCLJobScheduler(real.forNamespace(namespace), hostPackage, loader, ownApk)

    @RequiresApi(34)
    override fun getNamespace(): String? = real.namespace

    @RequiresApi(34)
    override fun getPendingJobsInAllNamespaces(): MutableMap<String, MutableList<JobInfo>> =
        real.pendingJobsInAllNamespaces.mapValuesTo(LinkedHashMap()) { (_, jobs) -> mine(jobs) }

    @RequiresApi(34)
    override fun cancelInAllNamespaces() {
        val own = ownApk() ?: return Unit.also { Log.w(TAG, "cancelInAllNamespaces() ignored: unknown app") }
        real.pendingJobsInAllNamespaces.forEach { (namespace, jobs) ->
            val scoped = real.forNamespace(namespace)
            jobs.filter { it.extras.getString(JobRouting.KEY_APK) == own }.forEach { scoped.cancel(it.id) }
        }
    }

    @RequiresApi(34)
    override fun canRunUserInitiatedJobs(): Boolean = real.canRunUserInitiatedJobs()

    @RequiresApi(34)
    override fun getPendingJobReason(jobId: Int): Int = real.getPendingJobReason(jobId)

    private companion object {
        const val TAG = "DCLJobScheduler"
    }
}
