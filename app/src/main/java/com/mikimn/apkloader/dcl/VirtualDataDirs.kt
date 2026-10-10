package com.mikimn.apkloader.dcl

import java.io.File

/**
 * Where a loaded APK's private storage lives. Every loaded app shares the host's uid and data
 * directory, so without this two loaded apps (or a loaded app and the host) write into the same
 * `files/`, `databases/` and `cache/` and corrupt each other's state.
 *
 * Layout: `<hostDataDir>/virtual/<package>/{files,cache,code_cache,no_backup,databases,app_<name>}`.
 * Pure path logic; [DCLContext] applies it.
 */
class VirtualDataDirs(hostDataDir: File, val packageName: String) {
    val dataDir = File(File(hostDataDir, "virtual"), packageName)
    val files = File(dataDir, "files")
    val cache = File(dataDir, "cache")
    val codeCache = File(dataDir, "code_cache")
    val noBackup = File(dataDir, "no_backup")
    val databases = File(dataDir, "databases")

    /** `getDir(name)`: `app_<name>`, like the platform. */
    fun dir(name: String) = File(dataDir, "app_$name")

    /** A plain file name under [files]; like the platform, path separators are not allowed. */
    fun file(name: String): File = File(files, flatName(name))

    /**
     * A database file. An absolute path is honored as-is (the platform does the same), which is
     * also how callers hand back a path they got from `getDatabasePath`.
     */
    fun database(name: String): File =
        if (name.startsWith(File.separator)) File(name) else File(databases, flatName(name))

    /**
     * SharedPreferences can't be redirected by path through the public Context API, so a loaded
     * app's preference files are isolated by name instead (they stay in the host's `shared_prefs/`).
     */
    fun prefsName(name: String?): String = "virtual.$packageName.${name ?: "null"}"

    /** An external-storage directory under [externalBase], e.g. the host's `getExternalFilesDir`. */
    fun external(externalBase: File, type: String? = null): File {
        val root = File(File(externalBase, "virtual"), packageName)
        return if (type.isNullOrEmpty()) root else File(root, type)
    }

    private fun flatName(name: String): String {
        require(name.indexOf(File.separatorChar) < 0) { "File $name contains a path separator" }
        return name
    }
}
