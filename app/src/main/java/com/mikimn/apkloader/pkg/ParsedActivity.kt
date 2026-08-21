package com.mikimn.apkloader.pkg

import android.content.pm.ActivityInfo.WindowLayout


/** @hide
 */
//@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
interface ParsedActivity : ParsedMainComponent {
    val colorMode: Int

    val configChanges: Int

    val documentLaunchMode: Int

    val launchMode: Int

    val lockTaskLaunchMode: Int

    val maxRecents: Int

    val maxAspectRatio: Float

    val minAspectRatio: Float

    val parentActivityName: String?

    val permission: String?

    /**
     * Gets the trusted host certificates of apps that are allowed to embed this activity.
     */
    val knownActivityEmbeddingCerts: Set<String?>

    val persistableMode: Int

    val privateFlags: Int

    val requestedVrComponent: String?

    val rotationAnimation: Int

    val resizeMode: Int

    val screenOrientation: Int

    val softInputMode: Int

    val targetActivity: String?

    val taskAffinity: String?

    val theme: Int

    val uiOptions: Int

    val windowLayout: WindowLayout?

    val isSupportsSizeChanges: Boolean

    val requiredDisplayCategory: String?

    /** Gets the permissions necessary for launching the activity when using content URIs.  */
    val requireContentUriPermissionFromCaller: Int
}