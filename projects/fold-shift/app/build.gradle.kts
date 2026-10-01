// Module-level build script for FoldShift's :app.
// Phase 1 skeleton: Compose + Material3 + DataStore. Keeps the signing
// config minimal so the fishking keystore is used by `assembleRelease`.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fishking.foldshift"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fishking.foldshift"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers
                .gradleProperty("FOLDSHIFT_STORE_FILE")
                .orElse("fishking-release.jks")
            storeFile = file(storeFilePath)
            // `.orElse(...)` followed by `.get()` so debug builds evaluate
            // this block even when no gradle.properties is present (the
            // defaults are empty, but release validation will fail loudly
            // at the package step if any value is missing or empty).
            storePassword = providers
                .gradleProperty("FOLDSHIFT_STORE_PASSWORD")
                .orElse("")
                .get()
            keyAlias = providers
                .gradleProperty("FOLDSHIFT_KEY_ALIAS")
                .orElse("fishking")
                .get()
            keyPassword = providers
                .gradleProperty("FOLDSHIFT_KEY_PASSWORD")
                .orElse("")
                .get()
        }
    }

    buildTypes {
        getByName("debug") {
            // Use debug signing for day-to-day installs.
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Compose compiler matching Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Required so we can inherit a Material Components theme for the
    // Activity at compileSdk 35. Compose still owns the UI; this is only
    // for the AppCompat-style parent.
    implementation("com.google.android.material:material:1.12.0")

    // Shizuku API client. Stellar in Shizuku compatibility mode exposes
    // the same AIDL surface, so the dependency is the right one to use
    // regardless of which Shizuku provider is installed.
    implementation("dev.rikka.shizuku:api:13.1.5")
    // Provides `rikka.shizuku.ShizukuProvider` which the Manifest
    // declaration needs to resolve at install time.
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}