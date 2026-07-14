package com.offlineassistant.app.platform

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlatformAdaptersTest {
    @Test
    fun appRequestsClockApiPermission() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )

        assertTrue(packageInfo.requestedPermissions.orEmpty().contains("com.android.alarm.permission.SET_ALARM"))
    }

    @Test
    fun timerAndAlarmIntentsCreateInBackgroundWithoutLeavingChat() {
        val timer = systemTimerIntent(300, "чай")
        assertEquals(AlarmClock.ACTION_SET_TIMER, timer.action)
        assertEquals(300, timer.getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
        assertEquals("чай", timer.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(timer.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(timer.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val alarm = systemAlarmIntent(7, 30, "будильник")
        assertEquals(AlarmClock.ACTION_SET_ALARM, alarm.action)
        assertEquals(7, alarm.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, alarm.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertTrue(alarm.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(alarm.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun opensLaunchableAppByPackageNameAndLabel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val adapters = AndroidPlatformAdapters(context)

        assertTrue(adapters.openApp(context.packageName, null))
        assertTrue(adapters.openApp(null, "Offline Assistant PoC"))
        val candidates = adapters.findLaunchableApps("Offline Assistant PoC")
        assertTrue(candidates.any { it.packageName == context.packageName })
        assertEquals(
            "Offline Assistant PoC",
            candidates.first { it.packageName == context.packageName }.appName,
        )
    }
}
