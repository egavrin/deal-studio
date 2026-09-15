package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import com.offlineassistant.deepseek.DeepSeekGenerationResult
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live comparison. It reads the credential only from the invoking process and writes generated sources plus
 * non-secret telemetry beneath the ignored app/build directory. Invoke with `DEEPSEEK_API_KEY=... ./gradlew :app:testDebugUnitTest
 * --tests ...LiveGenerationComparisonBenchmarkTest`.
 */
class LiveGenerationComparisonBenchmarkTest {
    @Test
    fun compareCanonicalDealAndExperimentalHtml5AcrossScenarios() {
        val apiKey = System.getenv("DEEPSEEK_API_KEY").orEmpty().trim()
        assumeTrue("Set DEEPSEEK_API_KEY to run the live comparison", apiKey.isNotBlank())

        val client = DeepSeekGenerationClient(apiKeyProvider = { apiKey })
        val outputRoot = File(
            requireNotNull(System.getProperty("offlineAssistant.repoRoot")),
            "app/build/live-generation-comparison/" +
                System.getenv("LIVE_BENCHMARK_OUTPUT_ID").orEmpty().ifBlank { System.currentTimeMillis().toString() }
        ).apply { mkdirs() }
        val scenarios = listOf(
            "medication_tracker" to
                "我最近要吃药，每天饭后半小时内吃一颗，请帮我生成一个吃药管理应用，可以统计我历史上有没有按时间吃药，可以反馈给医生。",
            "percent_calculator" to
                "сделай простое приложение для расчёта процентов и объяснения, как они устроены. чтобы можно было задать процент чего-то и получить в обе стороны результат.",
            "unit_converter" to
                "Сделай компактное приложение-конвертер: длина, вес, температура и объём. Пользователь вводит число, выбирает исходную и целевую единицы и сразу видит результат с коротким объяснением формулы. Нужны история последних конвертаций и кнопка очистки истории.",
            "habit_tracker" to
                "Создай приложение для привычек. Я добавляю привычку с названием, днями недели и целью по количеству выполнений. На главном экране хочу видеть сегодняшние привычки и кнопку «Выполнено», а также текущую серию. Нужны редактирование, история отметок и недельная статистика без выдуманных записей.",
            "household_budget" to
                "Сделай личное приложение для домашнего бюджета. Я добавляю доходы и расходы с суммой, категорией, датой и заметкой; могу создавать и редактировать категории и месячный лимит. На главном экране должны быть остаток месяца, ближайшие лимиты и быстрое добавление операции. Нужны история с фильтром, сводка по категориям и отчёт для экспорта. Начни с честного пустого состояния.",
            "travel_planner" to
                "Создай приложение для планирования поездки на несколько дней. Пользователь создаёт поездку с городами и датами, добавляет в дни события с временем, местом, заметкой и бюджетом, ведёт packing checklist и отмечает выполненные пункты. Нужны главный экран ближайшего события, маршрут по дням, общий бюджет, поиск по плану, редактирование и отчёт, которым можно поделиться. Не создавай демонстрационные поездки или события без ввода пользователя.",
            "arkanoid" to
                "Build a polished adaptive Arkanoid game named Neon Arkanoid. Use a retained logical canvas with a dark arcade theme, a ball, a touch-controlled paddle and a grid of destructible bricks. Animate continuously with frame-clock input. Implement bounded ball motion, wall and paddle bounce, brick collision, score, three lives, win and game-over states, and a reset action. Pointer input must move the paddle. Keep every game rule and state transition in app.deal; Deal UI only renders state and dispatches nominal actions. The logical canvas and pointer coordinate space must match and scale to any device. Use English text."
        )
        val selectedScenario = System.getenv("LIVE_BENCHMARK_SCENARIO").orEmpty().trim()
        val selectedScenarioNames = System.getenv("LIVE_BENCHMARK_SCENARIOS").orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val repetitions = System.getenv("LIVE_BENCHMARK_REPETITIONS").orEmpty().toIntOrNull() ?: REPETITIONS
        val runOffset = System.getenv("LIVE_BENCHMARK_RUN_OFFSET").orEmpty().toIntOrNull() ?: 0
        require(repetitions in 1..MAX_REPETITIONS) {
            "LIVE_BENCHMARK_REPETITIONS must be 1..$MAX_REPETITIONS"
        }
        require(runOffset >= 0) { "LIVE_BENCHMARK_RUN_OFFSET must not be negative" }
        val selectedScenarios = scenarios.filter { (scenario, _) ->
            when {
                selectedScenarioNames.isNotEmpty() -> scenario in selectedScenarioNames
                selectedScenario.isNotBlank() -> scenario == selectedScenario
                else -> true
            }
        }
        require(selectedScenarios.isNotEmpty()) {
            "Unknown LIVE_BENCHMARK_SCENARIO '$selectedScenario' / LIVE_BENCHMARK_SCENARIOS '$selectedScenarioNames'"
        }
        var pairIndex = scenarios.indexOfFirst { it.first == selectedScenarios.first().first } + runOffset
        selectedScenarios.forEach { (scenario, request) ->
            repeat(repetitions) { zeroBasedRun ->
                compare(
                    client = client,
                    outputRoot = outputRoot,
                    scenario = scenario,
                    run = runOffset + zeroBasedRun + 1,
                    canonicalFirst = pairIndex++ % 2 == 0,
                    request = request
                )
            }
        }
        writeSummary(outputRoot)
        println("LIVE_GENERATION_COMPARISON_OUTPUT ${outputRoot.absolutePath}")
    }

