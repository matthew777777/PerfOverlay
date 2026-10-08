package com.matthew.perfoverlay

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.matthew.perfoverlay.overlay.EnergyPlotView
import com.matthew.perfoverlay.record.SessionStore
import java.io.File
import java.util.Locale

/** Session preview: totals, mA replay plot, per-app breakdown. */
class SessionDetailActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path")
        val session =
            try {
                if (path == null) null else SessionStore.load(File(path))
            } catch (_: Exception) {
                null
            }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        if (session == null || session.rows.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Could not read this session."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            setContentView(ScrollView(this).apply { addView(root) })
            return
        }
        root.addView(TextView(this).apply {
            text = session.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = String.format(
                Locale.US, "%ds · %d samples @ %.1fs · avg %s · %s",
                session.durationSec.toInt(), session.rows.size, session.intervalSec,
                if (session.avgMa != null) String.format(Locale.US, "%.0fmA", session.avgMa) else "N/A",
                if (session.mwh != null) String.format(Locale.US, "%.1fmWh", session.mwh) else "N/A",
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, dp(8))
        })
        val plot = EnergyPlotView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140),
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        root.addView(plot)
        for (v in SessionStore.downsample(session.rows.map { it.ma?.toFloat() }, 240)) {
            plot.push(if (v == null || v.isNaN()) null else v.toDouble())
        }
        root.addView(TextView(this).apply {
            text = "Per-app breakdown"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        for (p in session.pkgs) {
            root.addView(TextView(this).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                text = String.format(
                    Locale.US, "%s\n  %ds · %d samples · avg %s · %s",
                    appLabel(p.pkg),
                    p.seconds.toInt(), p.samples,
                    if (p.avgMa != null) String.format(Locale.US, "%.0fmA", p.avgMa) else "N/A",
                    if (p.mwh != null) String.format(Locale.US, "%.1fmWh", p.mwh) else "N/A",
                )
                setPadding(0, 0, 0, dp(8))
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun appLabel(pkg: String): String {
        if (pkg == "unknown") return "unknown (no Usage Access?)"
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            "${packageManager.getApplicationLabel(info)} ($pkg)"
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
