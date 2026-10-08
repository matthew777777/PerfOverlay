package com.matthew.perfoverlay.monitor

import android.app.ActivityManager
import android.content.Context

class MemMonitor(context: Context) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun sample(): MemSample {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemSample(totalBytes = info.totalMem, availBytes = info.availMem)
    }
}
