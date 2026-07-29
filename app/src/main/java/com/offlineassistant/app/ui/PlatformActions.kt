package com.offlineassistant.app.ui

interface PlatformActions {
    fun copyText(label: String, text: String): Boolean
    fun findLaunchableApps(appName: String): List<AppCandidate>
    fun openApp(packageName: String?, appName: String?): Boolean
    fun hasPermission(permission: String): Boolean
    fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean
    fun cancelReminderNotification(reminderId: String): Boolean = false
    fun canCreateSystemTimer(): Boolean
    fun createSystemTimer(durationSeconds: Int, label: String?): Boolean
    fun canCreateSystemAlarm(): Boolean
    fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean
    fun openSystemAlarms(): Boolean
}

data class AppCandidate(
    val appName: String,
    val packageName: String
)

object NoOpPlatformActions : PlatformActions {
    override fun copyText(label: String, text: String): Boolean = false
    override fun findLaunchableApps(appName: String): List<AppCandidate> = emptyList()
    override fun openApp(packageName: String?, appName: String?): Boolean = false
    override fun hasPermission(permission: String): Boolean = true
    override fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean = false
    override fun canCreateSystemTimer(): Boolean = false
    override fun createSystemTimer(durationSeconds: Int, label: String?): Boolean = false
    override fun canCreateSystemAlarm(): Boolean = false
    override fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean = false
    override fun openSystemAlarms(): Boolean = false
}
