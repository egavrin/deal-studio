package com.offlineassistant.app.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSettingsRepositoryTest {
    @Test
    fun selectedVoiceModelPersistsAcrossRepositoryInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("offline_assistant_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val first = AssistantSettingsRepository(context)
        assertEquals(VoiceModel.TONE_RU_STREAMING, first.voiceModel)

        first.voiceModel = VoiceModel.ZIPFORMER_RU_INT8

        assertEquals(
            VoiceModel.ZIPFORMER_RU_INT8,
            AssistantSettingsRepository(context).voiceModel
        )
    }
}
