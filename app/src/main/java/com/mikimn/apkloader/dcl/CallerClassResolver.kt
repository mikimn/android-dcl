package com.mikimn.apkloader.dcl

/**
 * Finds the class name of whoever really called into the current method,
 * skipping this app's own frames.
 *
 * SecurityManager.getClassContext() (the classic cheap "get the call stack as
 * Class[] with no string-based re-resolution" trick) was tried first and is
 * present in the compileSdk stub - but confirmed on-device that it returns
 * null unconditionally on this ART build (not just in edge cases; every
 * single call). Thread.currentThread().stackTrace is the universally
 * reliable fallback: standard Java API, no SecurityManager dependency, works
 * on every Android version this project supports. It costs a bit more (class
 * names need matching against known loaded APKs by string, not classloader
 * identity), an acceptable tradeoff for a POC.
 */
object CallerClassResolver {
    private const val HOST_PACKAGE_PREFIX = "com.mikimn.apkloader."

    /**
     * The first stack frame's class name that isn't this app's own code
     * (including this resolver's own frame) - i.e. whoever actually called
     * into us, whether that's a loaded APK's own class or an Android
     * framework class.
     */
    fun findRealCallerClassName(): String? {
        return Thread.currentThread().stackTrace
            .firstOrNull {
                // dalvik.system.VMStack: confirmed on-device that Android's stackTrace,
                // unlike desktop JVM's, includes this native stack-capture implementation
                // detail as a real frame - never a meaningful caller.
                !it.className.startsWith(HOST_PACKAGE_PREFIX) &&
                    it.className != "dalvik.system.VMStack" &&
                    it.className != "java.lang.Thread"
            }
            ?.className
    }
}
