package com.mikimn.apkloader.dcl

import android.app.job.JobInfo
import android.content.ComponentName
import android.os.Parcel
import com.mikimn.apkloader.reflection.tryGetField
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Retargets a loaded app's `JobInfo` (WorkManager's `SystemJobScheduler`, `JobScheduler.schedule` directly) at the
 * host's [DCLJobService], the one manifest-declared `JobService`: the system validates the job's service against the
 * *host* manifest, so the loaded class cannot be named there. Which loaded class and APK run when the job fires
 * travels in the job's own persistable extras, so it survives a reboot and a restarted process, and is stripped again
 * before the loaded service sees its [android.app.job.JobParameters].
 *
 * `JobInfo` has no copy constructor and its service field is final, so a rewritten job is a parcel round-trip
 * copy with that field replaced.
 */
object JobRouting {
    const val KEY_CLASS = "dclJobClass"
    const val KEY_APK = "dclJobApk"
    const val KEY_PACKAGE = "dclJobPackage"

    /** A copy of [job] that runs [className] of [apkName] through [DCLJobService]; [packageName] is how the app named its service. */
    fun rewrite(job: JobInfo, hostPackage: String, className: String, apkName: String, packageName: String): JobInfo =
        copy(job).also {
            it.extras.putString(KEY_CLASS, className)
            it.extras.putString(KEY_APK, apkName)
            it.extras.putString(KEY_PACKAGE, packageName)
            setService(it, ComponentName(hostPackage, DCLJobService::class.java.name))
        }

    /** What the loaded app scheduled: the job as it named its own service, without the routing extras. */
    fun original(job: JobInfo): JobInfo {
        val cls = job.extras.getString(KEY_CLASS) ?: return job
        val pkg = job.extras.getString(KEY_PACKAGE) ?: return job
        return copy(job).also {
            setService(it, ComponentName(pkg, cls))
            listOf(KEY_CLASS, KEY_APK, KEY_PACKAGE).forEach { key -> it.extras.remove(key) }
        }
    }

    private fun copy(job: JobInfo): JobInfo {
        val parcel = Parcel.obtain()
        try {
            job.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return JobInfo.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun setService(job: JobInfo, service: ComponentName) {
        // Plain reflection on a hidden field can be denied outright (JobService.mEngine is, on Android 11): fall back
        // to HiddenApiBypass. Verified on API 30 only; a newer release that hides the field differently fails here.
        val field = JobInfo::class.java.tryGetField("service")
            ?: HiddenApiBypass.getInstanceFields(JobInfo::class.java)
                .filterIsInstance<java.lang.reflect.Field>().firstOrNull { it.name == "service" }
            ?: error("JobInfo.service not found on SDK ${android.os.Build.VERSION.SDK_INT}")
        field.isAccessible = true
        field.set(job, service)
    }
}
