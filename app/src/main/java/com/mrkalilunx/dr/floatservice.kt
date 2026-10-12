package com.mrkalilunx.dr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
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
    private var ballView: floatball? = null
    private var params: WindowManager.LayoutParams? = null
    private var hudView: drawcanvas? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var buttonSize = 0
    private var ballW = 0
    private var ballH = 0
    private var margin = 0
    private var screenWidth = 0
    private var screenHeight = 0
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
        val size = realScreenSize()
        screenWidth = size.x
        screenHeight = size.y
        ballW = (floatball.windowWidthDp * dm.density).toInt()
        ballH = (floatball.windowHeightDp * dm.density).toInt()
        margin = dp(8)
        statusBarHeight = getStatusBarHeight()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
    }

    // 服务实现 ComponentCallbacks2,系统在配置变化时会回调,无需在 manifest 声明
    // 旋转后刷新缓存尺寸、重排浮球、更新 HUD 窗口宽高触发重绘
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val size = realScreenSize()
        screenWidth = size.x
        screenHeight = size.y
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
        ballView = null

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
        ballView = ball
        val innerInset = dp(4)

        val p = WindowManager.LayoutParams(
            ballW,
            ballH,
            overlayType(),
            // NO_LIMITS 允许越出 app bounds,IN_SCREEN 让坐标基准变成整屏含状态栏
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        p.gravity = Gravity.TOP or Gravity.START

        // API 28+ 显式声明,否则拖进挖孔区会被顶回来
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        p.x = screenWidth - ballW - margin + innerInset
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

                    // rawX/rawY 是绝对屏幕坐标,直接加在起点上即可
                    // 钳到 0..屏幕减按钮 让球边缘能贴死四边但不整颗消失
                    p.x = (startX + dx).coerceIn(0, (screenWidth - ballW).coerceAtLeast(0))
                    p.y = (startY + dy).coerceIn(0, (screenHeight - ballH).coerceAtLeast(0))
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
        val opening = hudView == null

        // 先驱动升降动画,无论 HUD 创建或移除成败都照常播放
        ballView?.setRisen(opening)

        if (opening) {
            showHudCanvas()
        } else {
            removeHudCanvas()
        }
    }

    private fun showHudCanvas() {
        if (hudView != null) {
            return
        }

        val canvas = drawcanvas(this)

        // LAYOUT_IN_SCREEN 把窗口父 frame 从 app bounds 换成整块物理屏,原点回到 (0,0)
        // LAYOUT_NO_LIMITS 允许窗口越界,两者组合保证窗口铺满整屏不偏移
        val bounds = currentScreenBounds()

        val p = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
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

    // 旋转后按比例保持相对位置,再做边界收敛
    private fun repositionFloatButton() {
        val ball = floatView ?: return
        val p = params ?: return
        val size = realScreenSize()
        val newW = size.x
        val newH = size.y
        if (newW > 0 && newH > 0 && screenWidth > 0 && screenHeight > 0) {
            val rx = p.x.toFloat() / screenWidth.toFloat()
            val ry = p.y.toFloat() / screenHeight.toFloat()
            p.x = (rx * newW.toFloat()).toInt()
            p.y = (ry * newH.toFloat()).toInt()
        }
        screenWidth = newW
        screenHeight = newH
        p.x = p.x.coerceIn(0, (screenWidth - ballW).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenHeight - ballH).coerceAtLeast(0))
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

    // 物理整屏尺寸含状态栏,LAYOUT_IN_SCREEN 下窗口坐标以此为准
    private fun realScreenSize(): Point {
        val p = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            p.set(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
        }
        return p
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