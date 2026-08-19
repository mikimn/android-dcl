package com.mikimn.apkloader.dcl

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.os.TestLooperManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.RequiresApi

class DCLInstrumentation(private val baseInstrumentation: Instrumentation) : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        baseInstrumentation.onCreate(arguments)
    }

    override fun start() {
        baseInstrumentation.start()
    }

    override fun onStart() {
        baseInstrumentation.onStart()
    }

    override fun onException(obj: Any?, e: Throwable?): Boolean {
        return baseInstrumentation.onException(obj, e)
    }

    override fun sendStatus(resultCode: Int, results: Bundle?) {
        baseInstrumentation.sendStatus(resultCode, results)
    }

    override fun addResults(results: Bundle?) {
        baseInstrumentation.addResults(results)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        baseInstrumentation.finish(resultCode, results)
    }

    override fun setAutomaticPerformanceSnapshots() {
        baseInstrumentation.setAutomaticPerformanceSnapshots()
    }

    override fun startPerformanceSnapshot() {
        baseInstrumentation.startPerformanceSnapshot()
    }

    override fun endPerformanceSnapshot() {
        baseInstrumentation.endPerformanceSnapshot()
    }

    override fun onDestroy() {
        baseInstrumentation.onDestroy()
    }

    override fun getContext(): Context {
        return baseInstrumentation.getContext()
    }

    override fun getComponentName(): ComponentName {
        return baseInstrumentation.getComponentName()
    }

    override fun getTargetContext(): Context {
        return baseInstrumentation.getTargetContext()
    }

    override fun getProcessName(): String {
        return baseInstrumentation.getProcessName()
    }

    override fun isProfiling(): Boolean {
        return baseInstrumentation.isProfiling()
    }

    override fun startProfiling() {
        baseInstrumentation.startProfiling()
    }

    override fun stopProfiling() {
        baseInstrumentation.stopProfiling()
    }

    override fun setInTouchMode(inTouch: Boolean) {
        baseInstrumentation.setInTouchMode(inTouch)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun resetInTouchMode() {
        baseInstrumentation.resetInTouchMode()
    }

    override fun waitForIdle(recipient: Runnable?) {
        baseInstrumentation.waitForIdle(recipient)
    }

    override fun waitForIdleSync() {
        baseInstrumentation.waitForIdleSync()
    }

    override fun runOnMainSync(runner: Runnable?) {
        baseInstrumentation.runOnMainSync(runner)
    }

    override fun startActivitySync(intent: Intent?): Activity {
        return baseInstrumentation.startActivitySync(intent)
    }

    override fun startActivitySync(intent: Intent, options: Bundle?): Activity {
        return baseInstrumentation.startActivitySync(intent, options)
    }

    override fun addMonitor(monitor: ActivityMonitor?) {
        baseInstrumentation.addMonitor(monitor)
    }

    override fun addMonitor(
        filter: IntentFilter?,
        result: ActivityResult?,
        block: Boolean
    ): ActivityMonitor {
        return baseInstrumentation.addMonitor(filter, result, block)
    }

    override fun addMonitor(
        cls: String?,
        result: ActivityResult?,
        block: Boolean
    ): ActivityMonitor {
        return baseInstrumentation.addMonitor(cls, result, block)
    }

    override fun checkMonitorHit(monitor: ActivityMonitor?, minHits: Int): Boolean {
        return baseInstrumentation.checkMonitorHit(monitor, minHits)
    }

    override fun waitForMonitor(monitor: ActivityMonitor?): Activity {
        return baseInstrumentation.waitForMonitor(monitor)
    }

    override fun waitForMonitorWithTimeout(monitor: ActivityMonitor?, timeOut: Long): Activity {
        return baseInstrumentation.waitForMonitorWithTimeout(monitor, timeOut)
    }

    override fun removeMonitor(monitor: ActivityMonitor?) {
        baseInstrumentation.removeMonitor(monitor)
    }

    override fun invokeMenuActionSync(targetActivity: Activity?, id: Int, flag: Int): Boolean {
        return baseInstrumentation.invokeMenuActionSync(targetActivity, id, flag)
    }

    override fun invokeContextMenuAction(targetActivity: Activity?, id: Int, flag: Int): Boolean {
        return baseInstrumentation.invokeContextMenuAction(targetActivity, id, flag)
    }

    override fun sendStringSync(text: String?) {
        baseInstrumentation.sendStringSync(text)
    }

    override fun sendKeySync(event: KeyEvent?) {
        baseInstrumentation.sendKeySync(event)
    }

    override fun sendKeyDownUpSync(keyCode: Int) {
        baseInstrumentation.sendKeyDownUpSync(keyCode)
    }

    override fun sendCharacterSync(keyCode: Int) {
        baseInstrumentation.sendCharacterSync(keyCode)
    }

    override fun sendPointerSync(event: MotionEvent?) {
        baseInstrumentation.sendPointerSync(event)
    }

    override fun sendTrackballEventSync(event: MotionEvent?) {
        baseInstrumentation.sendTrackballEventSync(event)
    }

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return baseInstrumentation.newApplication(cl, className, context)
    }

    override fun callApplicationOnCreate(app: Application?) {
        baseInstrumentation.callApplicationOnCreate(app)
    }

    override fun newActivity(
        clazz: Class<*>?,
        context: Context?,
        token: IBinder?,
        application: Application?,
        intent: Intent?,
        info: ActivityInfo?,
        title: CharSequence?,
        parent: Activity?,
        id: String?,
        lastNonConfigurationInstance: Any?
    ): Activity {
        return baseInstrumentation.newActivity(
            clazz,
            context,
            token,
            application,
            intent,
            info,
            title,
            parent,
            id,
            lastNonConfigurationInstance
        )
    }

    override fun newActivity(cl: ClassLoader?, className: String?, intent: Intent?): Activity {
        return baseInstrumentation.newActivity(cl, className, intent)
    }

    override fun callActivityOnCreate(activity: Activity?, icicle: Bundle?) {
        baseInstrumentation.callActivityOnCreate(activity, icicle)
    }

    override fun callActivityOnCreate(
        activity: Activity?,
        icicle: Bundle?,
        persistentState: PersistableBundle?
    ) {
        baseInstrumentation.callActivityOnCreate(activity, icicle, persistentState)
    }

    override fun callActivityOnDestroy(activity: Activity?) {
        baseInstrumentation.callActivityOnDestroy(activity)
    }

    override fun callActivityOnRestoreInstanceState(
        activity: Activity,
        savedInstanceState: Bundle
    ) {
        baseInstrumentation.callActivityOnRestoreInstanceState(activity, savedInstanceState)
    }

    override fun callActivityOnRestoreInstanceState(
        activity: Activity,
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        baseInstrumentation.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState)
    }

    override fun callActivityOnPostCreate(activity: Activity, savedInstanceState: Bundle?) {
        baseInstrumentation.callActivityOnPostCreate(activity, savedInstanceState)
    }

    override fun callActivityOnPostCreate(
        activity: Activity,
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        baseInstrumentation.callActivityOnPostCreate(activity, savedInstanceState, persistentState)
    }

    override fun callActivityOnNewIntent(activity: Activity?, intent: Intent?) {
        baseInstrumentation.callActivityOnNewIntent(activity, intent)
    }

    override fun callActivityOnStart(activity: Activity?) {
        baseInstrumentation.callActivityOnStart(activity)
    }

    override fun callActivityOnRestart(activity: Activity?) {
        baseInstrumentation.callActivityOnRestart(activity)
    }

    override fun callActivityOnResume(activity: Activity?) {
        baseInstrumentation.callActivityOnResume(activity)
    }

    override fun callActivityOnStop(activity: Activity?) {
        baseInstrumentation.callActivityOnStop(activity)
    }

    override fun callActivityOnSaveInstanceState(activity: Activity, outState: Bundle) {
        baseInstrumentation.callActivityOnSaveInstanceState(activity, outState)
    }

    override fun callActivityOnSaveInstanceState(
        activity: Activity,
        outState: Bundle,
        outPersistentState: PersistableBundle
    ) {
        baseInstrumentation.callActivityOnSaveInstanceState(activity, outState, outPersistentState)
    }

    override fun callActivityOnPause(activity: Activity?) {
        baseInstrumentation.callActivityOnPause(activity)
    }

    override fun callActivityOnUserLeaving(activity: Activity?) {
        baseInstrumentation.callActivityOnUserLeaving(activity)
    }

    override fun callActivityOnPictureInPictureRequested(activity: Activity) {
        baseInstrumentation.callActivityOnPictureInPictureRequested(activity)
    }

    @Deprecated("Deprecated in Java")
    override fun startAllocCounting() {
        baseInstrumentation.startAllocCounting()
    }

    @Deprecated("Deprecated in Java")
    override fun stopAllocCounting() {
        baseInstrumentation.stopAllocCounting()
    }

    override fun getAllocCounts(): Bundle {
        return baseInstrumentation.allocCounts
    }

    override fun getBinderCounts(): Bundle {
        return baseInstrumentation.binderCounts
    }

    override fun getUiAutomation(): UiAutomation {
        return baseInstrumentation.uiAutomation
    }

    override fun getUiAutomation(flags: Int): UiAutomation {
        return baseInstrumentation.getUiAutomation(flags)
    }

    override fun acquireLooperManager(looper: Looper?): TestLooperManager {
        return baseInstrumentation.acquireLooperManager(looper)
    }
}