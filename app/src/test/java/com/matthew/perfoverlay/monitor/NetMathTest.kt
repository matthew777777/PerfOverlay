package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class NetMathTest {
    @Test
    fun ratePerSec() {
        assertEquals(1000.0, NetMath.ratePerSec(0L, 1000L, 1000L), 1e-9)
        assertEquals(500.0, NetMath.ratePerSec(1000L, 2000L, 2000L), 1e-9)
    }

    @Test
    fun zeroOnStallOrReset() {
        assertEquals(0.0, NetMath.ratePerSec(1000L, 1000L, 1000L), 0.0)
        assertEquals(0.0, NetMath.ratePerSec(5000L, 100L, 1000L), 0.0)
        assertEquals(0.0, NetMath.ratePerSec(0L, 100L, 0L), 0.0)
    }
}
