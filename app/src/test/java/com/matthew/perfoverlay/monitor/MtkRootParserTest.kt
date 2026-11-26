package com.matthew.perfoverlay.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MtkRootParserTest {
    @Test
    fun parsesGedFreqOppAndKhz() {
        assertEquals(46 to 265000L, MtkRootParser.parseGedFreq("46 265000"))
        assertNull(MtkRootParser.parseGedFreq("46"))
        assertNull(MtkRootParser.parseGedFreq("x 265000"))
        assertNull(MtkRootParser.parseGedFreq("46 0"))
    }

    @Test
    fun parsesGpuLoadFromFirstField() {
        assertEquals(0f, MtkRootParser.parseGedLoad("0 0 100")!!, 0.01f)
        assertEquals(87f, MtkRootParser.parseGedLoad("87 3 10")!!, 0.01f)
        assertEquals(100f, MtkRootParser.parseGedLoad("250 0 0")!!, 0.01f)
        assertNull(MtkRootParser.parseGedLoad(null))
    }

    @Test
    fun shortensDevfreqNames() {
        assertEquals("mali", MtkRootParser.shortDevfreqName("13000000.mali"))
        assertEquals("mtk-dvfsrc", MtkRootParser.shortDevfreqName("mtk-dvfsrc-devfreq"))
    }

    @Test
    fun parsesFullDumpSkippingUnreadableDevfreq() {
        val dump = """
            Banner line from su
            @@ ged_freq
            46 265000
            @@ ged_util
            0 0 100
            @@ devfreq:13000000.mali
            265000000
            @@ devfreq:mtk-dvfsrc-devfreq
            754000000
            @@ devfreq:*
        """.trimIndent() + "\n"
        val s = MtkRootParser.parse(dump)
        assertEquals(265000L, s.gpuFreqKhz)
        assertEquals(46, s.gpuOpp)
        assertEquals(0f, s.gpuLoadPct!!, 0.01f)
        assertEquals(
            listOf(DevfreqSample("mali", 265000L), DevfreqSample("mtk-dvfsrc", 754000L)),
            s.devfreqs,
        )
        assertTrue(!s.isEmpty)
    }

    @Test
    fun emptyDumpIsEmptySample() {
        assertTrue(MtkRootParser.parse("").isEmpty)
        assertTrue(RootSample.EMPTY.isEmpty)
    }
}
