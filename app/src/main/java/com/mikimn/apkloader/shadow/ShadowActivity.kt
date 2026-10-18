package com.mikimn.apkloader.shadow

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.reflection.tryGetValue

object ShadowActivity {
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

    /** The hidden `Activity.attach`; on releases that kept an older overload around, the one with most parameters. */
    private fun findAttach(): java.lang.reflect.Method =
        HiddenApiBypass.getDeclaredMethods(Activity::class.java)
            .filterIsInstance<java.lang.reflect.Method>()
            .filter { it.name == "attach" }
            .maxByOrNull { it.parameterCount }
            ?.apply { isAccessible = true }
            ?: error("Activity.attach not found on SDK ${Build.VERSION.SDK_INT}")

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
        val attach = findAttach()
        val roles = AttachArguments.rolesFor(
            attach.parameterTypes.map { it.name },
            signature = attach.parameterTypes.joinToString(", ") { it.name }
        )
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
        attach.invoke(activity, *args)
    }
}