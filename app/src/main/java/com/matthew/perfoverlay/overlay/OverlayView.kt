package com.matthew.perfoverlay.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.matthew.perfoverlay.R
import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.util.Format
import java.util.Locale

/**
 * Transparent floating panel: header (drag/collapse/reset/close) + energy
 * plot + monospace text readouts. Updated from the service sampler loop.
 */
class OverlayView(context: Context) : LinearLayout(context) {
    interface Callbacks {
        fun onReset()
        fun onClose()
        fun onCollapsed(collapsed: Boolean)
    }

    var callbacks: Callbacks? = null

    val header: View
    private val title: TextView
    private val plot: EnergyPlotView
    private val body: LinearLayout
    private val txtPower: TextView
    private val txtEnergy: TextView
    private val txtBat: TextView
    private val txtCpu: TextView
    private val txtCores: TextView
    private val txtGpu: TextView
    private val txtMem: TextView
    private val txtNet: TextView
    private val txtMini: TextView
    private val btnCollapse: Button
    private var collapsed = false

    fun isCollapsed(): Boolean = collapsed

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.overlay_bg)
        val pad = dp(8)
        setPadding(pad, dp(4), pad, pad)
        minimumWidth = dp(252)

        // Header row: drag anywhere on it; buttons stay clickable.
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        title = TextView(context).apply {
            text = "Perf"
            setTextColor(Color.rgb(0x8B, 0xC3, 0x4A))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(title)
        // Collapsed essentials live inline in the button row: one thin strip.
        txtMini = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
        }
        headerRow.addView(txtMini)
        btnCollapse = smallButton("▼").apply { setOnClickListener { setCollapsed(!collapsed) } }
        headerRow.addView(btnCollapse)
        headerRow.addView(smallButton("R").apply { setOnClickListener { callbacks?.onReset() } })
        headerRow.addView(smallButton("X").apply { setOnClickListener { callbacks?.onClose() } })
        addView(headerRow)
        header = headerRow

        body = LinearLayout(context).apply { orientation = VERTICAL }
        plot = EnergyPlotView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(96))
        }
        body.addView(plot)
        txtPower = monoText(12f, Color.WHITE)
        txtEnergy = monoText(12f, Color.rgb(0xFF, 0xE0, 0x82))
        txtBat = monoText(12f, Color.WHITE)
        txtCpu = monoText(12f, Color.WHITE)
        txtCores = monoText(10f, Color.rgb(0xB0, 0xBE, 0xC5))
        txtGpu = monoText(12f, Color.WHITE)
        txtMem = monoText(12f, Color.WHITE)
        txtNet = monoText(12f, Color.rgb(0xB0, 0xBE, 0xC5))
        addView(body)
    }

    fun setCollapsed(c: Boolean) {
        collapsed = c
        body.visibility = if (c) View.GONE else View.VISIBLE
        txtMini.visibility = if (c) View.VISIBLE else View.GONE
        // The Perf title is redundant next to the mini essentials line.
        title.visibility = if (c) View.GONE else View.VISIBLE
        btnCollapse.text = if (c) "▲" else "▼"
        // Unweighted when collapsed so the strip hugs its content instead of
        // stretching (a weight in a wrapping row fills the whole screen).
        (txtMini.layoutParams as LayoutParams).apply {
            width = if (c) LayoutParams.WRAP_CONTENT else 0
            weight = if (c) 0f else 1f
        }
        callbacks?.onCollapsed(c)
    }

    fun resetPlot() = plot.clear()

    /** Red Perf title while recording, green otherwise. */
    fun setRecording(recording: Boolean) {
        title.setTextColor(if (recording) Color.rgb(0xEF, 0x53, 0x50) else Color.rgb(0x8B, 0xC3, 0x4A))
    }

    fun update(s: FullSample) {
        plot.push(s.power.currentMa)
        txtPower.text = String.format(
            Locale.US, "%s %s %s",
            Format.watts(s.power.watts), Format.signedMa(s.power.currentMa, s.power.charging), Format.volts(s.power.voltageV),
        )
        val mwh = s.power.sessionMwh?.let { String.format(Locale.US, " (%.1fmWh)", it) } ?: ""
        txtEnergy.text = String.format(
            Locale.US, "avg %s %s%s",
            Format.milliamps(s.power.avgMa), Format.elapsed(s.power.elapsedMs), mwh,
        )
        txtBat.text = withTempColors(batLine(s))
        txtCpu.text = withTempColors(cpuLine(s))
        txtCores.text = coreLines(s)
        txtGpu.text = withTempColors(gpuLine(s))
        txtMem.text = String.format(
            Locale.US, "RAM %s/%s %s",
            Format.gib(s.mem.usedBytes), Format.gib(s.mem.totalBytes), Format.pct(s.mem.usedPct),
        )
        txtNet.text = String.format(
            Locale.US, "NET ↓%s ↑%s B/s",
            Format.rate(s.net.rxBytesPerSec), Format.rate(s.net.txBytesPerSec),
        )
        txtMini.text = withTempColors(miniLine(s))
    }

    /** Collapsed essentials: W, signed mA, V, battery %, battery temp. */
    private fun miniLine(s: FullSample): String {
        val p = s.power
        val parts = ArrayList<String>()
        parts.add(Format.watts(p.watts))
        parts.add(Format.signedMa(p.currentMa, p.charging))
        parts.add(Format.volts(p.voltageV))
        if (p.batteryPct != null) parts.add("${p.batteryPct}%")
        // Whole degrees: saves 2 chars so the strip never truncates.
        if (p.batteryTempC != null) parts.add(String.format(Locale.US, "%.0f°", p.batteryTempC))
        return parts.joinToString(" ")
    }

    private fun batLine(s: FullSample): String {
        val p = s.power
        val parts = ArrayList<String>()
        parts.add(if (p.batteryPct != null) "${p.batteryPct}%" else "N/A")
        if (p.batteryTempC != null) parts.add(Format.celsius(p.batteryTempC))
        val remain = p.energyRemainWh?.let { String.format(Locale.US, "%.2fWh", it) }
            ?: p.chargeRemainMah?.let { String.format(Locale.US, "%.0fmAh", it) }
        if (remain != null) parts.add(remain)
        if (s.thermal.skinC != null) parts.add("sk ${Format.celsius(s.thermal.skinC)}")
        return "BAT " + parts.joinToString(" ")
    }

    private fun cpuLine(s: FullSample): String {
        val parts = ArrayList<String>()
        if (s.cpu.totalLoadPct != null) parts.add(Format.pct(s.cpu.totalLoadPct))
        if (s.thermal.cpuC != null) parts.add(Format.celsius(s.thermal.cpuC))
        if (s.thermal.status >= 2) parts.add("HOT")
        return if (parts.isEmpty()) "CPU N/A" else "CPU " + parts.joinToString(" ")
    }

    private fun gpuLine(s: FullSample): String {
        val parts = ArrayList<String>()
        if (s.gpu.loadPct != null) parts.add(Format.pct(s.gpu.loadPct))
        if (s.gpu.freqKhz != null) parts.add(Format.freq(s.gpu.freqKhz))
        if (s.thermal.gpuC != null) parts.add(Format.celsius(s.thermal.gpuC))
        return if (parts.isEmpty()) "GPU N/A" else "GPU " + parts.joinToString(" ")
    }

    /** Tints every N.N° token: green <40, amber <50, red beyond. */
    private fun withTempColors(text: String): Spanned {
        val span = SpannableString(text)
        Regex("""\d+\.\d°""").findAll(text).forEach { m ->
            val v = m.value.dropLast(1).toFloatOrNull() ?: return@forEach
            val color = when {
                v >= 50 -> Color.rgb(0xEF, 0x53, 0x50)
                v >= 40 -> Color.rgb(0xFF, 0xCA, 0x28)
                else -> Color.rgb(0x9C, 0xCC, 0x65)
            }
            span.setSpan(
                ForegroundColorSpan(color), m.range.first, m.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return span
    }

    private fun coreLines(s: FullSample): String {
        val cores = s.cpu.cores
        if (cores.isEmpty()) return "cores: N/A"
        // Two compact rows of up to 4 cores: "12%@1.42G".
        val cells = cores.map { c ->
            val load = if (c.loadPct == null) "--" else "${c.loadPct.toInt()}%"
            "$load@${Format.freq(c.freqKhz)}"
        }
        val rows = cells.chunked(4).mapIndexed { row, chunk ->
            chunk.mapIndexed { i, cell -> "${row * 4 + i}:$cell" }.joinToString(" ")
        }
        return rows.joinToString("\n")
    }

    private fun monoText(sp: Float, color: Int): TextView =
        TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            body.addView(this)
        }

    private fun smallButton(label: String): Button =
        Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            minimumWidth = dp(36)
            minWidth = dp(36)
            minimumHeight = dp(36)
            minHeight = dp(36)
            setPadding(0, 0, 0, 0)
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
