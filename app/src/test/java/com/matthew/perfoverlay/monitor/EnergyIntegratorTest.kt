package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyIntegratorTest {
    @Test
    fun integratesChargeAndEnergy() {
        // 1000mA @ 4V = 4W for 3.6s (3600ms) => 1mAh, 4mWh.
        val e = EnergyIntegrator()
        e.addSample(1000.0, 4.0, 3600L)
        assertEquals(1.0, e.chargeMah, 1e-9)
        assertEquals(4.0, e.energyMwh, 1e-9)
        assertEquals(3600L, e.elapsedMs)
    }

    @Test
    fun accumulatesAcrossSamples() {
        val e = EnergyIntegrator()
        repeat(10) { e.addSample(500.0, 2.0, 1000L) }
        // 500mA * 10s = 5000mA*s = 1.3888..mAh; 2W * 10s = 5.555..mWh.
        assertEquals(5000.0 / 3600.0, e.chargeMah, 1e-9)
        assertEquals(20.0 / 3.6, e.energyMwh, 1e-9)
        assertEquals(10_000L, e.elapsedMs)
    }

    @Test
    fun ignoresUnknownAndNonPositiveDt() {
        val e = EnergyIntegrator()
        e.addSample(null, null, 1000L)
        e.addSample(1000.0, 4.0, 0L)
        e.addSample(1000.0, 4.0, -5L)
        assertEquals(0.0, e.chargeMah, 0.0)
        assertEquals(0.0, e.energyMwh, 0.0)
        assertEquals(1000L, e.elapsedMs)
        assertEquals(null, e.avgMa())
        assertEquals(null, e.energyMwhOrNull())
    }

    @Test
    fun sessionEnergyNeedsWattSamples() {
        val e = EnergyIntegrator()
        assertEquals(null, e.energyMwhOrNull())
        // Current without watts: avg works, energy stays unknown.
        e.addSample(1000.0, null, 3600L)
        assertEquals(1000.0, e.avgMa()!!, 1e-6)
        assertEquals(null, e.energyMwhOrNull())
        // 4W for 3.6s => 4mWh.
        e.addSample(1000.0, 4.0, 3600L)
        assertEquals(4.0, e.energyMwhOrNull()!!, 1e-9)
    }

    @Test
    fun usesCurrentMagnitude() {
        val e = EnergyIntegrator()
        e.addSample(-2000.0, 8.0, 1800L)
        assertEquals(1.0, e.chargeMah, 1e-9)
    }

    @Test
    fun averageCurrentOverSession() {
        val e = EnergyIntegrator()
        assertEquals(null, e.avgMa())
        // 1000mA for 3.6s => 1mAh over 0.001h => 1000mA average.
        e.addSample(1000.0, 4.0, 3600L)
        assertEquals(1000.0, e.avgMa()!!, 1e-6)
        // +500mA for 3.6s => 1.5mAh over 0.002h => 750mA average.
        e.addSample(500.0, 2.0, 3600L)
        assertEquals(750.0, e.avgMa()!!, 1e-6)
    }

    @Test
    fun resetClearsSession() {
        val e = EnergyIntegrator()
        e.addSample(1000.0, 4.0, 3600L)
        e.reset()
        assertEquals(0.0, e.chargeMah, 0.0)
        assertEquals(0.0, e.energyMwh, 0.0)
        assertEquals(0L, e.elapsedMs)
    }
}
