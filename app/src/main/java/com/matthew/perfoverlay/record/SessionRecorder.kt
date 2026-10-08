package com.matthew.perfoverlay.record

import com.matthew.perfoverlay.monitor.FullSample
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one CSV row per sample while recording. Columns:
 * t_ms,package,ma,v,w,cpu_total,mem_used_mb,rx_Bs,tx_Bs (empty = unknown).
 * Package names never contain commas, so no quoting is needed.
 */
class SessionRecorder(dir: File) {
    val file: File
    private val writer: BufferedWriter
    var samples: Int = 0
        private set

    init {
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        file = File(dir, "perfoverlay-$stamp.csv")
        writer = file.bufferedWriter()
        writer.write("t_ms,package,ma,v,w,cpu_total,mem_used_mb,rx_Bs,tx_Bs\n")
        writer.flush()
    }

    fun append(tMs: Long, pkg: String?, s: FullSample) {
        val row = StringBuilder()
            .append(tMs).append(',')
            .append(pkg ?: "unknown").append(',')
            .append(num(s.power.currentMa)).append(',')
            .append(num(s.power.voltageV)).append(',')
            .append(num(s.power.watts)).append(',')
            .append(num(s.cpu.totalLoadPct?.toDouble())).append(',')
            .append(if (s.mem.totalBytes > 0) s.mem.usedBytes / (1024 * 1024) else "").append(',')
            .append(num(s.net.rxBytesPerSec)).append(',')
            .append(num(s.net.txBytesPerSec))
            .toString()
        writer.write(row)
        writer.newLine()
        samples++
        // Flush every 10 samples: cheap insurance against process death.
        if (samples % 10 == 0) writer.flush()
    }

    fun close() {
        try {
            writer.flush()
            writer.close()
        } catch (_: Exception) {
        }
    }

    private fun num(v: Double?): String =
        if (v == null || v.isNaN()) "" else v.toString()
}
