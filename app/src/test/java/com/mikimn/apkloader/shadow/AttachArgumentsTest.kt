package com.mikimn.apkloader.shadow

import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.shadow.AttachArguments.Role
import org.junit.Assert.assertThrows
import org.junit.Test

class AttachArgumentsTest {
    // android 11 (API 30): final void attach(Context, ActivityThread, Instrumentation, IBinder, int, Application,
    // Intent, ActivityInfo, CharSequence, Activity, String, NonConfigurationInstances, Configuration, String,
    // IVoiceInteractor, Window, ActivityConfigCallback, IBinder)
    private val api30 = listOf(
        "android.content.Context", "android.app.ActivityThread", "android.app.Instrumentation", "android.os.IBinder",
        "int", "android.app.Application", "android.content.Intent", "android.content.pm.ActivityInfo",
        "java.lang.CharSequence", "android.app.Activity", "java.lang.String",
        "android.app.Activity\$NonConfigurationInstances", "android.content.res.Configuration", "java.lang.String",
        "com.android.internal.app.IVoiceInteractor", "android.view.Window",
        "android.view.ViewRootImpl\$ActivityConfigCallback", "android.os.IBinder"
    )

    @Test fun mapsTheApi30SignatureByType() {
        assertThat(AttachArguments.rolesFor(api30)).containsExactly(
            Role.CONTEXT, Role.MAIN_THREAD, Role.INSTRUMENTATION, Role.TOKEN, Role.IDENT, Role.APPLICATION,
            Role.INTENT, Role.ACTIVITY_INFO, Role.TITLE, Role.PARENT, Role.EMBEDDED_ID, Role.NON_CONFIG_INSTANCES,
            Role.CONFIGURATION, Role.REFERRER, Role.VOICE_INTERACTOR, Role.WINDOW, Role.CONFIG_CALLBACK, Role.ASSIST_TOKEN
        ).inOrder()
    }

    // API 31 appended the caller-info access token (another IBinder): it is accepted, and passed as null.
    @Test fun aTrailingExtraBinderIsAcceptedAndUnused() {
        val roles = AttachArguments.rolesFor(api30 + "android.os.IBinder" + "android.os.IBinder")
        assertThat(roles.takeLast(3)).containsExactly(Role.ASSIST_TOKEN, Role.UNUSED, Role.UNUSED).inOrder()
        val args = AttachArguments.build(roles) { it.name }
        assertThat(args.takeLast(2)).containsExactly(null, null)
        assertThat(args[3]).isEqualTo("TOKEN")
        assertThat(args[17]).isEqualTo("ASSIST_TOKEN")
    }

    @Test fun parametersAreFilledInTheOrderOfTheSignatureNotOfOurList() {
        // an invented release that moves the Window before the Intent: still resolved by type
        val moved = listOf("android.view.Window", "android.content.Intent", "android.content.Context")
        val args = AttachArguments.build(AttachArguments.rolesFor(moved)) { it.name }
        assertThat(args).asList().containsExactly("WINDOW", "INTENT", "CONTEXT").inOrder()
    }

    @Test fun anUnknownParameterTypeFailsLoudlyWithTheWholeSignature() {
        val e = assertThrows(AttachArguments.UnrecognizedParameter::class.java) {
            AttachArguments.rolesFor(api30 + "android.app.ActivityOptions", signature = "SIG")
        }
        assertThat(e).hasMessageThat().contains("#18")
        assertThat(e).hasMessageThat().contains("android.app.ActivityOptions")
        assertThat(e).hasMessageThat().contains("attach(SIG)")
    }

    @Test fun aSecondIntParameterIsNotGuessed() {
        assertThrows(AttachArguments.UnrecognizedParameter::class.java) {
            AttachArguments.rolesFor(listOf("int", "int"))
        }
    }

    @Test fun unusedAndCallbackRolesAreAlwaysNullEvenIfAValueIsOffered() {
        val roles = listOf(Role.TITLE, Role.VOICE_INTERACTOR, Role.CONFIG_CALLBACK, Role.UNUSED, Role.CONTEXT)
        assertThat(AttachArguments.build(roles) { "value" }).asList().containsExactly(null, null, null, null, "value").inOrder()
    }
}
