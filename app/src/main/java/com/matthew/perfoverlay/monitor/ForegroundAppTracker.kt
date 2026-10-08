package com.matthew.perfoverlay.monitor

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Foreground-app attribution for recordings. Needs Usage Access, granted by
 * the user in Settings (no root). Without it, samples tag as unknown.
 */
class ForegroundAppTracker(private val context: Context) {
    fun hasPermission(): Boolean {
        val appOps =
            try {
                context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            } catch (_: Exception) {
                return false
            }
        return try {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** Most recently used package in the last minute, or null. */
    fun foregroundPackage(): String? {
        if (!hasPermission()) return null
        val usm =
            try {
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            } catch (_: Exception) {
                return null
            }
        val now = System.currentTimeMillis()
        val stats =
            try {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            } catch (_: Exception) {
                return null
            }
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
