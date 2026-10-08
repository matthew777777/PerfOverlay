package com.matthew.perfoverlay.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {
    @Test
    fun parsesRowsAndSkipsGarbage() {
        assertNull(SessionStore.parseRow("short,line"))
        assertNull(SessionStore.parseRow("abc,com.x,1,2,3,4,5,6,7"))
        val r = SessionStore.parseRow("1000,com.matthew.rawlens,812.0,4.2,3.4,,4500,128.0,64.0")!!
        assertEquals(1000L, r.tMs)
        assertEquals("com.matthew.rawlens", r.pkg)
        assertEquals(812.0, r.ma!!, 0.0)
        assertNull(r.cpu)
        assertEquals(4500L, r.memMb)
    }

    @Test
    fun summarizesPerPackage() {
        val rows = listOf(
            row(0L, "a.app", 1000.0, 4.0),
            row(1000L, "a.app", 1000.0, 4.0),
            row(2000L, "b.app", 500.0, 2.0),
            row(3000L, "b.app", 500.0, 2.0),
        )
        val s = SessionStore.summarize(java.io.File("perfoverlay-20261008-221500.csv"), rows)
        assertEquals("2026-10-08 22:15", s.title)
        assertEquals(1.0, s.intervalSec, 1e-9)
        assertEquals(3.0, s.durationSec, 1e-9)
        assertEquals(750.0, s.avgMa!!, 1e-9)
        // mean 3W over 4 samples x 1s = 12J = 3.333mWh.
        assertEquals(12.0 / 3.6, s.mwh!!, 1e-9)
        assertEquals(2, s.pkgs.size)
        val a = s.pkgs[0]
        assertEquals("a.app", a.pkg)
        assertEquals(2, a.samples)
        assertEquals(2.0, a.seconds, 1e-9)
        assertEquals("perfoverlay-20261008-221500.csv", s.file.name)
    }

    @Test
    fun downsamplesEvenly() {
        val vs = (0 until 10).map { it.toFloat() }
        assertEquals(listOf(0f, 2f, 4f, 6f, 8f), SessionStore.downsample(vs, 5))
        assertEquals(vs, SessionStore.downsample(vs, 10))
        assertEquals(vs, SessionStore.downsample(vs, 99))
    }

    @Test
    fun displayNameFallsBack() {
        assertEquals("custom", SessionStore.displayName("custom.csv"))
    }

    private fun row(t: Long, pkg: String, ma: Double, w: Double) =
        SessionStore.Row(t, pkg, ma, 4.0, w, null, null, null, null)
}
