package com.mrkalilunx.dr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import java.util.Locale

// HUD 画布:框住整个可用区域 + 左上角屏幕信息面板
// 纯展示层,由外层 overlay 窗口加 FLAG_NOT_TOUCHABLE 保证触摸穿透
class drawcanvas(context: Context) : View(context) {

    private val ops = ArrayList<DrawOp>()

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0x99FFFFFF.toInt()
    }

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99000000.toInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
        textSize = sp(13f)
        textAlign = Paint.Align.LEFT
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) setupDemo(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (op in ops) {
            when (op) {
                is DrawOp.Rect -> canvas.drawRect(op.rect, op.paint)
                is DrawOp.RoundRect -> canvas.drawRoundRect(op.rect, op.rx, op.ry, op.paint)
                is DrawOp.Text -> canvas.drawText(op.text, op.x, op.y, op.paint)
            }
        }
    }

    private fun setupDemo(w: Int, h: Int) {
        ops.clear()

        val inset = dp(16f)

        // 框住整个物理屏幕四边
        ops.add(DrawOp.Rect(RectF(inset, inset, w - inset, h - inset), borderPaint))

        val lines = screenInfo()
        val fm = textPaint.fontMetrics
        val lineHeight = fm.descent - fm.ascent + dp(6f)
        val blockHeight = (fm.descent - fm.ascent) + (lines.size - 1) * lineHeight
        val maxWidth = lines.maxOf { textPaint.measureText(it) }

        val padH = dp(12f)
        val padV = dp(10f)

        // 面板下移到状态栏下方,避免与系统时间/图标视觉重叠
        val panelLeft = inset
        val panelTop = statusBarHeight().toFloat() + dp(8f)
        val panel = RectF(
            panelLeft,
            panelTop,
            panelLeft + maxWidth + padH * 2f,
            panelTop + blockHeight + padV * 2f
        )
        ops.add(DrawOp.RoundRect(panel, dp(8f), dp(8f), panelPaint))

        val textX = panelLeft + padH
        var baseline = panelTop + padV - fm.ascent
        for (line in lines) {
            ops.add(DrawOp.Text(line, textX, baseline, textPaint))
            baseline += lineHeight
        }
    }

    private fun screenInfo(): List<String> {
        val dm = resources.displayMetrics
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screen = screenSize(wm)

        return listOf(
            "分辨率 ${screen.first} x ${screen.second}",
            "密度 ${String.format(Locale.US, "%.2f", dm.density)} (${dm.densityDpi}dpi)",
            "刷新率 ${String.format(Locale.US, "%.1f", refreshRate(wm))} Hz",
            "状态栏 ${statusBarHeight()} px",
            "系统 Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
    }

    private fun screenSize(wm: WindowManager): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun refreshRate(wm: WindowManager): Float {
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display.mode?.refreshRate ?: display.refreshRate
        } else {
            @Suppress("DEPRECATION")
            display.refreshRate
        }
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private sealed class DrawOp {
        class Rect(val rect: RectF, val paint: Paint) : DrawOp()
        class RoundRect(val rect: RectF, val rx: Float, val ry: Float, val paint: Paint) : DrawOp()
        class Text(val text: String, val x: Float, val y: Float, val paint: Paint) : DrawOp()
    }
}