package com.offlineassistant.app.ui

import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.AudioTranscription
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.llm.FallbackParse
import com.offlineassistant.core.llm.FallbackParser
import com.offlineassistant.core.llm.StreamingFallbackParser
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.SpeechStopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempFile

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelFallbackTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun streamingAsrPartialUpdatesPreviewWithoutAppendingOrExecutingMessage() {
        val viewModel = ChatViewModel()
        viewModel.startVoiceRecording()
        val messageCount = viewModel.state.messages.size

        val state = viewModel.updateStreamingTranscript("поставь таймер")

        assertEquals("поставь таймер", state.transcriptPreview)
        assertEquals(true, state.isRecording)
        assertEquals(messageCount, state.messages.size)
    }

    @Test
    fun lowConfidenceCommandDoesNotRenderActionWidgetFromFallbackParser() {
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.STUB)
            },
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.COMMAND,
                    intent = Intents.SET_TIMER,
                    confidence = 0.9,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    answer = "Действия выбирает RuBERT, а Qwen отвечает только текстом.",
                )
            },
        )

        viewModel.updateInput("обратный отсчет на пять минут")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, assistant.widget?.type)
        assertEquals(Intents.UNKNOWN, assistant.debug?.intent)
        assertEquals(true, assistant.debug?.fallbackUsed)
        assertEquals(NluSource.FALLBACK_LLM, assistant.debug?.nluSource)
    }

    @Test
    fun fallbackThresholdProviderChangesRoutingWithoutRecreatingViewModel() {
        var threshold = 0.95
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.90,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    source = NluSource.RUBERT_TINY2,
                )
            },
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.ANSWER,
                    confidence = 0.8,
                    answer = "Команда ниже текущего порога уверенности.",
                )
            },
            fallbackThresholdProvider = { threshold },
        )

        viewModel.updateInput("Поставь таймер на 5 минут")
        val firstState = viewModel.sendText()
        val firstAssistant = firstState.messages.last() as ChatMessageUi.Assistant

        threshold = 0.80
        viewModel.updateInput("Поставь таймер на 5 минут")
        val secondState = viewModel.sendText()
        val secondAssistant = secondState.messages.last() as ChatMessageUi.Assistant

        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, firstAssistant.widget?.type)
        assertEquals(true, firstAssistant.debug?.fallbackUsed)
        assertEquals(WidgetTypes.TIMER_CARD, secondAssistant.widget?.type)
        assertEquals(false, secondAssistant.debug?.fallbackUsed)
    }

    @Test
    fun sendTextAppendsInspectableDebugHistoryEntry() {
        val viewModel = ChatViewModel()

        viewModel.updateInput("Поставь таймер на две минуты")
        viewModel.sendText()

        val debug = viewModel.state.debugHistory.single()
        assertEquals("Поставь таймер на две минуты", debug.transcript)
        assertEquals(Intents.SET_TIMER, debug.intent)
        assertEquals(NluSource.STUB, debug.nluSource)
        assertEquals("success", debug.actionResult)
        assertEquals(120, debug.slots?.get("duration_seconds")?.jsonPrimitive?.int)
        assertEquals(Intents.SET_TIMER, debug.normalizedCommand?.get("intent")?.jsonPrimitive?.content)
        assertEquals(120, debug.normalizedCommand?.get("slots")?.jsonObject?.get("duration_seconds")?.jsonPrimitive?.int)
        assertEquals(false, debug.fallbackUsed)
        assertNotNull(debug.latencyMs?.total)
    }

    @Test
    fun clearChatHistoryKeepsOnlyWelcomeMessageAndClearsDebugHistory() {
        val viewModel = ChatViewModel()

        viewModel.updateInput("Поставь таймер на две минуты")
        viewModel.sendText()
        val state = viewModel.clearChatHistory()

        assertEquals(1, state.messages.size)
        assertEquals("welcome", state.messages.single().id)
        assertEquals(null, state.latestDebugInfo)
        assertEquals(emptyList<com.offlineassistant.core.contracts.DebugInfo>(), state.debugHistory)
        assertEquals(false, state.isProcessing)
    }

    @Test
    fun clearNotesAndRemindersDeletesPersistedItems() {
        val noteStore = InMemoryNoteStore()
        val reminderStore = InMemoryReminderStore()
        val viewModel = ChatViewModel(noteStore = noteStore, reminderStore = reminderStore)
        noteStore.create("купить молоко", "2026-07-09T12:00:00+03:00")
        reminderStore.create("проверить духовку", "2026-07-10T09:00:00+03:00")

        val state = viewModel.clearNotesAndReminders()

        assertEquals(emptyList<com.offlineassistant.core.storage.StoredNote>(), noteStore.list())
        assertEquals(emptyList<com.offlineassistant.core.storage.StoredReminder>(), reminderStore.list())
        assertEquals("Заметки и напоминания очищены.", (state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun noteCopyActionCopiesPersistedNoteTextThroughPlatformActions() {
        val noteStore = InMemoryNoteStore()
        val platformActions = FakePlatformActions()
        val viewModel = ChatViewModel(noteStore = noteStore, platformActions = platformActions)
        val note = noteStore.create("купить молоко", "2026-07-09T12:00:00+03:00")

        viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.NOTE_COPY,
                widgetType = WidgetTypes.NOTE_CARD,
                payload = mapOf("note_id" to note.id),
            ),
        )

        assertEquals("купить молоко", platformActions.copiedText)
        assertEquals("Заметка скопирована.", (viewModel.state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun noteEditActionLoadsExistingNoteIntoInputAndSendUpdatesPersistedNote() {
        val noteStore = InMemoryNoteStore()
        val viewModel = ChatViewModel(noteStore = noteStore)
        val note = noteStore.create("купить молоко", "2026-07-09T12:00:00+03:00")

        viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.NOTE_EDIT,
                widgetType = WidgetTypes.NOTE_CARD,
                payload = mapOf("note_id" to note.id),
            ),
        )
        assertEquals("купить молоко", viewModel.state.inputText)

        viewModel.updateInput("купить молоко и яйца")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(1, noteStore.list().size)
        assertEquals("купить молоко и яйца", noteStore.list().single().text)
        assertEquals("Заметка обновлена.", assistant.text)
        assertEquals(WidgetTypes.NOTE_CARD, assistant.widget?.type)
        assertEquals("купить молоко и яйца", assistant.widget?.payload?.get("text")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun calculatorCopyActionCopiesResultThroughPlatformActions() {
        val platformActions = FakePlatformActions()
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.CALCULATOR_COPY,
                widgetType = WidgetTypes.CALCULATOR_CARD,
                payload = mapOf("result" to "4625"),
            ),
        )

        assertEquals("4625", platformActions.copiedText)
        assertEquals("Результат скопирован.", (viewModel.state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun openAppActionUsesPackageNameWhenPresent() {
        val platformActions = FakePlatformActions(openAppResult = true)
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.OPEN_APP,
                widgetType = WidgetTypes.OPEN_APP_CARD,
                payload = mapOf("package_name" to "com.android.settings", "app_name" to "Settings"),
            ),
        )

        assertEquals("com.android.settings", platformActions.openedPackageName)
        assertEquals("Settings", platformActions.openedAppName)
        assertEquals("Открываю Settings.", (viewModel.state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun openAppCommandAddsAlternativesWhenSeveralLaunchableAppsMatch() {
        val platformActions = FakePlatformActions(
            launchableApps = listOf(
                AppCandidate(appName = "Telegram", packageName = "org.telegram.messenger"),
                AppCandidate(appName = "Telegram X", packageName = "org.thunderdog.challegram"),
            ),
        )
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Открой Telegram")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant
        val payload = assistant.widget?.payload
        val alternatives = payload?.get("alternatives")?.jsonArray

        assertEquals(WidgetTypes.OPEN_APP_CARD, assistant.widget?.type)
        assertEquals("confirmation_required", payload?.get("state")?.jsonPrimitive?.contentOrNull)
        assertEquals(2, alternatives?.size)
        assertEquals("Telegram", alternatives?.get(0)?.jsonObject?.get("app_name")?.jsonPrimitive?.contentOrNull)
        assertEquals("org.telegram.messenger", alternatives?.get(0)?.jsonObject?.get("package_name")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun helpExampleActionInsertsTextIntoInput() {
        val viewModel = ChatViewModel()

        val state = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.HELP_EXAMPLE,
                widgetType = WidgetTypes.HELP_CARD,
                payload = mapOf("text" to "Поставь таймер на 5 минут"),
            ),
        )

        assertEquals("Поставь таймер на 5 минут", state.inputText)
        assertEquals("Вставил пример в команду.", (state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun clarificationSuggestionActionInsertsTextIntoInput() {
        val viewModel = ChatViewModel()

        val state = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.CLARIFICATION_SUGGESTION,
                widgetType = WidgetTypes.CLARIFICATION_CARD,
                payload = mapOf("text" to "На 7:30"),
            ),
        )

        assertEquals("На 7:30", state.inputText)
        assertEquals("Вставил уточнение в команду.", (state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun errorSuggestionSettingsActionShowsNavigationFeedback() {
        val viewModel = ChatViewModel()

        val state = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.ERROR_SUGGESTION,
                widgetType = WidgetTypes.ERROR_CARD,
                payload = mapOf("text" to "Открыть настройки", "target" to "settings"),
            ),
        )

        assertEquals("Открываю настройки.", (state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun voiceRecordingAppendsTranscriptAndAsrLatencyToDebugHistory() {
        val viewModel = ChatViewModel()

        viewModel.handleVoiceRecording(
            audioFile = File("voice.wav"),
            transcriber = FakeAudioTranscriber(
                text = "Поставь таймер на 5 минут",
                latencyMs = 37,
            ),
        )

        val user = viewModel.state.messages.filterIsInstance<ChatMessageUi.User>().single()
        val debug = viewModel.state.debugHistory.single()

        assertEquals("voice", user.source)
        assertEquals("Поставь таймер на 5 минут", user.text)
        assertEquals("Поставь таймер на 5 минут", viewModel.state.transcriptPreview)
        assertEquals("Поставь таймер на 5 минут", debug.transcript)
        assertEquals(Intents.SET_TIMER, debug.intent)
        assertEquals(37L, debug.latencyMs?.asr)
        assertNotNull(debug.latencyMs?.nlu)
        assertNotNull(debug.latencyMs?.total)
    }

    @Test
    fun voiceRecordingDeletesRecorderCacheFileAfterProcessing() {
        val viewModel = ChatViewModel()
        val audio = createTempFile(prefix = "voice-command-", suffix = ".wav").toFile()
        audio.writeText("fake wav")

        viewModel.handleVoiceRecording(
            audioFile = audio,
            transcriber = FakeAudioTranscriber(
                text = "Поставь таймер на 5 минут",
                latencyMs = 37,
            ),
        )

        assertEquals(false, audio.exists())
    }

    @Test
    fun asyncVoiceRecordingStreamsFallbackAnswerAfterTranscript() = runTest(testDispatcher) {
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)
            },
            fallbackParser = FakeStreamingFallbackParser(
                chunks = listOf("Локальный ", "ответ."),
                answer = "Локальный ответ.",
            ),
            ioDispatcher = testDispatcher,
        )
        val states = mutableListOf<ChatUiState>()

        viewModel.handleVoiceRecordingAsync(
            audioFile = File("voice.wav"),
            transcriber = FakeAudioTranscriber(
                text = "Почему небо синее?",
                latencyMs = 42,
            ),
        ) { states += it }
        assertEquals(true, states.any { state ->
            state.isProcessing && state.transcriptPreview == "Распознаю голос локально..."
        })

        advanceUntilIdle()

        val users = viewModel.state.messages.filterIsInstance<ChatMessageUi.User>()
        val assistants = viewModel.state.messages
            .filterIsInstance<ChatMessageUi.Assistant>()
            .filter { it.id != "welcome" }
        val debug = viewModel.state.debugHistory.single()

        assertEquals(1, users.size)
        assertEquals("voice", users.single().source)
        assertEquals("Почему небо синее?", users.single().text)
        assertEquals(1, assistants.size)
        assertEquals("Локальный ответ.", assistants.single().text)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, assistants.single().widget?.type)
        assertEquals(false, viewModel.state.isProcessing)
        assertEquals("Почему небо синее?", viewModel.state.transcriptPreview)
        assertEquals(42L, debug.latencyMs?.asr)
        assertEquals(true, debug.fallbackUsed)
        assertEquals(true, states.any { state ->
            state.messages
                .filterIsInstance<ChatMessageUi.Assistant>()
                .filter { it.id != "welcome" }
                .singleOrNull()
                ?.let { it.text == "Локальный ответ." && it.widget == null } == true
        })
    }

    @Test
    fun asyncVoiceRecordingDeletesRecorderCacheFileAfterProcessing() = runTest(testDispatcher) {
        val viewModel = ChatViewModel(ioDispatcher = testDispatcher)
        val audio = createTempFile(prefix = "voice-command-", suffix = ".wav").toFile()
        audio.writeText("fake wav")

        viewModel.handleVoiceRecordingAsync(
            audioFile = audio,
            transcriber = FakeAudioTranscriber(
                text = "Поставь таймер на 5 минут",
                latencyMs = 37,
            ),
        ) {}
        advanceUntilIdle()

        assertEquals(false, audio.exists())
    }

    @Test
    fun asyncTextFallbackFailureRendersErrorAndUnblocksUi() = runTest(testDispatcher) {
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)
            },
            fallbackParser = ThrowingStreamingFallbackParser("llama crashed on second request"),
            ioDispatcher = testDispatcher,
        )

        viewModel.updateInput("Почему приложение должно работать офлайн?")
        viewModel.sendTextAsync {}
        advanceUntilIdle()

        val assistant = viewModel.state.messages.last() as ChatMessageUi.Assistant
        assertEquals(false, viewModel.state.isProcessing)
        assertEquals(WidgetTypes.ERROR_CARD, assistant.widget?.type)
        assertEquals("Не получилось обработать команду.", assistant.text)
        assertEquals("error", assistant.debug?.actionResult)
    }

    @Test
    fun asyncTextHandlesTwoSequentialStreamingFallbackAnswersWithoutDuplicateFinalMessages() = runTest(testDispatcher) {
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)
            },
            fallbackParser = CountingStreamingFallbackParser(),
            ioDispatcher = testDispatcher,
        )

        viewModel.updateInput("Почему небо синее?")
        viewModel.sendTextAsync {}
        advanceUntilIdle()
        viewModel.updateInput("Почему вода мокрая?")
        viewModel.sendTextAsync {}
        advanceUntilIdle()

        val assistants = viewModel.state.messages
            .filterIsInstance<ChatMessageUi.Assistant>()
            .filter { it.id != "welcome" }
        assertEquals(false, viewModel.state.isProcessing)
        assertEquals(2, assistants.size)
        assertEquals("Локальный ответ 1.", assistants[0].text)
        assertEquals("Локальный ответ 2.", assistants[1].text)
        assertEquals(2, viewModel.state.debugHistory.size)
    }

    @Test
    fun asyncStreamingAnswerUsesOneSpeechMessageAndOneFinalization() = runTest(testDispatcher) {
        val speech = RecordingAssistantSpeech()
        val viewModel = ChatViewModel(
            nlu = NluParser {
                NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)
            },
            fallbackParser = FakeStreamingFallbackParser(
                chunks = listOf("Первое предложение. ", "Второе предложение."),
                answer = "Первое предложение. Второе предложение.",
            ),
            assistantSpeech = speech,
            ioDispatcher = testDispatcher,
        )

        viewModel.updateInput("Почему небо синее?")
        viewModel.sendTextAsync {}
        advanceUntilIdle()

        assertEquals(1, speech.begun.size)
        assertEquals(
            listOf("Первое предложение. Второе предложение."),
            speech.appended.map { it.second },
        )
        assertEquals(listOf(speech.begun.single() to "Первое предложение. Второе предложение."), speech.finished)
        assertEquals(listOf(SpeechStopReason.NEW_REQUEST), speech.stops)
    }

    @Test
    fun startingMicrophoneStopsCurrentSpeech() {
        val speech = RecordingAssistantSpeech()
        val viewModel = ChatViewModel(assistantSpeech = speech)

        viewModel.startVoiceRecording()

        assertEquals(listOf(SpeechStopReason.MICROPHONE_STARTED), speech.stops)
    }

    @Test
    fun partialTranscriptTracksOnlyTheCommonStablePrefix() {
        val viewModel = ChatViewModel()
        viewModel.startVoiceRecording()

        viewModel.updateStreamingTranscript("поставь таймер на")
        val state = viewModel.updateStreamingTranscript("поставь таймер на пять минут")

        assertEquals("поставь таймер на", state.stableTranscriptPrefix)
        assertEquals("поставь таймер на пять минут", state.transcriptPreview)
    }

    @Test
    fun reminderCommandShowsNotificationPermissionCardWhenPostNotificationsMissing() {
        val platformActions = FakePlatformActions(
            permissions = mapOf("POST_NOTIFICATIONS" to false),
        )
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Напомни через час проверить духовку")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(WidgetTypes.PERMISSION_CARD, assistant.widget?.type)
        assertEquals("POST_NOTIFICATIONS", assistant.widget?.payload?.get("permission")?.jsonPrimitive?.contentOrNull)
        assertEquals(Intents.CREATE_REMINDER, assistant.debug?.intent)
        assertEquals("permission_required", assistant.debug?.actionResult)
    }

    @Test
    fun reminderCommandSchedulesNotificationWhenPostNotificationsGranted() {
        val platformActions = FakePlatformActions(
            permissions = mapOf("POST_NOTIFICATIONS" to true),
        )
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Напомни через час проверить духовку")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(WidgetTypes.REMINDER_CARD, assistant.widget?.type)
        assertEquals("проверить духовку", platformActions.scheduledReminderText)
        assertNotNull(platformActions.scheduledReminderId)
        assertNotNull(platformActions.scheduledReminderAtMillis)
    }

    @Test
    fun timerCommandStartsPersistentInAppCountdown() {
        val platformActions = FakePlatformActions(systemTimerResult = true)
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Поставь таймер на 5 минут")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(null, platformActions.createdTimerDurationSeconds)
        assertEquals("Поставил таймер на 5 минут.", assistant.text)
        assertEquals(WidgetTypes.TIMER_CARD, assistant.widget?.type)
        assertEquals("in_app", assistant.widget?.payload?.get("mode")?.jsonPrimitive?.contentOrNull)
        assertEquals("running", assistant.widget?.payload?.get("state")?.jsonPrimitive?.contentOrNull)
        assertNotNull(assistant.widget?.payload?.get("ends_at_epoch_ms"))
    }

    @Test
    fun alarmCommandDelegatesToAndroidSystemAlarmAndReturnsPassiveCard() {
        val platformActions = FakePlatformActions(systemAlarmResult = true)
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Разбуди меня завтра в 7:30")
        val state = viewModel.sendText()
        val assistant = state.messages.last() as ChatMessageUi.Assistant

        assertEquals(7, platformActions.createdAlarmHour)
        assertEquals(30, platformActions.createdAlarmMinute)
        assertEquals("будильник", platformActions.createdAlarmLabel)
        assertEquals("Будильник создан в системном приложении.", assistant.text)
        assertEquals(WidgetTypes.ALARM_CARD, assistant.widget?.type)
        assertEquals("system_passive", assistant.widget?.payload?.get("mode")?.jsonPrimitive?.contentOrNull)
        assertEquals("scheduled", assistant.widget?.payload?.get("state")?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun alarmOpenSystemWidgetActionOpensSystemAlarmScreen() {
        val platformActions = FakePlatformActions()
        val viewModel = ChatViewModel(platformActions = platformActions)

        val state = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.ALARM_OPEN_SYSTEM,
                widgetType = WidgetTypes.ALARM_CARD,
            ),
        )

        assertEquals(1, platformActions.openedSystemAlarmCount)
        assertEquals("Открываю системный будильник.", (state.messages.last() as ChatMessageUi.Assistant).text)
    }

    @Test
    fun timerPauseAndResumeUpdateTheSameCard() {
        val platformActions = FakePlatformActions(systemTimerResult = false)
        val viewModel = ChatViewModel(platformActions = platformActions)

        viewModel.updateInput("Поставь таймер на 5 минут")
        val initial = viewModel.sendText()
        val timerId = (initial.messages.last() as ChatMessageUi.Assistant)
            .widget?.payload?.get("timer_id")?.jsonPrimitive?.content.orEmpty()

        val paused = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.TIMER_PAUSE,
                widgetType = WidgetTypes.TIMER_CARD,
                payload = mapOf("timer_id" to timerId, "remaining_seconds" to "297"),
            ),
        )
        val pausedCard = (paused.messages.last() as ChatMessageUi.Assistant).widget?.payload
        assertEquals("paused", pausedCard?.get("state")?.jsonPrimitive?.contentOrNull)
        assertEquals(297, pausedCard?.get("remaining_seconds")?.jsonPrimitive?.int)
        assertEquals(null, pausedCard?.get("ends_at_epoch_ms"))

        val resumed = viewModel.handleWidgetAction(
            com.offlineassistant.app.widgets.WidgetAction(
                name = com.offlineassistant.app.widgets.WidgetActionNames.TIMER_RESUME,
                widgetType = WidgetTypes.TIMER_CARD,
                payload = mapOf("timer_id" to timerId, "remaining_seconds" to "297"),
            ),
        )
        val resumedCard = (resumed.messages.last() as ChatMessageUi.Assistant).widget?.payload
        assertEquals("running", resumedCard?.get("state")?.jsonPrimitive?.contentOrNull)
        assertNotNull(resumedCard?.get("ends_at_epoch_ms"))
    }
}

