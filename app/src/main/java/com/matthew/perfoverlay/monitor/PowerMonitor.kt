package com.matthew.perfoverlay.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import kotlin.math.abs

/**
 * Battery current/voltage/power + session integration.
 * No permission needed. Some devices report 0 current (unsupported) — those
 * surface as null (N/A) rather than a fake 0W.
 */
class PowerMonitor(private val context: Context) {
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val integrator = EnergyIntegrator()
    private var lastSampleMs = SystemClock.elapsedRealtime()

    fun reset() {
        integrator.reset()
        lastSampleMs = SystemClock.elapsedRealtime()
    }

    fun sample(): PowerSample {
        val nowMs = SystemClock.elapsedRealtime()
        val dtMs = (nowMs - lastSampleMs).coerceAtLeast(0)
        lastSampleMs = nowMs

        val battery = stickyBatteryIntent()
        val charging = readCharging(battery)
        val currentMa = readCurrentMa(charging)
        val voltageV = readVoltageV(battery)
        val watts =
            if (currentMa != null && voltageV != null) currentMa / 1000.0 * voltageV else null
        integrator.addSample(currentMa, watts, dtMs)

        return PowerSample(
            currentMa = currentMa,
            voltageV = voltageV,
            watts = watts,
            avgMa = integrator.avgMa(),
            sessionMwh = integrator.energyMwhOrNull(),
            charging = charging,
            batteryPct = readBatteryPct(battery),
            batteryTempC = readBatteryTempC(battery),
            chargeRemainMah = readCounter(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, 1000.0),
            energyRemainWh = readCounter(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER, 1e9),
            elapsedMs = integrator.elapsedMs,
        )
    }

    private fun readCurrentMa(charging: Boolean?): Double? {
        val ua =
            try {
                batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (_: Exception) {
                return null
            }
        if (ua == Long.MIN_VALUE) return null
        // 0 while plugged in is real (charge full/limit, no flow); 0 on
        // battery is impossible for a running phone, so it means unsupported.
        if (ua == 0L) return if (charging == true) 0.0 else null
        return abs(ua) / 1000.0
    }

    private fun stickyBatteryIntent(): Intent? =
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        } catch (_: Exception) {
            null
        }

    private fun readVoltageV(battery: Intent?): Double? {
        val mv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        if (mv <= 0) return null
        return mv / 1000.0
    }

    private fun readBatteryPct(battery: Intent?): Int? {
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    private fun readBatteryTempC(battery: Intent?): Float? {
        // EXTRA_TEMPERATURE is tenths of a degree Celsius.
        val t = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (t == null || t == Int.MIN_VALUE) return null
        return t / 10f
    }

    /** Remaining charge (µAh→mAh) / energy (nWh→Wh); null when unsupported. */
    private fun readCounter(property: Int, divisor: Double): Double? {
        val v =
            try {
                batteryManager.getLongProperty(property)
            } catch (_: Exception) {
                return null
            }
        if (v == Long.MIN_VALUE || v <= 0) return null
        return v / divisor
    }

    /** True when plugged in (AC/USB/wireless), false on battery, null unknown. */
    private fun readCharging(battery: Intent?): Boolean? {
        if (battery == null) return null
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        if (plugged > 0) return true
        if (plugged == 0) return false
        return when (battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> false
            else -> null
        }
    }
}
