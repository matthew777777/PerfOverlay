package com.matthew.perfoverlay.overlay

import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.util.Format
import java.util.Locale

/**
 * One switchable overlay-plot series: how to pull it out of a [FullSample],
 * what the plot header calls it, and how to format peak values. Pure so unit
 * tests cover every extractor.
 */
data class PlotMetric(
    val key: String,
    val label: String,
    val extract: (FullSample) -> Double?,
    val format: (Double) -> String,
)

object PlotMetrics {
    const val DEFAULT_KEY = "ma"

    private fun num(fmt: String): (Double) -> String = { v -> String.format(Locale.US, fmt, v) }
    private val pct: (Double) -> String = { v -> String.format(Locale.US, "%.0f%%", v) }
    private val temp: (Double) -> String = { v -> String.format(Locale.US, "%.1f°", v) }

    /** Fixed metrics in tap-cycle order, with one entry per CPU core spliced in. */
    fun all(coreCount: Int): List<PlotMetric> {
        val list = ArrayList<PlotMetric>()
        list.add(PlotMetric("ma", "mA", { s -> s.power.currentMa }, num("%.0f")))
        list.add(PlotMetric("w", "W", { s -> s.power.watts }, num("%.2f")))
        list.add(PlotMetric("v", "V", { s -> s.power.voltageV }, num("%.2f")))
        list.add(PlotMetric("cpu", "CPU", { s -> s.cpu.totalLoadPct?.toDouble() }, pct))
        for (i in 0 until coreCount.coerceIn(0, 64)) {
            list.add(PlotMetric("core$i", "Core $i", { s -> s.cpu.cores.getOrNull(i)?.loadPct?.toDouble() }, pct))
        }
        list.add(
            PlotMetric(
                "cpuf", "CPU freq",
                { s -> s.cpu.cores.mapNotNull { it.freqKhz }.maxOrNull()?.toDouble() },
                { v -> Format.freq(v.toLong()) },
            ),
        )
        list.add(PlotMetric("gpu", "GPU", { s -> s.gpu.loadPct?.toDouble() }, pct))
        list.add(
            PlotMetric(
                "gpuf", "GPU freq",
                { s -> s.gpu.freqKhz?.toDouble() },
                { v -> Format.freq(v.toLong()) },
            ),
        )
        list.add(
            PlotMetric(
                "mem", "RAM",
                { s -> if (s.mem.totalBytes > 0) s.mem.usedBytes / (1024.0 * 1024.0 * 1024.0) else null },
                num("%.2fG"),
            ),
        )
        list.add(PlotMetric("batt", "Batt °C", { s -> s.power.batteryTempC?.toDouble() }, temp))
        list.add(PlotMetric("cput", "CPU °C", { s -> s.thermal.cpuC?.toDouble() }, temp))
        list.add(PlotMetric("gput", "GPU °C", { s -> s.thermal.gpuC?.toDouble() }, temp))
        list.add(PlotMetric("skint", "Skin °C", { s -> s.thermal.skinC?.toDouble() }, temp))
        list.add(
            PlotMetric(
                "rx", "NET ↓",
                { s -> s.net.rxBytesPerSec },
                { v -> Format.rate(v) + "/s" },
            ),
        )
        list.add(
            PlotMetric(
                "tx", "NET ↑",
                { s -> s.net.txBytesPerSec },
                { v -> Format.rate(v) + "/s" },
            ),
        )
        return list
    }

    /** Resolves a persisted key, falling back to mA for unknown keys. */
    fun byKey(key: String?, coreCount: Int): PlotMetric {
        val metrics = all(coreCount)
        return metrics.firstOrNull { it.key == key } ?: metrics.first()
    }
}
