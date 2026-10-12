package com.mrkalilunx.dr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

// 纯色扁平悬浮球:实心圆盘 + 中央白色取景框符号
// 无渐变无阴影无描边,只有整体极轻的呼吸缩放
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // 窗口边长,外部 addView 按此尺寸放置
        const val windowSizeDp = 52f
        // 圆盘半径
        private const val discRadiusDp = 20f
        // 取景框外接半边长,即符号中心到角的距离
        private const val frameHalfDp = 8f
        // 每个角的线段长度
        private const val cornerLenDp = 4f
        // 取景框线宽
        private const val frameStrokeDp = 1.8f
        private const val twoPi = 6.2831855f
    }

    private val density = resources.displayMetrics.density
    private val discR = discRadiusDp * density
    private val viewSizePx = windowSizeDp * density
    private val frameHalf = frameHalfDp * density
    private val cornerLen = cornerLenDp * density

    // 圆盘:纯色实心,无渐变无描边
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1C1C1E.toInt()
    }

    // 取景框:纯白描边,圆头线帽,扁平的简洁感
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = frameStrokeDp * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    // 取景框路径,四个角各两段线,构造期建好
    private val framePath = Path().apply {
        // 左上角
        moveTo(-frameHalf, -frameHalf + cornerLen)
        lineTo(-frameHalf, -frameHalf)
        lineTo(-frameHalf + cornerLen, -frameHalf)
        // 右上角
        moveTo(frameHalf - cornerLen, -frameHalf)
        lineTo(frameHalf, -frameHalf)
        lineTo(frameHalf, -frameHalf + cornerLen)
        // 右下角
        moveTo(frameHalf, frameHalf - cornerLen)
        lineTo(frameHalf, frameHalf)
        lineTo(frameHalf - cornerLen, frameHalf)
        // 左下角
        moveTo(-frameHalf + cornerLen, frameHalf)
        lineTo(-frameHalf, frameHalf)
        lineTo(-frameHalf, frameHalf - cornerLen)
    }

    // 动画进度,0..1
    private var breathT = 0f
    private var breathAnim: ValueAnimator? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = viewSizePx.toInt()
        setMeasuredDimension(s, s)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }

    private fun startAnimations() {
        if (breathAnim != null) {
            return
        }

        breathAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                breathT = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimations() {
        breathAnim?.cancel()
        breathAnim = null
    }

    // 绘制:全程零 new
    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.5f
        val cy = height * 0.5f

        // 呼吸相位,幅度仅 ±3%,安静到近乎静止
        val sinP = sin((breathT * twoPi).toDouble()).toFloat()
        val scale = 1f + 0.03f * sinP

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        // 实心圆盘
        canvas.drawCircle(0f, 0f, discR, discPaint)

        // 白色取景框
        canvas.drawPath(framePath, framePaint)

        canvas.restore()
    }
}
