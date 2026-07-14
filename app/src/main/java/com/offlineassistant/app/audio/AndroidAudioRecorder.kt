package com.offlineassistant.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

    @Synchronized
    fun start(
        captureWav: Boolean = true,
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
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
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
