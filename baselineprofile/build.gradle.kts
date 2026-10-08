plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

// Records the app's Baseline Profile on the phone: the screens a rep uses, walked by UiAutomator, and
// the classes and methods they run, written down so that ART compiles them before the first launch
// instead of interpreting them while the user waits. The result is app/src/main/baselineProfiles/
// baseline-prof.txt; R8 rewrites it for the minified build.
//
// Never through Gradle's connected tasks (connected…AndroidTest, generateBaselineProfile): they
// uninstall the app when they finish, and the phone's data with it. Build both APKs, install them with
// `adb install -r`, and run the generator with `am instrument` — see BaselineProfileGenerator.
android {
    namespace = "com.distrigo.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Recording a profile without root needs Android 13; the generator says so if the phone is older.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Matches the app's build type of the same name: the app without R8, so the profile names
        // classes and methods as they are in the source.
        create("nonMinifiedBenchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    // The app under test is not debuggable, so the generator cannot instrument it: it instruments
    // itself and drives the app from outside, as a user would.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Only the build type a profile is recorded on: the debug app is debuggable, and records nothing.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "nonMinifiedBenchmark" }
}
