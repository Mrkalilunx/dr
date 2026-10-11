package com.mrkalilunx.dr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

// 动态自绘悬浮小球
// 组成:径向渐变球体 + 呼吸缩放 + 旋转流光 + 声呐扩散环 + 边缘光 + 3 颗轨道粒子
// 所有 Shader / Paint 构造一次复用,onDraw 内零对象分配
// 窗口建议 56dp,视觉球体 46dp,外围余量用来画光晕和环
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // 窗口边长,外部 addView 请按这个尺寸放
        const val windowSizeDp = 56f
        // 视觉球体半径(直径约 46dp,视觉上和 48dp 差别极小)
        private const val ballRadiusDp = 23f

        private const val twoPi = 6.2831855f
        private const val twoPiOver3 = 2.0943952f
    }

    private val density = resources.displayMetrics.density
    private val ballR = ballRadiusDp * density
    private val viewSizePx = windowSizeDp * density

    // Paint / Shader 全部构造一次,之后只改 alpha / color
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -ballR * 0.30f, -ballR * 0.35f, ballR * 1.35f,
            intArrayOf(
                0xFFCDBEFF.toInt(), // 中心亮薰衣草
                0xFF8A6BFF.toInt(), // 主体亮紫
                0xFF4A2BC7.toInt(), // 主体中紫
                0xFF1E0E5E.toInt(), // 边缘暗紫
                0xFF0B0528.toInt()  // 外圈近黑紫
            ),
            floatArrayOf(0f, 0.25f, 0.55f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, ballR * 1.5f,
            intArrayOf(
                0x00000000,
                0x00000000,
                0x66A070FF,
                0x00A070FF
            ),
            floatArrayOf(0f, 0.55f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -ballR * 0.32f, -ballR * 0.42f, ballR * 0.90f,
            intArrayOf(
                0xE6FFFFFF.toInt(),
                0x55FFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.40f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = SweepGradient(
            0f, 0f,
            intArrayOf(
                0x00FFFFFF,
                0x00FFFFFF,
                0x00FFFFFF,
                0x99B8ECFF.toInt(),
                0x00FFFFFF,
                0x00FFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.40f, 0.47f, 0.50f, 0.53f, 0.60f, 1f)
        )
        alpha = 190
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0x80A8E4FF.toInt()
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        color = 0xFF7DE3FF.toInt()
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val particleColors = intArrayOf(
        0xFFFFD166.toInt(),
        0xFF7DE3FF.toInt(),
        0xFFFF7AD9.toInt()
    )

    // 动画进度,全部 0..1
    private var pulseT = 0f
    private var rotateT = 0f
    private var ringT = 0f

    private var pulseAnim: ValueAnimator? = null
    private var rotateAnim: ValueAnimator? = null
    private var ringAnim: ValueAnimator? = null

    // View 生命周期
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
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseT = it.animatedFraction
                invalidate()
            }
            start()
        }

        rotateAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotateT = it.animatedFraction
                invalidate()
            }
            start()
        }

        ringAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                ringT = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimations() {
        pulseAnim?.cancel()
        rotateAnim?.cancel()
        ringAnim?.cancel()
        pulseAnim = null
        rotateAnim = null
        ringAnim = null
    }

    // 绘制:全程零 new
    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.5f
        val cy = height * 0.5f

        // 呼吸相位
        val phase = pulseT * twoPi
        val sinPulse = sin(phase.toDouble()).toFloat()
        val pulse = 1f + 0.055f * sinPulse

        canvas.save()
        canvas.translate(cx, cy)

        // 外光晕,随呼吸明暗
        glowPaint.alpha = (135f + 75f * sinPulse).toInt().coerceIn(60, 210)
        canvas.drawCircle(0f, 0f, ballR * 1.5f, glowPaint)

        // 声呐环,两圈相位错开半个周期
        drawRing(canvas, ringT)
        drawRing(canvas, (ringT + 0.5f) % 1f)

        // 球体:脉冲缩放包住球 + 流光 + 高光 + 边缘光
        canvas.save()
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, ballR, ballPaint)

        canvas.save()
        canvas.rotate(rotateT * 360f)
        canvas.drawCircle(0f, 0f, ballR, sweepPaint)
        canvas.restore()

        canvas.drawCircle(0f, 0f, ballR, highlightPaint)
        canvas.drawCircle(0f, 0f, ballR - rimPaint.strokeWidth * 0.5f, rimPaint)
        canvas.restore()

        // 三颗轨道粒子(不跟随脉冲缩放,避免视觉累加)
        drawParticle(canvas, 0f, particleColors[0])
        drawParticle(canvas, twoPiOver3, particleColors[1])
        drawParticle(canvas, twoPiOver3 * 2f, particleColors[2])

        canvas.restore()
    }

    private fun drawRing(canvas: Canvas, t: Float) {
        val startR = ballR * 0.88f
        val endR = width * 0.5f - ringPaint.strokeWidth
        val r = startR + t * (endR - startR)
        val fade = 1f - t
        val alpha = (fade * fade * 220f).toInt()
        if (alpha <= 4) {
            return
        }
        ringPaint.alpha = alpha
        canvas.drawCircle(0f, 0f, r, ringPaint)
    }

    private fun drawParticle(canvas: Canvas, angleOffset: Float, color: Int) {
        val angle = rotateT * twoPi + angleOffset
        val orbitR = ballR * 1.12f
        val px = cos(angle.toDouble()).toFloat() * orbitR
        val py = sin(angle.toDouble()).toFloat() * orbitR
        val twinkle = 0.6f + 0.4f * sin((angle * 2f + rotateT * 6f).toDouble()).toFloat()
        particlePaint.color = color
        particlePaint.alpha = (twinkle * 230f).toInt().coerceIn(50, 230)
        canvas.drawCircle(px, py, 2.0f * density, particlePaint)
    }
}