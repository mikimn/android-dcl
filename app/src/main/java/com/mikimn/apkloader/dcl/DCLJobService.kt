package com.mikimn.apkloader.dcl

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The host's one `JobService` (declared in the manifest with `BIND_JOB_SERVICE`): every job of a loaded app is
 * scheduled against it by [DCLJobScheduler]. The job's extras say which loaded class to run ([JobRouting]); one
 * instance of that class is created per class (as the system keeps one service instance per component) and
 * `attach`ed like a hosted service ([HostedServices]), and its callbacks get the job's parameters without the
 * routing extras. The loaded service's `jobFinished` goes through this host's job engine, which the system's
 * binder talks to, so the loaded service shares it.
 */
class DCLJobService : JobService() {
    private val shadows = HashMap<String, JobService>()
    private val classOfJob = HashMap<Int, String>()

    // Why the last attempt to create a loaded job service failed: decides whether the job is worth retrying.
    private var lastFailure: Throwable? = null

    private fun shadowFor(className: String, apkName: String): JobService? = shadows[className] ?: try {
        val hosted = HostedServices.create(this, className, apkName)
        val shadow = hosted.service as JobService
        // jobFinished() calls the engine the host's onBind() created, not one of the shadow's own.
        val engine = HiddenApiBypass.getInstanceFields(JobService::class.java)
            .filterIsInstance<java.lang.reflect.Field>().firstOrNull { it.name == "mEngine" }
            ?.apply { isAccessible = true }
        engine?.set(shadow, engine.get(this))
        shadow.onCreate()
        shadow.also { shadows[className] = it }
    } catch (e: Throwable) {
        // A loaded job service that can't be created must not take the host process down.
        Log.e(TAG, "Loaded job service $className failed to start", e)
        lastFailure = e
        null
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val className = params.extras.getString(JobRouting.KEY_CLASS)
        val apkName = params.extras.getString(JobRouting.KEY_APK)
        if (className == null || apkName == null) {
            Log.e(TAG, "Job ${params.jobId} carries no loaded service; dropping it")
            return false
        }
        // The loaded service sees the extras it scheduled, not ours.
        listOf(JobRouting.KEY_CLASS, JobRouting.KEY_APK, JobRouting.KEY_PACKAGE).forEach { params.extras.remove(it) }
        val shadow = shadowFor(className, apkName) ?: return failedToStart(params, apkName)
        classOfJob[params.jobId] = className
        return try {
            shadow.onStartJob(params).also { if (!it) classOfJob.remove(params.jobId) }
        } catch (e: Throwable) {
            Log.e(TAG, "Loaded job service $className failed in onStartJob", e)
            classOfJob.remove(params.jobId)
            false
        }
    }

    /**
     * The loaded service could not be created. Returning false would tell the system (and WorkManager) the work
     * ran, so it would be silently lost: ask for a reschedule (the system backs it off) unless the failure is one
     * a retry cannot fix - the APK is gone, or the loader refused it ([ApkPolicy]).
     */
    private fun failedToStart(params: JobParameters, apkName: String): Boolean {
        val permanent = lastFailure is SecurityException || (apkName.startsWith("/") && !java.io.File(apkName).exists())
        if (permanent) {
            Log.e(TAG, "Job ${params.jobId} dropped: its loaded service can never start ($lastFailure)")
            return false
        }
        Log.w(TAG, "Job ${params.jobId} will be retried: its loaded service failed to start ($lastFailure)")
        android.os.Handler(mainLooper).post { jobFinished(params, true) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val className = classOfJob.remove(params.jobId) ?: params.extras.getString(JobRouting.KEY_CLASS) ?: return false
        listOf(JobRouting.KEY_CLASS, JobRouting.KEY_APK, JobRouting.KEY_PACKAGE).forEach { params.extras.remove(it) }
        return try {
            shadows[className]?.onStopJob(params) ?: false
        } catch (e: Throwable) {
            Log.e(TAG, "Loaded job service $className failed in onStopJob", e)
            false
        }
    }

    override fun onDestroy() {
        shadows.values.forEach {
            try { it.onDestroy() } catch (e: Throwable) { Log.e(TAG, "Loaded job service failed in onDestroy", e) }
        }
        shadows.clear()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DCLJobService"
    }
}
