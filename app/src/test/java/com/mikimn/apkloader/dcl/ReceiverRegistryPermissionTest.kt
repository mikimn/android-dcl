package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class ReceiverRegistryPermissionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun theInternalPermissionIsNamedAfterTheHostPackageNotHardcoded() {
        assertThat(ReceiverRegistry.internalBroadcastPermission(context))
            .isEqualTo("${context.packageName}.permission.INTERNAL_BROADCAST")
    }

    @Test fun anExportedReceiverKeepsItsOwnPermission() {
        assertThat(ReceiverRegistry.senderPermission(context, "com.example.SEND", exported = true)).isEqualTo("com.example.SEND")
        assertThat(ReceiverRegistry.senderPermission(context, null, exported = true)).isNull()
    }

    // Below API 33 the RECEIVER_NOT_EXPORTED flag does not exist, so a plain dynamic receiver is visible
    // to every app: a non-exported one must demand the host's own signature permission instead.
    @Test fun aNonExportedReceiverRequiresTheHostsOwnPermissionWhateverItDeclared() {
        val internal = ReceiverRegistry.internalBroadcastPermission(context)
        assertThat(ReceiverRegistry.senderPermission(context, null, exported = false)).isEqualTo(internal)
        assertThat(ReceiverRegistry.senderPermission(context, "com.example.SEND", exported = false)).isEqualTo(internal)
    }
}
