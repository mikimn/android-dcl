package com.mikimn.apkloader.shadow

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.reflection.findMethodByName
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

    fun attachActivity(
        aInfo: ActivityInfo,
        realActivity: Activity,
        activity: Activity,
        application: Application? = null,
        baseContext: Context = realActivity.baseContext
    ) {
        // final void android.app.Activity.attach(
        //      android.content.Context,
        //      android.app.ActivityThread,
        //      android.app.Instrumentation,
        //      android.os.IBinder,
        //      int,
        //      android.app.Application,
        //      android.content.Intent,
        //      android.content.pm.ActivityInfo,
        //      java.lang.CharSequence,
        //      android.app.Activity,
        //      java.lang.String,
        //      android.app.Activity$NonConfigurationInstances,
        //      android.content.res.Configuration,
        //      java.lang.String,
        //      com.android.internal.app.IVoiceInteractor,
        //      android.view.Window,
        //      android.view.ViewRootImpl$ActivityConfigCallback,
        //      android.os.IBinder)
        val activityClass = activity.javaClass

        val parameters = mutableListOf(
            baseContext,
            realActivity.tryGetValue("mMainThread"),
            realActivity.tryGetValue("mInstrumentation"),
            realActivity.tryGetValue("mToken"),
            realActivity.tryGetValue("mIdent"),
            application ?: realActivity.application,
            shadowIntent(
                realActivity.intent, aInfo.packageName ?: baseContext.packageName, activityClass,
                DCLActivity.HOST_ONLY_EXTRAS
            ),
            aInfo,
            null,
            realActivity.parent,
            realActivity.tryGetValue("mEmbeddedID"),
            realActivity.tryGetValue("mLastNonConfigurationInstances"),
            realActivity.tryGetValue("mCurrentConfig"),
            realActivity.tryGetValue("mReferrer"),
            null,
            realActivity.window,
            /* activityConfigCallback */ null,
            realActivity.tryGetValue("mAssistToken")
        )

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            parameters.add(
                /* initialCallerInfoAccessToken */ null
            )
        }

        val mAttach = activityClass.findMethodByName("attach")
        mAttach?.isAccessible = true
        mAttach?.invoke(
            activity,
            *parameters.toTypedArray()
        )
    }
}