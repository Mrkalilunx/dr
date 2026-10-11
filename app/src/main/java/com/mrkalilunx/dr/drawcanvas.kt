package com.mrkalilunx.dr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class drawcanvas(context: Context) : View(context) {

    sealed class DrawOp {
        class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val strokeWidth: Float) : DrawOp()
        class Text(val text: String, val x: Float, val y: Float, val color: Int, val size: Float) : DrawOp()
        class Rect(val rect: RectF, val fill: Int, val radius: Float) : DrawOp()
        class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Int) : DrawOp()
    }

    data class ButtonStyle(
        val fillColor: Int = Color.parseColor("#2196F3"),
        val pressColor: Int = Color.parseColor("#90CAF9"),
        val textColor: Int = Color.WHITE,
        val cornerRadius: Float = 24f,
        val textSize: Float = 40f,
        val text: String = ""
    )

    private class Button(
        val id: String,
        val rect: RectF,
        val style: ButtonStyle,
        val onClick: () -> Unit
    )

    private val drawOps = ArrayList<DrawOp>()
    private val buttons = ArrayList<Button>()
    private var pressedButton: Button? = null
    private var demoRequested = false
    private var demoDone = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var onCloseCanvas: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = Color.WHITE, strokeWidth: Float = 4f) {
        drawOps.add(DrawOp.Line(x1, y1, x2, y2, color, strokeWidth))
        invalidate()
    }

    fun drawText(text: String, x: Float, y: Float, color: Int = Color.WHITE, size: Float = 36f) {
        drawOps.add(DrawOp.Text(text, x, y, color, size))
        invalidate()
    }

    fun drawRect(rect: RectF, fill: Int, radius: Float = 0f) {
        drawOps.add(DrawOp.Rect(RectF(rect), fill, radius))
        invalidate()
    }

    fun drawCircle(cx: Float, cy: Float, r: Float, fill: Int) {
        drawOps.add(DrawOp.Circle(cx, cy, r, fill))
        invalidate()
    }

    fun addButton(id: String, rect: RectF, style: ButtonStyle, onClick: () -> Unit) {
        buttons.removeAll { it.id == id }
        buttons.add(Button(id, RectF(rect), style, onClick))
        invalidate()
    }

    fun removeButton(id: String) {
        buttons.removeAll { it.id == id }
        invalidate()
    }

    fun clear() {
        drawOps.clear()
        buttons.clear()
        pressedButton = null
        invalidate()
    }

    fun setupDemo() {
        demoRequested = true
        if (!demoDone && width > 0 && height > 0) {
            setupDemoInternal()
        }
    }

    private fun setupDemoInternal() {
        demoDone = true
        val w = width.toFloat()
        val h = height.toFloat()

        drawLine(0f, 0f, w, h, Color.parseColor("#80FFFFFF"), 4f)

        val startRect = RectF(dp(40f), dp(300f), dp(40f) + dp(220f), dp(300f) + dp(96f))
        addButton(
            "start",
            startRect,
            ButtonStyle(
                fillColor = Color.parseColor("#2196F3"),
                pressColor = Color.parseColor("#1565C0"),
                textColor = Color.WHITE,
                cornerRadius = dp(16f),
                textSize = dp(20f),
                text = "START"
            )
        ) {
            Toast.makeText(getContext(), "START", Toast.LENGTH_SHORT).show()
        }

        val closeRect = RectF(dp(40f), dp(440f), dp(40f) + dp(220f), dp(440f) + dp(96f))
        addButton(
            "close",
            closeRect,
            ButtonStyle(
                fillColor = Color.parseColor("#F44336"),
                pressColor = Color.parseColor("#B71C1C"),
                textColor = Color.WHITE,
                cornerRadius = dp(16f),
                textSize = dp(20f),
                text = "CLOSE"
            )
        ) {
            onCloseCanvas?.invoke()
        }

        drawText(
            "start: left=${startRect.left.toInt()} top=${startRect.top.toInt()}",
            startRect.centerX(), startRect.top - dp(16f),
            Color.WHITE, dp(15f)
        )
        drawText(
            "close: left=${closeRect.left.toInt()} top=${closeRect.top.toInt()}",
            closeRect.centerX(), closeRect.top - dp(16f),
            Color.WHITE, dp(15f)
        )
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (demoRequested && !demoDone && w > 0 && h > 0) {
            setupDemoInternal()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (op in drawOps) {
            when (op) {
                is DrawOp.Line -> {
                    strokePaint.color = op.color
                    strokePaint.strokeWidth = op.strokeWidth
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, strokePaint)
                }
                is DrawOp.Text -> {
                    textPaint.color = op.color
                    textPaint.textSize = op.size
                    canvas.drawText(op.text, op.x, op.y, textPaint)
                }
                is DrawOp.Rect -> {
                    fillPaint.color = op.fill
                    if (op.radius > 0f) {
                        canvas.drawRoundRect(op.rect, op.radius, op.radius, fillPaint)
                    } else {
                        canvas.drawRect(op.rect, fillPaint)
                    }
                }
                is DrawOp.Circle -> {
                    fillPaint.color = op.fill
                    canvas.drawCircle(op.cx, op.cy, op.r, fillPaint)
                }
            }
        }
        for (b in buttons) {
            fillPaint.color = if (b === pressedButton) b.style.pressColor else b.style.fillColor
            canvas.drawRoundRect(b.rect, b.style.cornerRadius, b.style.cornerRadius, fillPaint)

            textPaint.color = b.style.textColor
            textPaint.textSize = b.style.textSize
            val cx = b.rect.centerX()
            val cy = b.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(b.style.text, cx, cy, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedButton = hitButton(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pb = pressedButton
                pressedButton = null
                invalidate()
                if (pb != null && pb.rect.contains(event.x, event.y)) {
                    pb.onClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressedButton != null) {
                    pressedButton = null
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitButton(x: Float, y: Float): Button? {
        for (i in buttons.indices.reversed()) {
            if (buttons[i].rect.contains(x, y)) return buttons[i]
        }
        return null
    }
}