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
        val expectedProfile = System.getenv("GENERATED_APP_PROFILE")
            ?.takeIf(String::isNotBlank)
            ?.let(GeneratedAppProfile::valueOf)
            ?: GeneratedAppProfile.REALTIME_CANVAS
        val model = System.getenv("GENERATED_APP_MODEL")
            ?.takeIf(String::isNotBlank)
            ?.let(DeepSeekGenerationModel::valueOf)
            ?: DeepSeekGenerationModel.FLASH
        val client = DeepSeekGenerationClient(apiKeyProvider = { apiKey })

        val dealResult = client.generate(
            DeepSeekGenerationRequest(
                model = model,
                instructions = GeneratedAppPrompts.deepSeekDealInstructions(),
                input = GeneratedAppPrompts.deepSeekDealInput(request),
                maxOutputTokens = 8_192
            )
        )
        persist("deal-0.deal", dealResult.output)
        val deal = validateOrRepairDeal(client, model, request, dealResult.output)
        val executableContract = GeneratedAppUiContract.describe(deal)
        persist("deal-contract.txt", executableContract)

        val uiResult = client.generate(
            DeepSeekGenerationRequest(
                model = model,
                instructions = GeneratedAppPrompts.deepSeekUiInstructions(),
                input = GeneratedAppPrompts.deepSeekUiInput(request, executableContract),
                maxOutputTokens = 8_192
            )
        )
        persist("ui-0.json", uiResult.output)
        val uiSource = validateOrRepairUi(client, model, request, executableContract, deal, uiResult.output)
        val ui = A2UiGeneratedUi(A2UiParser.parseAndValidate(uiSource))
        GeneratedAppContractValidator.validate(ui, deal)

        assertEquals(expectedProfile, deal.profile)
        println(
            "Live generated app: UI ${uiResult.latencyMs} ms, DEAL ${dealResult.latencyMs} ms, " +
                "${deal.source.length} DEAL chars"
        )
    }

    private fun validateOrRepairUi(
        client: DeepSeekGenerationClient,
        model: DeepSeekGenerationModel,
        request: String,
        executableContract: String,
        deal: GeneratedDealProgram,
        first: String
    ): String {
        var current = first
        var patchFailure: String? = null
        repeat(3) { attempt ->
            val source = A2UiParser.extractDocument(current)
            val parserDiagnostic = runCatching {
                GeneratedAppContractValidator.validate(
                    A2UiGeneratedUi(A2UiParser.parseAndValidate(source)),
                    deal
                )
            }
                .exceptionOrNull()
                ?.message
                ?: return source
            val diagnostic = listOfNotNull(parserDiagnostic, patchFailure).joinToString("; ")
            val patch = client.generate(
                DeepSeekGenerationRequest(
                    model = model,
                    instructions = GeneratedAppPrompts.deepSeekUiRepairInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairUiInput(
                        request,
                        current,
                        diagnostic,
                        executableContract
                    ),
                    maxOutputTokens = 2_048
                )
            ).output
            persist("ui-patch-${attempt + 1}.txt", patch)
            runCatching { GeneratedSourcePatch.apply(current, patch, 64_000) }
                .onSuccess {
                    current = it
                    patchFailure = null
                    persist("ui-${attempt + 1}.json", current)
                }
                .onFailure { patchFailure = "Previous repair patch was rejected: ${it.message}" }
        }
        return A2UiParser.extractDocument(current)
    }

    private fun validateOrRepairDeal(
        client: DeepSeekGenerationClient,
        model: DeepSeekGenerationModel,
        request: String,
        first: String
    ): GeneratedDealProgram {
        var current = first
        var patchFailure: String? = null
        repeat(3) { attempt ->
            val result = runCatching { GeneratedDealCompiler.compileAndValidate(current) }
            result.getOrNull()?.let { return it }
            val diagnostic = listOfNotNull(result.exceptionOrNull()?.message, patchFailure).joinToString("; ")
            val profile = GeneratedDealCompiler.detectProfile(current)
            val patch = client.generate(
                DeepSeekGenerationRequest(
                    model = model,
                    instructions = GeneratedAppPrompts.deepSeekDealRepairInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairDealInput(request, profile, current, diagnostic),
                    maxOutputTokens = 2_048
                )
            ).output
            persist("deal-patch-${attempt + 1}.txt", patch)
            runCatching { GeneratedSourcePatch.apply(current, patch, 48_000) }
                .onSuccess {
                    current = it
                    patchFailure = null
                    persist("deal-${attempt + 1}.deal", current)
                }
                .onFailure { patchFailure = "Previous repair patch was rejected: ${it.message}" }
        }
        return GeneratedDealCompiler.compileAndValidate(current)
    }

    private fun persist(name: String, content: String) {
        val directory = File("build/reports/generated-app-live").apply(File::mkdirs)
        File(directory, name).writeText(content)
    }
}
