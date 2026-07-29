package com.offlineassistant.app.nlu

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import com.offlineassistant.app.models.NoOpModelRuntimeTelemetryStore
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.nlu.RussianExpressionNormalizer
import com.offlineassistant.core.nlu.RussianInverseTextNormalizer
import java.io.File
import java.nio.LongBuffer
import kotlin.math.exp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface RubertOnnxRunner {
    fun infer(text: String, modelPath: String): NluResult

    fun warmUp(modelPath: String) = Unit
}

class OnnxRubertNlu(
    private val onnxRunner: RubertOnnxRunner = OnnxRuntimeRubertRunner(),
    private val readinessProvider: () -> ModelReadiness = {
        ModelReadiness(ModelNames.RUBERT, false, "models/rubert/rubert-tiny2-intent-slots.onnx", "model file not installed")
    },
    private val telemetryStore: ModelRuntimeTelemetryStore = NoOpModelRuntimeTelemetryStore
) : NluParser {
    fun warmUp(): Result<Unit> {
        val readiness = readinessProvider()
        if (!readiness.ready) return Result.failure(IllegalStateException(readiness.detail))
        val started = System.nanoTime()
        return runCatching { onnxRunner.warmUp(readiness.location) }
            .onSuccess {
                telemetryStore.recordSuccess(ModelNames.RUBERT, ModelOperations.WARM_UP, elapsedMillis(started))
            }
            .onFailure { error ->
                telemetryStore.recordFailure(
                    ModelNames.RUBERT,
                    ModelOperations.WARM_UP,
                    elapsedMillis(started),
                    error.message ?: error::class.java.simpleName
                )
            }
    }

    override fun parse(input: String): NluResult = parse(input, readinessProvider())

    fun parse(text: String, readiness: ModelReadiness): NluResult {
        val started = System.nanoTime()
        if (!readiness.ready) {
            telemetryStore.recordFailure(
                modelName = ModelNames.RUBERT,
                operation = ModelOperations.INFERENCE,
                latencyMs = elapsedMillis(started),
                error = readiness.detail
            )
            return unavailableResult()
        }
        return try {
            onnxRunner.infer(text, readiness.location)
                .copy(source = NluSource.RUBERT_TINY2)
                .also {
                    telemetryStore.recordSuccess(
                        ModelNames.RUBERT,
                        ModelOperations.INFERENCE,
                        elapsedMillis(started)
                    )
                }
        } catch (error: Throwable) {
            telemetryStore.recordFailure(
                ModelNames.RUBERT,
                ModelOperations.INFERENCE,
                elapsedMillis(started),
                error.message ?: error::class.java.simpleName
            )
            unavailableResult()
        }
    }

    private fun unavailableResult() = NluResult(
        intent = Intents.UNKNOWN,
        confidence = 0.0,
        slots = JsonObject(emptyMap()),
        source = NluSource.UNAVAILABLE
    )

    private fun elapsedMillis(startedNanos: Long): Long = (System.nanoTime() - startedNanos).coerceAtLeast(0L) / 1_000_000L
}

