package com.matthew.perfoverlay.monitor

import android.util.Log
import com.matthew.perfoverlay.util.Sysfs
import java.io.File

/**
 * Best-effort GPU freq/load from vendor sysfs. Covered paths:
 *  - Adreno (kgsl):  /sys/class/kgsl/kgsl-3d0/gpubusy ("busy total"),
 *                    gpuclk / devfreq/cur_freq (Hz)
 *  - Generic devfreq GPU nodes (Mali Bifrost, etc.):
 *                    /sys/class/devfreq/*gpu*|*g3d*|*mali*/{cur_freq,load}
 *  - Older Mali:     .../utilization (direct %), sibling cur_freq
 * Anything unreadable degrades to N/A — never crashes, never fakes data.
 */
object GpuBusyParser {
    /** Parses "<busy> <total>" counter pairs (kgsl gpubusy, devfreq load). */
    fun parseBusyPair(text: String?): Pair<Long, Long>? {
        if (text == null) return null
        val parts = text.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val busy = parts[0].toLongOrNull() ?: return null
        val total = parts[1].toLongOrNull() ?: return null
        if (total <= 0) return null
        return busy to total
    }

    fun loadPct(prev: Pair<Long, Long>, cur: Pair<Long, Long>): Float? {
        val db = cur.first - prev.first
        val dt = cur.second - prev.second
        if (dt <= 0) return null
        return (db.coerceAtLeast(0) * 100f / dt).coerceIn(0f, 100f)
    }
}

class GpuMonitor {
    private data class Node(
        val busyPath: String?,
        val pctPath: String?,
        val freqPath: String?,
        val source: String,
    )

    private var node: Node? = null
    private var scanned = false
    private var prevBusy: Pair<Long, Long>? = null

    fun sample(): GpuSample {
        if (!scanned) {
            scanned = true
            node = findNode()
            Log.d("PerfOverlay", "gpu node: ${node?.source ?: "none found"}")
        }
        val n = node ?: return GpuSample(null, null, null)

        // Direct % first; fall through to busy-counter deltas when the
        // direct file exists but is unreadable.
        val directPct = n.pctPath?.let {
            Sysfs.readFirstLine(it)?.toFloatOrNull()?.coerceIn(0f, 100f)
        }
        val load: Float? = directPct ?: n.busyPath?.let { busyPath ->
            val cur = GpuBusyParser.parseBusyPair(Sysfs.readFirstLine(busyPath))
            val prev = prevBusy
            if (cur != null) prevBusy = cur
            if (cur != null && prev != null) GpuBusyParser.loadPct(prev, cur) else null
        }
        // devfreq/kgsl freqs are in Hz; normalize to kHz.
        val freqHz = n.freqPath?.let { Sysfs.readLong(it) }
        val freqKhz = freqHz?.takeIf { it > 0 }?.let { it / 1000 }
        return GpuSample(loadPct = load, freqKhz = freqKhz, source = n.source)
    }

    private fun findNode(): Node? {
        findKgslNode()?.let { return it }
        findDevfreqNode()?.let { return it }
        findMaliUtilNode()?.let { return it }
        return null
    }

    private fun findKgslNode(): Node? {
        val base = "/sys/class/kgsl/kgsl-3d0"
        // Direct % readings first (no delta math needed), gpubusy counters
        // as fallback.
        val pct = listOf("$base/gpu_busy_percentage", "$base/gpuload", "$base/devfreq/gpu_load")
            .firstOrNull { Sysfs.exists(it) }
        val busy = "$base/gpubusy".takeIf { Sysfs.exists(it) }
        if (pct == null && busy == null && !Sysfs.exists("$base/gpuclk")) return null
        val freq = listOf("$base/devfreq/cur_freq", "$base/gpuclk").firstOrNull { Sysfs.exists(it) }
        return Node(busyPath = busy, pctPath = pct, freqPath = freq, source = "kgsl")
    }

    private fun findDevfreqNode(): Node? {
        for (dir in Sysfs.listDirs("/sys/class/devfreq")) {
            val name = Sysfs.readFirstLine(dir.absolutePath + "/name") ?: dir.name
            val lower = name.lowercase()
            if (!lower.contains("gpu") && !lower.contains("gpufreq") && !lower.contains("g3d") &&
                !lower.contains("mali") && !lower.contains("kgsl") && !lower.contains("v3d")
            ) {
                continue
            }
            val base = dir.absolutePath
            val pct = "$base/mali_ondemand/utilisation".takeIf { Sysfs.exists(it) }
            val load = "$base/load".takeIf { Sysfs.exists(it) }
            val freq = "$base/cur_freq".takeIf { Sysfs.exists(it) }
            if (pct == null && load == null && freq == null) continue
            return Node(busyPath = load, pctPath = pct, freqPath = freq, source = "devfreq:$name")
        }
        return null
    }

    private fun findMaliUtilNode(): Node? {
        // Older Mali exposes a direct 0-100 utilization file; walk two levels
        // under /sys/devices to find it without root.
        val roots = listOf("/sys/devices/platform", "/sys/devices")
        for (root in roots) {
            for (dev in Sysfs.listDirs(root)) {
                val hit = searchUtilFile(dev, depth = 2)
                if (hit != null) {
                    val parent = hit.parent ?: continue
                    val freq = listOf("$parent/cur_freq", "$parent/clock").firstOrNull { Sysfs.exists(it) }
                    return Node(busyPath = null, pctPath = hit.absolutePath, freqPath = freq, source = "mali")
                }
            }
        }
        return null
    }

    private fun searchUtilFile(dir: File, depth: Int): File? {
        if (depth < 0) return null
        val kids =
            try {
                dir.listFiles()?.toList() ?: return null
            } catch (_: Exception) {
                return null
            }
        for (kid in kids) {
            val name = kid.name.lowercase()
            if (!kid.isDirectory && name == "utilization" &&
                (dir.absolutePath.lowercase().contains("mali") ||
                    dir.absolutePath.lowercase().contains("gpu"))
            ) {
                return kid
            }
        }
        if (depth == 0) return null
        for (kid in kids) {
            if (!kid.isDirectory) continue
            val lower = kid.absolutePath.lowercase()
            if (!lower.contains("mali") && !lower.contains("gpu")) continue
            searchUtilFile(kid, depth - 1)?.let { return it }
        }
        return null
    }
}
