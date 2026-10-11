package com.mrkalilunx.dr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

class floatservice : Service() {

    private lateinit var wm: WindowManager

    private var floatView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var hudView: drawcanvas? = null

    private var buttonSize = 0
    private var margin = 0
    private var screenWidth = 0
    private var statusBarHeight = 0
    private var touchSlop = 0

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        buttonSize = (floatball.windowSizeDp * dm.density).toInt()
        margin = dp(8)
        statusBarHeight = getStatusBarHeight()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        if (floatView == null) {
            showFloatButton()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeHudCanvas()

        floatView?.let {
            runCatching { wm.removeView(it) }
        }
        floatView = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun buildNotification(): Notification {
        val channelId = "dr_float"

        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, mainactivity::class.java),
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("dr")
            .setContentText("悬浮窗运行中")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun showFloatButton() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "没有悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val ball = floatball(this)
        val innerInset = dp(4)

        val p = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.START
        p.x = screenWidth - buttonSize - margin + innerInset
        p.y = statusBarHeight + margin + innerInset

        ball.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()

                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                    }

                    p.x = startX + dx
                    p.y = startY + dy
                    runCatching { wm.updateViewLayout(v, p) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleHud()
                    }
                    true
                }

                else -> false
            }
        }

        runCatching { wm.addView(ball, p) }
        floatView = ball
        params = p
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun toggleHud() {
        if (hudView != null) {
            removeHudCanvas()
        } else {
            showHudCanvas()
        }
    }

    private fun showHudCanvas() {
        if (hudView != null) {
            return
        }

        val canvas = drawcanvas(this)
        canvas.onCloseCanvas = { removeHudCanvas() }
        canvas.setupDemo()

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.START

        runCatching { wm.addView(canvas, p) }
        hudView = canvas
    }

    private fun removeHudCanvas() {
        hudView?.let {
            runCatching { wm.removeView(it) }
        }
        hudView = null
    }

    private fun dp(v: Int): Int {
        return (v * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) {
            resources.getDimensionPixelSize(id)
        } else {
            dp(24)
        }
    }
}