private class RecordingAssistantSpeech : AssistantSpeech {
    val begun = mutableListOf<String>()
    val appended = mutableListOf<Pair<String, String>>()
    val finished = mutableListOf<Pair<String, String>>()
    val stops = mutableListOf<SpeechStopReason>()

    override fun begin(messageId: String) {
        begun += messageId
    }

    override fun append(messageId: String, delta: String) {
        appended += messageId to delta
    }

    override fun finish(messageId: String, finalText: String) {
        finished += messageId to finalText
    }

    override fun stop(reason: SpeechStopReason) {
        stops += reason
    }
}

private class FakePlatformActions(
    private val copyResult: Boolean = true,
    private val openAppResult: Boolean = true,
    private val systemTimerResult: Boolean = true,
    private val systemAlarmResult: Boolean = true,
    private val permissions: Map<String, Boolean> = emptyMap(),
    private val launchableApps: List<AppCandidate> = emptyList(),
) : PlatformActions {
    var copiedText: String? = null
    var openedPackageName: String? = null
    var openedAppName: String? = null
    var scheduledReminderId: String? = null
    var scheduledReminderText: String? = null
    var scheduledReminderAtMillis: Long? = null
    var createdTimerDurationSeconds: Int? = null
    var createdTimerLabel: String? = null
    var createdAlarmHour: Int? = null
    var createdAlarmMinute: Int? = null
    var createdAlarmLabel: String? = null
    var openedSystemAlarmCount: Int = 0

    override fun copyText(label: String, text: String): Boolean {
        copiedText = text
        return copyResult
    }

    override fun openApp(packageName: String?, appName: String?): Boolean {
        openedPackageName = packageName
        openedAppName = appName
        return openAppResult
    }

    override fun findLaunchableApps(appName: String): List<AppCandidate> = launchableApps

    override fun hasPermission(permission: String): Boolean = permissions[permission] ?: true

    override fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean {
        scheduledReminderId = reminderId
        scheduledReminderText = text
        scheduledReminderAtMillis = triggerAtMillis
        return true
    }

    override fun canCreateSystemTimer(): Boolean = true

    override fun createSystemTimer(durationSeconds: Int, label: String?): Boolean {
        createdTimerDurationSeconds = durationSeconds
        createdTimerLabel = label
        return systemTimerResult
    }

    override fun canCreateSystemAlarm(): Boolean = true

    override fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean {
        createdAlarmHour = hour
        createdAlarmMinute = minute
        createdAlarmLabel = label
        return systemAlarmResult
    }

    override fun openSystemAlarms(): Boolean {
        openedSystemAlarmCount += 1
        return true
    }
}

