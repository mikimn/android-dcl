package com.mikimn.apkloader.pkg


interface ParsedMainComponent : ParsedComponent {
    val attributionTags: Array<String?>

    /**
     * A main component's name is a class name. This makes code slightly more readable.
     */
    val className: String

    val isDirectBootAware: Boolean

    val isEnabled: Boolean

    val isExported: Boolean

    val order: Int

    val processName: String?

    val splitName: String?

    /**
     * Returns the intent matching flags.
     */
    val intentMatchingFlags: Int
}