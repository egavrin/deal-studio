package com.offlineassistant.app.ui

import com.offlineassistant.app.voice.AudioTranscription
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.llm.AnswerEvent
import com.offlineassistant.core.llm.AnswerRequest
import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.ResearchCancellableAnswerProvider
import com.offlineassistant.core.llm.ResearchStatus
import com.offlineassistant.core.llm.StreamingAnswerProvider
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.skills.DeterministicSlotNormalizer
import com.offlineassistant.core.skills.SkillRegistry
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.storage.InMemoryTimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `streaming answer stays one assistant message`() = runTest(dispatcher) {
        val provider = FakeStreamingProvider()
        val viewModel = viewModel(provider)
        viewModel.updateInput("Почему небо синее?")

        viewModel.sendTextAsync {}
        advanceUntilIdle()

        val assistantMessages = viewModel.state.messages.filterIsInstance<ChatMessageUi.Assistant>()
        assertEquals(2, assistantMessages.size)
        assertEquals("Потому что свет рассеивается.", assistantMessages.last().text)
        assertFalse(viewModel.state.isProcessing)
        assertEquals("deepseek_answer", viewModel.state.latestDebugInfo?.actionResult)
    }

    @Test
    fun `dictation fills composer without sending a message`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeStreamingProvider())
        val initialMessageCount = viewModel.state.messages.size

        viewModel.startVoiceRecording(VoiceCaptureMode.DICTATION)
        viewModel.beginVoiceFinalization()
        viewModel.handleStreamingVoiceRecordingAsync(
            FakeTranscriptionSession("Продиктованный текст")
        ) {}
        advanceUntilIdle()

        assertEquals("Продиктованный текст", viewModel.state.inputText)
        assertEquals(initialMessageCount, viewModel.state.messages.size)
        assertFalse(viewModel.state.isProcessing)
    }

    @Test
    fun `conversation submits transcript and waits for spoken response`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeStreamingProvider())

        viewModel.startConversation()
        viewModel.startVoiceRecording(VoiceCaptureMode.CONVERSATION)
        viewModel.beginVoiceFinalization()
        viewModel.handleStreamingVoiceRecordingAsync(
            FakeTranscriptionSession("Почему небо синее?")
        ) {}
        advanceUntilIdle()

        assertTrue(viewModel.state.conversationActive)
        assertEquals(ConversationPhase.SPEAKING, viewModel.state.conversationPhase)
        assertEquals("Почему небо синее?", viewModel.state.messages.filterIsInstance<ChatMessageUi.User>().last().text)
    }

    @Test
    fun `follow up sends previous turns as DeepSeek context`() = runTest(dispatcher) {
        val provider = FakeStreamingProvider()
        val viewModel = viewModel(provider)

        viewModel.updateInput("Почему небо синее?")
        viewModel.sendTextAsync {}
        advanceUntilIdle()
        viewModel.updateInput("А теперь короче")
        viewModel.sendTextAsync {}
        advanceUntilIdle()

        val history = provider.requests.last().history
        assertEquals(2, history.size)
        assertEquals("Почему небо синее?", history.first().text)
        assertEquals("Потому что свет рассеивается.", history.last().text)
    }

    @Test
    fun `research releases composer and replaces one progress card`() = runTest(dispatcher) {
        val provider = FakeResearchProvider()
        val viewModel = viewModel(provider, Intents.WEB_RESEARCH)
        val snapshots = mutableListOf<ChatUiState>()
        viewModel.updateInput("Исследуй рынок локальных голосовых ассистентов")

        viewModel.sendTextAsync { snapshots += it }
        advanceUntilIdle()

        assertTrue(
            snapshots.any { snapshot ->
                !snapshot.isProcessing &&
                    snapshot.messages
                        .filterIsInstance<ChatMessageUi.Assistant>()
                        .any { it.widget?.payload?.get("state")?.jsonPrimitive?.content == "running" }
            }
        )
        val researchMessages = viewModel.state.messages
            .filterIsInstance<ChatMessageUi.Assistant>()
            .filter { it.widget?.type == WidgetTypes.RESEARCH_CARD }
        assertEquals(1, researchMessages.size)
        assertEquals(
            "completed",
            researchMessages.single().widget?.payload?.get("state")?.jsonPrimitive?.content
        )
        assertFalse(viewModel.state.isProcessing)
        assertEquals("exa_research_answer", viewModel.state.latestDebugInfo?.actionResult)
    }

    private fun viewModel(
        provider: FakeAnswerProvider,
        intent: String = Intents.UNKNOWN
    ): ChatViewModel {
        val engine = AssistantEngine(
            nlu = NluParser {
                NluResult(intent, 0.99, JsonObject(emptyMap()), NluSource.RUBERT_TINY2)
            },
            answerProvider = provider,
            slotNormalizer = DeterministicSlotNormalizer(),
            skillRegistry = SkillRegistry(emptyList())
        )
        return ChatViewModel(
            assistantEngineProvider = { engine },
            answerProvider = provider,
            noteStore = InMemoryNoteStore(),
            reminderStore = InMemoryReminderStore(),
            timerStore = InMemoryTimerStore(),
            ioDispatcher = dispatcher
        )
    }
}

private interface FakeAnswerProvider :
    StreamingAnswerProvider,
    CancellableAnswerProvider

private class FakeStreamingProvider : FakeAnswerProvider {
    val requests = mutableListOf<AnswerRequest>()

    override fun answer(input: String): AnswerResult = AnswerResult("Потому что свет рассеивается.", source = "deepseek_cloud")

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult {
        onToken("Потому что ")
        onToken("свет ")
        onToken("рассеивается.")
        return answer(input)
    }

    override fun answer(request: AnswerRequest, onToken: (String) -> Unit): AnswerResult {
        requests += request
        return answer(request.input, onToken)
    }

    override fun cancel() = Unit
}

private class FakeResearchProvider :
    FakeAnswerProvider,
    ResearchCancellableAnswerProvider {
    override fun answer(input: String): AnswerResult = completedResearch()

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = completedResearch()

    override fun answer(
        request: AnswerRequest,
        onToken: (String) -> Unit,
        onEvent: (AnswerEvent) -> Unit
    ): AnswerResult {
        onEvent(AnswerEvent.ResearchStarted(RESEARCH_RUN_ID))
        onEvent(AnswerEvent.ResearchProgress(RESEARCH_RUN_ID, ResearchStatus.READING, sourceCount = 3))
        return completedResearch()
    }

    override fun cancel() = Unit

    override fun cancelResearch(runId: String) = Unit

    private fun completedResearch() = AnswerResult(
        text = "Краткий итог.",
        source = "exa_agent",
        researchRunId = RESEARCH_RUN_ID,
        widget = WidgetPayload(
            WidgetTypes.RESEARCH_CARD,
            buildJsonObject {
                put("run_id", RESEARCH_RUN_ID)
                put("state", "completed")
                put("summary", "Краткий итог.")
            }
        )
    )

    private companion object {
        const val RESEARCH_RUN_ID = "agent_run_test"
    }
}

private class FakeTranscriptionSession(
    private val transcript: String
) : StreamingTranscriptionSession {
    override fun acceptPcm16(samples: ShortArray, sampleRate: Int) = Unit

    override fun finish() = AudioTranscription(transcript, null, 25)

    override fun cancel() = Unit
}
