package com.matthew.perfoverlay.record

import android.content.Context
import com.matthew.perfoverlay.util.Format
import java.io.File
import java.util.Locale

/**
 * Reads recorded sessions back: tolerant CSV parse, per-package rollups,
 * per-metric stats, display titles, and plot downsampling.
 * Pure except for [sessionsDir].
 */
object SessionStore {
    /** Current writer header (SessionRecorder). Kept here so reader/writer match. */
    const val HEADER_V2 =
        "t_ms,package,ma,v,w,charging,batt_pct,batt_temp,charge_mah,energy_wh," +
            "cpu_total,cpu_temp,gpu_temp,skin_temp,thermal_status,gpu_load,gpu_freq_khz," +
            "core_loads,core_freqs,mem_used_mb,mem_total_mb,rx_Bs,tx_Bs"

    data class Row(
        val tMs: Long,
        val pkg: String,
        val ma: Double?,
        val v: Double?,
        val w: Double?,
        val cpu: Float?,
        val memMb: Long?,
        val rx: Double?,
        val tx: Double?,
        val charging: Boolean? = null,
        val battPct: Int? = null,
        val battTemp: Float? = null,
        val chargeMah: Double? = null,
        val energyWh: Double? = null,
        val cpuTemp: Float? = null,
        val gpuTemp: Float? = null,
        val skinTemp: Float? = null,
        val thermalStatus: Int? = null,
        val gpuLoad: Float? = null,
        val gpuFreqKhz: Long? = null,
        val coreLoads: List<Float?> = emptyList(),
        val coreFreqs: List<Long?> = emptyList(),
        val memTotalMb: Long? = null,
    )

    data class PkgStats(
        val pkg: String,
        val samples: Int,
        val seconds: Double,
        val avgMa: Double?,
        val mwh: Double?,
        val avgW: Double? = null,
        val avgCpu: Double? = null,
    )

    /** Avg/min/max over the known values of one series. */
    data class MetricStats(
        val avg: Double,
        val min: Double,
        val max: Double,
        val count: Int,
    )

    /**
     * One graphed session series: how to pull it out of a [Row] and how to
     * format its avg/min/max. Mirrors the overlay's PlotMetrics.
     */
    data class SessionMetric(
        val key: String,
        val title: String,
        val label: String,
        val extract: (Row) -> Double?,
        val format: (Double) -> String,
    )

    val sessionMetrics: List<SessionMetric> = listOf(
        SessionMetric("ma", "Current", "mA", { r -> r.ma }, { v -> String.format(Locale.US, "%.0fmA", v) }),
        SessionMetric("w", "Power", "W", { r -> r.w }, { v -> String.format(Locale.US, "%.2fW", v) }),
        SessionMetric("v", "Voltage", "V", { r -> r.v }, { v -> String.format(Locale.US, "%.2fV", v) }),
        SessionMetric("cpu", "CPU total", "CPU", { r -> r.cpu?.toDouble() }, { v -> String.format(Locale.US, "%.0f%%", v) }),
        SessionMetric(
            "cpuf", "CPU freq (max core)", "CPU freq",
            { r -> r.coreFreqs.filterNotNull().maxOrNull()?.toDouble() },
            { v -> Format.freq(v.toLong()) },
        ),
        SessionMetric("gpu", "GPU load", "GPU", { r -> r.gpuLoad?.toDouble() }, { v -> String.format(Locale.US, "%.0f%%", v) }),
        SessionMetric(
            "gpuf", "GPU freq", "GPU freq",
            { r -> r.gpuFreqKhz?.toDouble() },
            { v -> Format.freq(v.toLong()) },
        ),
        SessionMetric(
            "mem", "Memory used", "RAM",
            { r -> r.memMb?.toDouble() },
            { v -> String.format(Locale.US, "%.2fGB", v / 1024.0) },
        ),
        SessionMetric("batt", "Battery temp", "Batt °C", { r -> r.battTemp?.toDouble() }, { v -> String.format(Locale.US, "%.1f°", v) }),
        SessionMetric("cput", "CPU temp", "CPU °C", { r -> r.cpuTemp?.toDouble() }, { v -> String.format(Locale.US, "%.1f°", v) }),
        SessionMetric("gput", "GPU temp", "GPU °C", { r -> r.gpuTemp?.toDouble() }, { v -> String.format(Locale.US, "%.1f°", v) }),
        SessionMetric("skint", "Skin temp", "Skin °C", { r -> r.skinTemp?.toDouble() }, { v -> String.format(Locale.US, "%.1f°", v) }),
        SessionMetric("rx", "Network down", "NET ↓", { r -> r.rx }, { v -> Format.rate(v) + "/s" }),
        SessionMetric("tx", "Network up", "NET ↑", { r -> r.tx }, { v -> Format.rate(v) + "/s" }),
    )

