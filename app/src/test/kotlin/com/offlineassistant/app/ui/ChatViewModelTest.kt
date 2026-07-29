package com.offlineassistant.app.ui

import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.CancellableAnswerProvider
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun viewModel(provider: FakeStreamingProvider): ChatViewModel {
        val engine = AssistantEngine(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.99, JsonObject(emptyMap()), NluSource.RUBERT_TINY2)
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

private class FakeStreamingProvider :
    StreamingAnswerProvider,
    CancellableAnswerProvider {
    override fun answer(input: String): AnswerResult = AnswerResult("Потому что свет рассеивается.", source = "deepseek_cloud")

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult {
        onToken("Потому что ")
        onToken("свет ")
        onToken("рассеивается.")
        return answer(input)
    }

    override fun cancel() = Unit
}
