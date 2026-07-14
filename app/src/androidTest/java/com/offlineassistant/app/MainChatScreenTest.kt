package com.offlineassistant.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.offlineassistant.app.storage.SharedPreferencesNoteStore
import com.offlineassistant.app.storage.SharedPreferencesReminderStore
import com.offlineassistant.app.ui.AppCandidate
import com.offlineassistant.app.ui.ChatViewModel
import com.offlineassistant.app.ui.PermissionNames
import com.offlineassistant.app.ui.PlatformActions
import com.offlineassistant.core.nlu.RuleBasedNlu
import org.junit.Assert.assertEquals
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MainChatScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUpHarness() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("offline_assistant_notes", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("offline_assistant_reminders", Context.MODE_PRIVATE).edit().clear().commit()
        grantPostNotificationsPermission()
        val viewModel = ChatViewModel(
            noteStore = SharedPreferencesNoteStore(context),
            reminderStore = SharedPreferencesReminderStore(context),
            nlu = RuleBasedNlu(),
            platformActions = TestPlatformActions(context),
        )
        compose.setContent {
            MaterialTheme {
                OfflineAssistantApp(injectedChatViewModel = viewModel)
            }
        }
    }

    @Test
    fun sendingTimerCommandShowsAssistantTextAndTimerCard() {
        compose.onNodeWithTag("chat_input").performTextInput("Поставь таймер на 5 минут")
        compose.onNodeWithContentDescription("Отправить").performClick()

        waitForText("Поставил таймер на 5 минут.")
        compose.onNodeWithText("Поставил таймер на 5 минут.").assertIsDisplayed()
        waitForTag("timer_card")
        compose.onNodeWithTag("timer_card").assertIsDisplayed()
    }

    @Test
    fun chatShellMatchesReferenceNavigationChrome() {
        compose.onNodeWithText("Assistant").assertIsDisplayed()
        compose.onNodeWithText("Чат").assertIsDisplayed()
        compose.onNodeWithText("История").assertIsDisplayed()
        compose.onNodeWithText("Навыки").assertIsDisplayed()
        compose.onNodeWithText("Настройки").assertIsDisplayed()
        compose.onNodeWithContentDescription("Записать голос").assertIsDisplayed()
    }

    @Test
    fun mainChatHidesRawDebugFooterAfterCommand() {
        compose.onNodeWithTag("chat_input").performTextInput("Поставь таймер на 5 минут")
        compose.onNodeWithContentDescription("Отправить").performClick()

        waitForTag("timer_card")
        assertEquals(
            emptyList<Any>(),
            compose.onAllNodesWithText("debug:", substring = true).fetchSemanticsNodes(),
        )
    }

    @Test
    fun weatherCalculatorAndReminderCommandsRenderReferenceCards() {
        compose.onNodeWithTag("chat_input").performTextInput("Какая погода сегодня?")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForTag("weather_card")
        compose.onNodeWithTag("weather_card").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("21°").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("chat_input").performTextInput("Посчитай 125 умножить на 37")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForTag("calculator_card")
        compose.onNodeWithTag("calculator_card").assertIsDisplayed()
        compose.onNodeWithText("4625").assertIsDisplayed()

        compose.onNodeWithTag("chat_input").performTextInput("Напомни через час проверить духовку")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForTag("reminder_card")
        compose.onNodeWithTag("reminder_card").assertIsDisplayed()
        compose.onNodeWithText("Проверить духовку").assertIsDisplayed()
    }

    @Test
    fun widgetActionButtonsShowVisibleFeedback() {
        compose.onNodeWithTag("chat_input").performTextInput("Поставь таймер на 5 минут")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForContentDescription("Отменить таймер")
        compose.onNodeWithContentDescription("Отменить таймер").performClick()
        waitForText("Таймер отменен.")
        compose.onNodeWithText("Таймер отменен.").assertIsDisplayed()

        compose.onNodeWithTag("chat_input").performTextInput("Напомни через час проверить духовку")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForContentDescription("Выполнить напоминание")
        compose.onNodeWithContentDescription("Выполнить напоминание").performClick()
        waitForText("Напоминание выполнено.")
        compose.onNodeWithText("Напоминание выполнено.").assertIsDisplayed()

        compose.onNodeWithTag("chat_input").performTextInput("Создай заметку купить молоко и яйца")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForContentDescription("Удалить заметку")
        compose.onNodeWithContentDescription("Удалить заметку").performClick()
        waitForText("Заметка удалена.")
        compose.onNodeWithText("Заметка удалена.").assertIsDisplayed()
    }

    @Test
    fun debugTabShowsLatestCommandDiagnostics() {
        compose.onNodeWithTag("chat_input").performTextInput("Поставь таймер на две минуты")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForText("Поставил таймер на 2 минут.")

        compose.onNodeWithText("История").performClick()

        waitForText("Debug history")
        assertDebugText("transcript: Поставь таймер на две минуты")
        assertDebugText("intent: set_timer")
        assertDebugText("fallback: false")
        assertDebugText("action result: success")
        assertDebugText("duration_seconds")
        assertDebugText("normalized command")
        assertDebugText("latency total")
        assertDebugText("latency nlu")
        assertDebugText("latency normalization")
        assertDebugText("latency skill")
    }

    @Test
    fun noteAndReminderWidgetActionsMutatePersistedStores() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notes = SharedPreferencesNoteStore(context)
        val reminders = SharedPreferencesReminderStore(context)

        compose.onNodeWithTag("chat_input").performTextInput("Создай заметку купить молоко и яйца")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitUntil(timeoutMillis = 30_000) { notes.list().size == 1 }
        assertEquals(1, notes.list().size)

        waitForContentDescription("Удалить заметку")
        compose.onNodeWithContentDescription("Удалить заметку").performClick()
        compose.waitUntil(timeoutMillis = 30_000) { notes.list().isEmpty() }
        assertEquals(emptyList<Any>(), notes.list())

        compose.onNodeWithTag("chat_input").performTextInput("Напомни через час проверить духовку")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitUntil(timeoutMillis = 30_000) { reminders.list().isNotEmpty() }
        assertEquals("scheduled", reminders.list().single().state)

        waitForContentDescription("Выполнить напоминание")
        compose.onNodeWithContentDescription("Выполнить напоминание").performClick()
        compose.waitUntil(timeoutMillis = 30_000) { reminders.list().singleOrNull()?.state == "completed" }
        assertEquals("completed", reminders.list().single().state)
    }

    @Test
    fun noteEditActionUpdatesPersistedStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notes = SharedPreferencesNoteStore(context)

        compose.onNodeWithTag("chat_input").performTextInput("Создай заметку купить молоко")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitUntil(timeoutMillis = 30_000) { notes.list().size == 1 }

        waitForContentDescription("Изменить заметку")
        compose.onNodeWithContentDescription("Изменить заметку").performClick()
        compose.onNodeWithTag("chat_input").performTextReplacement("купить молоко и яйца")
        compose.onNodeWithContentDescription("Отправить").performClick()

        waitForText("Заметка обновлена.")
        compose.waitUntil(timeoutMillis = 30_000) { notes.list().singleOrNull()?.text == "купить молоко и яйца" }
        assertEquals("купить молоко и яйца", notes.list().single().text)
    }

    @Test
    fun copyWidgetActionsWriteAndroidClipboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        compose.onNodeWithTag("chat_input").performTextInput("Создай заметку купить молоко и яйца")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForContentDescription("Скопировать заметку")
        compose.onNodeWithContentDescription("Скопировать заметку").performClick()
        waitForText("Заметка скопирована.")
        assertEquals("купить молоко и яйца", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context).toString())

        compose.onNodeWithTag("chat_input").performTextInput("Посчитай 125 умножить на 37")
        compose.onNodeWithContentDescription("Отправить").performClick()
        waitForContentDescription("Скопировать результат")
        compose.onNodeWithContentDescription("Скопировать результат").performClick()
        waitForText("Результат скопирован.")
        assertEquals("4625", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context).toString())
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertDebugText(text: String) {
        compose.onNodeWithTag("debug_history_list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun grantPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

private class TestPlatformActions(
    private val context: Context,
) : PlatformActions {
    override fun copyText(label: String, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return true
    }

    override fun findLaunchableApps(appName: String): List<AppCandidate> = emptyList()

    override fun openApp(packageName: String?, appName: String?): Boolean = false

    override fun hasPermission(permission: String): Boolean = when (permission) {
        PermissionNames.POST_NOTIFICATIONS -> true
        PermissionNames.RECORD_AUDIO -> true
        else -> true
    }

    override fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean = true

    override fun canCreateSystemTimer(): Boolean = false

    override fun createSystemTimer(durationSeconds: Int, label: String?): Boolean = false

    override fun canCreateSystemAlarm(): Boolean = false

    override fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean = false

    override fun openSystemAlarms(): Boolean = false
}
