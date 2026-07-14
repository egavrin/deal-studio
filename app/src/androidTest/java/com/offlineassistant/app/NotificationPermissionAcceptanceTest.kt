package com.offlineassistant.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.offlineassistant.app.platform.ReminderNotificationScheduler
import androidx.core.content.ContextCompat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

class NotificationPermissionAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun postNotificationsPermissionDialogCanBeGrantedAndReminderNotificationAppears() {
        assumeTrue("POST_NOTIFICATIONS is only runtime permission on Android 13+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Run after: adb shell pm revoke --user 0 com.offlineassistant.poc.debug android.permission.POST_NOTIFICATIONS",
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
        )

        sendReminder()
        compose.waitUntilAtLeastOneExists(hasTestTag("permission_card"), 30_000)
        compose.onNodeWithText("POST_NOTIFICATIONS").assertExists()

        compose.onNodeWithContentDescription("Разрешить permission").performClick()
        allowSystemPermissionDialog()
        compose.onNodeWithText("POST_NOTIFICATIONS разрешен. Можно создавать напоминания.").assertExists()

        sendReminder()
        compose.waitUntilAtLeastOneExists(hasTestTag("reminder_card"), 30_000)

        val notificationText = "проверить духовку"
        ReminderNotificationScheduler(context).schedule(
            reminderId = "permission-acceptance",
            text = notificationText,
            triggerAtMillis = System.currentTimeMillis() + 1_500L,
        )

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Thread.sleep(4_000L)
        device.openNotification()
        val notification = device.wait(Until.findObject(By.textContains(notificationText)), 10_000L)
        checkNotNull(notification) { "Reminder notification text was not visible after POST_NOTIFICATIONS grant" }
        device.pressBack()
    }

    private fun sendReminder() {
        compose.onNodeWithTag("chat_input").performTextInput("Напомни через час проверить духовку")
        compose.onNodeWithContentDescription("Отправить").performClick()
    }

    private fun allowSystemPermissionDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val allowByResource = device.wait(
            Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")),
            5_000L,
        )
        val allowButton = allowByResource ?: device.wait(
            Until.findObject(By.text(Pattern.compile("(?i).*(allow|разреш).*"))),
            5_000L,
        )
        checkNotNull(allowButton) { "System POST_NOTIFICATIONS permission dialog was not visible" }
        allowButton.click()
    }
}
