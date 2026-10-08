package com.matthew.perfoverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.matthew.perfoverlay.record.SessionStore
import java.io.File

/** Recorded sessions: tap to preview, long-press to delete. */
class SessionsActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var files: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
        list.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val file = files.getOrNull(pos) ?: return@OnItemLongClickListener false
            if (file.delete()) Toast.makeText(this, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
            refresh()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        files = SessionStore.list(this)
        val titles = files.map {
            try {
                SessionStore.listTitle(SessionStore.load(it))
            } catch (_: Exception) {
                it.name
            }
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titles)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
