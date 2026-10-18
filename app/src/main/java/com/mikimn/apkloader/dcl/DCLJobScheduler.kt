package com.mikimn.apkloader.dcl

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.os.Build
import android.util.Log

/**
 * What a loaded app gets for `getSystemService(JOB_SCHEDULER_SERVICE)`: the real scheduler, with jobs that name a
 * service of a loaded APK retargeted at [DCLJobService] on the way in ([JobRouting.rewrite]) and given back as the
 * app scheduled them on the way out (WorkManager lists pending jobs and filters them by its own service class).
 * Job ids are per *uid*, which loaded apps share with the host and with each other: not namespaced here.
 */
class DCLJobScheduler(
    private val real: JobScheduler,
    private val hostPackage: String,
    private val loader: () -> FileTrackingClassLoader?
) : JobScheduler() {
    private fun route(job: JobInfo): JobInfo {
        val className = job.service.className
        val apk = loader()?.ownerOf(className) ?: return job
        Log.i(TAG, "[Rewrite] job ${job.id} for $className (apk=${apk.name}) -> DCLJobService")
        // Remember the package the app itself named (it builds ComponentName(context, Class), whatever
        // context.getPackageName() said), so the job can be handed back exactly as it was scheduled.
        return JobRouting.rewrite(job, hostPackage, className, apk.name, job.service.packageName)
    }

    override fun schedule(job: JobInfo): Int = real.schedule(route(job))

    override fun enqueue(job: JobInfo, work: JobWorkItem): Int = real.enqueue(route(job), work)

    override fun cancel(jobId: Int) = real.cancel(jobId)

    override fun cancelAll() = real.cancelAll()

    override fun getAllPendingJobs(): MutableList<JobInfo> =
        real.allPendingJobs.mapTo(ArrayList()) { JobRouting.original(it) }

    override fun getPendingJob(jobId: Int): JobInfo? = real.getPendingJob(jobId)?.let { JobRouting.original(it) }

    // API 34+: a namespaced scheduler must keep routing too, or it would hand back the unwrapped real one.
    override fun forNamespace(namespace: String): JobScheduler =
        if (Build.VERSION.SDK_INT >= 34) DCLJobScheduler(real.forNamespace(namespace), hostPackage, loader) else this

    private companion object {
        const val TAG = "DCLJobScheduler"
    }
}
