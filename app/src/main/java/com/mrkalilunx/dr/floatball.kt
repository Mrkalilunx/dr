package com.mrkalilunx.dr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// 悬浮球 黑色圆点加竖线 图案逆时针 90 度 metaball 融合 透明背景
// 动效 欠阻尼弹簧位移 反向预备 速度驱动挤压拉伸 极轻呼吸浮动
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val windowWidthDp = 26f
        const val windowHeightDp = 31f

        // 图案在设计空间的几何 归一化 中心为原点 y 向下为正
        // 采用非正方形窗口并按 fitScale 填满窗口 让竖线能贴近窗口右沿

        // 圆点半径
        private const val DOT_R = 0.20f

        // 落下时圆心 y 贴住竖线
        private const val DOT_Y_LOW = 0.10f

        // 升起时圆心 y
        private const val DOT_Y_HIGH = -0.24f

        // 升起时圆点半径增大比例
        private const val DOT_GROW = 0.05f

        // 竖线位置
        private const val LINE_Y = 0.30f

        // 竖线静止半长
        private const val LINE_HALF = 0.40f

        // 竖线升起时半长
        private const val LINE_HALF_MAX = 0.44f

        // 竖线线宽
        private const val LINE_W = 0.11f

        // 整体图案旋转角度 逆时针 90 度 竖线在右 圆点在左 升起方向向左
        private const val ROT_DEG = -90f

        // 图案在屏幕坐标下的包围盒 归一化 未乘 fitScale
        // 右极值取竖线右沿 左极值取升起态圆点左沿 上下极值取竖线两端加端帽
        private const val DESIGN_X_MIN = -0.45f
        private const val DESIGN_X_MAX = 0.355f
        private const val DESIGN_Y_MAX = 0.495f

        // 包围盒外留的模糊安全边距 防止 BlurMaskFilter 外扩被窗口裁切
        private const val EDGE_PAD = 0.06f

        // 图案水平中心偏移 用于把图案摆正到窗口中心
        private const val DESIGN_CX = (DESIGN_X_MIN + DESIGN_X_MAX) / 2f

        // metaball 模糊半径系数 即 smin 的 k
        private const val BLUR_SCALE = 0.055f

        // alpha 阈值 0..1
        private const val THRESHOLD = 0.42f

        // 阈值化陡峭度
        private const val CONTRAST = 12f

        private const val INK = Color.BLACK

        // 弹簧 欠阻尼 阻尼比约 0.63 过冲约 8%
        private const val STIFFNESS = 280f
        private const val DAMPING = 21f
        private const val REST_POS_EPS = 0.0015f
        private const val REST_VEL_EPS = 0.02f

        // 预备动作 升起前反向轻压
        private const val PREP_DELAY_MS = 60L
        private const val PREP_POS = -0.12f

        // 呼吸与微浮动
        private const val TWO_PI = 6.2831855f
        private const val BREATH_PERIOD = 3.6f
        private const val BREATH_AMP = 0.018f
        private const val BREATH_DOT = 0.030f

        // 挤压拉伸
        private const val STRETCH_GAIN = 0.010f
        private const val STRETCH_MAX = 0.13f
        private const val SQUASH_RATIO = 0.55f
    }

    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = INK
    }

    private val composePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val thresholdMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, CONTRAST, -CONTRAST * THRESHOLD * 255f
        )
    )

    private var offscreen: Bitmap? = null
    private var offCanvas: Canvas? = null

    // 弹簧状态 pos 即 rise 值 可短暂越过 1 或低于 0
    private var pos = 0f
    private var vel = 0f
    private var springTarget = 0f
    private var wantRisen = false

    // 呼吸相位 0..1
    private var breathPhase = 0f

    private var lastFrameNs = 0L
    private var loopRunning = false

    private var fitScale = 1f

    private val prep = Runnable { springTarget = 1f }

    init {
        composePaint.colorFilter = ColorMatrixColorFilter(thresholdMatrix)
    }

    // HUD 开传 true 圆点升起 HUD 关传 false 圆点落下
    fun setRisen(risen: Boolean) {
        if (wantRisen == risen) return
        wantRisen = risen
        removeCallbacks(prep)

        if (risen) {
            // 先反向轻压做预备 再弹起
            springTarget = PREP_POS
            postDelayed(prep, PREP_DELAY_MS)
        } else {
            springTarget = 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w <= 0 || h <= 0) {
            offscreen?.recycle()
            offscreen = null
            offCanvas = null
            return
        }

        val old = offscreen
        if (old != null && old.width == w && old.height == h) return
        old?.recycle()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        offscreen = bmp
        offCanvas = Canvas(bmp)

        val xSpan = DESIGN_X_MAX - DESIGN_X_MIN + 2f * EDGE_PAD
        val ySpan = 2f * DESIGN_Y_MAX + 2f * EDGE_PAD
        fitScale = min(w / xSpan, h / ySpan)

        blurPaint.maskFilter = BlurMaskFilter(
            (fitScale * BLUR_SCALE).coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val bmp = offscreen ?: return
        val oc = offCanvas ?: return

        oc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val u = pos
        val focus = u * u

        val s = fitScale
        val dotY = DOT_Y_LOW + (DOT_Y_HIGH - DOT_Y_LOW) * u
        val dotR = DOT_R * (1f + DOT_GROW * focus)
        val lineHalf = LINE_HALF + (LINE_HALF_MAX - LINE_HALF) * focus
        val lineY = LINE_Y
        val halfW = LINE_W * 0.5f

        // 速度转成沿运动方向拉伸 垂直方向挤压
        val stretchAmt = (abs(vel) * STRETCH_GAIN).coerceAtMost(STRETCH_MAX)
        val stretch = 1f + stretchAmt
        val squash = 1f - stretchAmt * SQUASH_RATIO

        // 呼吸
        val breath = sin(breathPhase * TWO_PI)
        val bobPx = breath * BREATH_AMP * s
        val dotBreath = 1f + breath * BREATH_DOT

        oc.save()
        oc.translate(w / 2f - DESIGN_CX * s, h / 2f + bobPx)
        oc.rotate(ROT_DEG)

        // 竖线
        oc.drawRoundRect(
            -lineHalf * s,
            (lineY - halfW) * s,
            lineHalf * s,
            (lineY + halfW) * s,
            halfW * s,
            halfW * s,
            blurPaint
        )

        // 圆点 旋转后局部 y 轴即屏幕水平方向 也就是运动方向
        oc.save()
        oc.translate(0f, dotY * s)
        oc.scale(squash, stretch)
        oc.drawCircle(0f, 0f, dotR * dotBreath * s, blurPaint)
        oc.restore()

        oc.restore()

        canvas.drawBitmap(bmp, 0f, 0f, composePaint)
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!loopRunning) return

            val now = System.nanoTime()
            if (lastFrameNs == 0L) lastFrameNs = now
            var dt = (now - lastFrameNs) / 1_000_000_000f
            lastFrameNs = now

            // 掉帧保护
            if (dt > 0.05f) dt = 0.05f
            if (dt > 0f) step(dt)

            invalidate()
            postOnAnimation(this)
        }
    }

    private fun step(dt: Float) {
        // 呼吸相位一直推进 静止时也保持极轻浮动
        breathPhase += dt / BREATH_PERIOD
        if (breathPhase >= 1f) breathPhase -= 1f

        // 弹簧积分 半隐式欧拉 dt 有上限保证稳定
        val a = -STIFFNESS * (pos - springTarget) - DAMPING * vel
        vel += a * dt
        pos += vel * dt

        if (abs(pos - springTarget) < REST_POS_EPS && abs(vel) < REST_VEL_EPS) {
            pos = springTarget
            vel = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loopRunning = true
        lastFrameNs = 0L
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        loopRunning = false
        removeCallbacks(frame)
        removeCallbacks(prep)
        super.onDetachedFromWindow()
    }
}
