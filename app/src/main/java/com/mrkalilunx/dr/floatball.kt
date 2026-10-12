package com.mrkalilunx.dr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos

// 悬浮球 日出造型
// 黑色圆点在地平线上方缓缓落下 贴近地平线后回升
// 触底瞬间圆点微放大 地平线向两端延展 整体极轻呼吸收缩
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val windowSizeDp = 52f

        // 一次完整升降的时长 毫秒
        private const val CYCLE_MS = 4600L

        // 以下几何均以视图半宽为 1 归一化 中心为原点 y 向下为正

        // 底板外接半径
        private const val BODY_R = 0.77f

        // 底板圆角
        private const val BODY_CORNER = 0.34f

        // 圆点半径
        private const val DOT_R = 0.21f

        // 圆点高位中心
        private const val DOT_Y_HIGH = -0.22f

        // 圆点低位中心 底边刚好贴近地平线
        private const val DOT_Y_LOW = 0.06f

        // 地平线高度
        private const val LINE_Y = 0.30f

        // 地平线静止半长
        private const val LINE_HALF = 0.40f

        // 地平线延展半长
        private const val LINE_HALF_MAX = 0.44f

        // 地平线线宽
        private const val LINE_W = 0.09f

        // 圆点触底时的放大比例
        private const val DOT_GROW = 0.06f

        // 整体呼吸收缩比例
        private const val BREATH = 0.015f

        private const val COLOR_BODY = 0xFFF2F1ED.toInt()
        private const val COLOR_INK = 0xFF1A1A1A.toInt()

        private const val TWO_PI = 6.283185307179586
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_BODY
    }

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_INK
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = COLOR_INK
        strokeCap = Paint.Cap.ROUND
    }

    private val bodyPath = Path()
    private val bodyRect = RectF()

    // 缓入缓出后的升降量 0 为高位 1 为低位
    private var rise = 0f

    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val half = minOf(w, h) / 2f
        val r = BODY_R * half
        bodyRect.set(-r, -r, r, r)
        bodyPath.reset()
        bodyPath.addRoundRect(bodyRect, BODY_CORNER * half, BODY_CORNER * half, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val half = minOf(width, height) / 2f
        if (half <= 0f) return

        val u = rise
        // 平方后强调集中在低位 高位几乎无变化
        val focus = u * u

        val scale = 1f - BREATH * u
        val dotY = (DOT_Y_HIGH + (DOT_Y_LOW - DOT_Y_HIGH) * u) * half
        val dotR = DOT_R * (1f + DOT_GROW * focus) * half
        val lineHalf = (LINE_HALF + (LINE_HALF_MAX - LINE_HALF) * focus) * half
        val lineY = LINE_Y * half

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)

        canvas.drawPath(bodyPath, bodyPaint)

        linePaint.strokeWidth = LINE_W * half
        canvas.drawLine(-lineHalf, lineY, lineHalf, lineY, linePaint)

        canvas.drawCircle(0f, dotY, dotR, inkPaint)

        canvas.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnim()
    }

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }

    private fun startAnim() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val p = a.animatedFraction
                // 余弦缓动 两端速度趋零 形成自然的落下与回升
                rise = ((1.0 - cos(p * TWO_PI)) * 0.5).toFloat()
                invalidate()
            }
            start()
        }
    }

    private fun stopAnim() {
        animator?.cancel()
        animator = null
    }
}