    private fun compare(
        client: DeepSeekGenerationClient,
        outputRoot: File,
        scenario: String,
        run: Int,
        canonicalFirst: Boolean,
        request: String
    ) {
        lateinit var canonical: CanonicalAttempt
        lateinit var html5: Html5Result
        if (canonicalFirst) {
            canonical = generateCanonical(client, request)
            html5 = generateHtml5(client, request)
        } else {
            html5 = generateHtml5(client, request)
            canonical = generateCanonical(client, request)
        }

        val runDirectory = File(outputRoot, "$scenario/run-$run").apply { mkdirs() }
        canonical.bundle?.let { bundle ->
            File(runDirectory, "canonical.deal").writeText(bundle.deal)
        } ?: File(runDirectory, "canonical.raw.txt").writeText(canonical.rawOutput)
        File(runDirectory, "html5.html").writeText(html5.html)
        val result = buildJsonObject {
            put("scenario", scenario)
            put("run", run)
            put("order", if (canonicalFirst) "canonical-first" else "html5-first")
            put("model", DeepSeekGenerationModel.FLASH.apiId)
            put("maxOutputTokens", OUTPUT_LIMIT)
            put(
                "canonicalValidation",
                canonical.error ?: "raw-deal-framing-parsed; Android DEAL and auto-UI compiler not run in JVM benchmark"
            )
            put("canonicalSuccess", canonical.error == null)
            canonical.error?.let { put("canonicalFailure", it) }
            put("html5Validation", "standalone-html-normalized")
            put("canonicalWallMs", canonical.generation.latencyMs)
            canonical.generation.timeToFirstTokenMs?.let { put("canonicalFirstMs", it) }
            canonical.generation.inputTokens?.let { put("canonicalInputTokens", it) }
            canonical.generation.cachedInputTokens?.let { put("canonicalCachedInputTokens", it) }
            canonical.generation.outputTokens?.let { put("canonicalOutputTokens", it) }
            canonical.bundle?.let { bundle ->
                put("canonicalDealBytes", bundle.deal.encodeToByteArray().size)
            } ?: put("canonicalRawBytes", canonical.rawOutput.encodeToByteArray().size)
            put("html5WallMs", html5.generation.latencyMs)
            html5.generation.timeToFirstTokenMs?.let { put("html5FirstMs", it) }
            html5.generation.inputTokens?.let { put("html5InputTokens", it) }
            html5.generation.cachedInputTokens?.let { put("html5CachedInputTokens", it) }
            html5.generation.outputTokens?.let { put("html5OutputTokens", it) }
            put("html5Bytes", html5.html.encodeToByteArray().size)
            estimatedUsd(canonical.generation).let { put("canonicalEstimatedUsd", it) }
            estimatedUsd(html5.generation).let { put("html5EstimatedUsd", it) }
        }
        val encodedResult = JSON.encodeToString(result)
        File(runDirectory, "result.json").writeText(JSON_PRETTY.encodeToString(result) + "\n")
        File(outputRoot, "results.jsonl").appendText(encodedResult + "\n")
        println("LIVE_GENERATION_COMPARISON $encodedResult")
    }

