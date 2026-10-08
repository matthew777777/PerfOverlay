package com.matthew.perfoverlay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.ArrayDeque
import java.util.Locale

/**
 * Rolling live-current (mA) plot with filled area. Background stays
 * transparent — the parent overlay supplies the dim.
 */
class EnergyPlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val samples = ArrayDeque<Float>()
    var capacity: Int = 240

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        strokeWidth = 1f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x8B, 0xC3, 0x4A)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0x8B, 0xC3, 0x4A)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textSize = 24f
    }

    /** Pushes a live current reading in mA (null = unknown gap). */
    fun push(milliamps: Double?) {
        val v = milliamps?.takeIf { !it.isNaN() }?.toFloat() ?: Float.NaN
        samples.addLast(v)
        while (samples.size > capacity) samples.removeFirst()
        invalidate()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val padL = 8f
        val padR = 8f
        val padT = 30f
        val padB = 8f

        // Grid: 0 / 50% / 100% lines.
        for (f in floatArrayOf(0f, 0.5f, 1f)) {
            val y = padT + (h - padT - padB) * (1f - f)
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
        }

        var peak = 0.5f
        var top = Float.NaN
        for (s in samples) {
            if (s.isNaN()) continue
            if (top.isNaN() || s > top) top = s
            if (s > peak) peak = s
        }
        canvas.drawText(
            if (!top.isNaN()) String.format(Locale.US, "mA peak %.0f", top) else "mA —",
            padL, 22f, labelPaint,
        )

        if (samples.size < 2) return
        val plotW = w - padL - padR
        val plotH = h - padT - padB
        fun x(i: Int) = padL + plotW * i / (capacity - 1).coerceAtLeast(1)
        fun y(v: Float) = padT + plotH * (1f - (v / peak).coerceIn(0f, 1f))

        // Offset so newest sample sits at the right edge once full.
        val offset = (capacity - samples.size).coerceAtLeast(0)
        val line = Path()
        val fill = Path()
        var started = false
        var i = 0
        for (s in samples) {
            val xi = x(i + offset)
            if (s.isNaN()) {
                started = false
                i++
                continue
            }
            val yi = y(s)
            if (!started) {
                line.moveTo(xi, yi)
                fill.moveTo(xi, padT + plotH)
                fill.lineTo(xi, yi)
                started = true
            } else {
                line.lineTo(xi, yi)
                fill.lineTo(xi, yi)
            }
            i++
        }
        if (started) {
            fill.lineTo(x(i - 1 + offset), padT + plotH)
            fill.close()
            canvas.drawPath(fill, fillPaint)
            canvas.drawPath(line, linePaint)
        }

        // Current value dot.
        val last = samples.last
        if (!last.isNaN()) {
            canvas.drawCircle(x(i - 1 + offset), y(last), 4f, linePaint)
        }
    }
}
