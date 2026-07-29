package com.offlineassistant.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AndroidAudioRecorder(
    private val context: Context,
    private val sampleRate: Int = 16_000
) {
    private val isRecording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private var captureWav = true
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var voiceProcessingState = VoiceProcessingState()

    @Synchronized
    fun start(
        captureWav: Boolean = true,
        enableVoiceProcessing: Boolean = false,
        onPcmChunk: ((ShortArray) -> Unit)? = null
    ): Boolean {
        if (isRecording.get()) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)

        @SuppressLint("MissingPermission")
        val audioRecord = AudioRecord(
            if (enableVoiceProcessing) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            },
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }

        pcmBuffer.reset()
        this.captureWav = captureWav
        recorder = audioRecord
        configureVoiceProcessing(audioRecord, enableVoiceProcessing)
        isRecording.set(true)
        audioRecord.startRecording()
        worker = thread(name = "offline-assistant-audio-recorder") {
            val buffer = ByteArray(minBufferSize)
            while (isRecording.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    if (captureWav) {
                        synchronized(pcmBuffer) {
                            pcmBuffer.write(buffer, 0, read)
                        }
                    }
                    if (onPcmChunk != null) {
                        val samples = pcm16BytesToShorts(buffer, read)
                        runCatching { onPcmChunk(samples) }
                    }
                }
            }
        }
        return true
    }

    @Synchronized
    fun stop(): File? {
        if (!isRecording.getAndSet(false)) return null
        val audioRecord = recorder
        recorder = null
        if (audioRecord != null) {
            runCatching { audioRecord.stop() }
        }
        worker?.join(1_000)
        worker = null
        releaseVoiceProcessing()
        audioRecord?.release()

        val shouldWriteWav = captureWav
        captureWav = true
        if (!shouldWriteWav) return null

        val pcmBytes = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        if (pcmBytes.isEmpty()) return null
        val shorts = pcm16BytesToShorts(pcmBytes, pcmBytes.size)

        val file = File(context.cacheDir, "voice-command-${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use {
            PcmWavWriter.write(
                output = it,
                pcm = shorts,
                sampleRate = sampleRate,
                channelCount = 1
            )
        }
        return file
    }

    @Synchronized
    fun cancel() {
        if (!isRecording.getAndSet(false)) return
        val audioRecord = recorder
        recorder = null
        if (audioRecord != null) {
            runCatching { audioRecord.stop() }
        }
        worker?.join(1_000)
        worker = null
        releaseVoiceProcessing()
        audioRecord?.release()
        captureWav = true
        synchronized(pcmBuffer) { pcmBuffer.reset() }
    }

    @Synchronized
    fun currentVoiceProcessingState(): VoiceProcessingState = voiceProcessingState

    private fun configureVoiceProcessing(audioRecord: AudioRecord, enabled: Boolean) {
        releaseVoiceProcessing()
        if (!enabled) return
        acousticEchoCanceler = createAndEnableEffect(
            available = AcousticEchoCanceler.isAvailable(),
            create = { AcousticEchoCanceler.create(audioRecord.audioSessionId) }
        )
        noiseSuppressor = createAndEnableEffect(
            available = NoiseSuppressor.isAvailable(),
            create = { NoiseSuppressor.create(audioRecord.audioSessionId) }
        )
        automaticGainControl = createAndEnableEffect(
            available = AutomaticGainControl.isAvailable(),
            create = { AutomaticGainControl.create(audioRecord.audioSessionId) }
        )
        voiceProcessingState = VoiceProcessingState(
            acousticEchoCancellation = acousticEchoCanceler?.enabled == true,
            noiseSuppression = noiseSuppressor?.enabled == true,
            automaticGainControl = automaticGainControl?.enabled == true
        )
    }

    private fun <T : android.media.audiofx.AudioEffect> createAndEnableEffect(
        available: Boolean,
        create: () -> T?
    ): T? {
        if (!available) return null
        return runCatching {
            create()?.also { it.enabled = true }
        }.getOrNull()
    }

    private fun releaseVoiceProcessing() {
        runCatching { acousticEchoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { automaticGainControl?.release() }
        acousticEchoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null
        voiceProcessingState = VoiceProcessingState()
    }

    private fun pcm16BytesToShorts(bytes: ByteArray, size: Int): ShortArray {
        val shorts = ShortArray(size / 2)
        for (index in shorts.indices) {
            val low = bytes[index * 2].toInt() and 0xff
            val high = bytes[index * 2 + 1].toInt()
            shorts[index] = ((high shl 8) or low).toShort()
        }
        return shorts
    }
}

data class VoiceProcessingState(
    val acousticEchoCancellation: Boolean = false,
    val noiseSuppression: Boolean = false,
    val automaticGainControl: Boolean = false
)
