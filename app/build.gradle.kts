plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.unamentis"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.unamentis"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.unamentis.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Configure native code (Oboe, llama.cpp)
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // Enable NNAPI for TensorFlow Lite acceleration
        buildConfigField("boolean", "ENABLE_NNAPI", "true")

        // CMake configuration for native code (Oboe audio + llama.cpp LLM)
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments +=
                    listOf(
                        "-DANDROID_STL=c++_shared",
                        // llama.cpp configuration for Android:
                        // - Disable native CPU optimizations for cross-compile
                        // - Disable OpenMP (not available on Android)
                        // - Disable llamafile, tests, examples, server, curl
                        "-DLLAMA_NATIVE=OFF",
                        "-DGGML_OPENMP=OFF",
                        "-DGGML_LLAMAFILE=OFF",
                        "-DLLAMA_BUILD_TESTS=OFF",
                        "-DLLAMA_BUILD_EXAMPLES=OFF",
                        "-DLLAMA_BUILD_SERVER=OFF",
                        "-DLLAMA_CURL=OFF",
                    )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
            )
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true // Enable prefab for Oboe
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    // Configure native code build (C++/JNI for Oboe and llama.cpp)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Test options
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose BOM & Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)

    // TensorFlow Lite (VAD, ML)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)

    // ONNX Runtime (Silero VAD)
    implementation(libs.onnxruntime.android)

    // Oboe (Low-latency audio)
    implementation(libs.oboe)

    // MediaPipe LLM Inference (GPU acceleration)
    implementation("com.google.mediapipe:tasks-genai:0.10.21")

    // ExecuTorch (Qualcomm NPU acceleration)
    // Note: ExecuTorch AAR may need to be built from source or obtained from PyTorch releases
    // The official PyTorch Maven repo provides pre-built AARs for select configurations
    // implementation("org.pytorch:executorch-android:1.0.0")
    // implementation("org.pytorch:executorch-llama-android:1.0.0")
    // For now, we'll use a placeholder - uncomment when official AARs are available

    // DataStore (Preferences)
    implementation(libs.androidx.datastore.preferences)

    // Security (Encrypted Preferences)
    implementation(libs.androidx.security.crypto)

    // Image Loading (Coil)
    implementation(libs.coil.compose)

    // Work Manager (Background tasks)
    implementation(libs.androidx.work.runtime.ktx)

    // Glance (Compose Widgets)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)

    // Instrumentation Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.hilt.android.compiler)
}

// KtLint Configuration
ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Detekt Configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt.yml"))
}