    private fun writeSummary(outputRoot: File) {
        val rows = File(outputRoot, "results.jsonl").readLines().filter(String::isNotBlank)
            .map { JSON.parseToJsonElement(it).jsonObject }
        val csv = buildString {
            appendLine(
                "scenario,runs,canonical_success_rate,canonical_wall_p50_ms,canonical_wall_min_ms," +
                    "canonical_wall_max_ms,canonical_ttft_p50_ms,canonical_input_p50_tokens," +
                    "canonical_cached_input_p50_tokens,canonical_output_p50_tokens,canonical_estimated_cost_p50_usd," +
                    "html5_success_rate,html5_wall_p50_ms,html5_wall_min_ms,html5_wall_max_ms,html5_ttft_p50_ms," +
                    "html5_input_p50_tokens,html5_cached_input_p50_tokens,html5_output_p50_tokens," +
                    "html5_estimated_cost_p50_usd"
            )
            rows.groupBy { it.getValue("scenario").jsonPrimitive.content }.toSortedMap().forEach { (scenario, group) ->
                val canonicalWall = group.map { it.getValue("canonicalWallMs").jsonPrimitive.long }
                val canonicalTtft = group.longMetric("canonicalFirstMs")
                val canonicalInput = group.longMetric("canonicalInputTokens")
                val canonicalCachedInput = group.longMetric("canonicalCachedInputTokens")
                val canonicalOutput = group.longMetric("canonicalOutputTokens")
                val canonicalCost = group.estimatedCostMetric("canonical")
                val htmlWall = group.map { it.getValue("html5WallMs").jsonPrimitive.long }
                val htmlTtft = group.longMetric("html5FirstMs")
                val htmlInput = group.longMetric("html5InputTokens")
                val htmlCachedInput = group.longMetric("html5CachedInputTokens")
                val htmlOutput = group.longMetric("html5OutputTokens")
                val htmlCost = group.estimatedCostMetric("html5")
                appendLine(
                    listOf(
                        scenario, group.size, successRate(group, "canonicalSuccess"), median(canonicalWall), canonicalWall.min(), canonicalWall.max(),
                        median(canonicalTtft), median(canonicalInput), median(canonicalCachedInput), median(canonicalOutput),
                        formatUsd(median(canonicalCost)), successRate(group, "html5Success"), median(htmlWall), htmlWall.min(), htmlWall.max(),
                        median(htmlTtft), median(htmlInput), median(htmlCachedInput), median(htmlOutput), formatUsd(median(htmlCost))
                    ).joinToString(",")
                )
            }
        }
        File(outputRoot, "summary.csv").writeText(csv)
        File(outputRoot, "summary.json").writeText(
            "{\"rows\":${rows.size},\"summaryCsv\":\"summary.csv\",\"canonicalSuccess\":\"raw framing parsed; Android compiler not run in JVM benchmark\",\"pricing\":{\"model\":\"deepseek-v4-flash\",\"currency\":\"USD\",\"cacheHitPerMillion\":$CACHE_HIT_USD_PER_MILLION,\"cacheMissPerMillion\":$CACHE_MISS_USD_PER_MILLION,\"outputPerMillion\":$OUTPUT_USD_PER_MILLION}}\n"
        )
    }

    private fun median(values: List<Long>): Long = values.sorted().let { it[(it.size - 1) / 2] }
    private fun median(values: List<Double>): Double = values.sorted().let { it[(it.size - 1) / 2] }

