package com.matthew.perfoverlay.record

import android.content.Context
import java.io.File
import java.util.Locale

/**
 * Reads recorded sessions back: tolerant CSV parse, per-package rollups,
 * display titles, and plot downsampling. Pure except for [sessionsDir].
 */
object SessionStore {
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
    )

    data class PkgStats(
        val pkg: String,
        val samples: Int,
        val seconds: Double,
        val avgMa: Double?,
        val mwh: Double?,
    )

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

    fun list(context: Context): List<File> =
        try {
            sessionsDir(context).listFiles { f ->
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
        val rows = ArrayList<Row>()
        for (i in 1 until lines.size) {
            parseRow(lines[i])?.let { rows.add(it) }
        }
        return rows
    }

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
        val pkgs = rows.groupBy { it.pkg }.map { (pkg, rs) ->
            PkgStats(pkg, rs.size, rs.size * interval, avgMaOf(rs), mwhOf(rs))
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
