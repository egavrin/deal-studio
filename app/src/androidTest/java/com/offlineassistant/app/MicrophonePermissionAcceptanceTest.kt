package com.offlineassistant.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MicrophonePermissionAcceptanceTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun microphoneWithoutRecordAudioPermissionShowsPermissionCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Run after: adb shell pm revoke --user 0 com.offlineassistant.poc.debug android.permission.RECORD_AUDIO",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        )

        compose.onNodeWithContentDescription("Записать голос").performClick()

        compose.onNodeWithTag("permission_card").assertExists()
        compose.onNodeWithText("RECORD_AUDIO").assertExists()
        compose.onNodeWithText("Чтобы записать голосовую команду, нужно разрешение на микрофон.").assertExists()
    }
}
