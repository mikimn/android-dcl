package com.mikimn.apkloader

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.mikimn.apkloader.loader.ApkLoader
import com.mikimn.apkloader.ui.theme.APKLoaderTheme
import dalvik.system.BaseDexClassLoader
import java.io.File


class MainActivity : ComponentActivity() {
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
    }
}

fun loadAttempt(context: Context, loader: ApkLoader) {
    val mainActivity = loader.loadClass("com.android.calculator2.Calculator")
    val tempApkFile = loader.tempPath
    val baseClassLoader = context.classLoader
    if (baseClassLoader is BaseDexClassLoader) {
        val addDexPath =
            baseClassLoader.javaClass.superclass.getDeclaredMethod("addDexPath", String::class.java)
        addDexPath.isAccessible = true
        addDexPath.invoke(baseClassLoader, tempApkFile)

        val pfd = ParcelFileDescriptor.open(File(tempApkFile), MODE_READ_ONLY)
        val rp = ResourcesProvider.loadFromApk(pfd)

        val rl = ResourcesLoader()
        rl.addProvider(rp)

        val baseContext = (context as Activity).baseContext
        val fMainThread = baseContext.javaClass.getDeclaredField("mMainThread")
        fMainThread.isAccessible = true
        val mMainThread = fMainThread.get(baseContext)

        val fGetInstr = mMainThread.javaClass.getDeclaredMethod("getInstrumentation")
        val instrumentation = fGetInstr.invoke(mMainThread) as Instrumentation

        context.applicationContext.resources.addLoaders(rl)

        try {
            // val newApp = Instrumentation.newApplication(loader.loadClass("com.android.calculator2.CalculatorApplication"), context.applicationContext)
            val intent = Intent(context, mainActivity).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                        `package` = "com.google.android.calculator"
//                        component = ComponentName(context.packageName, "com.android.calculator2.Calculator")
            }
            // val activity = instrumentation.newActivity(baseClassLoader, "com.android.calculator2.Calculator", intent)
            // context.applicationContext.startActivity(intent)
        } catch (ex: ActivityNotFoundException) {
            Log.e("MainActivity", "Success", ex)
        }
    }
}


@Composable
fun MainLayout(modifier: Modifier = Modifier ) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            context.startActivity(DCLActivity.forApkAndActivityClass(context, "calculator.apk", "com.android.calculator2.Calculator"))
        }) {
            Text(
                text = "Load Calculator APK"
            )
        }
        Button(onClick = {
            context.startActivity(DCLActivity.forApkAndActivityClass(context, "simple.apk", "com.mikimn.simpleapp.MainActivity"))
        }) {
            Text(
                text = "Load Simple APK"
            )
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