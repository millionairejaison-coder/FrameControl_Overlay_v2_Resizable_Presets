package com.framecontrol.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

enum class GridType(val title: String, val category: String) {
    RULE_OF_THIRDS("Rule of Thirds", "Grid & Placement"),
    PHI_GRID("Golden Ratio (Phi 1:0.618:1)", "Grid & Placement"),
    FIBONACCI_SPIRAL("Golden Fibonacci Spiral", "Grid & Placement"),
    GOLDEN_TRIANGLE("Golden Triangle", "Grid & Placement"),
    CENTER_SYMMETRY("Center Crosshair & Symmetry", "Grid & Placement"),
    CONVERGING_LINES("Converging Vanishing Lines", "Lines & Flow"),
    DIAGONAL_CROSS("Dynamic Diagonals", "Lines & Flow"),
    S_CURVE("S-Curve Flow Guide", "Lines & Flow"),
    FRAME_WITHIN_FRAME("Frame Within a Frame + Safe Zone", "Framing & Isolation"),
    DUTCH_ANGLE("Dutch Angle (15° Roll Guide)", "Viewpoint")
}

class CompositionOverlayView(context: Context) : View(context) {

    var gridType: GridType = GridType.RULE_OF_THIRDS
        set(value) { field = value; invalidate() }

    var lineColor: Int = Color.parseColor("#00E5FF")
        set(value) { field = value; updatePaint(); invalidate() }

    var lineAlpha: Int = 200
        set(value) { field = value.coerceIn(30, 255); updatePaint(); invalidate() }

    var strokeWidthDp: Float = 1.8f
        set(value) { field = value.coerceIn(0.8f, 6f); updatePaint(); invalidate() }

    var flipSpiral: Boolean = false
        set(value) { field = value; invalidate() }

