package com.offlineassistant.app.assistant

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService

enum class DefaultAssistantStatus {
    SELECTED,
    NOT_SELECTED,
    UNAVAILABLE
}

class DefaultAssistantStatusRepository(context: Context) {
    private val appContext = context.applicationContext

    fun current(): DefaultAssistantStatus {
        val component = ComponentName(appContext, OfflineAssistantVoiceInteractionService::class.java)
        if (VoiceInteractionService.isActiveService(appContext, component)) {
            return DefaultAssistantStatus.SELECTED
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return DefaultAssistantStatus.UNAVAILABLE
        val roles = appContext.getSystemService(RoleManager::class.java)
            ?: return DefaultAssistantStatus.UNAVAILABLE
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return DefaultAssistantStatus.UNAVAILABLE
        return if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            DefaultAssistantStatus.SELECTED
        } else {
            DefaultAssistantStatus.NOT_SELECTED
        }
    }

    fun selectionIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roles = appContext.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        return runCatching {
            roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        }.getOrNull()
    }
}

class DefaultAssistantSettingsLauncher(private val context: Context) {
    fun open(): Boolean = SETTINGS_ACTIONS.any { action ->
        runCatching {
            context.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    private companion object {
        val SETTINGS_ACTIONS = listOf(
            Settings.ACTION_VOICE_INPUT_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
    }
}
