package com.mrkalilunx.dr

import android.animation.ValueAnimator
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

// 悬浮球 黑色圆点加地平线 背景完全透明
// HUD 开 圆点升起 HUD 关 圆点落下 升降由外部 setRisen 驱动 视图自身不做触摸
// metaball 融合 把地平线和圆点先画进离屏 ARGB Bitmap 绘制时加 BlurMaskFilter
// 让两者在 alpha 空间自然叠加 再用带 alpha 阈值化 ColorMatrixColorFilter 的 Paint
// 贴回画布 得到硬边 gooey 形状 靠近时长出脖颈 拉远自动断裂
class floatball @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val windowSizeDp = 52f

        // 升起时长 毫秒
        private const val RISE_MS = 340L

        // 落下时长 毫秒
        private const val FALL_MS = 340L

        // 以下几何均以 half = min(w,h)/2 为单位 中心为原点 y 向下为正

        // 圆点半径
        private const val DOT_R = 0.20f

        // 落下时圆心 y 贴住地平线
        private const val DOT_Y_LOW = 0.10f

        // 升起时圆心 y
        private const val DOT_Y_HIGH = -0.24f

        // 升起时圆点半径增大比例
        private const val DOT_GROW = 0.05f

        // 地平线高度
        private const val LINE_Y = 0.30f

        // 地平线静止半长
        private const val LINE_HALF = 0.40f

        // 地平线升起时半长
        private const val LINE_HALF_MAX = 0.44f

        // 地平线线宽
        private const val LINE_W = 0.11f

        // 模糊半径系数 乘 half 后即 smin 里的 k 融合平滑半径 越大越黏
        private const val BLUR_SCALE = 0.055f

        // alpha 阈值 0..1 越大形状越瘦 脖颈越早断
        private const val THRESHOLD = 0.42f

        // 阈值化陡峭度 越大边缘越硬
        private const val CONTRAST = 12f

        private const val INK = Color.BLACK
    }

    // 离屏绘制用 带 BlurMaskFilter
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = INK
    }

    // 贴回画布用 带 alpha 阈值 ColorMatrix
    private val composePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    // RGB 保持单位矩阵 只对 alpha 行做线性阈值化
    // a' = CONTRAST * a - CONTRAST * THRESHOLD * 255
    // a 在 THRESHOLD 处输出 0 稍大即饱和到 255 得到硬边 gooey
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

    // 0 完全落下 1 完全升起
    private var rise = 0f
    private var targetRise = 0f
    private var animator: ValueAnimator? = null

    init {
        composePaint.colorFilter = ColorMatrixColorFilter(thresholdMatrix)
    }

    // HUD 开传 true 圆点升起 HUD 关传 false 圆点落下
    // 重复同值无副作用 反向调用会从当前进度平滑折返
    fun setRisen(risen: Boolean) {
        val target = if (risen) 1f else 0f
        if (targetRise == target) return
        targetRise = target
        val goingUp = target > rise
        animator?.cancel()
        animator = ValueAnimator.ofFloat(rise, target).apply {
            duration = if (goingUp) RISE_MS else FALL_MS
            interpolator = if (goingUp) {
                DecelerateInterpolator(1.5f)
            } else {
                AccelerateInterpolator(1.5f)
            }
            addUpdateListener {
                rise = it.animatedValue as Float
                invalidate()
            }
            start()
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

        val half = min(w, h) / 2f
        val blurR = (half * BLUR_SCALE).coerceAtLeast(1f)
        blurPaint.maskFilter = BlurMaskFilter(blurR, BlurMaskFilter.Blur.NORMAL)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        val half = min(w, h) / 2f
        if (half <= 0f) return

        val bmp = offscreen ?: return
        val oc = offCanvas ?: return

        // 清空离屏 背景完全透明
        oc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val u = rise
        val focus = u * u
        val dotY = (DOT_Y_LOW + (DOT_Y_HIGH - DOT_Y_LOW) * u) * half
        val dotR = DOT_R * (1f + DOT_GROW * focus) * half
        val lineHalf = (LINE_HALF + (LINE_HALF_MAX - LINE_HALF) * focus) * half
        val lineY = LINE_Y * half
        val lineW = LINE_W * half
        val halfW = lineW * 0.5f

        // 离屏上画地平线胶囊加圆点 靠 BlurMaskFilter 在 alpha 空间融合
        oc.save()
        oc.translate(w / 2f, h / 2f)
        oc.drawRoundRect(
            -lineHalf, lineY - halfW,
            lineHalf, lineY + halfW,
            halfW, halfW, blurPaint
        )
        oc.drawCircle(0f, dotY, dotR, blurPaint)
        oc.restore()

        // 阈值化贴回 得到硬边 metaball 形状
        canvas.drawBitmap(bmp, 0f, 0f, composePaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
