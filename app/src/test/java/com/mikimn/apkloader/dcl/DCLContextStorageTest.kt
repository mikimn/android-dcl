package com.mikimn.apkloader.dcl

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class DCLContextStorageTest {
    private val host: Context = ApplicationProvider.getApplicationContext()
    private fun forPackage(pkg: String) = DCLContext(host, virtualPackage = pkg)
    private val a = forPackage("pkg.a")
    private val b = forPackage("pkg.b")
    private val plain = DCLContext(host)

    @Test fun contextWithoutAPackageIsUntouched() {
        assertThat(plain.filesDir).isEqualTo(host.filesDir)
        assertThat(plain.cacheDir).isEqualTo(host.cacheDir)
        assertThat(plain.dataDir).isEqualTo(host.dataDir)
        assertThat(plain.applicationInfo.dataDir).isEqualTo(host.applicationInfo.dataDir)
    }

    @Test fun directoriesAreRedirectedPerPackageAndCreated() {
        val root = java.io.File(java.io.File(host.dataDir, "virtual"), "pkg.a")
        assertThat(a.dataDir).isEqualTo(root)
        for (dir in listOf(a.filesDir, a.cacheDir, a.codeCacheDir, a.noBackupFilesDir, a.getDir("x", 0))) {
            assertThat(dir.path).startsWith(root.path)
            assertThat(dir.isDirectory).isTrue()
        }
        assertThat(b.filesDir).isNotEqualTo(a.filesDir)
        assertThat(a.getDir("x", 0).name).isEqualTo("app_x")
    }

    @Test fun fileApisReadAndWriteTheVirtualFilesDir() {
        a.openFileOutput("note.txt", Context.MODE_PRIVATE).use { it.write("hi".toByteArray()) }
        assertThat(java.io.File(a.filesDir, "note.txt").readText()).isEqualTo("hi")
        assertThat(a.getFileStreamPath("note.txt")).isEqualTo(java.io.File(a.filesDir, "note.txt"))
        assertThat(a.openFileInput("note.txt").use { it.readBytes().decodeToString() }).isEqualTo("hi")
        assertThat(a.fileList().toList()).contains("note.txt")

        // Not visible to another package, nor in the host's own files dir.
        assertThat(b.fileList().toList()).doesNotContain("note.txt")
        assertThat(java.io.File(host.filesDir, "note.txt").exists()).isFalse()

        assertThat(a.deleteFile("note.txt")).isTrue()
        assertThat(a.fileList().toList()).doesNotContain("note.txt")
    }

    @Test fun openFileOutputAppends() {
        a.openFileOutput("log", Context.MODE_PRIVATE).use { it.write("1".toByteArray()) }
        a.openFileOutput("log", Context.MODE_APPEND).use { it.write("2".toByteArray()) }
        assertThat(java.io.File(a.filesDir, "log").readText()).isEqualTo("12")
    }

    @Test fun fileNamesWithPathSeparatorsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { a.openFileOutput("../x", 0) }
    }

    @Test fun databasesLiveInTheVirtualDatabasesDirAndAreIsolated() {
        a.openOrCreateDatabase("x.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("create table t(v text)")
            db.execSQL("insert into t values ('from-a')")
        }
        assertThat(a.getDatabasePath("x.db")).isEqualTo(java.io.File(a.dataDir, "databases/x.db"))
        assertThat(a.getDatabasePath("x.db").exists()).isTrue()
        assertThat(a.databaseList().toList()).contains("x.db")
        assertThat(b.databaseList().toList()).doesNotContain("x.db")
        assertThat(host.getDatabasePath("x.db").exists()).isFalse()

        // Handing the path back (as SQLiteOpenHelper-style code does) reopens the same database.
        a.openOrCreateDatabase(a.getDatabasePath("x.db").path, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("select v from t", null).use { c -> c.moveToFirst(); assertThat(c.getString(0)).isEqualTo("from-a") }
        }

        assertThat(a.deleteDatabase("x.db")).isTrue()
        assertThat(a.getDatabasePath("x.db").exists()).isFalse()
    }

    @Test fun sharedPreferencesAreIsolatedByPackage() {
        a.getSharedPreferences("settings", 0).edit().putString("k", "a").commit()
        b.getSharedPreferences("settings", 0).edit().putString("k", "b").commit()
        assertThat(a.getSharedPreferences("settings", 0).getString("k", null)).isEqualTo("a")
        assertThat(b.getSharedPreferences("settings", 0).getString("k", null)).isEqualTo("b")
        assertThat(plain.getSharedPreferences("settings", 0).getString("k", null)).isNull()

        assertThat(a.deleteSharedPreferences("settings")).isTrue()
        assertThat(b.getSharedPreferences("settings", 0).getString("k", null)).isEqualTo("b")
    }

    @Test fun externalDirsAreRedirectedUnderTheHostsExternalDirs() {
        val hostExt = host.getExternalFilesDir(null)!!
        assertThat(a.getExternalFilesDir(null)).isEqualTo(java.io.File(hostExt, "virtual/pkg.a"))
        assertThat(a.getExternalFilesDir("Pictures")).isEqualTo(java.io.File(hostExt, "virtual/pkg.a/Pictures"))
        assertThat(a.externalCacheDir!!.path).contains("virtual/pkg.a")
        assertThat(a.getExternalFilesDirs(null).filterNotNull().all { it.path.contains("virtual/pkg.a") }).isTrue()
    }

    @Test fun applicationInfoReportsTheVirtualDataDirWithoutTouchingTheHosts() {
        assertThat(a.applicationInfo.dataDir).isEqualTo(a.dataDir.path)
        assertThat(a.applicationInfo).isSameInstanceAs(a.applicationInfo)
        assertThat(host.applicationInfo.dataDir).isNotEqualTo(a.dataDir.path)
    }

    @Test fun hostileNamesFromLoadedCodeAreRejectedAtTheContext() {
        assertThrows(IllegalArgumentException::class.java) { a.getDir("../../x", 0) }
        assertThrows(IllegalArgumentException::class.java) { a.getExternalFilesDir("../../x") }
        assertThrows(IllegalArgumentException::class.java) { a.getDatabasePath("../x.db") }
        assertThrows(IllegalArgumentException::class.java) { forPackage("../evil").filesDir }
    }
}
