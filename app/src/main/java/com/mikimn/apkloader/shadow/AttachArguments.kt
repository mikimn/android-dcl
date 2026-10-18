package com.mikimn.apkloader.shadow

/**
 * Builds the argument list of the hidden `Activity.attach(...)` from its **parameter types**, not from a
 * hardcoded position-per-API-level list. AOSP appends/reorders parameters between releases (a trailing
 * `IBinder` for the caller-info token in S, a shareable-activity token later, ...); matching by type means a
 * new trailing parameter of a known type is filled in, and anything we do not recognise **fails loudly** with
 * the whole signature in the message instead of invoking with the wrong arity and failing obscurely (or worse,
 * silently shifting every later argument).
 *
 * Types that occur more than once are told apart by their order of appearance: the first `IBinder` is the
 * activity token and the second the assist token (later ones are the caller-info/shareable tokens and stay
 * null); the first `String` is the embedded id and the second the referrer. Pure on type *names*, so it is
 * unit-testable without the hidden classes.
 *
 * Strict on purpose: a trailing *primitive* (`boolean`, `long`, a second `int`) is not guessed either. A default
 * value could silently change what the platform does with the activity; better to fail with the signature.
 */
object AttachArguments {
    /** What a parameter of `Activity.attach` stands for. */
    enum class Role {
        CONTEXT, MAIN_THREAD, INSTRUMENTATION, TOKEN, IDENT, APPLICATION, INTENT, ACTIVITY_INFO, TITLE, PARENT,
        EMBEDDED_ID, NON_CONFIG_INSTANCES, CONFIGURATION, REFERRER, VOICE_INTERACTOR, WINDOW, CONFIG_CALLBACK,
        ASSIST_TOKEN,

        /** A parameter we deliberately pass null for (extra tokens, unused callbacks). */
        UNUSED
    }

    class UnrecognizedParameter(message: String) : IllegalStateException(message)

    fun rolesFor(typeNames: List<String>, signature: String = typeNames.joinToString(", ")): List<Role> {
        var binders = 0
        var strings = 0
        var ints = 0
        return typeNames.mapIndexed { index, type ->
            when (type) {
                "android.content.Context" -> Role.CONTEXT
                "android.app.ActivityThread" -> Role.MAIN_THREAD
                "android.app.Instrumentation" -> Role.INSTRUMENTATION
                "android.os.IBinder" -> when (binders++) { 0 -> Role.TOKEN; 1 -> Role.ASSIST_TOKEN; else -> Role.UNUSED }
                "int" -> if (ints++ == 0) Role.IDENT else unrecognized(index, type, signature)
                "android.app.Application" -> Role.APPLICATION
                "android.content.Intent" -> Role.INTENT
                "android.content.pm.ActivityInfo" -> Role.ACTIVITY_INFO
                "java.lang.CharSequence" -> Role.TITLE
                "android.app.Activity" -> Role.PARENT
                "java.lang.String" -> when (strings++) { 0 -> Role.EMBEDDED_ID; 1 -> Role.REFERRER; else -> Role.UNUSED }
                "android.app.Activity\$NonConfigurationInstances" -> Role.NON_CONFIG_INSTANCES
                "android.content.res.Configuration" -> Role.CONFIGURATION
                "com.android.internal.app.IVoiceInteractor" -> Role.VOICE_INTERACTOR
                "android.view.Window" -> Role.WINDOW
                "android.view.ViewRootImpl\$ActivityConfigCallback" -> Role.CONFIG_CALLBACK
                else -> unrecognized(index, type, signature)
            }
        }
    }

    private fun unrecognized(index: Int, type: String, signature: String): Nothing =
        throw UnrecognizedParameter(
            "Activity.attach has a parameter #$index of unrecognized type $type; the hosting code does not know " +
                "this Android version's signature (attach($signature))"
        )

    /** The argument for each role, in the order of [roles]; roles without a value (UNUSED, callbacks) are null. */
    fun build(roles: List<Role>, valueOf: (Role) -> Any?): Array<Any?> =
        Array(roles.size) { i ->
            val role = roles[i]
            when (role) {
                Role.UNUSED, Role.TITLE, Role.VOICE_INTERACTOR, Role.CONFIG_CALLBACK -> null
                else -> valueOf(role)
            }
        }
}