class OnnxRuntimeRubertRunner(
    private val maxTokens: Int = 64,
    private val intraOpThreads: Int = 2,
    private val executionProvider: RubertExecutionProvider = RubertExecutionProvider.CPU
) : RubertOnnxRunner,
    AutoCloseable {
    private val lock = Any()
    private val environment = OrtEnvironment.getEnvironment()
    private var cachedRuntime: RubertRuntime? = null

    override fun warmUp(modelPath: String) {
        synchronized(lock) { runtimeFor(File(modelPath)) }
    }

    override fun infer(text: String, modelPath: String): NluResult = synchronized(lock) {
        val modelFile = File(modelPath)
        val runtime = runtimeFor(modelFile)
        val tensors = mutableListOf<OnnxTensor>()
        try {
            val encoded = runtime.tokenizer.encode(text, maxTokens)
            val inputs = buildMap<String, OnnxTensor> {
                runtime.session.inputNames.forEach { name ->
                    when (name) {
                        "input_ids" -> put(name, encoded.inputIds.toTensor(environment, tensors))
                        "attention_mask" -> put(name, encoded.attentionMask.toTensor(environment, tensors))
                        "token_type_ids" -> put(name, encoded.tokenTypeIds.toTensor(environment, tensors))
                    }
                }
                if (isEmpty()) {
                    put("input_ids", encoded.inputIds.toTensor(environment, tensors))
                    put("attention_mask", encoded.attentionMask.toTensor(environment, tensors))
                    put("token_type_ids", encoded.tokenTypeIds.toTensor(environment, tensors))
                }
            }

            runtime.session.run(inputs).use { output ->
                val intentLogits = output.intentLogits()
                val intentIndex = intentLogits.argmax()
                val modelSlots = output.slotLogits()
                    ?.let { slotLogits -> decodeSlots(text, encoded, slotLogits, runtime.slotLabels) }
                    ?: JsonObject(emptyMap())
                return NluResult(
                    intent = runtime.intentLabels.labelAt(intentIndex),
                    confidence = intentLogits.softmaxConfidence(intentIndex),
                    slots = modelSlots,
                    source = NluSource.RUBERT_TINY2
                )
            }
        } finally {
            tensors.forEach(OnnxTensor::close)
        }
    }

    override fun close() = synchronized(lock) {
        cachedRuntime?.session?.close()
        cachedRuntime = null
    }

    private fun runtimeFor(modelFile: File): RubertRuntime {
        val signature = "${modelFile.absolutePath}:${modelFile.length()}:${modelFile.lastModified()}"
        cachedRuntime?.takeIf { it.signature == signature }?.let { return it }
        cachedRuntime?.session?.close()
        val modelDir = modelFile.parentFile ?: error("RuBERT model must have a parent directory")
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setIntraOpNumThreads(if (executionProvider == RubertExecutionProvider.XNNPACK) 1 else intraOpThreads)
            setInterOpNumThreads(1)
            when (executionProvider) {
                RubertExecutionProvider.CPU -> Unit

                RubertExecutionProvider.XNNPACK -> addXnnpack(
                    mapOf("intra_op_num_threads" to intraOpThreads.toString())
                )

                RubertExecutionProvider.NNAPI -> addNnapi()

                RubertExecutionProvider.QNN_HTP -> addQnn(
                    mapOf(
                        "backend_path" to "libQnnHtp.so",
                        "htp_performance_mode" to "burst"
                    )
                )
            }
        }
        val session = options.use { environment.createSession(modelFile.absolutePath, it) }
        return RubertRuntime(
            signature = signature,
            session = session,
            tokenizer = WordPieceTokenizer(File(modelDir, "vocab.txt")),
            intentLabels = LabelSet(File(modelDir, "intent_labels.txt")),
            slotLabels = LabelSet(File(modelDir, "slot_labels.txt"))
        ).also { cachedRuntime = it }
    }

    private fun LongArray.toTensor(environment: OrtEnvironment, owner: MutableList<OnnxTensor>): OnnxTensor {
        val tensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(this), longArrayOf(1, size.toLong()))
        owner += tensor
        return tensor
    }
}

enum class RubertExecutionProvider {
    CPU,
    XNNPACK,
    NNAPI,
    QNN_HTP
}

private data class RubertRuntime(
    val signature: String,
    val session: OrtSession,
    val tokenizer: WordPieceTokenizer,
    val intentLabels: LabelSet,
    val slotLabels: LabelSet
)

private data class EncodedText(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
    val pieces: List<TokenPiece>
)

private data class TokenPiece(
    val id: Int,
    val start: Int,
    val end: Int
)

private class WordPieceTokenizer(vocabFile: File) {
    private val vocab: Map<String, Int> = vocabFile
        .takeIf { it.isFile }
        ?.readLines()
        ?.mapIndexed { index, token -> token to index }
        ?.toMap()
        ?: error("Missing RuBERT vocab file: ${vocabFile.absolutePath}")

