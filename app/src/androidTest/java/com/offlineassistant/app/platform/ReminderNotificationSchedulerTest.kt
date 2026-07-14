package com.offlineassistant.app.platform

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderNotificationSchedulerTest {
    @Test
    fun schedulesReminderNotificationWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scheduler = ReminderNotificationScheduler(context)

        val scheduled = scheduler.schedule(
            reminderId = "test-reminder",
            text = "проверить духовку",
            triggerAtMillis = System.currentTimeMillis() + 60_000L
        )

        assertTrue(scheduled)
    }
}
