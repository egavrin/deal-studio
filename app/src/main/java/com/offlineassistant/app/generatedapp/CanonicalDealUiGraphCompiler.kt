package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import com.offlineassistant.deepseek.DeepSeekFunctionTool
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Compiler-owned root view graph. The model can fill the view body, but not its typed boundary. */
internal class CanonicalDealUiGraphCompiler(
    private val rootState: String,
    private val validateProjection: (source: String, finalProjection: Boolean) -> String
) {
    private val acceptedSections = mutableListOf<AcceptedSection>()
    private var complete = false
    private var checkedIr: String? = null
    private var activeBatchHash: String? = null
    private var pendingRepairSectionId: String? = null
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
            rejectedPatches = rejectedPatches
        )
    }

    fun currentTool(): DeepSeekFunctionTool {
        val hash = snapshot().graphHash
        activeBatchHash = hash
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("base_hash") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive(hash)) }
                }
                putJsonObject("section_id") {
                    put("type", "string")
                    pendingRepairSectionId?.let { sectionId ->
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
                }
                putJsonObject("is_final") { put("type", "boolean") }
            }
            putJsonArray("required") {
                add(JsonPrimitive("base_hash"))
                add(JsonPrimitive("section_id"))
                add(JsonPrimitive("body"))
                add(JsonPrimitive("is_final"))
            }
            put("additionalProperties", false)
        }
        return DeepSeekFunctionTool(
            name = APPEND_DEAL_UI_SECTION_TOOL_NAME,
            description = pendingRepairSectionId?.let { sectionId ->
                "Repair the rejected '$sectionId' Deal UI section. Other section ids are unavailable until this section passes production validation."
            } ?: "Append one new cohesive top-level Deal UI section. Accepted section ids cannot be repeated. Each cumulative projection is production-checked before it becomes visible.",
            parameters = parameters,
            strict = true
        )
    }

    @Suppress("ReturnCount")
    fun apply(call: DeepSeekFunctionCall): CanonicalDealUiGraphApplyResult {
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
        pendingRepairSectionId?.let { pendingId ->
            if (sectionId != pendingId) {
                return reject("Repair Deal UI section $pendingId before appending $sectionId")
            }
        }
        val normalizedBody = normalizeSectionBody(sectionId, body)
        val structuralFailure = runCatching { validateSection(sectionId, normalizedBody) }.exceptionOrNull()
        if (structuralFailure != null) {
            val repairable = sectionId.matches(SECTION_ID) && acceptedSections.none { it.id == sectionId }
            return rejectBody(sectionId, body, structuralFailure.message.orEmpty(), repairable)
        }

        val candidateSections = acceptedSections + AcceptedSection(sectionId, normalizedBody)
        val source = CanonicalSourceNormalizer.dealUi(render(candidateSections))
        val finalProjection = isFinal && candidateSections.size >= MIN_PROGRESSIVE_SECTIONS
        val started = System.nanoTime()
        val result = runCatching { validateProjection(source, finalProjection) }
        validationLatencyMs += (System.nanoTime() - started).coerceAtLeast(0) / 1_000_000
        return result.fold(
            onSuccess = { ir ->
                acceptedSections += AcceptedSection(sectionId, normalizedBody)
                if (pendingRepairSectionId == sectionId) pendingRepairSectionId = null
                checkedIr = ir
                complete = finalProjection
                lastRejectedBody = ""
                lastDiagnostic = if (isFinal && !complete) {
                    "Append at least ${MIN_PROGRESSIVE_SECTIONS - acceptedSections.size} more independent top-level section before finalizing"
                } else {
                    ""
                }
                log += "ACCEPT\t$APPEND_DEAL_UI_SECTION_TOOL_NAME\t$sectionId\t${sha256(body)}" +
                    "\tfinalRequested=$isFinal\tcomplete=$complete"
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
        return CanonicalSourceNormalizer.dealUi(render(acceptedSections))
    }

    fun previewSource(): String = CanonicalSourceNormalizer.dealUi(render(acceptedSections))

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
        return reject(diagnostic)
    }

    private fun reject(diagnostic: String): CanonicalDealUiGraphApplyResult {
        rejectedPatches++
        log += "REJECT\t$diagnostic"
        return CanonicalDealUiGraphApplyResult(accepted = false, diagnostic = diagnostic)
    }

    private fun validateSection(sectionId: String, body: String) {
        require(sectionId.matches(SECTION_ID)) { "Deal UI section id must be a lowercase identifier" }
        require(acceptedSections.none { it.id == sectionId }) { "Deal UI section $sectionId already exists" }
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

    private fun render(sections: List<AcceptedSection>): String = buildString {
        appendLine("import * as app from \"./app\";")
        appendLine("import * as ui from \"./platform-ui.dealui-pack\";")
        appendLine()
        appendLine("// @ui-root")
        appendLine("export view App(state: app.$rootState): View {")
        if (sections.isEmpty()) {
            appendLine("  ui.Root(spacing: ui.spaceMd) {")
            appendLine("    ui.Text(value: \"Generating interface\", style: ui.textBody)")
            appendLine("  }")
        } else {
            appendLine("  ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {")
            sections.forEach { section ->
                appendLine("    // section:${section.id}")
                section.body.lines().forEach { appendLine("    $it") }
            }
            appendLine("  }")
        }
        appendLine("}")
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
        const val MAX_SECTION_ID_CHARS = 40
        const val MIN_PROGRESSIVE_SECTIONS = 2
        val JSON = Json { ignoreUnknownKeys = false }
        val BOUNDARY_DECLARATION = Regex("\\b(?:import|export|view)\\b|@ui-root")
        val ROOT_COMPONENT = Regex("\\bui\\.Root\\s*\\(")
        val SECTION_ID = Regex("[a-z][a-z0-9_]{0,39}")
    }
}

private data class AcceptedSection(val id: String, val body: String)

internal data class CanonicalDealUiGraphSnapshot(
    val graphHash: String,
    val partialDealUi: String,
    val lastRejectedBody: String,
    val diagnostic: String,
    val acceptedPatches: Int,
    val acceptedSectionIds: List<String>,
    val pendingRepairSectionId: String?,
    val rejectedPatches: Int
)

internal data class CanonicalDealUiGraphApplyResult(
    val accepted: Boolean,
    val completed: Boolean = false,
    val diagnostic: String?
)

internal const val APPEND_DEAL_UI_SECTION_TOOL_NAME = "append_deal_ui_section"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
