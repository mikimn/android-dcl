package com.mikimn.apkloader.pkg

import android.content.ComponentName
import android.os.Bundle


/** @hide
 */
//@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
interface ParsedComponent {
    val banner: Int

    val componentName: ComponentName

    val descriptionRes: Int

    val flags: Int

    val icon: Int

    val intents: List<Any?>

    val labelRes: Int

    val logo: Int

    val metaData: Bundle

    val name: String

    val nonLocalizedLabel: CharSequence?

    val packageName: String

    val properties: Map<String?, Any?>
}
