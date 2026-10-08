package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuStatParserTest {
    @Test
    fun parsesTotalAndPerCoreLines() {
        val snap = CpuStatParser.parse(
            listOf(
                "cpu  100 0 50 850 0 0 0 0 0 0",
                "cpu0 60 0 30 410 0 0 0 0 0 0",
                "cpu1 40 0 20 440 0 0 0 0 0 0",
                "intr 12345",
                "ctxt 678",
            ),
        )
        assertEquals(setOf("cpu", "cpu0", "cpu1"), snap.keys)
        assertEquals(1000L, snap["cpu"]!![0])
        assertEquals(850L, snap["cpu"]!![1])
    }

    @Test
    fun computesLoadBetweenSnapshots() {
        val prev = CpuStatParser.parse(listOf("cpu  100 0 50 850 0 0 0 0 0 0"))
        // +100 total jiffies, +25 idle => 75% busy.
        val cur = CpuStatParser.parse(listOf("cpu  150 0 75 875 0 0 0 0 0 0"))
        val loads = CpuStatParser.loads(prev, cur)
        assertEquals(75f, loads["cpu"]!!, 0.01f)
    }

    @Test
    fun nullLoadOnEmptyDelta() {
        val snap = CpuStatParser.parse(listOf("cpu0 60 0 30 410 0 0 0 0 0 0"))
        assertNull(CpuStatParser.loads(snap, snap)["cpu0"])
    }

    @Test
    fun convertsHpmUsageDeltas() {
        val prev = listOf(100L to 1000L, 200L to 1000L)
        val cur = listOf(150L to 1100L, 400L to 1200L)
        val (total, perCore) = CpuStatParser.hpmLoads(prev, cur)!!
        assertEquals(50f, perCore[0]!!, 0.01f)
        assertEquals(100f, perCore[1]!!, 0.01f)
        assertEquals(250f * 100f / 300f, total!!, 0.01f)
    }

    @Test
    fun rejectsMismatchedHpmSnapshots() {
        assertNull(CpuStatParser.hpmLoads(listOf(1L to 2L), listOf(1L to 2L, 3L to 4L)))
        assertNull(CpuStatParser.hpmLoads(emptyList(), emptyList()))
        assertNull(CpuStatParser.hpmLoads(listOf(1L to 2L), listOf(1L to 2L)))
    }

    @Test
    fun discoversCoreIndicesFromDirNames() {
        assertEquals(
            listOf(0, 1, 2, 7),
            CpuStatParser.coreIndices(listOf("cpu0", "cpu2", "cpu1", "cpu7", "cpufreq", "cpu", "online")),
        )
        assertEquals(emptyList<Int>(), CpuStatParser.coreIndices(listOf("cpu", "cpuidle")))
    }

    @Test
    fun ignoresMalformedLines() {
        val snap = CpuStatParser.parse(listOf("cpu", "cpu9 a b c d", "cpu2 1 2 3 4"))
        // "cpu" alone and non-numeric lines drop; short-but-numeric line keeps.
        assertEquals(setOf("cpu2"), snap.keys)
    }
}
