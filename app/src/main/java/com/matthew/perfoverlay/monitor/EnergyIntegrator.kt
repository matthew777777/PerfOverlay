package com.matthew.perfoverlay.monitor

import kotlin.math.abs

/**
 * Session energy integration (the "area under the power curve").
 * Pure — no Android dependencies, covered by unit tests.
 *
 * charge_mAh  = |mA| * dt_ms / 3_600_000
 * energy_mWh  = W    * dt_ms / 3_600      (= W * hours * 1000)
 */
class EnergyIntegrator {
    var chargeMah: Double = 0.0
        private set
    var energyMwh: Double = 0.0
        private set
    var elapsedMs: Long = 0L
        private set
    private var validMs: Long = 0L
    private var wattsMs: Long = 0L

    fun addSample(currentMa: Double?, watts: Double?, dtMs: Long) {
        if (dtMs <= 0) return
        elapsedMs += dtMs
        if (currentMa != null && !currentMa.isNaN()) {
            chargeMah += abs(currentMa) * dtMs / 3_600_000.0
            validMs += dtMs
        }
        if (watts != null && !watts.isNaN()) {
            energyMwh += watts * dtMs / 3_600.0
            wattsMs += dtMs
        }
    }

    /** Session-average current in mA; null until a real sample arrives. */
    fun avgMa(): Double? =
        if (validMs > 0) chargeMah / (validMs / 3_600_000.0) else null

    /** Session energy in mWh (area under the power curve); null if unknown. */
    fun energyMwhOrNull(): Double? = if (wattsMs > 0) energyMwh else null

    fun reset() {
        chargeMah = 0.0
        energyMwh = 0.0
        elapsedMs = 0L
        validMs = 0L
        wattsMs = 0L
    }
}
