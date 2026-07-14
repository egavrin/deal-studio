package com.offlineassistant.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.offlineassistant.app.settings.VoiceModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun voiceModelMenuSelectsRussianZipformerModel() {
        var selected: VoiceModel? = null
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    modelReadiness = emptyList(),
                    selectedVoiceModel = VoiceModel.WHISPER_BASE_Q5_1,
                    onVoiceModelChange = { selected = it },
                )
            }
        }

        compose.onNodeWithTag("voice_model_menu").performClick()
        compose.onNodeWithText("Zipformer RU INT8").assertIsDisplayed()
        compose.onNodeWithTag("voice_model_zipformer_ru_int8").performClick()

        assertEquals(VoiceModel.ZIPFORMER_RU_INT8, selected)
    }
}
