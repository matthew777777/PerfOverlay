package com.matthew.perfoverlay.util

import java.util.Locale

/** Pure display formatting. No Android dependencies so unit tests cover it. */
object Format {
    fun freq(freqKhz: Long?): String {
        if (freqKhz == null || freqKhz <= 0) return "N/A"
        return if (freqKhz >= 1_000_000) {
            String.format(Locale.US, "%.2fG", freqKhz / 1_000_000.0)
        } else {
            String.format(Locale.US, "%dM", freqKhz / 1000)
        }
    }

    fun pct(v: Float?): String =
        if (v == null || v.isNaN()) "N/A" else String.format(Locale.US, "%d%%", v.toInt())

    fun gib(bytes: Long): String =
        String.format(Locale.US, "%.1fG", bytes / (1024.0 * 1024.0 * 1024.0))

    fun watts(w: Double?): String =
        if (w == null || w.isNaN()) "N/A" else String.format(Locale.US, "%.2fW", w)

    fun milliamps(ma: Double?): String =
        if (ma == null || ma.isNaN()) "N/A" else String.format(Locale.US, "%.0fmA", ma)

    /** Live current with direction: +charging / -on battery / plain unknown. */
    fun signedMa(ma: Double?, charging: Boolean?): String {
        if (ma == null || ma.isNaN()) return "N/A"
        val sign = when (charging) {
            true -> "+"
            false -> "-"
            null -> ""
        }
        return String.format(Locale.US, "%s%.0fmA", sign, ma)
    }

    fun volts(v: Double?): String =
        if (v == null || v.isNaN()) "N/A" else String.format(Locale.US, "%.2fV", v)

    fun celsius(c: Float?): String =
        if (c == null || c.isNaN()) "N/A" else String.format(Locale.US, "%.1f°", c)

    /** Compact byte rate: 128, 1.5K, 2.5M, 1.2G. */
    fun rate(bytesPerSec: Double): String {
        if (bytesPerSec.isNaN() || bytesPerSec < 0) return "N/A"
        return when {
            bytesPerSec >= 1e9 -> String.format(Locale.US, "%.1fG", bytesPerSec / 1e9)
            bytesPerSec >= 1e6 -> String.format(Locale.US, "%.1fM", bytesPerSec / 1e6)
            bytesPerSec >= 1e3 -> String.format(Locale.US, "%.1fK", bytesPerSec / 1e3)
            else -> String.format(Locale.US, "%.0f", bytesPerSec)
        }
    }

    fun elapsed(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}
