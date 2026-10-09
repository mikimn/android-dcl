plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mikimn.apkloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mikimn.apkloader"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each instrumented test gets a fresh process (via Android Test Orchestrator): the loader
        // keeps process-global state (DCLContext statics, patched ActivityThread fields, the ATM
        // hook), so tests must not observe each other's loaded APKs.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    androidResources {
        additionalParameters.add("--package-id")
        additionalParameters.add("0x8f")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.truth)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.dexlib2)
    implementation(libs.axml)
    implementation(libs.lspass)

    // Should fix `Module with the Main dispatcher is missing. Add dependency providing the Main dispatcher, e.g. 'kotlinx-coroutines-android ...`
//     implementation(libs.coroutines)
//     implementation(libs.coroutinesAndroid)
}
// ---- Fixture APKs -> androidTest assets ------------------------------------------------------
// Each fixture module is built as a normal debug APK and copied to assets/fixtures/<name>.apk of
// the androidTest source set, where FixtureApks picks it up.
val fixtureNames = listOf("fx-hello", "fx-resources", "fx-application")
val fixtureAssetsDir = layout.buildDirectory.dir("generated/fixtures")

val syncFixtureApks = tasks.register<Sync>("syncFixtureApks") {
    into(fixtureAssetsDir.map { it.dir("fixtures") })
    fixtureNames.forEach { name ->
        val fixture = project(":fixtures:$name")
        dependsOn(fixture.tasks.matching { it.name == "assembleDebug" })
        from(fixture.layout.buildDirectory.file("outputs/apk/debug/$name-debug.apk")) {
            rename { "$name.apk" }
        }
    }
}

android.sourceSets.getByName("androidTest").assets.srcDir(fixtureAssetsDir)
tasks.matching { it.name.endsWith("AndroidTestAssets") }.configureEach { dependsOn(syncFixtureApks) }
