plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseSigningInputs = mapOf(
    "FIELDATLAS_KEYSTORE_PATH" to providers.environmentVariable("FIELDATLAS_KEYSTORE_PATH").orNull,
    "FIELDATLAS_STORE_PASSWORD" to providers.environmentVariable("FIELDATLAS_STORE_PASSWORD").orNull,
    "FIELDATLAS_KEY_ALIAS" to providers.environmentVariable("FIELDATLAS_KEY_ALIAS").orNull,
    "FIELDATLAS_KEY_PASSWORD" to providers.environmentVariable("FIELDATLAS_KEY_PASSWORD").orNull,
)
val hasAnyReleaseSigningInput = releaseSigningInputs.values.any { !it.isNullOrBlank() }
val hasAllReleaseSigningInputs = releaseSigningInputs.values.all { !it.isNullOrBlank() }
if (hasAnyReleaseSigningInput && !hasAllReleaseSigningInputs) {
    throw GradleException(
        "Incomplete Field Atlas release signing configuration; provide all four FIELDATLAS signing variables.",
    )
}

android {
    namespace = "xyz.fieldatlas"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.fieldatlas"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (hasAllReleaseSigningInputs) {
            create("ownerRelease") {
                storeFile = file(releaseSigningInputs.getValue("FIELDATLAS_KEYSTORE_PATH")!!)
                storePassword = releaseSigningInputs.getValue("FIELDATLAS_STORE_PASSWORD")
                keyAlias = releaseSigningInputs.getValue("FIELDATLAS_KEY_ALIAS")
                keyPassword = releaseSigningInputs.getValue("FIELDATLAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("ownerRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":llama"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
