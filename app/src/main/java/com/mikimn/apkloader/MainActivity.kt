package com.mikimn.apkloader

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun MainLayout(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            val intent = DCLActivity.intentForAPK("calculator.apk")
            context.startActivity(intent)
        }) {
            Text(
                text = "Load Calculator APK"
            )
        }
        Button(onClick = {
            context.startActivity(DCLActivity.intentForAPK("flappy-bird-1-3.apk"))
        }) {
            Text(
                text = "Load Flappy APK"
            )
        }
        Button(onClick = {
            context.startActivity(DCLActivity.intentForAPK("simple.apk"))
        }) {
            Text(
                text = "Load Simple APK"
            )
        }

        LazyVerticalGrid(
            modifier = Modifier,
            contentPadding = PaddingValues(8.dp),
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                packageManager
                    .getInstalledApplications(0)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .filter { it.name != null && it.name.contains("duck") }) { aInfo ->
                Box(
                    modifier = Modifier.padding(0.dp)
                ) {
                    Card(
                        modifier = Modifier,
                        onClick = {
                            context.startActivity(DCLActivity.intentForAPK(aInfo.publicSourceDir))
                        }) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                        ) {
                            Text(
                                aInfo.loadLabel(packageManager).toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(aInfo.packageName)
                        }
                    }
                }
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