    private val clsId = tokenId("[CLS]")
    private val sepId = tokenId("[SEP]")
    private val padId = vocab["[PAD]"] ?: 0
    private val unkId = tokenId("[UNK]")

    fun encode(text: String, maxTokens: Int): EncodedText {
        val contentPieces = tokenize(text).take(maxTokens - 2)
        val pieces = buildList {
            add(TokenPiece(clsId, -1, -1))
            addAll(contentPieces)
            add(TokenPiece(sepId, -1, -1))
        }
        val inputIds = LongArray(maxTokens) { index -> pieces.getOrNull(index)?.id?.toLong() ?: padId.toLong() }
        val attentionMask = LongArray(maxTokens) { index -> if (index < pieces.size) 1L else 0L }
        val tokenTypeIds = LongArray(maxTokens)
        return EncodedText(inputIds, attentionMask, tokenTypeIds, pieces)
    }

    private fun tokenize(text: String): List<TokenPiece> = Regex("[\\p{L}\\p{N}]+|[^\\s]").findAll(text).flatMap { match ->
        wordPieces(match.value, match.range.first)
    }.toList()

    private fun wordPieces(token: String, tokenStart: Int): List<TokenPiece> {
        val normalized = token
        vocab[normalized]?.let { return listOf(TokenPiece(it, tokenStart, tokenStart + normalized.length)) }
        val pieces = mutableListOf<TokenPiece>()
        var start = 0
        while (start < normalized.length) {
            var end = normalized.length
            var match: Int? = null
            while (start < end) {
                val piece = if (start == 0) normalized.substring(start, end) else "##${normalized.substring(start, end)}"
                match = vocab[piece]
                if (match != null) break
                end--
            }
            if (match == null) return listOf(TokenPiece(unkId, tokenStart, tokenStart + normalized.length))
            pieces += TokenPiece(match, tokenStart + start, tokenStart + end)
            start = end
        }
        return pieces
    }

    private fun tokenId(token: String): Int = vocab[token] ?: error("Missing required vocab token: $token")
}

private class LabelSet(file: File) {
    private val labels = file
        .takeIf { it.isFile }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: error("Missing RuBERT intent labels file: ${file.absolutePath}")

    fun labelAt(index: Int): String = labels.getOrElse(index) { Intents.UNKNOWN }
}

internal data class SlotSpan(
    val name: String,
    val start: Int,
    val end: Int
)

private fun decodeSlots(
    text: String,
    encoded: EncodedText,
    slotLogits: Array<FloatArray>,
    slotLabels: LabelSet
): JsonObject {
    val spans = mutableListOf<SlotSpan>()
    var activeName: String? = null
    var activeStart = -1
    var activeEnd = -1

    fun flush() {
        val name = activeName
        if (name != null && activeStart >= 0 && activeEnd > activeStart) {
            spans += SlotSpan(name, activeStart, activeEnd)
        }
        activeName = null
        activeStart = -1
        activeEnd = -1
    }

    encoded.pieces.forEachIndexed { index, piece ->
        if (piece.start < 0 || index >= slotLogits.size) return@forEachIndexed
        val label = slotLabels.labelAt(slotLogits[index].argmax())
        if (label == "O" || !label.contains("-")) {
            flush()
            return@forEachIndexed
        }
        val prefix = label.substringBefore("-")
        val name = label.substringAfter("-")
        if (prefix == "B" || activeName != name || piece.start > activeEnd + 1) {
            flush()
            activeName = name
            activeStart = piece.start
            activeEnd = piece.end
        } else {
            activeEnd = piece.end
        }
    }
    flush()

    return normalizeSlotSpans(text, spans)
}

