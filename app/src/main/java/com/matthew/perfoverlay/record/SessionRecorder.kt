package com.matthew.perfoverlay.record

import com.matthew.perfoverlay.monitor.FullSample
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one CSV row per sample while recording. Every readout the overlay
 * shows is a column: power, battery, CPU total + per-core loads/freqs, GPU,
 * temps, memory, network. Package names never contain commas and per-core
 * lists use ';' separators, so no quoting is needed.
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
        writer.write(SessionStore.HEADER_V2 + "\n")
        writer.flush()
    }

    fun append(tMs: Long, pkg: String?, s: FullSample) {
        val p = s.power
        val row = listOf(
            tMs.toString(),
            pkg ?: "unknown",
            num(p.currentMa),
            num(p.voltageV),
            num(p.watts),
            when (p.charging) {
                true -> "1"
                false -> "0"
                null -> ""
            },
            p.batteryPct?.toString() ?: "",
            num(p.batteryTempC?.toDouble()),
            num(p.chargeRemainMah),
            num(p.energyRemainWh),
            num(s.cpu.totalLoadPct?.toDouble()),
            num(s.thermal.cpuC?.toDouble()),
            num(s.thermal.gpuC?.toDouble()),
            num(s.thermal.skinC?.toDouble()),
            s.thermal.status.toString(),
            num(s.gpu.loadPct?.toDouble()),
            s.gpu.freqKhz?.toString() ?: "",
            s.cpu.cores.joinToString(";") { num(it.loadPct?.toDouble()) },
            s.cpu.cores.joinToString(";") { it.freqKhz?.toString() ?: "" },
            if (s.mem.totalBytes > 0) (s.mem.usedBytes / (1024 * 1024)).toString() else "",
            if (s.mem.totalBytes > 0) (s.mem.totalBytes / (1024 * 1024)).toString() else "",
            num(s.net.rxBytesPerSec),
            num(s.net.txBytesPerSec),
        ).joinToString(",")
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
