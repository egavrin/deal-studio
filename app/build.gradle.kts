plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val asrVulkanEnabled = providers.gradleProperty("asrVulkan")
    .map(String::toBoolean)
    .getOrElse(false)
val spirvHeadersDir = providers.gradleProperty("spirvHeadersDir").orNull
val spirvHeadersIncludeDir = providers.gradleProperty("spirvHeadersIncludeDir").orNull
val vulkanHeadersDir = providers.gradleProperty("vulkanHeadersDir").orNull

android {
    namespace = "com.offlineassistant.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.offlineassistant.poc"
        minSdk = if (asrVulkanEnabled) 28 else 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DOFFLINE_ASSISTANT_ASR_VULKAN=${if (asrVulkanEnabled) "ON" else "OFF"}"
                spirvHeadersDir?.let { arguments += "-DSPIRV-Headers_DIR=$it" }
                spirvHeadersIncludeDir?.let {
                    arguments += "-DOFFLINE_ASSISTANT_SPIRV_HEADERS_INCLUDE_DIR=$it"
                }
                vulkanHeadersDir?.let { arguments += "-DOFFLINE_ASSISTANT_VULKAN_HEADERS_DIR=$it" }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The PoC has no production keystore yet; this keeps release profiling installable.
            signingConfig = signingConfigs.getByName("debug")
            baselineProfile.automaticGenerationDuringBuild = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "lib/armeabi-v7a/**",
                "lib/x86/**",
                "lib/x86_64/**"
            )
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        warningsAsErrors = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.onnxruntime.android)
    implementation(libs.jtransforms)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":benchmark"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

ktlint {
    version.set(libs.versions.ktlint.get())
    android.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

baselineProfile {
    filter {
        include("com.offlineassistant.**")
    }
}
