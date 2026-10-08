package com.matthew.perfoverlay.monitor

import android.content.Context
import android.os.HardwarePropertiesManager
import android.util.Log
import com.matthew.perfoverlay.util.Sysfs

/**
 * Pure /proc/stat parsing + load math. Covered by unit tests.
 *
 * Snapshot maps "cpu" (total) and "cpuN" (per core) to [totalJiffies, idleJiffies].
 */
object CpuStatParser {
    fun parseLine(line: String): Pair<String, LongArray>? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 5) return null
        val id = parts[0]
        if (!id.startsWith("cpu")) return null
        val nums = parts.drop(1).map { it.toLongOrNull() ?: return null }
        val total = nums.sum()
        // idle + iowait; guard short lines.
        val idle = nums[3] + (nums.getOrNull(4) ?: 0L)
        return id to longArrayOf(total, idle)
    }

    fun parse(lines: List<String>): Map<String, LongArray> {
        val out = LinkedHashMap<String, LongArray>()
        for (line in lines) {
            if (!line.startsWith("cpu")) continue
            val parsed = try { parseLine(line) } catch (_: Exception) { null }
            if (parsed != null) out[parsed.first] = parsed.second
        }
        return out
    }

    /** Sorted core indices from directory names like cpu0..cpuN. */
    fun coreIndices(names: Collection<String>): List<Int> =
        names.mapNotNull {
            if (it.matches(Regex("cpu\\d+"))) it.removePrefix("cpu").toIntOrNull() else null
        }.sorted()

    /** Load % between two snapshots; null when the delta is empty. */
    fun loadPct(prev: LongArray, cur: LongArray): Float? {
        val dt = cur[0] - prev[0]
        val di = cur[1] - prev[1]
        if (dt <= 0) return null
        return ((dt - di).coerceAtLeast(0) * 100f / dt).coerceIn(0f, 100f)
    }

    fun loads(
        prev: Map<String, LongArray>,
        cur: Map<String, LongArray>,
    ): Map<String, Float?> = cur.mapValues { (id, c) -> prev[id]?.let { loadPct(it, c) } }

    /**
     * Converts HardwarePropertiesManager CpuUsageInfo active/total deltas to
     * (total %, per-core %). Snapshots hold (activeMs, totalMs) per core in
     * core order; null when unusable.
     */
    fun hpmLoads(
        prev: List<Pair<Long, Long>>,
        cur: List<Pair<Long, Long>>,
    ): Pair<Float?, Map<Int, Float>>? {
        if (prev.size != cur.size || cur.isEmpty()) return null
        val perCore = LinkedHashMap<Int, Float>()
        var totalActive = 0L
        var totalTotal = 0L
        for (i in cur.indices) {
            val da = cur[i].first - prev[i].first
            val dt = cur[i].second - prev[i].second
            if (dt <= 0) return null
            perCore[i] = (da.coerceAtLeast(0) * 100f / dt).coerceIn(0f, 100f)
            totalActive += da.coerceAtLeast(0)
            totalTotal += dt
        }
        if (totalTotal <= 0) return null
        return (totalActive * 100f / totalTotal).coerceIn(0f, 100f) to perCore
    }
}

class CpuMonitor(context: Context) {
    private val hpm: HardwarePropertiesManager? =
        try {
            context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
        } catch (_: Exception) {
            null
        }
    private var prev: Map<String, LongArray>? = null
    private var prevHpm: List<Pair<Long, Long>>? = null
    private var loggedHpm = false

    fun sample(): CpuSample {
        // Discover cores from sysfs so freq still shows when /proc/stat load
        // is blocked; fall back to whatever /proc/stat keys exist.
        val sysfsCores = CpuStatParser.coreIndices(Sysfs.listNames("/sys/devices/system/cpu"))
        val cur = CpuStatParser.parse(Sysfs.readLines("/proc/stat"))
        val prevSnap = prev
        prev = cur
        val statLoads = if (prevSnap != null) CpuStatParser.loads(prevSnap, cur) else emptyMap()
        val statCores = CpuStatParser.coreIndices(cur.keys)
        val indices = (sysfsCores.ifEmpty { statCores }).ifEmpty {
            (0 until Runtime.getRuntime().availableProcessors()).toList()
        }

        // Load: permission-free HardwarePropertiesManager first (works under
        // strict SELinux), /proc/stat deltas as fallback.
        val hpmLoads = readHpmLoads()
        val totalLoad = hpmLoads?.first ?: statLoads["cpu"]
        val cores = indices.map { index ->
            CpuCoreSample(
                index = index,
                loadPct = hpmLoads?.second?.get(index) ?: statLoads["cpu$index"],
                freqKhz = readCoreFreqKhz(index),
            )
        }
        return CpuSample(totalLoadPct = totalLoad, cores = cores)
    }

    private fun readHpmLoads(): Pair<Float?, Map<Int, Float>>? {
        val hpm = hpm ?: return null
        val usages =
            try {
                hpm.cpuUsages
            } catch (e: Exception) {
                if (!loggedHpm) {
                    loggedHpm = true
                    Log.d("PerfOverlay", "hpm cpuUsages failed: ${e.message}")
                }
                return null
            }
        if (!loggedHpm) {
            loggedHpm = true
            Log.d("PerfOverlay", "hpm cpuUsages: ${usages.joinToString { "${it.active}/${it.total}" }}")
        }
        val cur = usages.map { it.active to it.total }
        val prevSnap = prevHpm
        prevHpm = cur
        if (prevSnap == null) return null
        return CpuStatParser.hpmLoads(prevSnap, cur)
    }

    private fun readCoreFreqKhz(index: Int): Long? {
        if (index < 0) return null
        val base = "/sys/devices/system/cpu/cpu$index/cpufreq"
        // scaling_cur_freq is the live policy freq; cpuinfo_cur_freq is the
        // hardware ask — either may be blocked per device/SoC.
        return Sysfs.readLong("$base/scaling_cur_freq")
            ?: Sysfs.readLong("$base/cpuinfo_cur_freq")
    }
}
