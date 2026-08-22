package com.mikimn.apkloader

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mikimn.apkloader.dcl.DCLActivity
import com.mikimn.apkloader.dcl.DCLContext
import com.mikimn.apkloader.dcl.FileTrackingClassLoader
import com.mikimn.apkloader.ui.theme.APKLoaderTheme


data class APKEntryPoints(
    val packageName: String,
    val mainActivityClassName: String,
    val applicationClassName: String?
)


val ENTRY_POINTS = mapOf(
    // Single-activity apk
    "calculator.apk" to APKEntryPoints(
        "com.android.calculator2",
        "com.android.calculator2.Calculator",
        "com.android.calculator2.CalculatorApplication"
    ),
    // Simple debug compiled apk
    "simple.apk" to APKEntryPoints(
        "com.mikimn.simpleapp",
        "com.mikimn.simpleapp.MainActivity",
        null
    ),
    // Simple next activity hop
    "flappy-bird-1-3.apk" to APKEntryPoints(
        "com.dotgears.flappy",
        "com.dotgears.flappy.SplashScreen",
        null
    )
)

private val TEST_APK_DISPLAY_NAMES = mapOf(
    "calculator.apk" to "Calculator",
    "simple.apk" to "Simple App",
    "flappy-bird-1-3.apk" to "Flappy Bird"
)


class MainActivity : ComponentActivity() {
    companion object {
        private val ACTIVITY_WHITELIST = arrayOf(
            "com.dotgears.GameActivity",
            "com.dotgears.flappy.SplashScreen",
            "com.mikimn.simpleapp.MainActivity",
            "com.android.calculator2.Calculator"
        )
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DCLContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            APKLoaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainLayout(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (classLoader is FileTrackingClassLoader) {
            val loader = classLoader as FileTrackingClassLoader
            loader.clearLast()
        }
    }

    override fun startActivity(intent: Intent) {
        if (intent.component?.className in ACTIVITY_WHITELIST) {
            return super.startActivity(DCLActivity.forActivityClass(intent, intent.component?.className ?: ""))
        }

        super.startActivity(intent)
    }
}

/** A single tile in the main screen's grid: either a bundled sample APK or a real installed app. */
private sealed interface LoaderTile {
    val title: String
    val subtitle: String
    val isTestApk: Boolean
    val icon: Drawable?
    val intent: Intent
}

private class TestApkTile(assetName: String, packageManager: PackageManager) : LoaderTile {
    override val title = TEST_APK_DISPLAY_NAMES[assetName] ?: assetName
    override val subtitle = assetName
    override val isTestApk = true
    override val icon: Drawable? = packageManager.defaultActivityIcon
    override val intent: Intent by lazy { DCLActivity.intentForAPK(assetName) }
}

private class InstalledAppTile(info: ApplicationInfo, packageManager: PackageManager) : LoaderTile {
    override val title: String = info.loadLabel(packageManager).toString()
    override val subtitle: String = info.packageName
    override val isTestApk = false
    override val icon: Drawable? = runCatching { info.loadIcon(packageManager) }.getOrNull()

    // intentForAPK() only understands "base.apk"-suffixed or "/system/"-prefixed device paths;
    // some OEM-partitioned apps' publicSourceDir doesn't match that (e.g.
    // /data/app/BackupAndRestore/BackupAndRestore.apk), so this must stay lazy - building the
    // tile list shouldn't fail just because one of ~100 installed apps has an unusual path.
    override val intent: Intent by lazy { DCLActivity.intentForAPK(info.publicSourceDir) }
}

@Composable
fun MainLayout(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val tiles = remember {
        val testApkTiles = ENTRY_POINTS.keys.map { TestApkTile(it, packageManager) }
        val installedAppTiles = packageManager
            .getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .map { InstalledAppTile(it, packageManager) }
        testApkTiles + installedAppTiles
    }

    LazyVerticalGrid(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tiles, key = { it.subtitle }) { tile ->
            LoaderTileCard(tile, onClick = {
                try {
                    context.startActivity(tile.intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to launch ${tile.subtitle}", e)
                    Toast.makeText(context, "Can't load ${tile.title}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }
}

@Composable
private fun LoaderTileCard(tile: LoaderTile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        colors = CardDefaults.cardColors(
            containerColor = if (tile.isTestApk) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AndroidView(
                factory = { ctx -> ImageView(ctx) },
                update = { it.setImageDrawable(tile.icon) },
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tile.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (tile.isTestApk) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "TEST APK",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainLayoutPreview() {
    APKLoaderTheme {
        MainLayout()
    }
}
