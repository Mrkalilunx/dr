package com.mrkalilunx.dr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
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
    private var hudParams: WindowManager.LayoutParams? = null

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

    // 服务实现 ComponentCallbacks2,系统在配置变化时会回调,无需在 manifest 声明
    // 旋转后刷新缓存尺寸、重排浮球、更新 HUD 窗口宽高触发重绘
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        statusBarHeight = getStatusBarHeight()

        repositionFloatButton()

        if (hudView != null) {
            updateHudLayout()
        }
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

        // MATCH_PARENT 会被窗口管理器按 app bounds 解析(扣除状态栏),
        // 这里直接用物理屏尺寸,NO_LIMITS 保证窗口从屏幕左上角铺满整屏
        val bounds = currentScreenBounds()

        val p = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.START

        // API 28+ 允许窗口绘制到挖孔区域,避免 cutout 避让把顶部线裁掉
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        runCatching { wm.addView(canvas, p) }
        hudView = canvas
        hudParams = p
    }

    // 旋转后把浮球拉回新的右上角,避免留在屏幕外或错位
    private fun repositionFloatButton() {
        val ball = floatView ?: return
        val p = params ?: return
        val innerInset = dp(4)
        p.x = screenWidth - buttonSize - margin + innerInset
        p.y = statusBarHeight + margin + innerInset
        runCatching { wm.updateViewLayout(ball, p) }
    }

    // 旋转后更新 HUD 窗口宽高,View 尺寸变化会触发 drawcanvas.onSizeChanged 重建内容
    private fun updateHudLayout() {
        val canvas = hudView ?: return
        val p = hudParams ?: return
        val bounds = currentScreenBounds()
        p.width = bounds.width()
        p.height = bounds.height()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.updateViewLayout(canvas, p) }
    }

    private fun currentScreenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
    }

    private fun removeHudCanvas() {
        hudView?.let {
            runCatching { wm.removeView(it) }
        }
        hudView = null
        hudParams = null
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