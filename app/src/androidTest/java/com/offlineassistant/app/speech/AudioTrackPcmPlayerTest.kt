package com.offlineassistant.app.speech

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioTrackPcmPlayerTest {
    @Test
    fun playWaitsUntilSubmittedPcmHasPlayed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioTrackPcmPlayer(context)
        val started = SystemClock.elapsedRealtime()

        try {
            player.play(PcmAudio(samples = FloatArray(SAMPLE_RATE), sampleRate = SAMPLE_RATE))
        } finally {
            player.close()
        }

        val elapsedMillis = SystemClock.elapsedRealtime() - started
        assertTrue("AudioTrack returned before playback: ${elapsedMillis}ms", elapsedMillis >= 800L)
        assertTrue("AudioTrack playback took too long: ${elapsedMillis}ms", elapsedMillis < 3_000L)
    }

    @Test
    fun streamsTwoChunksInOneOrderedPlaybackSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = AudioTrackPcmPlayer(context)
        val events = mutableListOf<String>()
        val started = SystemClock.elapsedRealtime()

        try {
            player.play(
                PcmAudio(samples = FloatArray(SAMPLE_RATE / 2), sampleRate = SAMPLE_RATE),
                onPlaybackStarted = { events += "first-start" },
                onPlaybackCompleted = { events += "first-end" }
            )
            player.play(
                PcmAudio(samples = FloatArray(SAMPLE_RATE / 2), sampleRate = SAMPLE_RATE),
                onPlaybackStarted = { events += "second-start" },
                onPlaybackCompleted = { events += "second-end" }
            )
            player.finish()
        } finally {
            player.close()
        }

        val elapsedMillis = SystemClock.elapsedRealtime() - started
        assertEquals(listOf("first-start", "first-end", "second-start", "second-end"), events)
        assertTrue("Streaming playback returned early: ${elapsedMillis}ms", elapsedMillis >= 800L)
        assertTrue("Streaming playback took too long: ${elapsedMillis}ms", elapsedMillis < 3_000L)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
