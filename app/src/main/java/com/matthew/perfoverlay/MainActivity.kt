package com.matthew.perfoverlay

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.matthew.perfoverlay.monitor.ForegroundAppTracker
import com.matthew.perfoverlay.overlay.OverlayService

/** Launcher screen: permissions, sampling, overlay, recording, sessions. */
class MainActivity : Activity() {
    private lateinit var txtOverlayState: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnToggle: Button
    private lateinit var rgInterval: RadioGroup
    private lateinit var txtUsageState: TextView
    private lateinit var txtRecordState: TextView
    private lateinit var btnRecord: Button
    private lateinit var tracker: ForegroundAppTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtOverlayState = findViewById(R.id.txtOverlayState)
        txtStatus = findViewById(R.id.txtStatus)
        btnToggle = findViewById(R.id.btnToggle)
        rgInterval = findViewById(R.id.rgInterval)

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnToggle.setOnClickListener {
            if (OverlayService.isRunning) {
                OverlayService.stop(this)
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    )
                    return@setOnClickListener
                }
                requestNotificationPermissionIfNeeded()
                saveInterval()
                OverlayService.start(this)
            }
            refresh()
            // isRunning flips async in onStartCommand; re-read so the label
            // never sticks on the pre-tap state.
            btnToggle.postDelayed({ refresh() }, 600)
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            if (OverlayService.isRunning) OverlayService.reset(this)
        }
        tracker = ForegroundAppTracker(this)
        txtUsageState = findViewById(R.id.txtUsageState)
        txtRecordState = findViewById(R.id.txtRecordState)
        btnRecord = findViewById(R.id.btnRecord)
        findViewById<Button>(R.id.btnUsagePerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnRecord.setOnClickListener {
            if (OverlayService.isRecording) {
                OverlayService.recordStop(this)
                Toast.makeText(this, "Recording saved to Sessions", Toast.LENGTH_SHORT).show()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    )
                    return@setOnClickListener
                }
                if (!tracker.hasPermission()) {
                    Toast.makeText(this, "No Usage Access — apps will tag as unknown", Toast.LENGTH_LONG).show()
                }
                saveInterval()
                OverlayService.recordStart(this)
            }
            refresh()
            btnRecord.postDelayed({ refresh() }, 600)
        }
        findViewById<Button>(R.id.btnSessions).setOnClickListener {
            startActivity(Intent(this, SessionsActivity::class.java))
        }
        rgInterval.setOnCheckedChangeListener { _, _ -> saveInterval() }
        restoreInterval()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val granted = Settings.canDrawOverlays(this)
        txtOverlayState.text = getString(
            if (granted) R.string.overlay_granted else R.string.overlay_missing,
        )
        txtOverlayState.setTextColor(if (granted) 0xFF8BC34A.toInt() else 0xFFEF5350.toInt())
        val running = OverlayService.isRunning
        txtStatus.text = getString(if (running) R.string.status_running else R.string.status_stopped)
        txtStatus.setTextColor(if (running) 0xFF8BC34A.toInt() else 0xFF9AA0A6.toInt())
        btnToggle.text = getString(if (running) R.string.stop_overlay else R.string.start_overlay)
        val usage = tracker.hasPermission()
        txtUsageState.text = getString(if (usage) R.string.usage_granted else R.string.usage_missing)
        txtUsageState.setTextColor(if (usage) 0xFF8BC34A.toInt() else 0xFFEF5350.toInt())
        val recording = OverlayService.isRecording
        txtRecordState.text = getString(if (recording) R.string.recording_on else R.string.recording_off)
        txtRecordState.setTextColor(if (recording) 0xFFEF5350.toInt() else 0xFF9AA0A6.toInt())
        btnRecord.text = getString(if (recording) R.string.stop_record else R.string.start_record)
    }

    private fun saveInterval() {
        val ms = when (rgInterval.checkedRadioButtonId) {
            R.id.rb500 -> 500L
            R.id.rb2000 -> 2000L
            else -> 1000L
        }
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
            .putLong(OverlayService.KEY_INTERVAL_MS, ms).apply()
    }

    private fun restoreInterval() {
        when (getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).getLong(OverlayService.KEY_INTERVAL_MS, 1000L)) {
            500L -> rgInterval.check(R.id.rb500)
            2000L -> rgInterval.check(R.id.rb2000)
            else -> rgInterval.check(R.id.rb1000)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
