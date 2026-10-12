package com.matthew.perfoverlay

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.matthew.perfoverlay.record.SessionStore
import java.io.File

/** Recorded sessions: tap to preview, Delete button per row. */
class SessionsActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var files: List<File> = emptyList()
    private var titles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "Recorded sessions"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, Typeface.BOLD)
        })
        empty = TextView(this).apply {
            text = "No sessions yet — start recording from the main screen."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(24), 0, 0)
            gravity = Gravity.CENTER
        }
        list = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply { topMargin = dp(12) }
            emptyView = empty
        }
        root.addView(empty)
        root.addView(list)
        setContentView(root)

        list.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, pos, _ ->
            val file = files.getOrNull(pos) ?: return@OnItemClickListener
            startActivity(Intent(this, SessionDetailActivity::class.java).putExtra("path", file.absolutePath))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        files = SessionStore.list(this)
        titles = files.map {
            try {
                SessionStore.listTitle(SessionStore.load(it))
            } catch (_: Exception) {
                it.name
            }
        }
        list.adapter = SessionAdapter()
    }

    private inner class SessionAdapter : android.widget.BaseAdapter() {
        override fun getCount(): Int = files.size
        override fun getItem(pos: Int): Any = files[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@SessionsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // A focusable button in the row swallows ListView item clicks,
                // so row taps would never open the detail screen.
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setPadding(dp(4), dp(8), dp(4), dp(8))
                addView(TextView(this@SessionsActivity).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(com.google.android.material.button.MaterialButton(this@SessionsActivity).apply {
                    text = "Delete"
                    isFocusable = false
                    isFocusableInTouchMode = false
                })
            }
            (row.getChildAt(0) as TextView).text = titles[pos]
            val file = files[pos]
            (row.getChildAt(1) as com.google.android.material.button.MaterialButton).setOnClickListener {
                if (file.delete()) Toast.makeText(this@SessionsActivity, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                refresh()
            }
            return row
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
