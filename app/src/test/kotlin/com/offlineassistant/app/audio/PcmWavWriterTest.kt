package com.offlineassistant.app.audio

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmWavWriterTest {
    @Test
    fun writesPcm16MonoWavHeaderAndSamples() {
        val output = ByteArrayOutputStream()
        PcmWavWriter.write(
            output = output,
            pcm = shortArrayOf(0, Short.MAX_VALUE, Short.MIN_VALUE),
            sampleRate = 16_000,
            channelCount = 1
        )

        val wav = output.toByteArray()

        assertEquals("RIFF", wav.asAscii(0, 4))
        assertEquals("WAVE", wav.asAscii(8, 4))
        assertEquals("fmt ", wav.asAscii(12, 4))
        assertEquals("data", wav.asAscii(36, 4))
        assertEquals(42, wav.readLeInt(4))
        assertEquals(16, wav.readLeInt(16))
        assertEquals(1, wav.readLeShort(20))
        assertEquals(1, wav.readLeShort(22))
        assertEquals(16_000, wav.readLeInt(24))
        assertEquals(32_000, wav.readLeInt(28))
        assertEquals(2, wav.readLeShort(32))
        assertEquals(16, wav.readLeShort(34))
        assertEquals(6, wav.readLeInt(40))
        assertArrayEquals(byteArrayOf(0, 0, -1, 127, 0, -128), wav.copyOfRange(44, 50))
    }
}

private fun ByteArray.asAscii(offset: Int, length: Int): String = copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

private fun ByteArray.readLeShort(offset: Int): Int = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.readLeInt(offset: Int): Int = (this[offset].toInt() and 0xff) or
    ((this[offset + 1].toInt() and 0xff) shl 8) or
    ((this[offset + 2].toInt() and 0xff) shl 16) or
    ((this[offset + 3].toInt() and 0xff) shl 24)
