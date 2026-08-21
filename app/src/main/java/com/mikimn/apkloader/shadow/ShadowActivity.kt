package com.mikimn.apkloader.shadow

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import com.mikimn.apkloader.reflection.findMethodByName
import com.mikimn.apkloader.reflection.tryGetValue

object ShadowActivity {
    fun attachActivity(
        aInfo: ActivityInfo,
        realActivity: Activity,
        activity: Activity,
        application: Application? = null
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
            realActivity.baseContext,
            realActivity.tryGetValue("mMainThread"),
            realActivity.tryGetValue("mInstrumentation"),
            realActivity.tryGetValue("mToken"),
            realActivity.tryGetValue("mIdent"),
            application ?: realActivity.application,
            Intent(realActivity.baseContext, activityClass),
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