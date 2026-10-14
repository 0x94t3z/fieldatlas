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

// Built from vendored pinned source, never downloaded as an opaque prebuilt .so.
val generatedLeviathanJni = layout.buildDirectory.dir("generated/leviathanJni")
val generatedLeviathanAssets = layout.buildDirectory.dir("generated/leviathanAssets")
val buildLeviathanNative by tasks.registering(Exec::class) {
    environment("CARGO_NET_OFFLINE", gradle.startParameter.isOffline.toString())
    inputs.dir(rootProject.file("native/leviathan"))
    inputs.dir(rootProject.file("third_party/leviathan/src"))
    inputs.file(rootProject.file("third_party/leviathan/Cargo.toml"))
    inputs.file(rootProject.file("tools/android/build_leviathan_native.py"))
    outputs.dir(generatedLeviathanJni)
    outputs.dir(generatedLeviathanAssets)
    commandLine("python3", rootProject.file("tools/android/build_leviathan_native.py").absolutePath,
        "--ndk", android.ndkDirectory.absolutePath,
        "--output", generatedLeviathanJni.get().asFile.absolutePath,
        "--assets", generatedLeviathanAssets.get().asFile.absolutePath)
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
    ndkVersion = "29.0.13113456"
    sourceSets.getByName("main").jniLibs.srcDir(generatedLeviathanJni)
    sourceSets.getByName("main").assets.srcDir(generatedLeviathanAssets)
    sourceSets.getByName("main").assets.srcDir(generatedBenchmarkAssets)
    namespace = "xyz.fieldatlas"
    compileSdk = 36
    // Allow testing the signed release on a phone without installing a second debug app.
    testBuildType = providers.gradleProperty("fieldatlasTestBuildType").getOrElse("debug")

    defaultConfig {
        applicationId = "xyz.fieldatlas"
        minSdk = 33
        targetSdk = 36
        versionCode = 20
        versionName = "1.4.1"
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

tasks.named("preBuild").configure { dependsOn(syncBenchmarkAssets, buildLeviathanNative) }

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
    // Embedded PDF text on every supported Android version (the platform API needs Android 15).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
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
    // UI instrumentation needs its host activity, but it must not ship in an ordinary release.
    if (android.testBuildType == "release") {
        add("releaseImplementation", libs.androidx.compose.ui.test.manifest)
    }
}
