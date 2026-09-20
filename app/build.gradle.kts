
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.distrigo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.distrigo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    // MigrationTestHelper builds a database at any exported version from these JSON files, so the
    // migration tests read the same schemas the migrations were copied from.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Room schema export. Until now every version bump relied on fallbackToDestructiveMigration(),
// so no schema was ever recorded and the on-device v32 DDL is not recoverable from this repo.
// Exporting from 33 onward is what makes the generated CREATE TABLE available to copy verbatim
// into a Migration, and every future migration verifiable.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    // Gson — the drafts' items_json and the wilaya list. It used to arrive through Retrofit's
    // converter-gson; declared on its own, at the version that dependency resolved, now that the
    // unused Retrofit client is gone.
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    // Coroutines — للعمليات غير المتزامنة
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    // Virtual time for the flows tested off the device: DraftAutosave's debounce is measured, not waited out.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.maps.android:maps-compose:6.5.0")
    // Barcode scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    // Transitively pulled in by androidx.compose.ui:ui-graphics (still on 1.0.1 as of Compose BOM 2026.06.01) — force the 16KB-aligned release
    implementation("androidx.graphics:graphics-path:1.1.0")
    // WorkManager — automatic backups run as a periodic job, with workers built by Hilt.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    androidTestImplementation(libs.androidx.work.testing)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    // Paging — pagination Room + Compose (historique complet paginé)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    // QR code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.compose.foundation:foundation")

    // Navigation Compose — POC step, Dashboard tab only
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt — dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}