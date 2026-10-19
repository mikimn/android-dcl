package com.mikimn.apkloader.shadow

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.reflection.tryGetValue

object ShadowActivity {
    private const val TAG = "ShadowActivity"

    /**
     * The `Intent` a hosted activity sees from `getIntent()`: a copy of what actually launched the
     * host (extras, action, data, type, **flags** - including URI-grant flags - and clip data),
     * pointed at the shadow class, minus the extras the loader itself uses to route the launch.
     * Flags and clip data are kept on purpose: that is what the real system hands the activity, so
     * don't "clean them up". The component carries the *loaded* package ([packageName], from the
     * activity's `ActivityInfo`), matching what the app sees from `getPackageName()`.
     * Without this the shadow got a bare `Intent(context, class)` and could not read anything its
     * launcher passed. [hostIntent] is not modified.
     */
    fun shadowIntent(
        hostIntent: Intent?,
        packageName: String,
        activityClass: Class<*>,
        hostOnlyExtras: Collection<String>
    ): Intent = Intent(hostIntent ?: Intent()).apply {
        component = ComponentName(packageName, activityClass.name)
        hostOnlyExtras.forEach { removeExtra(it) }
    }

    /** The real `Activity.attach` of this device and what each of its parameters stands for. */
    class AttachTarget(val method: java.lang.reflect.Method, val roles: List<AttachArguments.Role>) {
        val signature: String get() = method.parameterTypes.joinToString(", ") { it.name }
    }

    // The signature cannot change within a process: resolved once.
    private val attachTarget: AttachTarget by lazy { resolveAttach() }

    /** Resolves the hidden `Activity.attach` (on releases that kept an older overload, the one with most parameters). */
    internal fun resolveAttach(): AttachTarget {
        val method = HiddenApiBypass.getDeclaredMethods(Activity::class.java)
            .filterIsInstance<java.lang.reflect.Method>()
            .filter { it.name == "attach" }
            .maxByOrNull { it.parameterCount }
            ?.apply { isAccessible = true }
            ?: error("Activity.attach not found on SDK ${Build.VERSION.SDK_INT}")
        val signature = method.parameterTypes.joinToString(", ") { it.name }
        return AttachTarget(method, AttachArguments.rolesFor(method.parameterTypes.map { it.name }, signature))
    }

    // What the platform cannot do without: a hardcoded field name that was renamed reads as null, and an
    // attach() given no thread/token/... fails far from here. Say which one.
    private val requiredRoles = listOf(
        AttachArguments.Role.CONTEXT, AttachArguments.Role.MAIN_THREAD, AttachArguments.Role.INSTRUMENTATION,
        AttachArguments.Role.TOKEN, AttachArguments.Role.APPLICATION, AttachArguments.Role.INTENT,
        AttachArguments.Role.ACTIVITY_INFO
    )

    fun attachActivity(
        aInfo: ActivityInfo,
        realActivity: Activity,
        activity: Activity,
        application: Application? = null,
        baseContext: Context = realActivity.baseContext
    ) {
        // Activity.attach is hidden and its parameter list changes between Android releases, so it is called
        // by parameter *type* (see AttachArguments), never positionally per API level.
        val activityClass = activity.javaClass
        val target = try {
            attachTarget
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot use Activity.attach on SDK ${Build.VERSION.SDK_INT} (${Build.MODEL})", e)
            throw e
        }
        val roles = target.roles
        val intent = shadowIntent(
            realActivity.intent, aInfo.packageName ?: baseContext.packageName, activityClass,
            DCLActivity.HOST_ONLY_EXTRAS
        )
        val args = AttachArguments.build(roles) { role ->
            when (role) {
                AttachArguments.Role.CONTEXT -> baseContext
                AttachArguments.Role.MAIN_THREAD -> realActivity.tryGetValue("mMainThread")
                AttachArguments.Role.INSTRUMENTATION -> realActivity.tryGetValue("mInstrumentation")
                AttachArguments.Role.TOKEN -> realActivity.tryGetValue("mToken")
                AttachArguments.Role.IDENT -> realActivity.tryGetValue("mIdent")
                AttachArguments.Role.APPLICATION -> application ?: realActivity.application
                AttachArguments.Role.INTENT -> intent
                AttachArguments.Role.ACTIVITY_INFO -> aInfo
                AttachArguments.Role.PARENT -> realActivity.parent
                AttachArguments.Role.EMBEDDED_ID -> realActivity.tryGetValue("mEmbeddedID")
                AttachArguments.Role.NON_CONFIG_INSTANCES -> realActivity.tryGetValue("mLastNonConfigurationInstances")
                AttachArguments.Role.CONFIGURATION -> realActivity.tryGetValue("mCurrentConfig")
                AttachArguments.Role.REFERRER -> realActivity.tryGetValue("mReferrer")
                AttachArguments.Role.WINDOW -> realActivity.window
                AttachArguments.Role.ASSIST_TOKEN -> realActivity.tryGetValue("mAssistToken")
                else -> null
            }
        }
        roles.forEachIndexed { n, role ->
            check(role !in requiredRoles || args[n] != null) {
                "Activity.attach needs a value for $role but got null on SDK ${Build.VERSION.SDK_INT} " +
                    "(a hardcoded Activity field name probably changed); attach(${target.signature})"
            }
        }
        try {
            target.method.invoke(activity, *args)
        } catch (e: Throwable) {
            Log.e(TAG, "Activity.attach(${target.signature}) failed on SDK ${Build.VERSION.SDK_INT}", e)
            throw e
        }
    }
}