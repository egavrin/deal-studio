import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val embeddedDeepSeekApiKey =
    providers.gradleProperty("DEEPSEEK_API_KEY").orNull
        ?: localProperties.getProperty("DEEPSEEK_API_KEY")
        ?: "sk-7923c848a3be46c2af5a7bc10d260e3c"
val embeddedDeepSeekApiKeyRevision = embeddedDeepSeekApiKey
    .takeIf(String::isNotBlank)
    ?.let { value ->
        MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
    .orEmpty()

android {
    namespace = "com.offlineassistant.app"
    compileSdk = 37

    val releaseStoreFile = providers.gradleProperty("OFFLINE_ASSISTANT_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.gradleProperty("OFFLINE_ASSISTANT_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.gradleProperty("OFFLINE_ASSISTANT_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.gradleProperty("OFFLINE_ASSISTANT_RELEASE_KEY_PASSWORD").orNull
    val releaseSigningConfigured = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("production") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.dealstudio.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField(
                "String",
                "EMBEDDED_DEEPSEEK_API_KEY",
                embeddedDeepSeekApiKey.asBuildConfigString()
            )
            buildConfigField(
                "String",
                "EMBEDDED_DEEPSEEK_API_KEY_REVISION",
                embeddedDeepSeekApiKeyRevision.asBuildConfigString()
            )
        }
        release {
            buildConfigField("String", "EMBEDDED_DEEPSEEK_API_KEY", "\"\"")
            buildConfigField("String", "EMBEDDED_DEEPSEEK_API_KEY_REVISION", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("production")
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
    implementation(project(":deepseek-connector"))

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
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

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
                        "com.offlineassistant.app.DealStudioActivity*",
                        "com.offlineassistant.app.ui.theme.*",
                        "com.offlineassistant.app.generatedapp.GeneratedAppStudioScreenKt*"
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

tasks.withType<Test>().configureEach {
    systemProperty("offlineAssistant.repoRoot", rootProject.projectDir.absolutePath)
    providers.systemProperty("offlineAssistant.generatedDealCandidateDir").orNull?.let { candidateDir ->
        systemProperty("offlineAssistant.generatedDealCandidateDir", candidateDir)
    }
    providers.systemProperty("offlineAssistant.generatedUiCandidateDir").orNull?.let { candidateDir ->
        systemProperty("offlineAssistant.generatedUiCandidateDir", candidateDir)
    }
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
