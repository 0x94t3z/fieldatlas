plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// One maintained question file; the APK copy is generated, never hand-edited.
val generatedBenchmarkAssets = layout.buildDirectory.dir("generated/benchmarkAssets")
val syncBenchmarkAssets by tasks.registering(Sync::class) {
    from(rootProject.file("benchmarks/questions.json"))
    into(generatedBenchmarkAssets.map { it.dir("benchmark") })
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
    sourceSets.getByName("main").assets.srcDir(generatedBenchmarkAssets)
    namespace = "xyz.fieldatlas"
    compileSdk = 36
    // Allow testing the signed release on a phone without installing a second debug app.
    testBuildType = providers.gradleProperty("fieldatlasTestBuildType").getOrElse("debug")

    defaultConfig {
        applicationId = "xyz.fieldatlas"
        minSdk = 33
        targetSdk = 36
        versionCode = 15
        versionName = "1.3.0"
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
            // Instrumentation directly calls app APIs that R8 may otherwise inline/remove.
            // Normal release builds remain fully optimized.
            isMinifyEnabled = testBuildType != "release"
            isShrinkResources = testBuildType != "release"
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

tasks.named("preBuild").configure { dependsOn(syncBenchmarkAssets) }

// Opt-in host runner uses the same SQLite version, with desktop JNI instead of Android JNI.
// Nothing from this configuration is included in the APK or ordinary unit tests.
val desktopSqlite by configurations.creating
dependencies {
    desktopSqlite("androidx.sqlite:sqlite-jvm:${libs.versions.sqlite.get()}") { isTransitive = false }
    desktopSqlite("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}") { isTransitive = false }
}
tasks.withType<Test>().configureEach {
    if (!System.getenv("FIELDATLAS_DESKTOP_CONFIG").isNullOrBlank()) {
        doFirst {
            classpath = desktopSqlite + classpath.filter {
                !it.name.startsWith("sqlite-") && it.name != "sqlite.jar"
            }
        }
        outputs.upToDateWhen { false }
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
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // 0.3.47 ships a 4-KB-aligned libvosk.so; verify the final APK's ELF segments in CI.
    implementation("com.alphacephei:vosk-android:0.3.75")
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