    private fun List<kotlinx.serialization.json.JsonObject>.longMetric(name: String): List<Long> = mapNotNull { it[name]?.jsonPrimitive?.long }

    private fun List<kotlinx.serialization.json.JsonObject>.estimatedCostMetric(prefix: String): List<Double> = map { row ->
        row["${prefix}EstimatedUsd"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: estimatedUsd(
                input = row["${prefix}InputTokens"]?.jsonPrimitive?.long ?: 0L,
                cached = row["${prefix}CachedInputTokens"]?.jsonPrimitive?.long ?: 0L,
                output = row["${prefix}OutputTokens"]?.jsonPrimitive?.long ?: 0L
            )
    }

    private fun successRate(rows: List<kotlinx.serialization.json.JsonObject>, field: String): String {
        val successful = rows.count { it[field]?.jsonPrimitive?.booleanOrNull ?: true }
        return "%.2f".format(java.util.Locale.US, successful.toDouble() / rows.size)
    }

    private fun estimatedUsd(generation: DeepSeekGenerationResult): Double {
        val input = generation.inputTokens ?: return 0.0
        return estimatedUsd(input.toLong(), (generation.cachedInputTokens ?: 0).toLong(), (generation.outputTokens ?: 0).toLong())
    }

    private fun estimatedUsd(input: Long, cached: Long, output: Long): Double {
        val boundedCached = cached.coerceIn(0, input)
        val uncached = input - boundedCached
        return (
            boundedCached * CACHE_HIT_USD_PER_MILLION +
                uncached * CACHE_MISS_USD_PER_MILLION +
                output * OUTPUT_USD_PER_MILLION
            ) / 1_000_000.0
    }

    private fun formatUsd(value: Double): String = "%.8f".format(java.util.Locale.US, value)

    private fun generateCanonical(client: DeepSeekGenerationClient, request: String): CanonicalAttempt {
        val generation = client.generate(
            DeepSeekGenerationRequest(
                model = DeepSeekGenerationModel.FLASH,
                instructions = CanonicalBundlePrompts.instructions,
                input = CanonicalBundlePrompts.initialInput(request),
                maxOutputTokens = OUTPUT_LIMIT,
                temperature = 0.0
            )
        )
        val parsed = runCatching { CanonicalBundleProtocol.parseDeal(generation.output) }
        return CanonicalAttempt(
            bundle = parsed.getOrNull()?.let { CanonicalSourceBundle(deal = it, dealUi = "") },
            rawOutput = generation.output,
            generation = generation,
            error = parsed.exceptionOrNull()?.message
        )
    }

    private fun generateHtml5(client: DeepSeekGenerationClient, request: String): Html5Result {
        val generation = client.generate(
            DeepSeekGenerationRequest(
                model = DeepSeekGenerationModel.FLASH,
                instructions = ExperimentalHtml5Prompt.INSTRUCTIONS,
                input = ExperimentalHtml5Prompt.input(request),
                maxOutputTokens = OUTPUT_LIMIT,
                temperature = 0.0
            )
        )
        return Html5Result(normalizeExperimentalHtml(generation.output), generation)
    }

    private data class CanonicalAttempt(
        val bundle: CanonicalSourceBundle?,
        val rawOutput: String,
        val generation: DeepSeekGenerationResult,
        val error: String?
    )

    private data class Html5Result(
        val html: String,
        val generation: DeepSeekGenerationResult
    )

    private companion object {
        const val REPETITIONS = 3
        const val MAX_REPETITIONS = 5

        // Shared production-client cap for both direct HTML and canonical tool generation.
        const val OUTPUT_LIMIT = 16_384

        // Official DeepSeek V4-Flash off-peak USD / 1M tokens, retrieved 2026-09-14.
        const val CACHE_HIT_USD_PER_MILLION = 0.007
        const val CACHE_MISS_USD_PER_MILLION = 0.22
        const val OUTPUT_USD_PER_MILLION = 0.66
        val JSON = Json
        val JSON_PRETTY = Json { prettyPrint = true }
    }
}
