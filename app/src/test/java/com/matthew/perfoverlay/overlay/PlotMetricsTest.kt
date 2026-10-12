package com.matthew.perfoverlay.overlay

import com.matthew.perfoverlay.monitor.CpuCoreSample
import com.matthew.perfoverlay.monitor.CpuSample
import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.monitor.GpuSample
import com.matthew.perfoverlay.monitor.MemSample
import com.matthew.perfoverlay.monitor.NetSample
import com.matthew.perfoverlay.monitor.PowerSample
import com.matthew.perfoverlay.monitor.ThermalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlotMetricsTest {
    @Test
    fun cycleOrderStartsWithMaThenWAndSplicesCores() {
        val ms = PlotMetrics.all(8)
        assertEquals("ma", ms[0].key)
        assertEquals("w", ms[1].key)
        assertEquals("v", ms[2].key)
        assertEquals("cpu", ms[3].key)
        assertEquals((0 until 8).map { "core$it" }, ms.subList(4, 12).map { it.key })
        assertEquals("cpuf", ms[12].key)
        // 4 power/cpu + 8 cores + cpuf/gpu/gpuf/mem/batt/cput/gput/skint/rx/tx.
        assertEquals(22, ms.size)
        assertEquals(ms.size, ms.map { it.key }.toSet().size)
    }

    @Test
    fun byKeyFallsBackToMa() {
        assertEquals("ma", PlotMetrics.byKey(null, 8).key)
        assertEquals("ma", PlotMetrics.byKey("bogus", 8).key)
        assertEquals("ma", PlotMetrics.byKey("core3", 0).key)
        assertEquals("core3", PlotMetrics.byKey("core3", 8).key)
        assertEquals("w", PlotMetrics.byKey("w", 8).key)
    }

    @Test
    fun extractorsReadFullSample() {
        val s = testSample()
        fun v(key: String): Double? = PlotMetrics.byKey(key, 8).extract(s)
        assertEquals(500.0, v("ma")!!, 0.0)
        assertEquals(2.0, v("w")!!, 0.0)
        assertEquals(4.0, v("v")!!, 0.0)
        assertEquals(35.0, v("cpu")!!, 0.0)
        assertEquals(30.0, v("core3")!!, 0.0)
        assertEquals(1_000_007.0, v("cpuf")!!, 0.0)
        assertEquals(45.0, v("gpu")!!, 0.0)
        assertEquals(610_000.0, v("gpuf")!!, 0.0)
        assertEquals(6.0, v("mem")!!, 1e-6)
        assertEquals(38.1, v("batt")!!, 1e-3)
        assertEquals(46.2, v("cput")!!, 1e-3)
        assertEquals(44.0, v("gput")!!, 1e-3)
        assertEquals(33.0, v("skint")!!, 1e-3)
        assertEquals(1280.0, v("rx")!!, 0.0)
        assertEquals(450.0, v("tx")!!, 0.0)
    }

    @Test
    fun extractorsYieldNullForUnknown() {
        val s = FullSample(
            power = PowerSample(null, null, null, null, null, null, null, null, null, null, 0L),
            cpu = CpuSample(null, emptyList()),
            gpu = GpuSample(null, null, null),
            mem = MemSample(0L, 0L),
            thermal = ThermalSample(null, null, null, 0),
            net = NetSample(0.0, 0.0),
        )
        for (m in PlotMetrics.all(0)) {
            if (m.key == "rx" || m.key == "tx") continue // network is never null
            assertNull("expected null for ${m.key}", m.extract(s))
        }
    }

    @Test
    fun formatsDontCrash() {
        val s = testSample()
        for (m in PlotMetrics.all(8)) {
            val v = m.extract(s) ?: 0.0
            m.format(v)
        }
    }

    private fun testSample(): FullSample {
        val cores = List(8) { i -> CpuCoreSample(i, 10f * i, 1_000_000L + i) }
        return FullSample(
            power = PowerSample(500.0, 4.0, 2.0, 480.0, 12.5, true, 52, 38.1f, 2140.0, 8.3, 3_600L),
            cpu = CpuSample(35f, cores),
            gpu = GpuSample(45f, 610_000L, "kgsl"),
            mem = MemSample(8L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024),
            thermal = ThermalSample(46.2f, 44.0f, 33.0f, 0),
            net = NetSample(1280.0, 450.0),
        )
    }
}
