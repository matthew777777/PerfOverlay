package com.matthew.perfoverlay.monitor

data class PowerSample(
    val currentMa: Double?,
    val voltageV: Double?,
    val watts: Double?,
    val avgMa: Double?,
    val sessionMwh: Double?,
    val charging: Boolean?,
    val batteryPct: Int?,
    val batteryTempC: Float?,
    val chargeRemainMah: Double?,
    val energyRemainWh: Double?,
    val elapsedMs: Long,
)

data class CpuCoreSample(
    val index: Int,
    val loadPct: Float?,
    val freqKhz: Long?,
)

data class CpuSample(
    val totalLoadPct: Float?,
    val cores: List<CpuCoreSample>,
)

data class GpuSample(
    val loadPct: Float?,
    val freqKhz: Long?,
    val source: String?,
)

data class MemSample(
    val totalBytes: Long,
    val availBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0)
    val usedPct: Float get() =
        if (totalBytes > 0) usedBytes * 100f / totalBytes else Float.NaN
}

data class ThermalSample(
    val cpuC: Float?,
    val gpuC: Float?,
    val skinC: Float?,
    /** PowerManager thermal status (0 none … 6 shutdown). Never null. */
    val status: Int,
)

data class NetSample(
    val rxBytesPerSec: Double,
    val txBytesPerSec: Double,
)

data class FullSample(
    val power: PowerSample,
    val cpu: CpuSample,
    val gpu: GpuSample,
    val mem: MemSample,
    val thermal: ThermalSample,
    val net: NetSample,
)
