import groovy.json.JsonSlurper
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
        ?: ""
val embeddedDeepSeekApiKeyRevision = embeddedDeepSeekApiKey
    .takeIf(String::isNotBlank)
    ?.let { value ->
        MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
    .orEmpty()
val embeddedCerebrasApiKey =
    providers.gradleProperty("CEREBRAS_API_KEY").orNull
        ?: localProperties.getProperty("CEREBRAS_API_KEY")
        ?: ""
val embeddedCerebrasApiKeyRevision = embeddedCerebrasApiKey
    .takeIf(String::isNotBlank)
    ?.let { value ->
        MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
    .orEmpty()

abstract class GenerateDealUiPackSource : DefaultTask() {
    @get:org.gradle.api.tasks.InputFiles
    abstract val packFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.InputFiles
    abstract val manifestFiles: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.InputFile
    abstract val toolchainLockFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val releaseGateFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    private data class PackContractMetadata(
        val typeContracts: LinkedHashMap<String, String>,
        val typeDependencies: Map<String, Set<String>>,
        val componentContracts: LinkedHashMap<String, String>,
        val componentPropTypes: Map<String, String>,
        val componentExtraTypes: Map<String, Set<String>>,
        val componentDependencies: Map<String, Set<String>>,
        val tokenContracts: LinkedHashMap<String, String>,
        val tokenTypes: Map<String, String>
    )

    private val mobileCoreComponents = linkedSetOf(
        "AppTheme", "Root", "Column", "Row", "Stack", "Scroll", "Grid", "Card", "Section", "Hero",
        "MetricGroup", "ActionBar", "TopBar",
        "Text", "IntText", "NumberText", "Icon", "Button", "IconButton", "TextField", "IntField",
        "NumberField", "TimeField", "Toggle", "Checkbox", "Choice", "ChoiceItem", "Slider", "ProgressBar",
        "ProgressRing", "NumberProgressBar", "NumberProgressRing", "Spacer", "Divider", "Badge", "Stat",
        "IntStat", "NumberStat", "ListItem", "IntListItem", "EmptyState", "Snackbar", "Tabs", "TabItem",
        "NavigationBar", "NavigationItem", "BarChart", "Sparkline", "Modal", "Dialog", "BottomSheet", "Route"
    )

    private fun parseContractMetadata(packSource: String): PackContractMetadata {
        val classDeclaration = Regex(
            "export\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}",
            RegexOption.DOT_MATCHES_ALL
        )
        val fieldDeclaration = Regex(
            "([A-Za-z_][A-Za-z0-9_]*)(\\?)?\\s*:\\s*" +
                "([A-Za-z_][A-Za-z0-9_]*(?:\\[\\])?)(\\s*=\\s*[^;]+)?"
        )
        val componentDeclaration = Regex(
            "(?m)^export component ([A-Za-z_][A-Za-z0-9_]*)\\(props: ([A-Za-z_][A-Za-z0-9_]*)\\): View \\{ (.*) \\}$"
        )
        val requiredChildren = Regex(
            "children required ([A-Za-z_][A-Za-z0-9_]*(?: \\| [A-Za-z_][A-Za-z0-9_]*)*)"
        )
        val requiredParent = Regex("parent required ([A-Za-z_][A-Za-z0-9_]*)")
        val eventDeclaration = Regex(
            "event ([A-Za-z_][A-Za-z0-9_]*)(?:\\(payload:\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\[\\])?)\\))?"
        )
        val tokenDeclaration = Regex(
            "(?m)^export token ([A-Za-z_][A-Za-z0-9_]*): ([A-Za-z_][A-Za-z0-9_]*) ="
        )

        val typeContracts = linkedMapOf<String, String>()
        val typeDependencies = linkedMapOf<String, Set<String>>()
        classDeclaration.findAll(packSource).forEach { match ->
            val fields = fieldDeclaration.findAll(match.groupValues[2]).toList()
            typeContracts[match.groupValues[1]] = fields.joinToString(",") { field ->
                val optional = field.groupValues[2].isNotBlank() || field.groupValues[4].isNotBlank()
                "${field.groupValues[1]}${if (optional) "?" else ""}:${field.groupValues[3]}"
            }
            typeDependencies[match.groupValues[1]] = fields
                .map { it.groupValues[3].removeSuffix("[]") }
                .filterTo(linkedSetOf()) { dependency -> dependency.firstOrNull()?.isUpperCase() == true }
        }
        val componentContracts = linkedMapOf<String, String>()
        val componentPropTypes = linkedMapOf<String, String>()
        val componentExtraTypes = linkedMapOf<String, Set<String>>()
        val componentDependencies = linkedMapOf<String, Set<String>>()
        componentDeclaration.findAll(packSource).forEach { match ->
            val name = match.groupValues[1]
            val metadata = match.groupValues[3]
            val dependencies = linkedSetOf<String>()
            val extraTypes = linkedSetOf<String>()
            val constraints = buildList {
                when {
                    "children optional" in metadata -> add("children:?")

                    else -> requiredChildren.find(metadata)?.groupValues?.get(1)?.let { children ->
                        val names = children.split(" | ")
                        dependencies += names
                        add("children:${names.joinToString("|")}")
                    }
                }
                requiredParent.find(metadata)?.groupValues?.get(1)?.let { parent ->
                    dependencies += parent
                    add("parent:$parent")
                }
                eventDeclaration.findAll(metadata).forEach { event ->
                    val payload = event.groupValues[2]
                    payload.removeSuffix("[]").takeIf { it.firstOrNull()?.isUpperCase() == true }?.let(extraTypes::add)
                    val typedPayload = payload.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()
                    add("event:${event.groupValues[1]}$typedPayload")
                }
            }
            componentContracts[name] = buildString {
                append(name)
                append("(${match.groupValues[2]})")
                if (constraints.isNotEmpty()) append(constraints.joinToString(prefix = "[", postfix = "]"))
            }
            componentPropTypes[name] = match.groupValues[2]
            componentExtraTypes[name] = extraTypes
            componentDependencies[name] = dependencies
        }
        val tokenContracts = linkedMapOf<String, String>()
        val tokenTypes = linkedMapOf<String, String>()
        tokenDeclaration.findAll(packSource).forEach { match ->
            tokenContracts[match.groupValues[1]] = "${match.groupValues[1]}:${match.groupValues[2]}"
            tokenTypes[match.groupValues[1]] = match.groupValues[2]
        }
        return PackContractMetadata(
            typeContracts,
            typeDependencies,
            componentContracts,
            componentPropTypes,
            componentExtraTypes,
            componentDependencies,
            tokenContracts,
            tokenTypes
        )
    }

    private fun componentClosure(
        selected: Set<String>,
        componentDependencies: Map<String, Set<String>>
    ): Set<String> {
        val result = selected.toMutableSet()
        val pending = ArrayDeque(selected)
        while (pending.isNotEmpty()) {
            componentDependencies.getValue(pending.removeFirst()).forEach { dependency ->
                if (result.add(dependency)) pending.addLast(dependency)
            }
        }
        return result
    }

    private fun buildGenerationContract(
        metadata: PackContractMetadata,
        packVersion: String,
        selectedComponents: Set<String> = metadata.componentContracts.keys,
        semanticHints: Map<String, String> = emptyMap(),
        globalRules: List<String> = emptyList()
    ): String = buildString {
        val components = componentClosure(selectedComponents, metadata.componentDependencies)
        val types = components.mapTo(linkedSetOf()) { metadata.componentPropTypes.getValue(it) }
        components.forEach { types += metadata.componentExtraTypes.getValue(it) }
        val pendingTypes = ArrayDeque(types)
        while (pendingTypes.isNotEmpty()) {
            metadata.typeDependencies[pendingTypes.removeFirst()].orEmpty().forEach { dependency ->
                if (dependency in metadata.typeContracts && types.add(dependency)) pendingTypes.addLast(dependency)
            }
        }
        appendLine("Deal UI component signatures ($packVersion):")
        appendLine("Props (`?` means optional):")
        metadata.typeContracts.forEach { (name, fields) ->
            if (name in types) appendLine("$name{$fields}")
        }
        appendLine("Components (`children:?` allows arbitrary children; named children and parents are required):")
        metadata.componentContracts.forEach { (name, contract) ->
            if (name in components) appendLine(contract)
        }
        appendLine("Tokens:")
        metadata.tokenContracts.forEach { (name, contract) ->
            if (metadata.tokenTypes.getValue(name) in types) appendLine(contract)
        }
        if (globalRules.isNotEmpty()) {
            appendLine("Semantic rules:")
            globalRules.forEach { appendLine("- $it") }
        }
        val selectedHints = semanticHints.filterKeys { it in components }
        if (selectedHints.isNotEmpty()) {
            appendLine("Component usage:")
            selectedHints.forEach { (name, hint) -> appendLine("$name: $hint") }
        }
    }.trimEnd()

    private fun String.kotlinString(): String = "\"" +
        replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    private fun Map<String, String>.kotlinStringMap(): String = entries.joinToString(",\n") {
        "            ${it.key.kotlinString()} to ${it.value.kotlinString()}"
    }

    private fun Map<String, Set<String>>.kotlinStringSetMap(): String = entries.joinToString(",\n") { entry ->
        val values = entry.value.joinToString(", ") { it.kotlinString() }
        "            ${entry.key.kotlinString()} to setOf($values)"
    }

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val toolchainProperties = toolchainLockFile.get().asFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "Invalid toolchain lock entry: $line" }
                line.substring(0, separator) to line.substring(separator + 1)
            }
        val currentPackVersion = "deal-studio-dealui-pack-v14"
        require(toolchainProperties["COMPONENT_PACK_VERSION"] == currentPackVersion) {
            "toolchain.lock component pack must be $currentPackVersion"
        }
        val releaseLedger = JsonSlurper().parse(releaseGateFile.get().asFile) as Map<*, *>
        require(releaseLedger["schemaVersion"] == "deal-studio-pack-release-gates-v1") {
            "Invalid Pack v14 release-gate schema"
        }
        require(releaseLedger["packVersion"] == currentPackVersion) { "Release-gate packVersion mismatch" }
        val promotionStatus = releaseLedger["promotionStatus"]?.toString()
        val gateStatuses = (releaseLedger["gates"] as? Map<*, *>)?.values?.map(Any?::toString).orEmpty()
        require(gateStatuses.isNotEmpty() && gateStatuses.all { it in setOf("PASS", "FAIL", "PENDING") }) {
            "Release gates must use PASS, FAIL, or PENDING"
        }
        require(promotionStatus in setOf("PASS", "FAIL", "PENDING")) { "Invalid promotionStatus" }
        require(promotionStatus != "PASS" || gateStatuses.all { it == "PASS" }) {
            "Pack v14 cannot be promoted while any required gate is not PASS"
        }
        packFiles.files.sortedBy { it.name }.forEach { sourceFile ->
            val packSource = sourceFile.readText()
            val version = requireNotNull(Regex("pack version \\\"[^\\\"]*v(\\d+)\\\";").find(packSource)) {
                "Pack version is missing from ${sourceFile.name}"
            }.groupValues[1]
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(sourceFile.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            if (version == "14") {
                require(toolchainProperties["COMPONENT_PACK_SHA256"] == digest) {
                    "toolchain.lock component pack digest is stale: expected $digest"
                }
            }
            val manifestFile = manifestFiles.files.singleOrNull { it.name == "deal-studio-v$version.agent.json" }
            val manifest = manifestFile?.let { JsonSlurper().parse(it) as Map<*, *> }
            val destination = outputDirectory.get().file(
                "com/offlineassistant/app/generatedapp/GeneratedCanonicalDealUiPackV$version.kt"
            ).asFile
            destination.parentFile.mkdirs()
            val contractMetadata = parseContractMetadata(packSource)
            val manifestDigest = manifestFile?.let {
                MessageDigest.getInstance("SHA-256").digest(it.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
            }.orEmpty()
            val bundleDigest = MessageDigest.getInstance("SHA-256")
                .digest("$digest:$manifestDigest".encodeToByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
            val semanticHints = (manifest?.get("components") as? Map<*, *>)?.map { (name, entry) ->
                val values = entry as? Map<*, *> ?: error("Manifest component $name must be an object")
                name.toString() to listOfNotNull(values["usage"]?.toString(), values["example"]?.toString())
                    .joinToString(" Example: ")
            }?.toMap().orEmpty()
            val globalRules = (manifest?.get("globalRules") as? List<*>)?.map { it.toString() }.orEmpty()
            if (version == "14") {
                require(manifest?.get("version") == "deal-studio-agent-semantics-v1") { "Invalid v14 agent manifest version" }
                require(manifest["packVersion"] == "deal-studio-dealui-pack-v14") { "Agent manifest packVersion mismatch" }
                require(semanticHints.keys == contractMetadata.componentContracts.keys) {
                    "Agent manifest component coverage differs from pack: missing=${contractMetadata.componentContracts.keys - semanticHints.keys}, extra=${semanticHints.keys - contractMetadata.componentContracts.keys}"
                }
                val packTokens = contractMetadata.tokenContracts.keys
                val manifestTokenEntries = (manifest["themeTokens"] as? List<*>)?.map { it.toString() }.orEmpty()
                require(manifestTokenEntries.size == manifestTokenEntries.toSet().size) {
                    "Agent manifest themeTokens contains duplicates"
                }
                val manifestTokens = manifestTokenEntries.toSet()
                val semanticTokenTypes = setOf(
                    "ThemeStyle", "ShapeStyle", "DensityStyle", "SurfaceStyle", "TypographyStyle",
                    "ContrastStyle", "BackgroundStyle", "MotionStyle", "SemanticTone", "Emphasis",
                    "SectionRole", "CardRole", "ButtonHierarchy", "SurfaceTreatment"
                )
                val requiredManifestTokens = contractMetadata.tokenTypes
                    .filterValues { it in semanticTokenTypes }
                    .keys
                require(manifestTokens == requiredManifestTokens) {
                    "Agent manifest typed token coverage differs from pack: " +
                        "missing=${requiredManifestTokens - manifestTokens}, extra=${manifestTokens - requiredManifestTokens}"
                }
                require(packTokens.containsAll(manifestTokens)) { "Agent manifest has invalid token references" }
                val componentEntries = manifest["components"] as? Map<*, *> ?: error("Manifest components must be an object")
                componentEntries.forEach { (name, rawEntry) ->
                    val entry = rawEntry as? Map<*, *> ?: error("Manifest component $name must be an object")
                    require(entry.keys.all { it == "usage" || it == "example" }) {
                        "Manifest component $name has unsupported fields"
                    }
                    require(entry["usage"]?.toString()?.isNotBlank() == true) {
                        "Manifest component $name must have a non-empty usage hint"
                    }
                }
                require(globalRules.isNotEmpty() && globalRules.all(String::isNotBlank)) {
                    "Agent manifest globalRules must be non-empty strings"
                }
                val domainWords = Regex("(?i)\\b(medication|chess|weather|todo|dose|workout|arkanoid)\\b")
                require(!domainWords.containsMatchIn(manifestFile.readText())) { "Agent manifest must remain domain-neutral" }
            }
            val missingCoreComponents = mobileCoreComponents - contractMetadata.componentContracts.keys
            if (version == "14") {
                require(missingCoreComponents.isEmpty()) {
                    "Mobile core components missing from ${sourceFile.name}: ${missingCoreComponents.joinToString()}"
                }
            }
            val availableMobileCoreComponents = mobileCoreComponents.intersect(contractMetadata.componentContracts.keys)
            val encodedLines = packSource.lines().joinToString(",\n") { line ->
                val escaped = line
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                "        \"$escaped\""
            }
            val encodedManifestLines = manifestFile?.readText()?.lines()?.joinToString(",\n") { line ->
                "        ${line.kotlinString()}"
            }.orEmpty().ifEmpty { "        \"\"" }
            val generationContract = buildGenerationContract(
                metadata = contractMetadata,
                packVersion = "deal-studio-dealui-pack-v$version",
                semanticHints = semanticHints,
                globalRules = globalRules
            )
            val mobileCoreGenerationContract = buildGenerationContract(
                metadata = contractMetadata,
                packVersion = "deal-studio-dealui-pack-v$version mobile-core",
                selectedComponents = availableMobileCoreComponents,
                semanticHints = semanticHints,
                globalRules = globalRules
            )
            val encodedContractLines = generationContract.lines().joinToString(",\n") { line ->
                val escaped = line
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("$", "\\$")
                "        \"$escaped\""
            }
            val encodedMobileCoreContractLines = mobileCoreGenerationContract.lines().joinToString(",\n") { line ->
                "        ${line.kotlinString()}"
            }
            destination.writeText(
                """
                    package com.offlineassistant.app.generatedapp

                    internal object GeneratedCanonicalDealUiPackV$version {
                        const val SHA256: String = "$digest"
                        const val MANIFEST_SHA256: String = "$manifestDigest"
                        const val BUNDLE_SHA256: String = "$bundleDigest"
                        val AGENT_MANIFEST_SOURCE: String = listOf(
                    $encodedManifestLines
                        ).joinToString("\n")
                        val SOURCE: String = listOf(
                    $encodedLines
                        ).joinToString("\n")
                        val GENERATION_CONTRACT: String = listOf(
                    $encodedContractLines
                        ).joinToString("\n")
                        val MOBILE_CORE_GENERATION_CONTRACT: String = listOf(
                    $encodedMobileCoreContractLines
                        ).joinToString("\n")
                        val MOBILE_CORE_COMPONENTS: Set<String> = setOf(
                            ${availableMobileCoreComponents.joinToString(", ") { it.kotlinString() }}
                        )
                        val COMPONENT_CONTRACTS: Map<String, String> = mapOf(
                    ${contractMetadata.componentContracts.kotlinStringMap()}
                        )
                        val COMPONENT_PROP_TYPES: Map<String, String> = mapOf(
                    ${contractMetadata.componentPropTypes.kotlinStringMap()}
                        )
                        val COMPONENT_EXTRA_TYPES: Map<String, Set<String>> = mapOf(
                    ${contractMetadata.componentExtraTypes.kotlinStringSetMap()}
                        )
                        val COMPONENT_DEPENDENCIES: Map<String, Set<String>> = mapOf(
                    ${contractMetadata.componentDependencies.kotlinStringSetMap()}
                        )
                        val TYPE_CONTRACTS: Map<String, String> = mapOf(
                    ${contractMetadata.typeContracts.kotlinStringMap()}
                        )
                        val TYPE_DEPENDENCIES: Map<String, Set<String>> = mapOf(
                    ${contractMetadata.typeDependencies.kotlinStringSetMap()}
                        )
                        val TOKEN_CONTRACTS: Map<String, String> = mapOf(
                    ${contractMetadata.tokenContracts.kotlinStringMap()}
                        )
                        val TOKEN_TYPES: Map<String, String> = mapOf(
                    ${contractMetadata.tokenTypes.kotlinStringMap()}
                        )
                        val SEMANTIC_HINTS: Map<String, String> = mapOf(
                    ${semanticHints.kotlinStringMap()}
                        )
                    }
                """.trimIndent() + "\n"
            )
        }
    }
}

val generateDealUiPackSource = tasks.register<GenerateDealUiPackSource>("generateDealUiPackSource") {
    packFiles.from(
        rootProject.layout.projectDirectory.file("tooling/deal-ui-pack/deal-studio-v14.dealui-pack")
    )
    manifestFiles.from(rootProject.layout.projectDirectory.file("tooling/deal-ui-pack/deal-studio-v14.agent.json"))
    toolchainLockFile.set(rootProject.layout.projectDirectory.file("tooling/deal-android-bridge/toolchain.lock"))
    releaseGateFile.set(rootProject.layout.projectDirectory.file("tooling/deal-ui-pack/benchmarks/v14/gate-status.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/source/dealUiPack/kotlin"))
}

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
            buildConfigField(
                "String",
                "EMBEDDED_CEREBRAS_API_KEY",
                embeddedCerebrasApiKey.asBuildConfigString()
            )
            buildConfigField(
                "String",
                "EMBEDDED_CEREBRAS_API_KEY_REVISION",
                embeddedCerebrasApiKeyRevision.asBuildConfigString()
            )
        }
        release {
            buildConfigField("String", "EMBEDDED_DEEPSEEK_API_KEY", "\"\"")
            buildConfigField("String", "EMBEDDED_DEEPSEEK_API_KEY_REVISION", "\"\"")
            buildConfigField("String", "EMBEDDED_CEREBRAS_API_KEY", "\"\"")
            buildConfigField("String", "EMBEDDED_CEREBRAS_API_KEY_REVISION", "\"\"")
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

    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateDealUiPackSource,
            GenerateDealUiPackSource::outputDirectory
        )
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
