package com.matthew.perfoverlay.monitor

import com.matthew.perfoverlay.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun freqFormatsGhzAndMhz() {
        assertEquals("2.84G", Format.freq(2_840_000L))
        assertEquals("1.42G", Format.freq(1_420_000L))
        assertEquals("844M", Format.freq(844_000L))
        assertEquals("N/A", Format.freq(null))
        assertEquals("N/A", Format.freq(0L))
    }

    @Test
    fun memSampleMath() {
        val m = MemSample(totalBytes = 8L * 1024 * 1024 * 1024, availBytes = 2L * 1024 * 1024 * 1024)
        assertEquals(6L * 1024 * 1024 * 1024, m.usedBytes)
        assertEquals(75f, m.usedPct, 0.01f)
        assertEquals("6.0G", Format.gib(m.usedBytes))
    }

    @Test
    fun celsiusAndRate() {
        assertEquals("38.1°", Format.celsius(38.06f))
        assertEquals("N/A", Format.celsius(null))
        assertEquals("128", Format.rate(128.0))
        assertEquals("1.5K", Format.rate(1536.0))
        assertEquals("2.5M", Format.rate(2_500_000.0))
    }

    @Test
    fun signedMaShowsChargeDirection() {
        assertEquals("+1464mA", Format.signedMa(1464.0, true))
        assertEquals("-812mA", Format.signedMa(812.0, false))
        assertEquals("812mA", Format.signedMa(812.0, null))
        assertEquals("N/A", Format.signedMa(null, true))
    }

    @Test
    fun elapsedFormatsMmSs() {
        assertEquals("00:05", Format.elapsed(5_000L))
        assertEquals("05:12", Format.elapsed(312_000L))
    }
}
