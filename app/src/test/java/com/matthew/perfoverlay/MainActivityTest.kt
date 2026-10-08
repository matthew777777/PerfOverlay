package com.matthew.perfoverlay

import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against content sliding under the status bar on edge-to-edge
 * (targetSdk 35): an ancestor of the activity content must consume
 * system-window insets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test
    fun contentRootFitsSystemWindows() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        var v: View? = activity.findViewById(R.id.btnOverlayPerm)
        var fits = false
        while (v != null) {
            if (v.fitsSystemWindows) {
                fits = true
                break
            }
            v = v.parent as? View
        }
        assertTrue("content is not inset below the status bar", fits)
    }
}
