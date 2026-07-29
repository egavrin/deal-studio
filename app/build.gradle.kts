plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.offlineassistant.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.offlineassistant.poc"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
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
            signingConfig = signingConfigs.getByName("debug")
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
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":deepseek-connector"))
    kover(project(":core"))
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
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

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

kover {
    currentProject {
        createVariant("ci") {
            add("debug")
        }
    }
    reports {
        variant("ci") {
            filters {
                excludes {
                    classes(
                        "com.offlineassistant.app.MainActivity*",
                        "com.offlineassistant.app.AssistantRuntimeContainer*",
                        "com.offlineassistant.app.OfflineAssistantApplication*",
                        "com.offlineassistant.app.audio.*",
                        "com.offlineassistant.app.models.ModelReadinessRepository*",
                        "com.offlineassistant.app.platform.*",
                        "com.offlineassistant.app.storage.*",
                        "com.offlineassistant.app.ui.*ScreenKt*",
                        "com.offlineassistant.app.ui.theme.*",
                        "com.offlineassistant.app.widgets.*"
                    )
                }
            }
            verify {
                rule("Unit-testable app and core line coverage") {
                    minBound(45)
                }
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
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
    automaticGenerationDuringBuild = false
    filter {
        include("com.offlineassistant.**")
    }
}
