package com.matthew.perfoverlay.overlay

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.matthew.perfoverlay.monitor.CpuCoreSample
import com.matthew.perfoverlay.monitor.CpuSample
import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.monitor.GpuSample
import com.matthew.perfoverlay.monitor.MemSample
import com.matthew.perfoverlay.monitor.NetSample
import com.matthew.perfoverlay.monitor.PowerSample
import com.matthew.perfoverlay.monitor.ThermalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the on-device crash where each readout TextView was
 * added to the outer layout and then re-added to the body container
 * ("The specified child already has a parent"). Constructing the view would
 * throw before the fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayViewTest {
    @Test
    fun constructsUpdatesAndCollapsesWithSingleParenting() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = OverlayView(activity)

        view.update(testSample())
        view.setCollapsed(true)
        view.setCollapsed(false)
        view.resetPlot()

        // Every attached child is parented exactly to its container.
        assertSingleParenting(view)
        // Header + body at top level; header holds title, mini, 3 buttons;
        // body holds plot + 8 readouts.
        assertEquals(2, view.childCount)
        assertEquals(5, (view.getChildAt(0) as ViewGroup).childCount)
        val body = view.getChildAt(1) as ViewGroup
        assertEquals(9, body.childCount)
        assertTrue(body.getChildAt(0) is EnergyPlotView)
        // Avg line carries the session energy-area measure.
        val energy = body.getChildAt(2) as android.widget.TextView
        assertEquals("avg 480mA 00:03 (12.5mWh)", energy.text.toString())
    }

    @Test
    fun collapsedModeShowsMiniEssentials() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = OverlayView(activity)
        view.update(testSample())

        val header = view.getChildAt(0) as ViewGroup
        val title = header.getChildAt(0)
        val mini = header.getChildAt(1) as android.widget.TextView
        val body = view.getChildAt(1)
        assertEquals(View.VISIBLE, body.visibility)
        assertEquals(View.GONE, mini.visibility)
        assertEquals(View.VISIBLE, title.visibility)

        view.setCollapsed(true)
        assertTrue(view.isCollapsed())
        assertEquals(View.GONE, body.visibility)
        assertEquals(View.VISIBLE, mini.visibility)
        assertEquals(View.GONE, title.visibility)
        // Essentials: W, signed mA, V, battery %, battery temp.
        assertEquals("2.00W +500mA 4.00V 52% 38°", mini.text.toString())
        // Unweighted so the wrapping strip hugs its content (a weight would
        // stretch it full-screen).
        val collapsedParams = mini.layoutParams as android.widget.LinearLayout.LayoutParams
        assertEquals(0f, collapsedParams.weight, 0f)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, collapsedParams.width)

        view.setCollapsed(false)
        assertEquals(View.VISIBLE, body.visibility)
        assertEquals(View.GONE, mini.visibility)
        assertEquals(View.VISIBLE, title.visibility)
        val expandedParams = mini.layoutParams as android.widget.LinearLayout.LayoutParams
        assertEquals(1f, expandedParams.weight, 0f)
    }

    @Test
    fun recordingTintsTitleRed() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = OverlayView(activity)
        val title = (view.getChildAt(0) as ViewGroup).getChildAt(0) as android.widget.TextView
        view.setRecording(true)
        assertEquals(0xFFEF5350.toInt(), title.currentTextColor)
        view.setRecording(false)
        assertEquals(0xFF8BC34A.toInt(), title.currentTextColor)
    }

    private fun assertSingleParenting(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            assertEquals(group, child.parent)
            if (child is ViewGroup) assertSingleParenting(child)
        }
    }

    private fun testSample(): FullSample {
        val cores = List(8) { i -> CpuCoreSample(i, 10f * i, 1_000_000L + i) }
        return FullSample(
            power = PowerSample(500.0, 4.0, 2.0, 480.0, 12.5, true, 52, 38.1f, 2140.0, 8.3, 3_600L),
            cpu = CpuSample(35f, cores),
            gpu = GpuSample(45f, 610_000L, "kgsl"),
            mem = MemSample(8L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024),
            thermal = ThermalSample(46.2f, 44.0f, 33.0f, 0),
            net = NetSample(1280.0, 450.0),
        )
    }
}
