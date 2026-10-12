package com.mrkalilunx.dr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

// 简约悬浮球:深色玻璃珠 + 左上柔光 + 边缘细描边 + 外光晕 + 一道旋转细弧
// 只有两个动效,呼吸极轻,弧线 6 秒一圈,onDraw 内零对象分配
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // 窗口边长,外部 addView 按此尺寸放置
        const val windowSizeDp = 52f
        // 球体半径,外围留光晕余量
        private const val ballRadiusDp = 20f
        private const val twoPi = 6.2831855f
    }

    private val density = resources.displayMetrics.density
    private val ballR = ballRadiusDp * density
    private val viewSizePx = windowSizeDp * density

    // 球体:左上偏移的柔和径向渐变,石板蓝灰到近黑,给球体一层体积感
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -ballR * 0.30f, -ballR * 0.35f, ballR * 1.35f,
            intArrayOf(
                0xFF525A72.toInt(),
                0xFF323848.toInt(),
                0xFF1A1D29.toInt(),
                0xFF0E1018.toInt()
            ),
            floatArrayOf(0f, 0.45f, 0.80f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // 外光晕:低亮度白光,让球体从任意背景上浮起
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, ballR * 1.28f,
            intArrayOf(
                0x00000000,
                0x1AFFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // 镜面高光:左上角一小片柔光,是球体唯一的亮部
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -ballR * 0.38f, -ballR * 0.42f, ballR * 0.85f,
            intArrayOf(
                0x55FFFFFF,
                0x18FFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // 边缘光:一道极细描边,给球体和背景之间划线
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = 0x33FFFFFF
    }

    // 旋转弧线:球体内缘一道细弧,唯一的活动元素
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xCCFFFFFF.toInt()
    }

    // 弧线所在圆的外接矩形,构造期定好
    private val arcRect = RectF().apply {
        val r = ballR * 0.90f
        set(-r, -r, r, r)
    }

    // 动画进度,0..1
    private var pulseT = 0f
    private var spinT = 0f

    private var pulseAnim: ValueAnimator? = null
    private var spinAnim: ValueAnimator? = null

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
        if (pulseAnim != null) {
            return
        }

        pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseT = it.animatedFraction
                invalidate()
            }
            start()
        }

        spinAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                spinT = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimations() {
        pulseAnim?.cancel()
        spinAnim?.cancel()
        pulseAnim = null
        spinAnim = null
    }

    // 绘制:全程零 new
    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.5f
        val cy = height * 0.5f

        // 呼吸相位,幅度仅 ±3%,安静到近乎静止
        val sinPulse = sin((pulseT * twoPi).toDouble()).toFloat()
        val pulse = 1f + 0.03f * sinPulse

        canvas.save()
        canvas.translate(cx, cy)

        // 外光晕,随呼吸极轻微明暗
        glowPaint.alpha = (150f + 40f * sinPulse).toInt().coerceIn(100, 200)
        canvas.drawCircle(0f, 0f, ballR * 1.28f, glowPaint)

        // 球体:呼吸缩放包住球体 + 高光 + 描边
        canvas.save()
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, ballR, ballPaint)
        canvas.drawCircle(0f, 0f, ballR, highlightPaint)
        canvas.drawCircle(0f, 0f, ballR - rimPaint.strokeWidth * 0.5f, rimPaint)
        canvas.restore()

        // 旋转弧线:球体内缘缓缓转动,是画面里唯一明显在动的元素
        canvas.save()
        canvas.rotate(spinT * 360f)
        canvas.drawArc(arcRect, -90f, 70f, false, arcPaint)
        canvas.restore()

        canvas.restore()
    }
}
