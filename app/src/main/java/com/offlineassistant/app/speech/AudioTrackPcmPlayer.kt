package com.offlineassistant.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class AudioTrackPcmPlayer(context: Context) : PcmAudioPlayer {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private var activeTrack: AudioTrack? = null
    private var activeSampleRate: Int? = null
    private var focusRequest: AudioFocusRequest? = null
    private var submittedFrames = 0L
    private val playbackMarkers = ArrayDeque<PlaybackMarker>()
    private val callbackExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "assistant-audio-progress").apply { isDaemon = true }
    }

    init {
        callbackExecutor.scheduleWithFixedDelay(
            ::dispatchPlaybackCallbacks,
            0L,
            PLAYBACK_POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    override fun play(audio: PcmAudio) {
        play(audio, {}, {})
        finish()
    }

    override fun play(audio: PcmAudio, onPlaybackStarted: () -> Unit) {
        play(audio, onPlaybackStarted, {})
        finish()
    }

    override fun play(
        audio: PcmAudio,
        onPlaybackStarted: () -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        if (audio.samples.isEmpty()) {
            onPlaybackStarted()
            onPlaybackCompleted()
            return
        }
        check(audio.sampleRate > 0) { "Invalid PCM sample rate: ${audio.sampleRate}" }
        val track = synchronized(lock) {
            val sessionTrack = activeTrack ?: createTrackLocked(audio.sampleRate)
            check(activeSampleRate == audio.sampleRate) {
                "PCM sample rate changed inside one response: $activeSampleRate -> ${audio.sampleRate}"
            }
            val startFrame = submittedFrames
            val endFrame = startFrame + audio.samples.size
            submittedFrames = endFrame
            playbackMarkers += PlaybackMarker(
                startFrame = startFrame,
                endFrame = endFrame,
                onStarted = onPlaybackStarted,
                onCompleted = onPlaybackCompleted
            )
            sessionTrack
        }
        try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            writeAll(track, audio.samples)
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    override fun finish() {
        val session = synchronized(lock) {
            val track = activeTrack ?: return
            PlaybackSession(track, submittedFrames, activeSampleRate ?: return)
        }
        val remainingFrames = (session.endFrame - currentPlayedFrames(session.track)).coerceAtLeast(0L)
        val remainingMillis = remainingFrames * 1_000L / session.sampleRate
        val deadline = SystemClock.elapsedRealtime() + remainingMillis + PLAYBACK_TIMEOUT_MARGIN_MS
        while (isActive(session.track)) {
            dispatchPlaybackCallbacks()
            if (currentPlayedFrames(session.track) >= session.endFrame) break
            check(SystemClock.elapsedRealtime() < deadline) {
                "AudioTrack playback timed out before ${session.endFrame} frames"
            }
            SystemClock.sleep(PLAYBACK_POLL_INTERVAL_MS)
        }
        dispatchPlaybackCallbacks()
        synchronized(lock) {
            if (activeTrack === session.track) releaseActiveTrackLocked()
        }
    }

    override fun stop() {
        synchronized(lock) { releaseActiveTrackLocked() }
    }

    override fun close() {
        stop()
        callbackExecutor.shutdownNow()
    }

    private fun createTrackLocked(sampleRate: Int): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS) stop()
            }
            .build()
        check(audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Audio focus was not granted"
        }

        val minimumBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(max(minimumBuffer, sampleRate * Float.SIZE_BYTES / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        focusRequest = request
        activeTrack = track
        activeSampleRate = sampleRate
        submittedFrames = 0L
        playbackMarkers.clear()
        return track
    }

    private fun writeAll(track: AudioTrack, samples: FloatArray) {
        var offset = 0
        while (offset < samples.size && isActive(track)) {
            val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                if (!isActive(track)) return
                error("AudioTrack write failed: $written")
            }
            check(written > 0) { "AudioTrack accepted no PCM samples" }
            offset += written
        }
    }

    private fun dispatchPlaybackCallbacks() {
        val callbacks = mutableListOf<() -> Unit>()
        synchronized(lock) {
            val track = activeTrack ?: return
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
            val playedFrames = currentPlayedFrames(track)
            while (playbackMarkers.isNotEmpty()) {
                val marker = playbackMarkers.first()
                if (!marker.started && playedFrames >= marker.startFrame) {
                    marker.started = true
                    callbacks += marker.onStarted
                }
                if (playedFrames >= marker.endFrame) {
                    if (!marker.started) {
                        marker.started = true
                        callbacks += marker.onStarted
                    }
                    playbackMarkers.removeFirst()
                    callbacks += marker.onCompleted
                } else {
                    break
                }
            }
        }
        callbacks.forEach { callback -> runCatching(callback) }
    }

    private fun isActive(track: AudioTrack): Boolean = synchronized(lock) { activeTrack === track }

    private fun currentPlayedFrames(track: AudioTrack): Long = runCatching {
        track.playbackHeadPosition.toLong() and UNSIGNED_INT_MASK
    }.getOrElse {
        if (!isActive(track)) return 0L
        throw it
    }

    private fun releaseActiveTrackLocked() {
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
        }
        activeTrack = null
        activeSampleRate = null
        submittedFrames = 0L
        playbackMarkers.clear()
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private data class PlaybackMarker(
        val startFrame: Long,
        val endFrame: Long,
        val onStarted: () -> Unit,
        val onCompleted: () -> Unit,
        var started: Boolean = false
    )

    private data class PlaybackSession(
        val track: AudioTrack,
        val endFrame: Long,
        val sampleRate: Int
    )

    private companion object {
        const val PLAYBACK_POLL_INTERVAL_MS = 10L
        const val PLAYBACK_TIMEOUT_MARGIN_MS = 1_500L
        const val UNSIGNED_INT_MASK = 0xffff_ffffL
    }
}
