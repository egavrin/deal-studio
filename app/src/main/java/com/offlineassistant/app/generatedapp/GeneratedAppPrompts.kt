package com.offlineassistant.app.generatedapp

internal object GeneratedAppPrompts {
    fun gemmaUi(request: String): String = """
        <start_of_turn>user
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.uiDsl}

        Build presentation for this interactive mini-app:
        ${request.singleLine(300)}

        Return one complete Compact UI DSL expression and nothing else.
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

    fun deepSeekUiInstructions(): String = """
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.a2uiWire}
    """.trimIndent()

    fun deepSeekUiInput(request: String): String = """
        Build presentation for this interactive mini-app:
        ${request.singleLine(300)}

        Return one complete A2UI JSON document and nothing else.
    """.trimIndent()

    fun deepSeekRepairUiInput(
        request: String,
        invalidSource: String,
        diagnostic: String
    ): String = """
        Replace the invalid document with one complete valid A2UI JSON document.
        Original app request: ${request.singleLine(300)}
        Parser diagnostic: ${diagnostic.singleLine(300)}

        Invalid document:
        ${invalidSource.take(16000)}

        Re-read contract ${GeneratedAppLanguageContracts.A2UI_VERSION}. Return only the replacement document.
    """.trimIndent()

    fun qwenDeal(request: String, profile: GeneratedAppProfile? = null): String = """
        <|im_start|>system
        ${deepSeekDealInstructions()}<|im_end|>
        <|im_start|>user
        ${deepSeekDealInput(request, profile)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun deepSeekDealInstructions(): String = """
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.dealCore}
    """.trimIndent()

    fun deepSeekDealInput(request: String, profile: GeneratedAppProfile? = null): String = """
        Build this interactive mini-app:
        ${request.singleLine(300)}

        ${GeneratedAppLanguageContracts.dealProfiles(profile)}

        Implement the actual requested app rather than the grammar-reference app. Return one complete raw
        DEAL module and nothing else. Recheck every required global, action, reset and sandbox limit before ending.
    """.trimIndent()

    fun repairDeal(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        <|im_start|>system
        ${deepSeekDealInstructions()}<|im_end|>
        <|im_start|>user
        ${deepSeekRepairDealInput(request, profile, invalidSource, diagnostic)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun deepSeekRepairDealInput(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        Replace the invalid module with one complete valid DEAL module.
        Original app request: ${request.singleLine(300)}
        Compiler or semantic diagnostic: ${diagnostic.singleLine(400)}

        Required profile contract and grammar reference:
        ${GeneratedAppLanguageContracts.dealProfiles(profile)}

        Invalid module:
        ${invalidSource.take(12000)}

        Re-read contract ${GeneratedAppLanguageContracts.VERSION}. Preserve the requested app behavior, but do
        not preserve invalid syntax. The replacement must resolve the diagnostic and must not be byte-for-byte
        identical to the rejected module. Return only the complete replacement DEAL module.
    """.trimIndent()

    private fun String.singleLine(limit: Int): String = replace('\n', ' ').replace('\r', ' ').take(limit)
}
