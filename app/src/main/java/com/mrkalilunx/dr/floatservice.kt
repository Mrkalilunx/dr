package com.mrkalilunx.dr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class floatservice : Service() {

    private lateinit var wm: WindowManager

    private var floatView: TextView? = null
    private var panelView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var buttonSize = 0
    private var margin = 0
    private var screenWidth = 0
    private var statusBarHeight = 0
    private var touchSlop = 0

    private var isPanelShowing = false
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
        buttonSize = dp(48)
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
        removePanel()

        floatView?.let {
            wm.removeView(it)
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

        val tv = TextView(this)
        tv.text = "dr"
        tv.textSize = 16f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(0xFFFFFFFF.toInt())

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xFF3F51B5.toInt())
        tv.background = bg

        val p = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.START
        p.x = screenWidth - buttonSize - margin
        p.y = statusBarHeight + margin

        tv.setOnTouchListener { v, event ->
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
                    wm.updateViewLayout(v, p)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        togglePanel()
                    }
                    true
                }

                else -> false
            }
        }

        wm.addView(tv, p)
        floatView = tv
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

    private fun togglePanel() {
        if (isPanelShowing) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panelView != null) {
            return
        }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(0xEEFFFFFF.toInt())
        panel.setPadding(dp(8), dp(8), dp(8), dp(8))

        val labels = listOf("Toast A", "Toast B", "改按钮文字", "关闭面板")

        for (label in labels) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener {
                when (label) {
                    "Toast A" -> Toast.makeText(this, "按钮 A", Toast.LENGTH_SHORT).show()
                    "Toast B" -> Toast.makeText(this, "按钮 B", Toast.LENGTH_SHORT).show()
                    "改按钮文字" -> floatView?.text = "OK"
                    "关闭面板" -> removePanel()
                }
            }
            panel.addView(b)
        }

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.END
        p.x = margin
        p.y = (params?.y ?: 0) + buttonSize + margin

        wm.addView(panel, p)
        panelView = panel
        isPanelShowing = true
    }

    private fun removePanel() {
        panelView?.let {
            wm.removeView(it)
        }
        panelView = null
        isPanelShowing = false
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