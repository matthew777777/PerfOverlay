package com.matthew.perfoverlay.monitor

import android.content.Context
import android.os.HardwarePropertiesManager
import android.os.PowerManager
import android.util.Log
import com.matthew.perfoverlay.util.Sysfs

/**
 * Device temperatures. Primary source is the permission-free platform
 * HardwarePropertiesManager; fallback is a thermal_zone sysfs scan (readable
 * on permissive devices, denied on strict SELinux like this Xiaomi).
 */
object ThermalMapper {
    /** Our normalized reading: (sensorType, celsius, name). Types mirror HPM. */
    data class Reading(val type: Int, val celsius: Float, val name: String)

    const val TYPE_CPU = 0
    const val TYPE_GPU = 1
    const val TYPE_SKIN = 3

    /**
     * Picks cpu/gpu/skin from mixed readings. HPM-typed readings win; when
     * several share a type, the hottest wins (closest to the throttle point).
     */
    fun select(readings: List<Reading>, status: Int = 0): ThermalSample {
        fun hottest(type: Int): Float? =
            readings.filter { it.type == type && !it.celsius.isNaN() }
                .maxOfOrNull { it.celsius }
        return ThermalSample(
            cpuC = hottest(TYPE_CPU), gpuC = hottest(TYPE_GPU),
            skinC = hottest(TYPE_SKIN), status = status,
        )
    }

    /** Maps a thermal_zone type string to a sensor type, or null if unknown. */
    fun classifyZone(type: String): Int? {
        val t = type.lowercase()
        return when {
            "gpu" in t -> TYPE_GPU
            "skin" in t || "quiet" in t -> TYPE_SKIN
            "cpu" in t || "cluster" in t || "big" in t || "little" in t ||
                "silver" in t || "gold" in t || t.startsWith("tcap") -> TYPE_CPU
            // Known non-CPU/GPU/skin zones stay unmapped (battery temp comes
            // from the battery intent instead).
            else -> null
        }
    }
}

class ThermalMonitor(context: Context) {
    private val hpm: HardwarePropertiesManager? =
        try {
            context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as? HardwarePropertiesManager
        } catch (_: Exception) {
            null
        }
    private val powerManager: PowerManager? =
        try {
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (_: Exception) {
            null
        }
    private var loggedHpm = false

    fun sample(): ThermalSample {
        val readings = ArrayList<ThermalMapper.Reading>()
        readHpm(readings)
        if (readings.isEmpty()) readThermalZones(readings)
        val status =
            try {
                powerManager?.getCurrentThermalStatus() ?: 0
            } catch (_: Exception) {
                0
            }
        return ThermalMapper.select(readings, status)
    }

    private fun readHpm(out: MutableList<ThermalMapper.Reading>) {
        val hpm = hpm ?: return
        val types = mapOf(
            HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU to ThermalMapper.TYPE_CPU,
            HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU to ThermalMapper.TYPE_GPU,
            HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN to ThermalMapper.TYPE_SKIN,
        )
        val summary = StringBuilder()
        for ((hpmType, mapped) in types) {
            val temps =
                try {
                    hpm.getDeviceTemperatures(hpmType, HardwarePropertiesManager.TEMPERATURE_CURRENT)
                } catch (_: Exception) {
                    continue
                }
            if (!loggedHpm) summary.append("$hpmType=[${temps.joinToString()}];")
            for (t in temps) {
                if (t.isNaN() || t == HardwarePropertiesManager.UNDEFINED_TEMPERATURE) continue
                out.add(ThermalMapper.Reading(mapped, t, ""))
            }
        }
        if (!loggedHpm) {
            loggedHpm = true
            Log.d("PerfOverlay", "hpm temps: $summary")
        }
    }

    private fun readThermalZones(out: MutableList<ThermalMapper.Reading>) {
        for (dir in Sysfs.listDirs("/sys/class/thermal")) {
            if (!dir.name.startsWith("thermal_zone")) continue
            val type = Sysfs.readFirstLine(dir.absolutePath + "/type") ?: continue
            val kind = ThermalMapper.classifyZone(type) ?: continue
            // Zone temps are millidegrees; some vendors use decidegree.
            val raw = Sysfs.readLong(dir.absolutePath + "/temp") ?: continue
            val celsius = if (raw > 1000) raw / 1000f else raw / 10f
            if (celsius < -50 || celsius > 150) continue
            out.add(ThermalMapper.Reading(kind, celsius, type))
        }
    }
}
