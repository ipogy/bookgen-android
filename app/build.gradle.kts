import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val localPropsFile = rootProject.layout.projectDirectory.file("local.properties").asFile
val localProps = Properties().apply {
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

val rustWorkspaceDir: File = rootProject.layout.projectDirectory.dir("rust-core").asFile
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi/kotlin")
val jniLibsOutDir = layout.buildDirectory.dir("generated/rustJniLibs")
val isWindows = System.getProperty("os.name").lowercase().contains("win")
val hostSharedLibName = when {
    isWindows -> "bookgen_core.dll"
    System.getProperty("os.name").lowercase().contains("mac") -> "libbookgen_core.dylib"
    else -> "libbookgen_core.so"
}
val cargoExe = if (isWindows) {
    File(System.getProperty("user.home"), ".cargo/bin/cargo.exe").takeIf { it.exists() }?.absolutePath ?: "cargo"
} else {
    "cargo"
}
val sdkDirPath = localProps.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: File(System.getProperty("user.home"), "AppData/Local/Android/Sdk").absolutePath
val pinnedNdkVersion = "28.2.13676358"
val ndkDirPath = System.getenv("ANDROID_NDK_HOME")
    ?: File(sdkDirPath, "ndk/$pinnedNdkVersion").absolutePath

val requestedAbis = (providers.gradleProperty("rustAbis").orNull ?: "arm64-v8a,armeabi-v7a,x86_64")
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }

val buildRustHost by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build bookgen-core host shared library for UniFFI binding generation and JVM tests"
    workingDir = rustWorkspaceDir
    environment.remove("RUSTFLAGS")
    environment.remove("CARGO_ENCODED_RUSTFLAGS")
    commandLine(cargoExe, "build", "--locked", "-p", "bookgen-core")
    inputs.files(
        File(rustWorkspaceDir, "Cargo.toml"),
        File(rustWorkspaceDir, "Cargo.lock"),
        File(rustWorkspaceDir, "bookgen-core/Cargo.toml"),
    )
    inputs.dir(File(rustWorkspaceDir, "bookgen-core/src"))
    inputs.dir(File(rustWorkspaceDir, "bookgen-core/assets"))
    outputs.file(File(rustWorkspaceDir, "target/debug/$hostSharedLibName"))
}

val generateUniffiBindings by tasks.registering(Exec::class) {
    group = "rust"
    description = "Generate Kotlin bindings from bookgen-core via workspace uniffi-bindgen"
    dependsOn(buildRustHost)
    workingDir = rustWorkspaceDir
    environment.remove("RUSTFLAGS")
    environment.remove("CARGO_ENCODED_RUSTFLAGS")
    val hostLib = File(rustWorkspaceDir, "target/debug/$hostSharedLibName")
    val outDir = uniffiOutDir.get().asFile
    val uniffiToml = File(rustWorkspaceDir, "bookgen-core/uniffi.toml")
    commandLine(
        cargoExe,
        "run",
        "--locked",
        "-p",
        "uniffi-bindgen",
        "--",
        "generate",
        "--library",
        hostLib.absolutePath,
        "--language",
        "kotlin",
        "--no-format",
        "--out-dir",
        outDir.absolutePath,
    )
    inputs.file(hostLib)
    inputs.file(uniffiToml)
    outputs.dir(uniffiOutDir)
}

val buildRustAndroidDebugJni by tasks.registering(Exec::class) {
    group = "rust"
    description = "Cross-compile bookgen-core for Android ABIs via cargo-ndk (dev profile)"
    workingDir = rustWorkspaceDir
    environment.remove("RUSTFLAGS")
    environment.remove("CARGO_ENCODED_RUSTFLAGS")
    environment("ANDROID_NDK_HOME", ndkDirPath)
    val args = mutableListOf(cargoExe, "ndk")
    requestedAbis.forEach { abi ->
        args.add("-t")
        args.add(abi)
    }
    args.addAll(
        listOf(
            "-P",
            "26",
            "-o",
            jniLibsOutDir.get().asFile.absolutePath,
            "build",
            "--locked",
            "-p",
            "bookgen-core",
            "--profile",
            "android-jni-dev",
        )
    )
    commandLine(args)
    inputs.files(
        File(rustWorkspaceDir, "Cargo.toml"),
        File(rustWorkspaceDir, "Cargo.lock"),
        File(rustWorkspaceDir, ".cargo/config.toml"),
        File(rustWorkspaceDir, "bookgen-core/Cargo.toml"),
    )
    inputs.dir(File(rustWorkspaceDir, "bookgen-core/src"))
    inputs.dir(File(rustWorkspaceDir, "bookgen-core/assets"))
    outputs.dir(jniLibsOutDir)
}

android {
    namespace = "com.bookgen.app"
    compileSdk = 36
    ndkVersion = pinnedNdkVersion

    defaultConfig {
        applicationId = "com.bookgen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.add(uniffiOutDir.get().asFile.absolutePath)
            jniLibs.directories.add(jniLibsOutDir.get().asFile.absolutePath)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.systemProperty(
                "jna.library.path",
                File(rustWorkspaceDir, "target/debug").absolutePath
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(generateUniffiBindings)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(buildRustAndroidDebugJni)
}

tasks.matching { it.name.startsWith("test") && it.name.endsWith("UnitTest") }.configureEach {
    dependsOn(buildRustHost, generateUniffiBindings)
    inputs.file(File(rustWorkspaceDir, "target/debug/$hostSharedLibName"))
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.work.runtime.ktx)

    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.jna.jvm)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
