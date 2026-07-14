package com.offlineassistant.app.audio

import java.io.OutputStream

object PcmWavWriter {
    fun write(
        output: OutputStream,
        pcm: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ) {
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val dataSize = pcm.size * bytesPerSample
        val byteRate = sampleRate * channelCount * bytesPerSample
        val blockAlign = channelCount * bytesPerSample

        output.writeAscii("RIFF")
        output.writeLeInt(36 + dataSize)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeLeInt(16)
        output.writeLeShort(1)
        output.writeLeShort(channelCount)
        output.writeLeInt(sampleRate)
        output.writeLeInt(byteRate)
        output.writeLeShort(blockAlign)
        output.writeLeShort(bitsPerSample)
        output.writeAscii("data")
        output.writeLeInt(dataSize)
        pcm.forEach { output.writeLeShort(it.toInt()) }
    }
}

private fun OutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun OutputStream.writeLeShort(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun OutputStream.writeLeInt(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
    write((value ushr 16) and 0xff)
    write((value ushr 24) and 0xff)
}
