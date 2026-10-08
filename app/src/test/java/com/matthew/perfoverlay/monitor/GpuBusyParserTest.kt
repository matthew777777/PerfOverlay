package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpuBusyParserTest {
    @Test
    fun parsesBusyTotalPair() {
        assertEquals(12345L to 67890L, GpuBusyParser.parseBusyPair("12345 67890"))
        assertEquals(1L to 2L, GpuBusyParser.parseBusyPair("  1   2  \n"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(GpuBusyParser.parseBusyPair(null))
        assertNull(GpuBusyParser.parseBusyPair(""))
        assertNull(GpuBusyParser.parseBusyPair("50%"))
        assertNull(GpuBusyParser.parseBusyPair("100"))
        assertNull(GpuBusyParser.parseBusyPair("10 0"))
        assertNull(GpuBusyParser.parseBusyPair("a b"))
    }

    @Test
    fun computesDeltaLoad() {
        // +200 busy of +400 total => 50%.
        assertEquals(50f, GpuBusyParser.loadPct(100L to 1000L, 300L to 1400L)!!, 0.01f)
    }

    @Test
    fun nullLoadOnEmptyDelta() {
        assertNull(GpuBusyParser.loadPct(5L to 10L, 5L to 10L))
    }
}