internal fun normalizeSlotSpans(text: String, spans: List<SlotSpan>): JsonObject = buildJsonObject {
    spans.forEach { span ->
        val raw = text.substring(span.start, span.end).trim().trimEnd('?', '.', '!', ',')
        if (raw.none(Char::isLetterOrDigit)) return@forEach
        when (span.name) {
            "duration" -> normalizeDurationSeconds(raw)?.let { put("duration_seconds", it) }

            "time" -> normalizeTime(raw)?.let { put("time", it) }

            "date", "due_at", "start_at", "end_at", "range_start", "range_end" -> put(span.name, raw)

            "location" -> put("location", raw.capitalizeForSlot())

            "note_text" -> put("text", raw)

            "reminder_text" -> put("reminder_text", raw)

            "app_name" -> put("app_name", raw)

            "expression" -> RussianExpressionNormalizer.normalize(raw)?.let { put("expression", it) }

            "percent" -> Regex("\\d{1,3}").find(RussianInverseTextNormalizer.normalizeNumbers(raw))
                ?.value?.let { put("percent", it) }

            else -> put(span.name, raw)
        }
    }
}

private fun normalizeDurationSeconds(raw: String): Int? {
    val lower = RussianInverseTextNormalizer.normalizeNumbers(raw.lowercase())
    val value = Regex("\\d+").find(lower)?.value?.toIntOrNull()
        ?: if (lower.trim() == "час") 1 else null
    return when {
        value == null -> null
        lower.contains("час") -> value * 3600
        else -> value * 60
    }
}

private fun normalizeTime(raw: String): String? {
    val normalized = RussianInverseTextNormalizer.normalizeNumbers(raw.lowercase())
    val match = Regex("([01]?\\d|2[0-3])[:.]([0-5]\\d)").find(normalized)
    return match?.let {
        "%02d:%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    } ?: RussianInverseTextNormalizer.normalizeSpokenTime(raw)
}

private fun String.capitalizeForSlot(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun OrtSession.Result.intentLogits(): FloatArray {
    val entries = iterator().asSequence().toList()
    val value = entries.firstOrNull { it.key.contains("intent", ignoreCase = true) }?.value
        ?: entries.firstOrNull()?.value
        ?: error("RuBERT ONNX result did not contain intent logits")
    return value.asFloatVector()
}

private fun OrtSession.Result.slotLogits(): Array<FloatArray>? {
    val entries = iterator().asSequence().toList()
    val value = entries.firstOrNull { it.key.contains("slot", ignoreCase = true) }?.value
        ?: return null
    return value.asFloatMatrix()
}

private fun OnnxValue.asFloatVector(): FloatArray = when (val raw = value) {
    is FloatArray -> raw

    is Array<*> -> {
        val first = raw.firstOrNull() ?: error("Empty ONNX tensor output")
        when (first) {
            is FloatArray -> first
            is Array<*> -> first.firstOrNull() as? FloatArray ?: error("Unsupported nested ONNX tensor output")
            else -> error("Unsupported ONNX tensor output: ${first::class.java.name}")
        }
    }

    else -> error("Unsupported ONNX tensor output: ${raw?.javaClass?.name}")
}

private fun OnnxValue.asFloatMatrix(): Array<FloatArray> = when (val raw = value) {
    is Array<*> -> {
        val first = raw.firstOrNull() ?: error("Empty ONNX tensor output")
        when (first) {
            is FloatArray -> raw.map { it as FloatArray }.toTypedArray()
            is Array<*> -> first.map { it as FloatArray }.toTypedArray()
            else -> error("Unsupported ONNX matrix output: ${first::class.java.name}")
        }
    }

    else -> error("Unsupported ONNX matrix output: ${raw?.javaClass?.name}")
}

private fun FloatArray.argmax(): Int {
    var bestIndex = 0
    var bestValue = this.firstOrNull() ?: return 0
    for (index in 1 until size) {
        if (this[index] > bestValue) {
            bestValue = this[index]
            bestIndex = index
        }
    }
    return bestIndex
}

private fun FloatArray.softmaxConfidence(index: Int): Double {
    if (isEmpty()) return 0.0
    val max = maxOrNull() ?: 0f
    val exps = map { exp((it - max).toDouble()) }
    val denominator = exps.sum().takeIf { it > 0.0 } ?: return 0.0
    return exps.getOrElse(index) { 0.0 } / denominator
}
