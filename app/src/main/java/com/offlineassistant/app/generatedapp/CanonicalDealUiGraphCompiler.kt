package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import com.offlineassistant.deepseek.DeepSeekFunctionTool
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Compiler-owned root view graph. The model can fill the view body, but not its typed boundary. */
internal class CanonicalDealUiGraphCompiler(
    private val rootState: String,
    private val requiredActions: Set<String> = emptySet(),
    private val requiredCapabilityComponents: Set<String> = emptySet(),
    private val validateProjection: (source: String, finalProjection: Boolean) -> String
) {
    private val acceptedSections = mutableListOf<AcceptedSection>()
    private val deferredSections = mutableListOf<DeferredSection>()
    private var complete = false
    private var checkedIr: String? = null
    private var checkedCoverage = UiCoverage(requiredActions, requiredCapabilityComponents)
    private var activeBatchHash: String? = null
    private var pendingRepairSectionId: String? = null
    private var theme = GeneratedAppThemeSpec.DEFAULT
    private var themeSelected = false
    private var lastRejectedBody: String = ""
    private var lastDiagnostic: String = ""
    private val log = mutableListOf<String>()

    var rejectedPatches: Int = 0
        private set
    var validationLatencyMs: Long = 0
        private set

    val isComplete: Boolean get() = complete

    fun snapshot(): CanonicalDealUiGraphSnapshot {
        val source = render(acceptedSections)
        return CanonicalDealUiGraphSnapshot(
            graphHash = sha256(source),
            partialDealUi = source,
            lastRejectedBody = lastRejectedBody,
            diagnostic = lastDiagnostic,
            acceptedPatches = acceptedSections.size,
            acceptedSectionIds = acceptedSections.map(AcceptedSection::id),
            pendingRepairSectionId = pendingRepairSectionId,
            deferredSectionCount = deferredSections.size,
            pendingActionNames = checkedCoverage.pendingActions.sorted(),
            pendingCapabilityComponents = checkedCoverage.pendingCapabilityComponents.sorted(),
            rejectedPatches = rejectedPatches
        )
    }

    fun currentTool(): DeepSeekFunctionTool {
        val hash = snapshot().graphHash
        activeBatchHash = hash
        val minimumBatchSections = if (acceptedSections.isEmpty() && pendingRepairSectionId == null) {
            MIN_PROGRESSIVE_SECTIONS
        } else {
            1
        }
        val completionSectionId = if (
            acceptedSections.isNotEmpty() && pendingRepairSectionId == null
        ) {
            "completion-${acceptedSections.size + 1}"
        } else {
            null
        }
        val expectsTheme = !themeSelected && pendingRepairSectionId == null
        val obligations = snapshot().obligationSummary()
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("base_hash") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive(hash)) }
                }
                if (expectsTheme) {
                    putJsonObject("theme") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("primary") {
                                put("type", "string")
                                put("pattern", "^#[0-9A-Fa-f]{6}$")
                            }
                            putJsonObject("secondary") {
                                put("type", "string")
                                put("pattern", "^#[0-9A-Fa-f]{6}$")
                            }
                            enumProperty("style", GeneratedAppThemeSpec.STYLES)
                            enumProperty("shape", GeneratedAppThemeSpec.SHAPES)
                            enumProperty("density", GeneratedAppThemeSpec.DENSITIES)
                            enumProperty("surface", GeneratedAppThemeSpec.SURFACES)
                        }
                        putJsonArray("required") {
                            THEME_PROPERTIES.forEach { add(JsonPrimitive(it)) }
                        }
                        put("additionalProperties", false)
                    }
                }
                putJsonObject("sections") {
                    put("type", "array")
                    put("minItems", minimumBatchSections)
                    put(
                        "maxItems",
                        if (pendingRepairSectionId == null && completionSectionId == null) MAX_BATCH_SECTIONS else 1
                    )
                    put(
                        "items",
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("section_id") {
                                    put("type", "string")
                                    pendingRepairSectionId?.let { sectionId ->
                                        putJsonArray("enum") { add(JsonPrimitive(sectionId)) }
                                    } ?: completionSectionId?.let { sectionId ->
                                        putJsonArray("enum") { add(JsonPrimitive(sectionId)) }
                                    } ?: run {
                                        put("minLength", 1)
                                        put("maxLength", MAX_SECTION_ID_CHARS)
                                    }
                                }
                                putJsonObject("body") {
                                    put("type", "string")
                                    put("minLength", 1)
                                    put("maxLength", MAX_SECTION_CHARS)
                                    if (pendingRepairSectionId != null) {
                                        put(
                                            "description",
                                            "Return a complete corrected replacement for the rejected section. " +
                                                "Resolve this exact compiler diagnostic and do not repeat the " +
                                                "rejected expression: ${lastDiagnostic.take(DIAGNOSTIC_DESCRIPTION_CHARS)}"
                                        )
                                    } else if (obligations.isNotBlank()) {
                                        put(
                                            "description",
                                            "The cumulative UI must satisfy these compiler-owned obligations before " +
                                                "finalization: $obligations"
                                        )
                                    }
                                }
                                putJsonObject("is_final") { put("type", "boolean") }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("section_id"))
                                add(JsonPrimitive("body"))
                                add(JsonPrimitive("is_final"))
                            }
                            put("additionalProperties", false)
                        }
                    )
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("base_hash"))
                if (expectsTheme) add(JsonPrimitive("theme"))
                add(JsonPrimitive("sections"))
            }
            put("additionalProperties", false)
        }
        return DeepSeekFunctionTool(
            name = SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME,
            description = pendingRepairSectionId?.let { sectionId ->
                "Repair only the rejected '$sectionId' Deal UI section in one checked batch."
            } ?: "Select one compact application theme and submit 1-$MAX_BATCH_SECTIONS complete runtime " +
                "surfaces. Every application screen is one ui.Route; ui.Widget and host clocks are separate surfaces. " +
                "The compiler owns the theme and root boundaries and validates every surface. " +
                obligations.takeIf(String::isNotBlank).orEmpty(),
            parameters = parameters,
            strict = true
        )
    }

    @Suppress("ReturnCount")
    fun apply(call: DeepSeekFunctionCall): CanonicalDealUiGraphApplyResult {
        if (call.name == APPEND_DEAL_UI_SECTION_TOOL_NAME) return applySectionCall(call)
        if (call.name != SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME) {
            return reject("Expected $SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME, received ${call.name}")
        }
        val root = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse { return reject("Tool arguments are invalid JSON: ${it.message}") }
        if (!themeSelected) {
            val requestedTheme = runCatching {
                GeneratedAppThemeSpec.fromTool(root.getValue("theme").jsonObject)
            }.getOrElse { return reject(it.message ?: "Invalid generated-app theme") }
            theme = requestedTheme
            themeSelected = true
        } else if ("theme" in root) {
            return reject("Generated-app theme is selected once and cannot change during section repair")
        }
        if (!root.keys.containsAll(setOf("base_hash", "sections"))) {
            return reject("$SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME requires base_hash and sections")
        }
        val sections = runCatching { root.getValue("sections").jsonArray }
            .getOrElse { return reject("sections must be an array") }
        val expectedSize = if (pendingRepairSectionId == null) {
            val minimum = if (acceptedSections.isEmpty()) MIN_PROGRESSIVE_SECTIONS else 1
            minimum..MAX_BATCH_SECTIONS
        } else {
            1..1
        }
        if (sections.size !in expectedSize) {
            return reject("Deal UI batch must contain ${expectedSize.first}..${expectedSize.last} sections")
        }

        var accepted = false
        var lastResult = CanonicalDealUiGraphApplyResult(accepted = false, diagnostic = null)
        sections.forEachIndexed { index, sectionElement ->
            if (complete) return@forEachIndexed
            val section = runCatching { sectionElement.jsonObject }.getOrElse {
                lastResult = reject("Deal UI batch section $index must be an object")
                return@forEachIndexed
            }
            val sectionCall = DeepSeekFunctionCall(
                callId = "${call.callId}:$index",
                name = APPEND_DEAL_UI_SECTION_TOOL_NAME,
                arguments = buildJsonObject {
                    put("base_hash", root.getValue("base_hash"))
                    put("section_id", section["section_id"] ?: JsonPrimitive(""))
                    put("body", section["body"] ?: JsonPrimitive(""))
                    put("is_final", section["is_final"] ?: JsonPrimitive(false))
                }.toString()
            )
            lastResult = applySectionCall(sectionCall)
            accepted = accepted || lastResult.accepted
        }
        return CanonicalDealUiGraphApplyResult(
            accepted = accepted,
            completed = complete,
            diagnostic = snapshot().diagnostic.takeIf(String::isNotBlank) ?: lastResult.diagnostic,
            rejectedCandidateFingerprint = lastResult.rejectedCandidateFingerprint
        )
    }

    @Suppress("ReturnCount")
    private fun applySectionCall(call: DeepSeekFunctionCall): CanonicalDealUiGraphApplyResult {
        if (complete) return reject("Deal UI graph is already complete")
        if (call.name != APPEND_DEAL_UI_SECTION_TOOL_NAME) {
            return reject("Expected $APPEND_DEAL_UI_SECTION_TOOL_NAME, received ${call.name}")
        }
        val root = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse { return reject("Tool arguments are invalid JSON: ${it.message}") }
        if (!root.keys.containsAll(setOf("base_hash", "section_id", "body", "is_final"))) {
            return reject("$APPEND_DEAL_UI_SECTION_TOOL_NAME requires base_hash, section_id, body and is_final")
        }
        val expectedHash = activeBatchHash ?: snapshot().graphHash
        val actualHash = root["base_hash"]?.jsonPrimitive?.contentOrNull
        if (actualHash != expectedHash) return reject("Stale Deal UI graph hash $actualHash; expected $expectedHash")

        val sectionId = root["section_id"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        val isFinal = root["is_final"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: return reject("is_final must be a boolean")
        val normalizedBody = normalizeSectionBody(sectionId, body)
        pendingRepairSectionId?.let { pendingId ->
            if (sectionId != pendingId) {
                val structuralFailure = runCatching { validateSection(sectionId, normalizedBody) }.exceptionOrNull()
                if (structuralFailure != null) {
                    return reject(structuralFailure.message.orEmpty(), sha256("$sectionId\u0000${body.trim()}"))
                }
                deferredSections += DeferredSection(sectionId, normalizedBody, isFinal)
                log += "DEFER\t$sectionId\tuntil=$pendingId\t${sha256(body)}\tfinalRequested=$isFinal"
                return CanonicalDealUiGraphApplyResult(
                    accepted = false,
                    diagnostic = "Deferred Deal UI section $sectionId until rejected section $pendingId is repaired"
                )
            }
        }
        val structuralFailure = runCatching { validateSection(sectionId, normalizedBody) }.exceptionOrNull()
        if (structuralFailure != null) {
            val repairable = sectionId.matches(SECTION_ID) && acceptedSections.none { it.id == sectionId }
            return rejectBody(sectionId, body, structuralFailure.message.orEmpty(), repairable)
        }

        val candidateSections = acceptedSections + AcceptedSection(sectionId, normalizedBody)
        val source = render(candidateSections)
        val started = System.nanoTime()
        val result = runCatching { checkedProjection(source, isFinal, candidateSections.size) }
        validationLatencyMs += (System.nanoTime() - started).coerceAtLeast(0) / 1_000_000
        return result.fold(
            onSuccess = { projection ->
                acceptedSections += AcceptedSection(sectionId, normalizedBody)
                if (pendingRepairSectionId == sectionId) pendingRepairSectionId = null
                checkedIr = projection.ir
                checkedCoverage = projection.coverage
                complete = projection.final
                lastRejectedBody = ""
                lastDiagnostic = when {
                    isFinal && acceptedSections.size < MIN_PROGRESSIVE_SECTIONS ->
                        "Append at least ${MIN_PROGRESSIVE_SECTIONS - acceptedSections.size} more complete " +
                            "runtime surface before finalizing"

                    isFinal && !projection.coverage.isComplete -> projection.coverage.diagnostic()

                    else -> ""
                }
                log += "ACCEPT\t$APPEND_DEAL_UI_SECTION_TOOL_NAME\t$sectionId\t${sha256(body)}" +
                    "\tfinalRequested=$isFinal\tcomplete=$complete"
                if (!complete && pendingRepairSectionId == null) drainDeferredSections()
                CanonicalDealUiGraphApplyResult(
                    accepted = true,
                    completed = complete,
                    diagnostic = lastDiagnostic.takeIf(String::isNotBlank)
                )
            },
            onFailure = { failure ->
                rejectBody(
                    sectionId = sectionId,
                    body = body,
                    diagnostic = failure.message ?: "Deal UI compiler rejected body",
                    repairable = true
                )
            }
        )
    }

    fun finishSource(): String {
        require(complete) { "Deal UI graph has not been finalized" }
        return render(acceptedSections)
    }

    fun previewSource(): String = render(acceptedSections)

    fun currentIr(): String = requireNotNull(checkedIr) { "No Deal UI section has been accepted" }

    fun finishIr(): String {
        require(complete) { "Deal UI graph has not been finalized" }
        return currentIr()
    }

    fun patchLog(): String = log.joinToString("\n")

    private fun rejectBody(
        sectionId: String,
        body: String,
        diagnostic: String,
        repairable: Boolean
    ): CanonicalDealUiGraphApplyResult {
        if (repairable) pendingRepairSectionId = sectionId
        lastRejectedBody = body
        lastDiagnostic = diagnostic
        return reject(diagnostic, sha256("$sectionId\u0000${body.trim()}"))
    }

    private fun reject(
        diagnostic: String,
        rejectedCandidateFingerprint: String = sha256(diagnostic)
    ): CanonicalDealUiGraphApplyResult {
        rejectedPatches++
        log += "REJECT\t$diagnostic"
        return CanonicalDealUiGraphApplyResult(
            accepted = false,
            diagnostic = diagnostic,
            rejectedCandidateFingerprint = rejectedCandidateFingerprint
        )
    }

    private fun validateSection(sectionId: String, body: String) {
        require(sectionId.matches(SECTION_ID)) {
            "Deal UI section id must be a lowercase slug containing letters, digits, underscores or hyphens"
        }
        require(acceptedSections.none { it.id == sectionId }) { "Deal UI section $sectionId already exists" }
        require(deferredSections.none { it.id == sectionId }) { "Deal UI section $sectionId is already deferred" }
        require(body.isNotBlank()) { "Deal UI section body cannot be empty" }
        require(body.length <= MAX_SECTION_CHARS) { "Deal UI section exceeds $MAX_SECTION_CHARS characters" }
        require("```" !in body) { "Deal UI section must not contain Markdown fences" }
        val code = body.codeOnly()
        require(!BOUNDARY_DECLARATION.containsMatchIn(code)) {
            "Deal UI section cannot add imports, exports, views or @ui-root declarations"
        }
        require(!ROOT_COMPONENT.containsMatchIn(code)) {
            "Deal UI sections are already wrapped by compiler-owned ui.Root"
        }
    }

    private fun normalizeSectionBody(sectionId: String, body: String): String = body
        .lines()
        .filterNot { it.trim() == "// section:$sectionId" }
        .joinToString("\n")
        .trim()

    private fun drainDeferredSections() {
        while (!complete && pendingRepairSectionId == null && deferredSections.isNotEmpty()) {
            val deferred = deferredSections.removeAt(0)
            val candidateSections = acceptedSections + AcceptedSection(deferred.id, deferred.body)
            val source = render(candidateSections)
            val started = System.nanoTime()
            val result = runCatching { checkedProjection(source, deferred.isFinal, candidateSections.size) }
            validationLatencyMs += (System.nanoTime() - started).coerceAtLeast(0) / 1_000_000
            result.fold(
                onSuccess = { projection ->
                    acceptedSections += AcceptedSection(deferred.id, deferred.body)
                    checkedIr = projection.ir
                    checkedCoverage = projection.coverage
                    complete = projection.final
                    lastRejectedBody = ""
                    lastDiagnostic = when {
                        deferred.isFinal && acceptedSections.size < MIN_PROGRESSIVE_SECTIONS ->
                            "Append at least ${MIN_PROGRESSIVE_SECTIONS - acceptedSections.size} more complete " +
                                "runtime surface before finalizing"

                        deferred.isFinal && !projection.coverage.isComplete -> projection.coverage.diagnostic()

                        else -> ""
                    }
                    log += "ACCEPT_DEFERRED\t$APPEND_DEAL_UI_SECTION_TOOL_NAME\t${deferred.id}\t" +
                        "${sha256(deferred.body)}\tfinalRequested=${deferred.isFinal}\tcomplete=$complete"
                },
                onFailure = { failure ->
                    pendingRepairSectionId = deferred.id
                    lastRejectedBody = deferred.body
                    lastDiagnostic = failure.message ?: "Deal UI compiler rejected deferred body"
                    rejectedPatches++
                    log += "REJECT_DEFERRED\t${deferred.id}\t$lastDiagnostic"
                }
            )
        }
    }

    private fun render(sections: List<AcceptedSection>): String = buildString {
        appendLine("import * as app from \"./app\";")
        appendLine("import * as ui from \"./platform-ui.dealui-pack\";")
        appendLine()
        appendLine("// @ui-root")
        appendLine("export view App(state: app.$rootState): View {")
        appendLine("  ui.AppTheme(${theme.asDealUiArguments()}) {")
        if (sections.isEmpty()) {
            appendLine("    ui.Root(spacing: ui.spaceMd) {")
            appendLine("      ui.Text(value: \"Generating interface\", style: ui.textBody)")
            appendLine("    }")
        } else {
            appendLine("    ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {")
            sections.forEach { section ->
                appendLine("      // section:${section.id}")
                section.body.lines().forEach { appendLine("      $it") }
            }
            appendLine("    }")
        }
        appendLine("  }")
        appendLine("}")
    }

    private fun checkedProjection(source: String, requestedFinal: Boolean, sectionCount: Int): CheckedProjection {
        val previewIr = validateProjection(source, false)
        val previewProgram = CanonicalDealUiParser.parse(previewIr)
        val coverage = coverage(previewProgram)
        val final = requestedFinal && sectionCount >= MIN_PROGRESSIVE_SECTIONS && coverage.isComplete
        return CheckedProjection(
            ir = if (final) validateProjection(source, true) else previewIr,
            coverage = coverage,
            final = final
        )
    }

    private fun coverage(program: CanonicalDealUiProgram): UiCoverage {
        val boundActions = program.metadata.reachableInputActions
        val hostComponents = program.metadata.usedComponents.mapTo(linkedSetOf()) { it.substringAfterLast('.') }
        return UiCoverage(
            pendingActions = requiredActions - boundActions,
            pendingCapabilityComponents = requiredCapabilityComponents - hostComponents
        )
    }

    private fun String.codeOnly(): String {
        val result = StringBuilder(length)
        var index = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < length) {
            val current = this[index]
            val next = getOrNull(index + 1)
            when {
                lineComment -> {
                    lineComment = current != '\n'
                    result.append(if (current == '\n') '\n' else ' ')
                }

                blockComment -> {
                    if (current == '*' && next == '/') {
                        result.append("  ")
                        index++
                        blockComment = false
                    } else {
                        result.append(if (current == '\n') '\n' else ' ')
                    }
                }

                quote != null -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == quote) {
                        quote = null
                    }
                }

                current == '/' && next == '/' -> {
                    result.append("  ")
                    index++
                    lineComment = true
                }

                current == '/' && next == '*' -> {
                    result.append("  ")
                    index++
                    blockComment = true
                }

                current == '"' || current == '\'' || current == '`' -> {
                    quote = current
                    result.append(' ')
                }

                else -> result.append(current)
            }
            index++
        }
        return result.toString()
    }

    private companion object {
        const val MAX_SECTION_CHARS = 24_000
        const val DIAGNOSTIC_DESCRIPTION_CHARS = 800
        const val MAX_SECTION_ID_CHARS = 40
        const val MAX_BATCH_SECTIONS = 6
        const val MIN_PROGRESSIVE_SECTIONS = 1
        val THEME_PROPERTIES = listOf("primary", "secondary", "style", "shape", "density", "surface")
        val JSON = Json { ignoreUnknownKeys = false }
        val BOUNDARY_DECLARATION = Regex("\\b(?:import|export|view)\\b|@ui-root")
        val ROOT_COMPONENT = Regex("\\bui\\.Root\\s*\\(")
        val SECTION_ID = Regex("[a-z][a-z0-9_-]{0,39}")
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.enumProperty(name: String, values: Set<String>) {
    putJsonObject(name) {
        put("type", "string")
        putJsonArray("enum") { values.sorted().forEach { add(JsonPrimitive(it)) } }
    }
}

private data class AcceptedSection(val id: String, val body: String)

private data class DeferredSection(val id: String, val body: String, val isFinal: Boolean)

private data class CheckedProjection(val ir: String, val coverage: UiCoverage, val final: Boolean)

private data class UiCoverage(
    val pendingActions: Set<String>,
    val pendingCapabilityComponents: Set<String>
) {
    val isComplete: Boolean = pendingActions.isEmpty() && pendingCapabilityComponents.isEmpty()

    fun diagnostic(): String = "Finalization deferred; append a cohesive UI section that satisfies " +
        buildList {
            if (pendingActions.isNotEmpty()) add("action bindings ${pendingActions.sorted().joinToString()}")
            if (pendingCapabilityComponents.isNotEmpty()) {
                add("host components ${pendingCapabilityComponents.sorted().joinToString()}")
            }
        }.joinToString(" and ")
}

internal data class CanonicalDealUiGraphSnapshot(
    val graphHash: String,
    val partialDealUi: String,
    val lastRejectedBody: String,
    val diagnostic: String,
    val acceptedPatches: Int,
    val acceptedSectionIds: List<String>,
    val pendingRepairSectionId: String?,
    val deferredSectionCount: Int,
    val pendingActionNames: List<String>,
    val pendingCapabilityComponents: List<String>,
    val rejectedPatches: Int
) {
    fun obligationSummary(): String = buildList {
        if (pendingActionNames.isNotEmpty()) add("actions=${pendingActionNames.joinToString()}")
        if (pendingCapabilityComponents.isNotEmpty()) {
            add("host_components=${pendingCapabilityComponents.joinToString()}")
        }
    }.joinToString("; ")
}

internal data class CanonicalDealUiGraphApplyResult(
    val accepted: Boolean,
    val completed: Boolean = false,
    val diagnostic: String?,
    val rejectedCandidateFingerprint: String? = null
)

internal const val APPEND_DEAL_UI_SECTION_TOOL_NAME = "append_deal_ui_section"
internal const val SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME = "submit_deal_ui_sections"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
