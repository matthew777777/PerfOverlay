package com.matthew.perfoverlay.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.matthew.perfoverlay.MainActivity
import com.matthew.perfoverlay.R
import com.matthew.perfoverlay.monitor.CpuMonitor
import com.matthew.perfoverlay.monitor.ForegroundAppTracker
import com.matthew.perfoverlay.monitor.FullSample
import com.matthew.perfoverlay.monitor.GpuMonitor
import com.matthew.perfoverlay.monitor.MemMonitor
import com.matthew.perfoverlay.monitor.NetMonitor
import com.matthew.perfoverlay.monitor.PowerMonitor
import com.matthew.perfoverlay.monitor.ThermalMonitor
import com.matthew.perfoverlay.record.SessionRecorder
import com.matthew.perfoverlay.record.SessionStore

/**
 * Foreground service hosting the floating profiler overlay.
 * Sampling runs on a background thread; view updates post to the main thread.
 */
class OverlayService : Service() {
    companion object {
        const val ACTION_START = "com.matthew.perfoverlay.action.START"
        const val ACTION_STOP = "com.matthew.perfoverlay.action.STOP"
        const val ACTION_RESET = "com.matthew.perfoverlay.action.RESET"
        const val ACTION_RECORD_START = "com.matthew.perfoverlay.action.RECORD_START"
        const val ACTION_RECORD_STOP = "com.matthew.perfoverlay.action.RECORD_STOP"
        const val PREFS = "perf_overlay"
        const val KEY_INTERVAL_MS = "interval_ms"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isRecording: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }

        fun reset(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_RESET))
        }

        fun recordStart(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_RECORD_START)
            if (isRunning) context.startService(intent) else context.startForegroundService(intent)
        }

        fun recordStop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_RECORD_STOP))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlay: OverlayView? = null
    private var samplerThread: HandlerThread? = null
    private var sampler: Handler? = null
    private var mainHandler: Handler? = null

    private lateinit var power: PowerMonitor
    private lateinit var cpu: CpuMonitor
    private lateinit var gpu: GpuMonitor
    private lateinit var mem: MemMonitor
    private lateinit var thermal: ThermalMonitor
    private lateinit var net: NetMonitor
    private lateinit var tracker: ForegroundAppTracker
    private var recorder: SessionRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        power = PowerMonitor(this)
        cpu = CpuMonitor(this)
        gpu = GpuMonitor()
        mem = MemMonitor(this)
        thermal = ThermalMonitor(this)
        net = NetMonitor()
        tracker = ForegroundAppTracker(this)
        mainHandler = Handler(mainLooper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESET -> {
                power.reset()
                val o = overlay
                if (o != null) mainHandler?.post { o.resetPlot() }
                return START_STICKY
            }
            ACTION_RECORD_START -> {
                startRecording()
                return START_STICKY
            }
            ACTION_RECORD_STOP -> {
                stopRecording()
                return START_STICKY
            }
            else -> { /* START: fall through */ }
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        attachOverlay()
        startLoop()
        isRunning = true
        return START_STICKY
    }

    private fun startRecording() {
        if (!isRunning) {
            if (!Settings.canDrawOverlays(this)) return
            startForegroundNotification()
            attachOverlay()
            startLoop()
            isRunning = true
        }
        if (isRecording) return
        try {
            recorder = SessionRecorder(SessionStore.sessionsDir(this))
            isRecording = true
            val o = overlay
            if (o != null) mainHandler?.post { o.setRecording(true) }
        } catch (_: Exception) {
            recorder = null
            isRecording = false
        }
    }

    private fun stopRecording() {
        val r = recorder
        recorder = null
        isRecording = false
        try {
            r?.close()
        } catch (_: Exception) {
        }
        val o = overlay
        if (o != null) mainHandler?.post { o.setRecording(false) }
    }

    override fun onDestroy() {
        stopRecording()
        isRunning = false
        sampler?.removeCallbacksAndMessages(null)
        samplerThread?.quitSafely()
        samplerThread = null
        sampler = null
        val wm = windowManager
        val view = overlay
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            }
        }
        windowManager = null
        overlay = null
        super.onDestroy()
    }

    private fun intervalMs(): Long =
        getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_INTERVAL_MS, 1000L)
            .coerceIn(250L, 5000L)

    private fun startLoop() {
        if (samplerThread != null) return
        val thread = HandlerThread("perf-overlay-sampler").also { it.start() }
        samplerThread = thread
        val handler = Handler(thread.looper)
        sampler = handler
        // Prime CPU/GPU delta samplers so the first visible frame has loads.
        handler.post {
            try {
                cpu.sample()
                gpu.sample()
            } catch (_: Exception) {
            }
            handler.post(object : Runnable {
                override fun run() {
                    tick()
                    handler.postDelayed(this, intervalMs())
                }
            })
        }
    }

    private fun tick() {
        val sample: FullSample = try {
            FullSample(
                power = power.sample(), cpu = cpu.sample(), gpu = gpu.sample(),
                mem = mem.sample(), thermal = thermal.sample(), net = net.sample(),
            )
        } catch (_: Exception) {
            return
        }
        val rec = recorder
        if (rec != null) {
            try {
                rec.append(sample.power.elapsedMs, tracker.foregroundPackage(), sample)
            } catch (_: Exception) {
            }
        }
        val view = overlay ?: return
        mainHandler?.post {
            try {
                view.update(sample)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = OverlayView(this)
        overlay = view
        // Fixed compact width: WRAP_CONTENT + weighted header stretched full-screen.
        // Collapsed, the unweighted strip wraps its content instead (onCollapsed).
        val widthPx = (300 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 120
        }
        view.callbacks = object : OverlayView.Callbacks {
            override fun onReset() {
                power.reset()
                view.resetPlot()
            }

            override fun onClose() {
                stopSelf()
            }

            override fun onCollapsed(collapsed: Boolean) {
                params.width = if (collapsed) {
                    WindowManager.LayoutParams.WRAP_CONTENT
                } else {
                    (300 * resources.displayMetrics.density).toInt()
                }
                try {
                    wm.updateViewLayout(view, params)
                } catch (_: Exception) {
                }
            }
        }
        // Drag by the header title.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel("prof", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, "prof")
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Profiling over other apps — tap to open, Stop to end.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }
}
