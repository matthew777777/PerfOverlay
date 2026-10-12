package com.matthew.perfoverlay.record

import com.matthew.perfoverlay.monitor.CpuCoreSample
import com.matthew.perfoverlay.monitor.CpuSample
import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.monitor.GpuSample
import com.matthew.perfoverlay.monitor.MemSample
import com.matthew.perfoverlay.monitor.NetSample
import com.matthew.perfoverlay.monitor.PowerSample
import com.matthew.perfoverlay.monitor.ThermalSample
import java.io.File
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

    @Test
    fun recorderRoundTripsEveryOverlayField() {
        val dir = java.nio.file.Files.createTempDirectory("rec").toFile()
        try {
            val rec = SessionRecorder(dir)
            rec.append(0L, "com.example.app", fullSample())
            rec.append(1000L, null, fullSample())
            rec.close()
            assertEquals(2, rec.samples)
            val rows = SessionStore.parse(rec.file)
            assertEquals(2, rows.size)
            val r = rows[0]
            assertEquals(0L, r.tMs)
            assertEquals("com.example.app", r.pkg)
            assertEquals(500.0, r.ma!!, 0.0)
            assertEquals(4.0, r.v!!, 0.0)
            assertEquals(2.0, r.w!!, 0.0)
            assertEquals(true, r.charging)
            assertEquals(52, r.battPct)
            assertEquals(38.1f, r.battTemp!!, 1e-6f)
            assertEquals(2140.0, r.chargeMah!!, 0.0)
            assertEquals(8.3, r.energyWh!!, 0.0)
            assertEquals(35f, r.cpu!!, 0f)
            assertEquals(46.2f, r.cpuTemp!!, 1e-6f)
            assertEquals(44.0f, r.gpuTemp!!, 1e-6f)
            assertEquals(33.0f, r.skinTemp!!, 1e-6f)
            assertEquals(3, r.thermalStatus)
            assertEquals(45f, r.gpuLoad!!, 0f)
            assertEquals(610_000L, r.gpuFreqKhz)
            assertEquals(8, r.coreLoads.size)
            assertEquals(30f, r.coreLoads[3]!!, 0f)
            assertEquals(8, r.coreFreqs.size)
            assertEquals(1_000_007L, r.coreFreqs[7])
            assertEquals(6144L, r.memMb)
            assertEquals(8192L, r.memTotalMb)
            assertEquals(1280.0, r.rx!!, 0.0)
            assertEquals(450.0, r.tx!!, 0.0)
            assertEquals("unknown", rows[1].pkg)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun parsesLegacyNineColumnFiles() {
        val dir = java.nio.file.Files.createTempDirectory("legacy").toFile()
        try {
            val f = File(dir, "perfoverlay-20261008-221500.csv")
            f.writeText(
                "t_ms,package,ma,v,w,cpu_total,mem_used_mb,rx_Bs,tx_Bs\n" +
                    "1000,com.x,812.0,4.2,3.4,,4500,128.0,64.0\n",
            )
            val rows = SessionStore.parse(f)
            assertEquals(1, rows.size)
            val r = rows[0]
            assertEquals(812.0, r.ma!!, 0.0)
            assertNull(r.cpu)
            assertEquals(4500L, r.memMb)
            assertNull(r.gpuLoad)
            assertNull(r.battTemp)
            assertEquals(emptyList<Float?>(), r.coreLoads)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun statsOfComputesAvgMinMax() {
        assertNull(SessionStore.statsOf(emptyList()))
        assertNull(SessionStore.statsOf(listOf(null, Double.NaN)))
        val s = SessionStore.statsOf(listOf(1.0, null, 3.0, Double.NaN))!!
        assertEquals(2.0, s.avg, 1e-9)
        assertEquals(1.0, s.min, 1e-9)
        assertEquals(3.0, s.max, 1e-9)
        assertEquals(2, s.count)
    }

    @Test
    fun everySessionMetricExtractsAndFormats() {
        val dir = java.nio.file.Files.createTempDirectory("metrics").toFile()
        try {
            val rec = SessionRecorder(dir)
            rec.append(0L, "com.example.app", fullSample())
            rec.close()
            val row = SessionStore.parse(rec.file).single()
            val keys = SessionStore.sessionMetrics.map { it.key }
            assertEquals(keys.size, keys.toSet().size)
            for (m in SessionStore.sessionMetrics) {
                val v = m.extract(row)
                org.junit.Assert.assertNotNull("no data for ${m.key}", v)
                m.format(v!!)
                org.junit.Assert.assertNotNull(
                    "no stats for ${m.key}",
                    SessionStore.statsOf(listOf(v)),
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun listFilesFiltersAndSorts() {
        val dir = java.nio.file.Files.createTempDirectory("sessions").toFile()
        try {
            File(dir, "perfoverlay-20261008-220000.csv").writeText("t_ms,package\n")
            File(dir, "perfoverlay-20261008-230000.csv").writeText("t_ms,package\n")
            File(dir, "notes.txt").writeText("x")
            File(dir, "perfoverlay-20261008-230000.meta").writeText("x")
            val names = SessionStore.listFiles(dir).map { it.name }
            assertEquals(
                listOf("perfoverlay-20261008-230000.csv", "perfoverlay-20261008-220000.csv"),
                names,
            )
            assertEquals(emptyList<String>(), SessionStore.listFiles(File(dir, "missing")).map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun row(t: Long, pkg: String, ma: Double, w: Double) =
        SessionStore.Row(t, pkg, ma, 4.0, w, null, null, null, null)

    private fun fullSample(): FullSample {
        val cores = List(8) { i -> CpuCoreSample(i, 10f * i, 1_000_000L + i) }
        return FullSample(
            power = PowerSample(500.0, 4.0, 2.0, 480.0, 12.5, true, 52, 38.1f, 2140.0, 8.3, 3_600L),
            cpu = CpuSample(35f, cores),
            gpu = GpuSample(45f, 610_000L, "kgsl"),
            mem = MemSample(8L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024),
            thermal = ThermalSample(46.2f, 44.0f, 33.0f, 3),
            net = NetSample(1280.0, 450.0),
        )
    }
}
