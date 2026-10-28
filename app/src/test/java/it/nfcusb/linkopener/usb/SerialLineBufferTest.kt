package it.nfcusb.linkopener.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialLineBufferTest {
    @Test
    fun reassemblesLineSplitAcrossReads() {
        val buffer = SerialLineBuffer()
        val first = buffer.append("URL:https://esempio.it/tags/".toByteArray())
        val second = buffer.append("P04a\n".toByteArray())

        assertTrue(first.lines.isEmpty())
        assertFalse(first.overflow)
        assertEquals(listOf("URL:https://esempio.it/tags/P04a"), second.lines)
    }

    @Test
    fun splitsMultipleLinesAndStripsCarriageReturn() {
        val buffer = SerialLineBuffer()
        val raw = "URL:https://a.it/1\r\nDEBUG:x\n".toByteArray()
        val result = buffer.append(raw)

        assertEquals(listOf("URL:https://a.it/1", "DEBUG:x"), result.lines)
        assertFalse(result.overflow)
    }

    @Test
    fun keepsPartialLineUntilNewline() {
        val buffer = SerialLineBuffer()
        assertTrue(buffer.append("URL:https://a".toByteArray()).lines.isEmpty())
        assertEquals(listOf("URL:https://a.it/x"), buffer.append(".it/x\n".toByteArray()).lines)
    }

    @Test
    fun discardsOverlongLineUntilNextNewline() {
        val buffer = SerialLineBuffer(maxBytes = 8)
        val first = buffer.append("123456789".toByteArray())
        val second = buffer.append("abc\nOK\n".toByteArray())

        assertTrue(first.overflow)
        assertTrue(first.lines.isEmpty())
        assertEquals(listOf("OK"), second.lines)
        assertFalse(second.overflow)
    }

    @Test
    fun keepsLineThatFitsExactly() {
        val buffer = SerialLineBuffer(maxBytes = 4)
        val result = buffer.append("abcd\n".toByteArray())

        assertEquals(listOf("abcd"), result.lines)
        assertFalse(result.overflow)
    }
}
