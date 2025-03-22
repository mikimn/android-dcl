package com.mikimn.apkloader

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mikimn.apkloader.loader.ApkLoader
import com.mikimn.apkloader.loader.ApkLoaderImpl
import com.mikimn.apkloader.ui.theme.APKLoaderTheme
import dalvik.system.BaseDexClassLoader
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DCLActivity : ComponentActivity() {
    private lateinit var reader: AssetReader
    private lateinit var loader: ApkLoader
    private lateinit var mainActivity: Class<*>

    private lateinit var shadowActivity: Activity

    companion object {
        private const val KEY_ACTIVITY_CLASS = "activityClassName"
        private const val KEY_APK_ASSET_FILE_NAME = "apkAssetFileName"

        fun forApkAndActivityClass(context: Context, apkFileName: String, activityClassName: String): Intent {
            return Intent(context, DCLActivity::class.java).apply {
                putExtra(KEY_APK_ASSET_FILE_NAME, apkFileName)
                putExtra(KEY_ACTIVITY_CLASS, activityClassName)
            }
        }
    }

    object FieldMapper {
        fun <T : Any> copy(to: T, from: T) {
            try {
                val fromFields = getAllFields(from.javaClass)
                val toFields = getAllFields(to.javaClass)

                fromFields.let {
                    for (field in fromFields) {
                        val matchingField = toFields.firstOrNull { it.name.split(".").last() == field.name.split(".").last() && it.type == field.type }
                        matchingField?.let {
                            try {
                                field.isAccessible = true
                                matchingField.isAccessible = true
                                matchingField.set(to, field.get(from))
                            } catch (e: IllegalAccessException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        private fun getAllFields(paramClass: Class<*>): List<Field> {

            var theClass: Class<*>? = paramClass
            val fields = ArrayList<Field>()
            try {
                while (theClass != null) {
                    theClass.declaredFields.let { Collections.addAll(fields, *it) }
                    theClass.fields.let { Collections.addAll(fields, *it) }
                    theClass = theClass.superclass
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return fields
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        val apkFileName = intent.extras?.getString(KEY_APK_ASSET_FILE_NAME)
        val activityClassName = intent.extras?.getString(KEY_ACTIVITY_CLASS)

        if (apkFileName == null || activityClassName == null) {
            throw IllegalArgumentException("DCLActivity must be initialized with forApkAndActivityClass only")
        }

        reader = AssetReader(this)
        loader = ApkLoaderImpl(reader.readFile(apkFileName), classLoader)

        mainActivity = loader.loadClass(activityClassName)

        val tempApkFile = loader.tempPath
        val pfd = ParcelFileDescriptor.open(File(tempApkFile), MODE_READ_ONLY)
        val rp = ResourcesProvider.loadFromApk(pfd)

        val rl = ResourcesLoader()
        rl.addProvider(rp)

        resources.addLoaders(rl)
        baseContext.resources.addLoaders(rl)
        application.resources.addLoaders(rl)

        try {
            shadowActivity = mainActivity.newInstance() as Activity


            val base = baseContext
            val fClassLoader = base.javaClass.tryGetField("mClassLoader")
            fClassLoader?.isAccessible = true
            fClassLoader?.set(base, loader.loader)

            if (mainActivity.simpleName.contains("Calculator")) {
                val appClass = loader.loadClass("com.android.calculator2.CalculatorApplication")
                val shadowApp: Application = appClass.newInstance() as Application

                FieldMapper.copy(shadowApp, application)

                val appOnCreate = shadowApp.javaClass.tryGetMethod("onCreate")
                appOnCreate?.isAccessible = true
                appOnCreate?.invoke(shadowApp)

                val fPackageInfo = baseContext.javaClass.tryGetField("mPackageInfo")
                fPackageInfo?.isAccessible = true
                val mPackageInfo = fPackageInfo?.get(baseContext)
                val fPackageApplication = mPackageInfo?.javaClass?.tryGetField("mApplication")
                fPackageApplication?.isAccessible = true
                fPackageApplication?.set(mPackageInfo, shadowApp)

                val fMainThread = baseContext.javaClass.tryGetField("mMainThread")
                fMainThread?.isAccessible = true
                val mMainThread = fMainThread?.get(baseContext)
                val fInitialApp = mMainThread?.javaClass?.tryGetField("mInitialApplication")
                fInitialApp?.isAccessible = true
                fInitialApp?.set(mMainThread, shadowApp)

                // final void android.app.Activity.attach(
                //      android.content.Context,
                //      android.app.ActivityThread,
                //      android.app.Instrumentation,
                //      android.os.IBinder,
                //      int,
                //      android.app.Application,
                //      android.content.Intent,
                //      android.content.pm.ActivityInfo,
                //      java.lang.CharSequence,
                //      android.app.Activity,
                //      java.lang.String,
                //      android.app.Activity$NonConfigurationInstances,
                //      android.content.res.Configuration,
                //      java.lang.String,
                //      com.android.internal.app.IVoiceInteractor,
                //      android.view.Window,
                //      android.view.ViewRootImpl$ActivityConfigCallback,
                //      android.os.IBinder)
                val mAttach = this.mainActivity.superclass.superclass.superclass.superclass.superclass.superclass.declaredMethods.find { it.name.endsWith("attach") }
                mAttach?.isAccessible = true
                mAttach?.invoke(
                    shadowActivity,
                    baseContext,
                    this.tryGetValue("mMainThread"),
                    this.tryGetValue("mInstrumentation"),
                    this.tryGetValue("mToken"),
                    this.tryGetValue("mIdent"),
                    shadowApp,
                    Intent(baseContext, mainActivity),
                    ActivityInfo(),
                    "Calculator",
                    parent,
                    this.tryGetValue("mEmbeddedID"),
                    this.tryGetValue("mLastNonConfigurationInstances"),
                    this.tryGetValue("mCurrentConfig"),
                    this.tryGetValue("mReferrer"),
                    null,
                    window,
                    /* activityConfigCallback */ null,
                    this.tryGetValue("mAssistToken")
                )
                shadowActivity.setTheme(0x7f15036e) // Quickfix AppCompat theme

                val instrumentation: Instrumentation = shadowActivity.tryGetValue("mInstrumentation")!!
                instrumentation.callActivityOnCreate(shadowActivity, savedInstanceState)
                FieldMapper.copy(this, shadowActivity)
            } else {
                FieldMapper.copy(shadowActivity, this)
                tryInvoke("onCreate", arrayOf(Bundle::class.java), arrayOf(savedInstanceState))
            }
            Log.i("DCLActivity", "TEST2")
        } catch (ve: VerifyError) {
            ve.printStackTrace()
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContent {
                APKLoaderTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Column(
                            modifier = Modifier.padding(innerPadding)
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Error Loading: ${ve.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tryInvoke("onStart", emptyArray(), emptyArray())
    }

    override fun onRestart() {
        super.onRestart()
        tryInvoke("onRestart", emptyArray(), emptyArray())
    }

    override fun onResume() {
        super.onResume()
        tryInvoke("onResume", emptyArray(), emptyArray())
    }

    override fun onPause() {
        super.onPause()
        tryInvoke("onPause", emptyArray(), emptyArray())
    }

    override fun onDestroy() {
        // File(loader.tempPath).delete()
        super.onDestroy()
        tryInvoke("onDestroy", emptyArray(), emptyArray())
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        tryInvoke("onPostCreate", arrayOf(Bundle::class.java), arrayOf(savedInstanceState))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        tryInvoke("onSaveInstanceState", arrayOf(Bundle::class.java), arrayOf(outState))
    }

    private fun tryInvoke(methodName: String, parameterTypes: Array<out Class<*>>, parameters: Array<Any?>) {
        try {
            val refMethod = mainActivity.tryGetMethod(methodName, *parameterTypes)
            refMethod?.let {
                refMethod.isAccessible = true
                Log.i("DCLActivity", "$methodName(${parameters.joinToString(", ")})")
                refMethod.invoke(shadowActivity, *parameters)
            }

            if (refMethod == null) {
                Log.i("DCLActivity", "$methodName = null")
            }
        } catch (_: Exception) {

        }
        FieldMapper.copy(this, shadowActivity)
    }
}


fun <T> Class<T>.tryGetMethod(name: String, vararg parameterTypes: Class<*>): Method? {
    return try {
        getDeclaredMethod(name, *parameterTypes)
    } catch (ex: NoSuchMethodException) {
        try {
            getMethod(name, *parameterTypes)
        } catch (ex: NoSuchMethodException) {
            superclass?.let {
                superclass.tryGetMethod(name, *parameterTypes)
            }
        }
    }
}

fun <T> Class<T>.tryGetField(name: String): Field? {
    return try {
        getDeclaredField(name)
    } catch (ex: NoSuchFieldException) {
        try {
            getField(name)
        } catch (ex: NoSuchFieldException) {
            superclass?.let {
                superclass.tryGetField(name)
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Any.tryGetValue(fieldName: String): T? {
    val field = javaClass.tryGetField(fieldName)
    field?.isAccessible = true
    return field?.get(this) as T?
}



/**
 * Extract a zip file into any directory
 *
 * @param zipFile src zip file
 * @param extractTo directory to extract into.
 * There will be new folder with the zip's name inside [extractTo] directory.
 * @param extractHere no extra folder will be created and will be extracted
 * directly inside [extractTo] folder.
 *
 * @return the extracted directory i.e, [extractTo] folder if [extractHere] is `true`
 * and [extractTo]\zipFile\ folder otherwise.
 */
private fun unzip(
    zipFile: ZipInputStream,
    outputDir: File
): File? {
    return try {
        val buffer = ByteArray(2048)

        zipFile.closeEntry()
        var zipEntry: ZipEntry? = zipFile.nextEntry
        while (zipEntry != null) {
            val entry = zipEntry
            if (entry.isDirectory) {
                val d = File(outputDir, entry.name)
                if (!d.exists()) d.mkdirs()
            } else {
                val f = File(outputDir, entry.name)
                if (f.parentFile?.exists() != true)  f.parentFile?.mkdirs()

                f.outputStream().use { output ->
                    var len: Int
                    while ((zipFile.read(buffer).also { len = it }) > 0) {
                        output.write(buffer, 0, len)
                    }
                }
            }

            zipFile.closeEntry()
            zipEntry = zipFile.nextEntry
        }

        outputDir
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun BaseDexClassLoader.addDexPathEx(path: String) {
    val addDexPath = javaClass.superclass.getDeclaredMethod(
        "addDexPath",
        String::class.java
    )
    addDexPath.isAccessible = true
    addDexPath.invoke(this, path)
}
