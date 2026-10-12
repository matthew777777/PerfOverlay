package com.matthew.perfoverlay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.matthew.perfoverlay.record.SessionStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Row taps must open [SessionDetailActivity]: a focusable Delete button
 * inside the row otherwise swallows ListView item clicks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionsActivityTest {
    @Test
    fun rowHasNoFocusableDescendants() {
        seedSession()
        val activity = Robolectric.buildActivity(SessionsActivity::class.java).setup().get()
        val list = findList(activity)
        val row = list.adapter.getView(0, null, list)
        assertFalse("row has focusable child, item clicks will not fire", row.hasFocusable())
    }

    @Test
    fun tappingRowOpensDetailWithPath() {
        val file = seedSession()
        val activity = Robolectric.buildActivity(SessionsActivity::class.java).setup().get()
        val list = findList(activity)
        val row = list.adapter.getView(0, null, list)
        list.performItemClick(row, 0, list.adapter.getItemId(0))
        val next = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(SessionDetailActivity::class.java.name, next.component!!.className)
        assertEquals(file.absolutePath, next.getStringExtra("path"))
    }

    @Test
    fun sessionsContentFitsSystemWindows() {
        seedSession()
        val activity = Robolectric.buildActivity(SessionsActivity::class.java).setup().get()
        assertTrue("sessions content slides under the status bar", contentFits(findList(activity)))
    }

    @Test
    fun detailContentFitsSystemWindows() {
        val file = seedSession()
        val intent = Intent().putExtra("path", file.absolutePath)
        val activity = Robolectric.buildActivity(SessionDetailActivity::class.java, intent).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertTrue("detail content slides under the status bar", contentFits(content.getChildAt(0)))
    }

    private fun contentFits(start: View): Boolean {
        var v: View? = start
        while (v != null) {
            if (v.fitsSystemWindows) return true
            v = v.parent as? View
        }
        return false
    }

    private fun seedSession(): File {
        val dir = SessionStore.sessionsDir(RuntimeEnvironment.getApplication())
        dir.mkdirs()
        val file = File(dir, "perfoverlay-20261009-120000.csv")
        file.writeText("t_ms,package,ma,v,w,cpu,mem_mb,rx,tx\n0,com.x,100.0,4.0,0.4,,100,0.0,0.0\n")
        return file
    }

    private fun findList(activity: SessionsActivity): ListView {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        for (i in 0 until content.childCount) {
            val root = content.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until root.childCount) {
                val child = root.getChildAt(j)
                if (child is ListView) return child
            }
        }
        throw AssertionError("ListView not found")
    }
}
