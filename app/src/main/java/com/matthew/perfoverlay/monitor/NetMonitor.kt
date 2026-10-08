package com.matthew.perfoverlay.monitor

import android.net.TrafficStats
import android.os.SystemClock

/** Device-wide network throughput from the permission-free TrafficStats API. */
object NetMath {
    /** Bytes/sec between two (bytes, elapsedMs) snapshots; 0 on reset/stall. */
    fun ratePerSec(prevBytes: Long, curBytes: Long, dtMs: Long): Double {
        if (dtMs <= 0) return 0.0
        val d = curBytes - prevBytes
        if (d < 0) return 0.0 // counter reset (reboot) — skip one frame
        return d * 1000.0 / dtMs
    }
}

class NetMonitor {
    private var prevRx = TrafficStats.UNSUPPORTED.toLong()
    private var prevTx = TrafficStats.UNSUPPORTED.toLong()
    private var prevMs = 0L

    fun sample(): NetSample {
        val nowMs = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dtMs = nowMs - prevMs
        val sample =
            if (prevMs == 0L || rx == TrafficStats.UNSUPPORTED.toLong()) {
                NetSample(0.0, 0.0)
            } else {
                NetSample(
                    NetMath.ratePerSec(prevRx, rx, dtMs),
                    NetMath.ratePerSec(prevTx, tx, dtMs),
                )
            }
        prevRx = rx
        prevTx = tx
        prevMs = nowMs
        return sample
    }
}
