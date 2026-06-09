package com.diarynote.ui.canvas

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.diarynote.data.model.*
import kotlin.math.abs
import kotlin.math.sqrt

class DrawingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // --- State ---
    private val committedStrokes = mutableListOf<StrokeData>()
    private val undoStack = mutableListOf<List<StrokeData>>()
    private val redoStack = mutableListOf<List<StrokeData>>()
    private var currentPoints = mutableListOf<StrokePoint>()

    // Lasso
    private val lassoPath = Path()
    private var lassoPoints = mutableListOf<PointF>()
    var selectedStrokeIndices = mutableListOf<Int>()
        private set
    private var selectionBounds = RectF()

    // --- Tool settings ---
    var currentTool: BrushType = BrushType.PEN
    var currentColor: Int = Color.BLACK
    var currentStrokeWidth: Float = 4f
    var isLassoMode: Boolean = false
    var pageType: PageType = PageType.RULED

    // Custom font
    var customTypeface: Typeface? = null

    // Callbacks
    var onStrokeCompleted: ((List<StrokeData>) -> Unit)? = null

    // --- Paint objects ---
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D4E0")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFAAAA")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    private val selectionFillPaint = Paint().apply {
        color = Color.parseColor("#302196F3")
        style = Paint.Style.FILL
    }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    // --- Palm rejection ---
    // Track active pointer: only stylus or first-touch is drawing pointer
    private var activePointerId = -1
    private var isStylusActive = false
    private val PALM_TOUCH_SIZE_THRESHOLD = 0.3f // normalized touch major > this = palm
    private val MIN_STYLUS_PRESSURE = 0.01f

    // --- Zoom/pan ---
    var scaleFactor = 1f
    var translateX = 0f
    var translateY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap!!)
        redrawAll()
    }

    private fun redrawAll() {
        val canvas = bitmapCanvas ?: return
        canvas.drawColor(Color.WHITE)
        drawPageLines(canvas)
        for (stroke in committedStrokes) drawStroke(canvas, stroke)
    }

    private fun drawPageLines(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val lineSpacing = 60f
        val marginX = 80f

        when (pageType) {
            PageType.RULED -> {
                canvas.drawLine(marginX, 0f, marginX, h, marginPaint)
                var y = lineSpacing
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, rulePaint)
                    y += lineSpacing
                }
            }
            PageType.GRID -> {
                var x = 0f
                while (x < w) { canvas.drawLine(x, 0f, x, h, rulePaint); x += lineSpacing }
                var y = 0f
                while (y < h) { canvas.drawLine(0f, y, w, y, rulePaint); y += lineSpacing }
            }
            PageType.BLANK -> { /* nothing */ }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: StrokeData) {
        if (stroke.points.size < 2) return
        val paint = if (stroke.brushType == BrushType.ERASER) eraserPaint else strokePaint
        paint.color = stroke.color
        paint.alpha = stroke.alpha

        val path = Path()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)

        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            // Pressure-based width
            val pressureWidth = stroke.strokeWidth * (0.5f + curr.pressure * 1.5f)
            paint.strokeWidth = pressureWidth.coerceIn(1f, stroke.strokeWidth * 3f)

            // Smooth curve
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        canvas.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // Draw current stroke live
        if (currentPoints.size >= 2 && !isLassoMode) {
            val livePath = Path()
            livePath.moveTo(currentPoints[0].x, currentPoints[0].y)
            for (i in 1 until currentPoints.size) {
                val prev = currentPoints[i - 1]
                val curr = currentPoints[i]
                val pressureWidth = currentStrokeWidth * (0.5f + curr.pressure * 1.5f)
                strokePaint.strokeWidth = pressureWidth.coerceIn(1f, currentStrokeWidth * 3f)
                strokePaint.color = if (currentTool == BrushType.ERASER) Color.WHITE else currentColor
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                livePath.quadTo(prev.x, prev.y, midX, midY)
            }
            canvas.drawPath(livePath, strokePaint)
        }

        // Draw lasso
        if (isLassoMode && lassoPoints.isNotEmpty()) {
            canvas.drawPath(lassoPath, lassoPaint)
            if (selectedStrokeIndices.isNotEmpty()) {
                canvas.drawRect(selectionBounds, selectionFillPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Palm rejection logic
        val toolType = event.getToolType(event.actionIndex)
        val isStylusEvent = toolType == MotionEvent.TOOL_TYPE_STYLUS

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == -1) {
                    // Accept stylus always; accept finger only if no stylus active
                    if (isStylusEvent) {
                        activePointerId = event.getPointerId(event.actionIndex)
                        isStylusActive = true
                    } else if (!isStylusActive) {
                        // Check if it's a palm (large touch area)
                        val touchMajor = event.touchMajor
                        val normalizedSize = touchMajor / maxOf(width, height).toFloat()
                        if (normalizedSize < PALM_TOUCH_SIZE_THRESHOLD) {
                            activePointerId = event.getPointerId(event.actionIndex)
                        }
                        // else: palm, reject
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId) {
                    finishStroke()
                    activePointerId = -1
                    if (isStylusEvent) isStylusActive = false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                isStylusActive = false
                currentPoints.clear()
            }
        }

        // Only process move for active pointer
        if (activePointerId == -1) return true

        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                addPoint(event, pointerIndex)
                if (isLassoMode) {
                    lassoPath.reset()
                    lassoPoints.clear()
                    selectedStrokeIndices.clear()
                    lassoPath.moveTo(event.getX(pointerIndex), event.getY(pointerIndex))
                    lassoPoints.add(PointF(event.getX(pointerIndex), event.getY(pointerIndex)))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Use historical points for smoother input
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    val hx = event.getHistoricalX(pointerIndex, h)
                    val hy = event.getHistoricalY(pointerIndex, h)
                    val hp = event.getHistoricalPressure(pointerIndex, h)
                    val ht = event.getHistoricalEventTime(h)
                    if (isLassoMode) {
                        lassoPath.lineTo(hx, hy)
                        lassoPoints.add(PointF(hx, hy))
                    } else {
                        currentPoints.add(StrokePoint(hx, hy, hp, ht))
                    }
                }
                addPoint(event, pointerIndex)
                if (isLassoMode) {
                    lassoPath.lineTo(event.getX(pointerIndex), event.getY(pointerIndex))
                    lassoPoints.add(PointF(event.getX(pointerIndex), event.getY(pointerIndex)))
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                addPoint(event, pointerIndex)
                if (isLassoMode) {
                    lassoPath.close()
                    computeLassoSelection()
                } else {
                    finishStroke()
                }
                activePointerId = -1
                if (isStylusEvent) isStylusActive = false
                invalidate()
            }
        }
        return true
    }

    private fun addPoint(event: MotionEvent, pointerIndex: Int) {
        val pressure = event.getPressure(pointerIndex).coerceIn(MIN_STYLUS_PRESSURE, 1f)
        currentPoints.add(
            StrokePoint(
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
                pressure = pressure,
                timestamp = event.eventTime
            )
        )
    }

    private fun finishStroke() {
        if (currentPoints.size < 2) { currentPoints.clear(); return }
        val stroke = StrokeData(
            points = currentPoints.toList(),
            color = if (currentTool == BrushType.ERASER) Color.WHITE else currentColor,
            strokeWidth = currentStrokeWidth,
            brushType = currentTool
        )

        if (currentTool == BrushType.ERASER) {
            eraseByStroke(stroke)
        } else {
            saveUndoState()
            committedStrokes.add(stroke)
            drawStroke(bitmapCanvas!!, stroke)
        }

        currentPoints.clear()
        onStrokeCompleted?.invoke(committedStrokes.toList())
        redoStack.clear()
        invalidate()
    }

    private fun eraseByStroke(eraseStroke: StrokeData) {
        // Remove strokes that intersect erase path
        val toRemove = mutableListOf<StrokeData>()
        for (stroke in committedStrokes) {
            if (strokesIntersect(stroke, eraseStroke)) toRemove.add(stroke)
        }
        if (toRemove.isNotEmpty()) {
            saveUndoState()
            committedStrokes.removeAll(toRemove)
            redrawAll()
        }
    }

    private fun strokesIntersect(a: StrokeData, b: StrokeData): Boolean {
        val threshold = maxOf(a.strokeWidth, b.strokeWidth) * 2f
        for (pa in a.points) {
            for (pb in b.points) {
                val dx = pa.x - pb.x
                val dy = pa.y - pb.y
                if (sqrt(dx * dx + dy * dy) < threshold) return true
            }
        }
        return false
    }

    private fun computeLassoSelection() {
        selectedStrokeIndices.clear()
        if (lassoPoints.size < 3) return

        for ((index, stroke) in committedStrokes.withIndex()) {
            if (stroke.points.any { pt -> isPointInPolygon(PointF(pt.x, pt.y), lassoPoints) }) {
                selectedStrokeIndices.add(index)
            }
        }

        if (selectedStrokeIndices.isNotEmpty()) {
            computeSelectionBounds()
        }
        invalidate()
    }

    private fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x; val yi = polygon[i].y
            val xj = polygon[j].x; val yj = polygon[j].y
            if ((yi > point.y) != (yj > point.y) &&
                point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun computeSelectionBounds() {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for (idx in selectedStrokeIndices) {
            for (pt in committedStrokes[idx].points) {
                if (pt.x < minX) minX = pt.x; if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x; if (pt.y > maxY) maxY = pt.y
            }
        }
        selectionBounds.set(minX - 8f, minY - 8f, maxX + 8f, maxY + 8f)
    }

    fun deleteSelectedStrokes() {
        if (selectedStrokeIndices.isEmpty()) return
        saveUndoState()
        selectedStrokeIndices.sortedDescending().forEach { committedStrokes.removeAt(it) }
        selectedStrokeIndices.clear()
        lassoPath.reset()
        lassoPoints.clear()
        redrawAll()
        invalidate()
    }

    fun moveSelectedStrokes(dx: Float, dy: Float) {
        if (selectedStrokeIndices.isEmpty()) return
        saveUndoState()
        for (idx in selectedStrokeIndices) {
            val old = committedStrokes[idx]
            committedStrokes[idx] = old.copy(
                points = old.points.map { it.copy(x = it.x + dx, y = it.y + dy) }
            )
        }
        redrawAll()
        invalidate()
    }

    // --- Undo/Redo ---
    private fun saveUndoState() {
        undoStack.add(committedStrokes.map { it.copy(points = it.points.toList()) })
        if (undoStack.size > 50) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(committedStrokes.map { it.copy(points = it.points.toList()) })
        committedStrokes.clear()
        committedStrokes.addAll(undoStack.removeLast())
        redrawAll()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        saveUndoState()
        committedStrokes.clear()
        committedStrokes.addAll(redoStack.removeLast())
        redrawAll()
        invalidate()
    }

    // --- Load/Save ---
    fun loadStrokes(strokes: List<StrokeData>) {
        committedStrokes.clear()
        committedStrokes.addAll(strokes)
        redrawAll()
        invalidate()
    }

    fun getStrokes(): List<StrokeData> = committedStrokes.toList()

    fun clearCanvas() {
        saveUndoState()
        committedStrokes.clear()
        redrawAll()
        invalidate()
    }

    fun getBitmap(): Bitmap? = bitmap
}
