package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class GeneratedAppCloudLiveTest {
    @Test
    fun `live cloud artifacts satisfy production contracts when key is provided`() {
        val apiKey = System.getenv("DEEPSEEK_API_KEY").orEmpty()
        assumeTrue("DEEPSEEK_API_KEY is required for the opt-in live test", apiKey.isNotBlank())
        val request = System.getenv("GENERATED_APP_REQUEST")
            ?.takeIf(String::isNotBlank)
            ?: "Build an Arkanoid game with bricks, lives, score, and touch paddle control"
        val client = DeepSeekGenerationClient(apiKeyProvider = { apiKey })

        val uiResult = client.generate(
            DeepSeekGenerationRequest(
                model = DeepSeekGenerationModel.PRO,
                instructions = GeneratedAppPrompts.deepSeekUiInstructions(),
                input = GeneratedAppPrompts.deepSeekUiInput(request),
                maxOutputTokens = 4_096
            )
        )
        persist("ui-0.json", uiResult.output)
        val uiSource = validateOrRepairUi(client, request, uiResult.output)
        A2UiParser.parseAndValidate(uiSource)

        val dealResult = client.generate(
            DeepSeekGenerationRequest(
                model = DeepSeekGenerationModel.PRO,
                instructions = GeneratedAppPrompts.deepSeekDealInstructions(),
                input = GeneratedAppPrompts.deepSeekDealInput(request),
                maxOutputTokens = 4_096
            )
        )
        persist("deal-0.deal", dealResult.output)
        val deal = validateOrRepairDeal(client, request, dealResult.output)

        assertEquals(GeneratedAppProfile.REALTIME_CANVAS, deal.profile)
        println(
            "Live generated app: UI ${uiResult.latencyMs} ms, DEAL ${dealResult.latencyMs} ms, " +
                "${deal.source.length} DEAL chars"
        )
    }

    private fun validateOrRepairUi(
        client: DeepSeekGenerationClient,
        request: String,
        first: String
    ): String {
        var current = first
        repeat(2) { attempt ->
            val source = A2UiParser.extractDocument(current)
            val diagnostic = runCatching { A2UiParser.parseAndValidate(source) }
                .exceptionOrNull()
                ?.message
                ?: return source
            current = client.generate(
                DeepSeekGenerationRequest(
                    model = DeepSeekGenerationModel.PRO,
                    instructions = GeneratedAppPrompts.deepSeekUiInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairUiInput(request, current, diagnostic),
                    maxOutputTokens = 4_096
                )
            ).output
            persist("ui-${attempt + 1}.json", current)
        }
        return A2UiParser.extractDocument(current)
    }

    private fun validateOrRepairDeal(
        client: DeepSeekGenerationClient,
        request: String,
        first: String
    ): GeneratedDealProgram {
        var current = first
        repeat(2) { attempt ->
            val result = runCatching { GeneratedDealCompiler.compileAndValidate(current) }
            result.getOrNull()?.let { return it }
            val diagnostic = result.exceptionOrNull()?.message.orEmpty()
            val profile = GeneratedDealCompiler.detectProfile(current)
            current = client.generate(
                DeepSeekGenerationRequest(
                    model = DeepSeekGenerationModel.PRO,
                    instructions = GeneratedAppPrompts.deepSeekDealInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairDealInput(request, profile, current, diagnostic),
                    maxOutputTokens = 4_096
                )
            ).output
            persist("deal-${attempt + 1}.deal", current)
        }
        return GeneratedDealCompiler.compileAndValidate(current)
    }

    private fun persist(name: String, content: String) {
        val directory = File("build/reports/generated-app-live").apply(File::mkdirs)
        File(directory, name).writeText(content)
    }
}
