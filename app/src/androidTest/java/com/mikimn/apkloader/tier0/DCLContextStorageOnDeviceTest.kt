package com.mikimn.apkloader.tier0

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mikimn.apkloader.dcl.DCLContext
import com.mikimn.apkloader.testing.LogcatCrashRule
import com.mikimn.apkloader.testing.Tier0
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The redirect leans on real `ContextImpl` behavior (an absolute database path is used as-is,
 * file names are validated, preference files are named by prefix), which Robolectric only
 * approximates, so the same contract is checked on a real device Context.
 */
@Tier0
@RunWith(AndroidJUnit4::class)
class DCLContextStorageOnDeviceTest {
    @get:Rule val crashRule = LogcatCrashRule()
    private val host: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val a = DCLContext(host, virtualPackage = "pkg.a")
    private val b = DCLContext(host, virtualPackage = "pkg.b")
    private val root = File(File(host.dataDir, "virtual"), "pkg.a")

    @Test fun filesCacheAndDirsAreUnderTheVirtualRoot() {
        assertThat(a.dataDir).isEqualTo(root)
        for (dir in listOf(a.filesDir, a.cacheDir, a.codeCacheDir, a.noBackupFilesDir, a.getDir("x", 0))) {
            assertThat(dir.path).startsWith(root.path)
            assertThat(dir.isDirectory).isTrue()
        }
        assertThat(a.applicationInfo.dataDir).isEqualTo(root.path)
        assertThat(host.applicationInfo.dataDir).isNotEqualTo(root.path)
    }

    @Test fun fileApisAreIsolatedFromOtherPackagesAndTheHost() {
        a.openFileOutput("note.txt", Context.MODE_PRIVATE).use { it.write("hi".toByteArray()) }
        assertThat(File(a.filesDir, "note.txt").readText()).isEqualTo("hi")
        assertThat(a.openFileInput("note.txt").use { it.readBytes().decodeToString() }).isEqualTo("hi")
        assertThat(b.fileList().toList()).doesNotContain("note.txt")
        assertThat(File(host.filesDir, "note.txt").exists()).isFalse()
        assertThat(a.deleteFile("note.txt")).isTrue()
    }

    @Test fun databasesWorkThroughBothTheNameAndTheReturnedPath() {
        a.openOrCreateDatabase("x.db", Context.MODE_PRIVATE, null).use {
            it.execSQL("create table t(v text)"); it.execSQL("insert into t values ('a')")
        }
        assertThat(a.getDatabasePath("x.db")).isEqualTo(File(root, "databases/x.db"))
        assertThat(a.databaseList().toList()).contains("x.db")
        assertThat(b.databaseList().toList()).doesNotContain("x.db")
        assertThat(host.getDatabasePath("x.db").exists()).isFalse()
        a.openOrCreateDatabase(a.getDatabasePath("x.db").path, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("select v from t", null).use { c -> c.moveToFirst(); assertThat(c.getString(0)).isEqualTo("a") }
        }
        assertThat(a.deleteDatabase("x.db")).isTrue()
    }

    @Test fun sharedPreferencesAreIsolatedByPackage() {
        a.getSharedPreferences("s", 0).edit().putString("k", "a").commit()
        assertThat(b.getSharedPreferences("s", 0).getString("k", null)).isNull()
        assertThat(host.getSharedPreferences("s", 0).getString("k", null)).isNull()
        assertThat(a.deleteSharedPreferences("s")).isTrue()
    }

    @Test fun externalDirsNestUnderTheHostsExternalDirs() {
        val hostExt = host.getExternalFilesDir(null) ?: return // no external storage on this device
        assertThat(a.getExternalFilesDir(null)).isEqualTo(File(hostExt, "virtual/pkg.a"))
        assertThat(a.externalCacheDir!!.path).contains("virtual/pkg.a")
    }
}