    /** Avg/min/max over known values; null when the series is entirely unknown. */
    fun statsOf(values: Collection<Double?>): MetricStats? {
        var n = 0
        var sum = 0.0
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        for (v in values) {
            if (v == null || v.isNaN()) continue
            n++
            sum += v
            if (v < min) min = v
            if (v > max) max = v
        }
        if (n == 0) return null
        return MetricStats(avg = sum / n, min = min, max = max, count = n)
    }

    data class Session(
        val file: File,
        val title: String,
        val rows: List<Row>,
        val intervalSec: Double,
        val durationSec: Double,
        val avgMa: Double?,
        val mwh: Double?,
        val pkgs: List<PkgStats>,
    )

    fun sessionsDir(context: Context): File =
        context.getExternalFilesDir("sessions") ?: File(context.filesDir, "sessions")

    fun list(context: Context): List<File> = listFiles(sessionsDir(context))

    fun listFiles(dir: File): List<File> =
        try {
            dir.listFiles { f ->
                f.isFile && f.name.startsWith("perfoverlay-") && f.name.endsWith(".csv")
            }?.sortedByDescending { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

    /** "perfoverlay-20261008-221500.csv" -> "2026-10-08 22:15". */
    fun displayName(fileName: String): String {
        val m = Regex("""perfoverlay-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})\.csv""").matchEntire(fileName)
        return if (m != null) {
            val (y, mo, d, h, mi) = m.destructured
            "$y-$mo-$d $h:$mi"
        } else {
            fileName.removeSuffix(".csv")
        }
    }

    fun parse(file: File): List<Row> {
        val lines =
            try {
                file.bufferedReader().use { it.readLines() }
            } catch (_: Exception) {
                return emptyList()
            }
        if (lines.isEmpty() || !lines[0].startsWith("t_ms,")) return emptyList()
        val v2 = lines[0].split(',').map { it.trim() }.toSet().containsAll(
            listOf("charging", "core_loads", "mem_total_mb"),
        )
        val rows = ArrayList<Row>()
        for (i in 1 until lines.size) {
            val row = if (v2) parseRowV2(lines[0], lines[i]) else parseRow(lines[i])
            if (row != null) rows.add(row)
        }
        return rows
    }

    /** Legacy 9-column rows (pre-1.1 sessions). */
    fun parseRow(line: String): Row? {
        val c = line.split(',')
        if (c.size < 9) return null
        val t = c[0].toLongOrNull() ?: return null
        fun d(s: String): Double? = s.toDoubleOrNull()?.takeIf { !it.isNaN() }
        return Row(
            tMs = t,
            pkg = c[1].ifEmpty { "unknown" },
            ma = d(c[2]),
            v = d(c[3]),
            w = d(c[4]),
            cpu = c[5].toFloatOrNull()?.takeIf { !it.isNaN() },
            memMb = c[6].toLongOrNull(),
            rx = d(c[7]),
            tx = d(c[8]),
        )
    }

    /** Current-format rows, located by header name so column order can evolve. */
    fun parseRowV2(header: String, line: String): Row? {
        val names = header.split(',')
        val c = line.split(',')
        // Tolerate short/trailing-comma rows; require the leading essentials.
        if (c.size < 9) return null
        fun col(name: String): String {
            val i = names.indexOf(name)
            return if (i >= 0 && i < c.size) c[i] else ""
        }
        fun d(s: String): Double? = s.toDoubleOrNull()?.takeIf { !it.isNaN() }
        fun f(s: String): Float? = s.toFloatOrNull()?.takeIf { !it.isNaN() }
        val t = col("t_ms").toLongOrNull() ?: return null
        return Row(
            tMs = t,
            pkg = col("package").ifEmpty { "unknown" },
            ma = d(col("ma")),
            v = d(col("v")),
            w = d(col("w")),
            cpu = f(col("cpu_total")),
            memMb = col("mem_used_mb").toLongOrNull(),
            rx = d(col("rx_Bs")),
            tx = d(col("tx_Bs")),
            charging = col("charging").let { if (it == "1") true else if (it == "0") false else null },
            battPct = col("batt_pct").toIntOrNull(),
            battTemp = f(col("batt_temp")),
            chargeMah = d(col("charge_mah")),
            energyWh = d(col("energy_wh")),
            cpuTemp = f(col("cpu_temp")),
            gpuTemp = f(col("gpu_temp")),
            skinTemp = f(col("skin_temp")),
            thermalStatus = col("thermal_status").toIntOrNull(),
            gpuLoad = f(col("gpu_load")),
            gpuFreqKhz = col("gpu_freq_khz").toLongOrNull(),
            coreLoads = col("core_loads").splitList().map { f(it) },
            coreFreqs = col("core_freqs").splitList().map { it.toLongOrNull() },
            memTotalMb = col("mem_total_mb").toLongOrNull(),
        )
    }

    /** ';'-separated per-core column; blank means the device reported no cores. */
    private fun String.splitList(): List<String> =
        if (isBlank()) emptyList() else split(';').take(64)

    fun summarize(file: File, rows: List<Row>): Session {
        val interval = medianIntervalSec(rows)
        val duration = if (rows.size > 1) (rows.last().tMs - rows.first().tMs) / 1000.0 else 0.0
        fun avgMaOf(rs: List<Row>): Double? {
            val vs = rs.mapNotNull { it.ma }
            return if (vs.isEmpty()) null else vs.average()
        }
        fun mwhOf(rs: List<Row>): Double? {
            val vs = rs.mapNotNull { it.w }
            if (vs.isEmpty()) return null
            return vs.average() * (rs.size * interval) / 3.6
        }
        fun avgWOf(rs: List<Row>): Double? {
            val vs = rs.mapNotNull { it.w }
            return if (vs.isEmpty()) null else vs.average()
        }
        fun avgCpuOf(rs: List<Row>): Double? {
            val vs = rs.mapNotNull { it.cpu?.toDouble() }
            return if (vs.isEmpty()) null else vs.average()
        }
        val pkgs = rows.groupBy { it.pkg }.map { (pkg, rs) ->
            PkgStats(pkg, rs.size, rs.size * interval, avgMaOf(rs), mwhOf(rs), avgWOf(rs), avgCpuOf(rs))
        }.sortedByDescending { it.samples }
        return Session(
            file = file,
            title = displayName(file.name),
            rows = rows,
            intervalSec = interval,
            durationSec = duration,
            avgMa = avgMaOf(rows),
            mwh = mwhOf(rows),
            pkgs = pkgs,
        )
    }

    fun load(file: File): Session = summarize(file, parse(file))

    /** Median sample gap; falls back to 1s for short files. */
    fun medianIntervalSec(rows: List<Row>): Double {
        if (rows.size < 3) return 1.0
        val gaps = rows.zipWithNext { a, b -> (b.tMs - a.tMs).coerceAtLeast(0) }.sorted()
        val median = gaps[gaps.size / 2]
        return if (median > 0) median / 1000.0 else 1.0
    }

    /** Evenly thins a series to at most max points for the replay plot. */
    fun downsample(values: List<Float?>, max: Int): List<Float?> {
        if (values.size <= max || max <= 0) return values
        val step = values.size.toDouble() / max
        return List(max) { i -> values[(i * step).toInt().coerceIn(values.indices)] }
    }

    fun listTitle(s: Session): String =
        String.format(
            Locale.US, "%s · %ds · %d app%s · %s · %s",
            s.title, s.durationSec.toInt(), s.pkgs.size, if (s.pkgs.size == 1) "" else "s",
            if (s.avgMa != null) String.format(Locale.US, "%.0fmA", s.avgMa) else "N/A",
            if (s.mwh != null) String.format(Locale.US, "%.1fmWh", s.mwh) else "N/A",
        )
}
