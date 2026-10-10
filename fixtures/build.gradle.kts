// Shared configuration for every fixture app. Fixtures are deliberately tiny, dependency-free
// (framework classes only, Java, no AndroidX) apps that each isolate one loader capability.
// They keep the default resource package id 0x7f, which is exactly what must not collide with
// the host's 0x8f.
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileSdk = 34
            defaultConfig {
                minSdk = 30
                targetSdk = 34
                versionCode = 1
                versionName = "1.0"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            sourceSets.getByName("main").java.srcDir(rootProject.file("fixtures/common/java"))
            buildTypes.getByName("release").isMinifyEnabled = false
        }
    }
}
