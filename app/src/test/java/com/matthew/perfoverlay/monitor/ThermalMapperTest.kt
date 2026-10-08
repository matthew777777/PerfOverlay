package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalMapperTest {
    @Test
    fun selectsHottestPerType() {
        val s = ThermalMapper.select(
            listOf(
                ThermalMapper.Reading(ThermalMapper.TYPE_CPU, 44.0f, "cpu0"),
                ThermalMapper.Reading(ThermalMapper.TYPE_CPU, 52.5f, "cpu1"),
                ThermalMapper.Reading(ThermalMapper.TYPE_GPU, 41.0f, "gpu"),
            ),
        )
        assertEquals(52.5f, s.cpuC!!, 0.01f)
        assertEquals(41.0f, s.gpuC!!, 0.01f)
        assertNull(s.skinC)
    }

    @Test
    fun ignoresNaN() {
        val s = ThermalMapper.select(
            listOf(ThermalMapper.Reading(ThermalMapper.TYPE_CPU, Float.NaN, "cpu")),
        )
        assertNull(s.cpuC)
    }

    @Test
    fun classifiesZoneTypes() {
        assertEquals(ThermalMapper.TYPE_CPU, ThermalMapper.classifyZone("cpu0-a7"))
        assertEquals(ThermalMapper.TYPE_CPU, ThermalMapper.classifyZone("cluster0"))
        assertEquals(ThermalMapper.TYPE_GPU, ThermalMapper.classifyZone("gpu0"))
        assertEquals(ThermalMapper.TYPE_GPU, ThermalMapper.classifyZone("GPU-therm"))
        assertEquals(ThermalMapper.TYPE_SKIN, ThermalMapper.classifyZone("skin-therm"))
        assertNull(ThermalMapper.classifyZone("battery"))
        assertNull(ThermalMapper.classifyZone("charger"))
        assertNull(ThermalMapper.classifyZone("pa_therm"))
    }
}
