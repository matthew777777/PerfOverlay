package com.matthew.perfoverlay

import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.matthew.perfoverlay.overlay.EnergyPlotView
import com.matthew.perfoverlay.record.SessionStore
import com.matthew.perfoverlay.util.Format
import java.io.File
import java.util.Locale

/** Session preview: totals, one replay graph + avg/min/max per metric, per-app breakdown. */
class SessionDetailActivity : androidx.appcompat.app.AppCompatActivity() {
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
            fitsSystemWindows = true
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
        // One replay graph per recorded metric, each with avg/min/max.
        for (metric in SessionStore.sessionMetrics) {
            val values = session.rows.map { metric.extract(it) }
            val stats = SessionStore.statsOf(values) ?: continue
            root.addView(TextView(this).apply {
                text = metric.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(2))
            })
            root.addView(TextView(this).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                text = "avg ${metric.format(stats.avg)} · min ${metric.format(stats.min)} · max ${metric.format(stats.max)}"
                setPadding(0, 0, 0, dp(4))
            })
            val plot = EnergyPlotView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(110),
                )
                setBackgroundColor(0xFF000000.toInt())
                metricLabel = metric.label
                metricFormat = metric.format
            }
            root.addView(plot)
            plot.setSeries(
                SessionStore.downsample(values.map { it?.toFloat() }, 240),
            )
        }
        coreSection(session)?.let { root.addView(it) }
        root.addView(TextView(this).apply {
            text = "Per-app breakdown"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        for (p in session.pkgs) {
            val extra = listOfNotNull(
                p.avgW?.let { String.format(Locale.US, "%.2fW", it) },
                p.avgCpu?.let { String.format(Locale.US, "CPU %.0f%%", it) },
            ).joinToString(" · ")
            root.addView(TextView(this).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                text = String.format(
                    Locale.US, "%s\n  %ds · %d samples · avg %s · %s%s",
                    appLabel(p.pkg),
                    p.seconds.toInt(), p.samples,
                    if (p.avgMa != null) String.format(Locale.US, "%.0fmA", p.avgMa) else "N/A",
                    if (p.mwh != null) String.format(Locale.US, "%.1fmWh", p.mwh) else "N/A",
                    if (extra.isNotEmpty()) " · $extra" else "",
                )
                setPadding(0, 0, 0, dp(8))
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    /** Per-core load/freq table; null when the session has no core data. */
    private fun coreSection(session: SessionStore.Session): LinearLayout? {
        val cores = session.rows.maxOfOrNull { maxOf(it.coreLoads.size, it.coreFreqs.size) } ?: 0
        if (cores == 0) return null
        var anyLoad = false
        var anyFreq = false
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = "CPU cores"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        for (i in 0 until cores) {
            val loads = session.rows.map { it.coreLoads.getOrNull(i)?.toDouble() }
            val freqs = session.rows.map { it.coreFreqs.getOrNull(i)?.toDouble() }
            val loadStats = SessionStore.statsOf(loads)
            val freqStats = SessionStore.statsOf(freqs)
            if (loadStats != null) anyLoad = true
            if (freqStats != null) anyFreq = true
            val loadText = loadStats?.let {
                String.format(Locale.US, "avg %.0f%% · max %.0f%%", it.avg, it.max)
            } ?: "load N/A"
            val freqText = freqStats?.let {
                "@${Format.freq(it.avg.toLong())} avg"
            } ?: ""
            box.addView(TextView(this).apply {
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                text = "core $i: $loadText $freqText".trim()
            })
        }
        if (!anyLoad && !anyFreq) return null
        return box
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