private class FakeAudioTranscriber(
    private val text: String?,
    private val latencyMs: Long,
) : AudioTranscriber {
    override fun transcribe(audioFile: File): AudioTranscription = AudioTranscription(
        text = text,
        error = null,
        latencyMs = latencyMs,
    )
}

private class FakeStreamingFallbackParser(
    private val chunks: List<String>,
    private val answer: String,
) : StreamingFallbackParser {
    override fun parse(input: String, nlu: NluResult): FallbackParse =
        FallbackParse(
            kind = FallbackKind.ANSWER,
            confidence = 0.65,
            answer = answer,
        )

    override fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse {
        chunks.forEach(onToken)
        return parse(input, nlu)
    }
}

private class ThrowingStreamingFallbackParser(
    private val message: String,
) : StreamingFallbackParser {
    override fun parse(input: String, nlu: NluResult): FallbackParse {
        error(message)
    }

    override fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse {
        error(message)
    }
}

private class CountingStreamingFallbackParser : StreamingFallbackParser {
    private var calls = 0

    override fun parse(input: String, nlu: NluResult): FallbackParse =
        parse(input, nlu) {}

    override fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse {
        calls += 1
        val answer = "Локальный ответ $calls."
        onToken("Локальный ")
        onToken("ответ $calls.")
        return FallbackParse(
            kind = FallbackKind.ANSWER,
            confidence = 0.8,
            answer = answer,
        )
    }
}
