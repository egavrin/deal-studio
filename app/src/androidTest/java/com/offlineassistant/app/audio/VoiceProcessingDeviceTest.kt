package com.offlineassistant.app.audio

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceProcessingDeviceTest {
    @Test
    fun conversationCaptureEnablesAcousticEchoCancellation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "RECORD_AUDIO must be granted before the device acceptance test",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
        val recorder = AndroidAudioRecorder(context)
        try {
            assertTrue(
                "VOICE_COMMUNICATION AudioRecord could not start",
                recorder.start(captureWav = false, enableVoiceProcessing = true)
            )
            val state = recorder.currentVoiceProcessingState()
            Log.i(TAG, "Voice processing state: $state")
            assertTrue(
                "Acoustic echo cancellation is unavailable on this device",
                state.acousticEchoCancellation
            )
        } finally {
            recorder.cancel()
        }
    }

    private companion object {
        const val TAG = "VoiceProcessingTest"
    }
}
