package fakeapp

import com.mikimn.apkloader.dcl.CallerClassResolver

/** Stands in for a loaded APK's class: lives outside the host package prefix. */
object FakeLoadedApp {
    fun whoCalled(): String? = CallerClassResolver.findRealCallerClassName()
}