    var isResizeEditMode: Boolean = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val editBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3300E5FF")
    }

    private val editHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD600")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        updatePaint()
    }

    private fun updatePaint() {
        val density = resources.displayMetrics.density
        paint.color = lineColor
        paint.alpha = lineAlpha
        paint.strokeWidth = strokeWidthDp * density

        dotPaint.color = lineColor
        dotPaint.alpha = (lineAlpha + 35).coerceAtMost(255)

        borderPaint.color = lineColor
        borderPaint.alpha = (lineAlpha * 0.7f).toInt().coerceAtLeast(80)
        borderPaint.strokeWidth = (1.2f * density)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Outer subtle frame boundary so the user sees the exact camera viewfinder bounds
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, borderPaint)

        when (gridType) {
            GridType.RULE_OF_THIRDS -> drawGrid(canvas, w, h, 1f / 3f, 2f / 3f)
            GridType.PHI_GRID -> drawGrid(canvas, w, h, 0.382f, 0.618f)
            GridType.FIBONACCI_SPIRAL -> drawFibonacciSpiral(canvas, w, h)
            GridType.GOLDEN_TRIANGLE -> drawGoldenTriangle(canvas, w, h)
            GridType.CENTER_SYMMETRY -> drawCenterSymmetry(canvas, w, h)
            GridType.CONVERGING_LINES -> drawConvergingLines(canvas, w, h)
            GridType.DIAGONAL_CROSS -> drawDiagonals(canvas, w, h)
            GridType.S_CURVE -> drawSCurve(canvas, w, h)
            GridType.FRAME_WITHIN_FRAME -> drawFrameWithinFrame(canvas, w, h)
            GridType.DUTCH_ANGLE -> drawDutchAngle(canvas, w, h)
        }

        if (isResizeEditMode) {
            drawResizeHandles(canvas, w, h)
        }
    }

    private fun drawResizeHandles(canvas: Canvas, w: Float, h: Float) {
        val density = resources.displayMetrics.density
        canvas.drawRect(0f, 0f, w, h, editBgPaint)

        val handleRadius = 14f * density
        // 4 corners
        canvas.drawCircle(handleRadius, handleRadius, handleRadius, editHandlePaint)
        canvas.drawCircle(w - handleRadius, handleRadius, handleRadius, editHandlePaint)
        canvas.drawCircle(handleRadius, h - handleRadius, handleRadius, editHandlePaint)
        canvas.drawCircle(w - handleRadius, h - handleRadius, handleRadius, editHandlePaint)

        // Center move badge
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawCircle(cx, cy, 22f * density, editHandlePaint)

        val info = "DRAG CENTER TO MOVE • DRAG CORNERS TO RESIZE (${width}x${height}px)"
        textPaint.textSize = 11f * density
        val tw = textPaint.measureText(info)
        canvas.drawText(info, (w - tw) / 2f, (cy - 30f * density).coerceAtLeast(24f * density), textPaint)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float, r1: Float, r2: Float) {
        val x1 = w * r1
        val x2 = w * r2
        val y1 = h * r1
        val y2 = h * r2

        canvas.drawLine(x1, 0f, x1, h, paint)
        canvas.drawLine(x2, 0f, x2, h, paint)
        canvas.drawLine(0f, y1, w, y1, paint)
        canvas.drawLine(0f, y2, w, y2, paint)

        val radius = paint.strokeWidth * 2.4f
        canvas.drawCircle(x1, y1, radius, dotPaint)
        canvas.drawCircle(x2, y1, radius, dotPaint)
        canvas.drawCircle(x1, y2, radius, dotPaint)
        canvas.drawCircle(x2, y2, radius, dotPaint)
    }

    private fun drawFibonacciSpiral(canvas: Canvas, w: Float, h: Float) {
        canvas.save()
        if (flipSpiral) {
            canvas.scale(-1f, 1f, w / 2f, h / 2f)
        }

        val path = Path()
        var left = 0f
        var top = 0f
        var right = w
        var bottom = h
        val phi = 0.618034f

        for (step in 0..6) {
            val rw = right - left
            val rh = bottom - top
            when (step % 4) {
                0 -> {
                    val splitX = left + rw * phi
                    canvas.drawLine(splitX, top, splitX, bottom, paint)
                    path.addArc(RectF(left, top, left + 2 * (splitX - left), bottom), 180f, 90f)
                    left = splitX
                }
                1 -> {
                    val splitY = top + rh * phi
                    canvas.drawLine(left, splitY, right, splitY, paint)
                    path.addArc(RectF(right - 2 * (right - left), top, right, top + 2 * (splitY - top)), 270f, 90f)
                    top = splitY
                }
                2 -> {
                    val splitX = right - rw * phi
                    canvas.drawLine(splitX, top, splitX, bottom, paint)
                    path.addArc(RectF(splitX - (right - splitX), bottom - 2 * (bottom - top), right, bottom), 0f, 90f)
                    right = splitX
                }
                3 -> {
                    val splitY = bottom - rh * phi
                    canvas.drawLine(left, splitY, right, splitY, paint)
                    path.addArc(RectF(left, splitY - (bottom - splitY), left + 2 * (right - left), bottom), 90f, 90f)
                    bottom = splitY
                }
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawGoldenTriangle(canvas: Canvas, w: Float, h: Float) {
        canvas.drawLine(0f, 0f, w, h, paint)
        val d2 = w * w + h * h
        val x1 = (w * h * h) / d2
        val y1 = (w * w * h) / d2
        canvas.drawLine(0f, h, x1, y1, paint)

        val x2 = w - x1
        val y2 = h - y1
        canvas.drawLine(w, 0f, x2, y2, paint)

        val r = paint.strokeWidth * 2.5f
        canvas.drawCircle(x1, y1, r, dotPaint)
        canvas.drawCircle(x2, y2, r, dotPaint)
    }

    private fun drawCenterSymmetry(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawLine(cx, 0f, cx, h, paint)
        canvas.drawLine(0f, cy, w, cy, paint)
        val radius = min(w, h) * 0.14f
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.drawCircle(cx, cy, paint.strokeWidth * 2.5f, dotPaint)
    }

    private fun drawConvergingLines(canvas: Canvas, w: Float, h: Float) {
        val vx = w / 2f
        val vy = h * 0.42f
        canvas.drawLine(0f, vy, w, vy, paint)
        canvas.drawLine(0f, h, vx, vy, paint)
        canvas.drawLine(w, h, vx, vy, paint)
        canvas.drawLine(0f, 0f, vx, vy, paint)
        canvas.drawLine(w, 0f, vx, vy, paint)
        canvas.drawLine(w * 0.25f, h, vx, vy, paint)
        canvas.drawLine(w * 0.75f, h, vx, vy, paint)
        canvas.drawCircle(vx, vy, paint.strokeWidth * 3f, dotPaint)
    }

    private fun drawDiagonals(canvas: Canvas, w: Float, h: Float) {
        canvas.drawLine(0f, 0f, w, h, paint)
        canvas.drawLine(w, 0f, 0f, h, paint)
        canvas.drawCircle(w / 2f, h / 2f, paint.strokeWidth * 2.5f, dotPaint)
    }

    private fun drawSCurve(canvas: Canvas, w: Float, h: Float) {
        val path = Path()
        val startX = if (flipSpiral) w * 0.75f else w * 0.25f
        val endX = if (flipSpiral) w * 0.25f else w * 0.75f
        val ctrl1X = if (flipSpiral) w * 0.05f else w * 0.95f
        val ctrl2X = if (flipSpiral) w * 0.95f else w * 0.05f

        path.moveTo(startX, h * 0.08f)
        path.cubicTo(ctrl1X, h * 0.35f, ctrl2X, h * 0.65f, endX, h * 0.92f)
        canvas.drawPath(path, paint)

        canvas.drawCircle(startX, h * 0.08f, paint.strokeWidth * 2.5f, dotPaint)
        canvas.drawCircle(endX, h * 0.92f, paint.strokeWidth * 2.5f, dotPaint)
    }

    private fun drawFrameWithinFrame(canvas: Canvas, w: Float, h: Float) {
        val m1x = w * 0.12f
        val m1y = h * 0.12f
        canvas.drawRect(m1x, m1y, w - m1x, h - m1y, paint)

        val m2x = w * 0.22f
        val m2y = h * 0.22f
        canvas.drawRect(m2x, m2y, w - m2x, h - m2y, paint)

        canvas.drawLine(0f, 0f, m1x, m1y, paint)
        canvas.drawLine(w, 0f, w - m1x, m1y, paint)
        canvas.drawLine(0f, h, m1x, h - m1y, paint)
        canvas.drawLine(w, h, w - m1x, h - m1y, paint)
    }

    private fun drawDutchAngle(canvas: Canvas, w: Float, h: Float) {
        canvas.save()
        val angle = if (flipSpiral) -15f else 15f
        canvas.rotate(angle, w / 2f, h / 2f)
        drawGrid(canvas, w, h, 1f / 3f, 2f / 3f)
        canvas.restore()
    }
}
