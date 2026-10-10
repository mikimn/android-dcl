package com.mikimn.apkloader.dcl

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class VirtualDataDirsTest {
    private val host = File("/data/data/host")
    private val dirs = VirtualDataDirs(host, "com.example.app")

    @Test fun layoutIsUnderVirtualPackage() {
        val root = File("/data/data/host/virtual/com.example.app")
        assertThat(dirs.dataDir).isEqualTo(root)
        assertThat(dirs.files).isEqualTo(File(root, "files"))
        assertThat(dirs.cache).isEqualTo(File(root, "cache"))
        assertThat(dirs.codeCache).isEqualTo(File(root, "code_cache"))
        assertThat(dirs.noBackup).isEqualTo(File(root, "no_backup"))
        assertThat(dirs.databases).isEqualTo(File(root, "databases"))
        assertThat(dirs.dir("stuff")).isEqualTo(File(root, "app_stuff"))
    }

    @Test fun differentPackagesNeverShareADirectory() {
        val other = VirtualDataDirs(host, "com.example.other")
        assertThat(other.dataDir).isNotEqualTo(dirs.dataDir)
        assertThat(other.files.path).doesNotContain("com.example.app")
    }

    @Test fun fileNamesAreFlatLikeThePlatform() {
        assertThat(dirs.file("a.txt")).isEqualTo(File(dirs.files, "a.txt"))
        assertThrows(IllegalArgumentException::class.java) { dirs.file("../escape") }
        assertThrows(IllegalArgumentException::class.java) { dirs.file("sub/dir") }
    }

    @Test fun databaseNamesAreFlatButAbsolutePathsAreHonored() {
        assertThat(dirs.database("x.db")).isEqualTo(File(dirs.databases, "x.db"))
        assertThrows(IllegalArgumentException::class.java) { dirs.database("sub/x.db") }
        val absolute = File(dirs.databases, "y.db").path
        assertThat(dirs.database(absolute)).isEqualTo(File(absolute))
    }

    @Test fun preferenceNamesArePrefixedPerPackage() {
        assertThat(dirs.prefsName("settings")).isEqualTo("virtual.com.example.app.settings")
        assertThat(dirs.prefsName(null)).isEqualTo("virtual.com.example.app.null")
        assertThat(VirtualDataDirs(host, "other").prefsName("settings")).isNotEqualTo(dirs.prefsName("settings"))
    }

    @Test fun externalDirsNestUnderVirtualPackageAndType() {
        val ext = File("/sdcard/Android/data/host/files")
        assertThat(dirs.external(ext)).isEqualTo(File(ext, "virtual/com.example.app"))
        assertThat(dirs.external(ext, "Pictures")).isEqualTo(File(ext, "virtual/com.example.app/Pictures"))
        assertThat(dirs.external(ext, "")).isEqualTo(dirs.external(ext))
    }
}
