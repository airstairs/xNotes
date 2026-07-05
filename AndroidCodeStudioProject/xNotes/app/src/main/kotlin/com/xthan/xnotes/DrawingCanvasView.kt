package com.xthan.xnotes

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.util.AttributeSet

class DrawingCanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class StrokeData(val pointsStr: String, val color: Int, val width: Float)

    val pages = mutableListOf<MutableList<StrokeData>>(mutableListOf())
    val redoPages = mutableListOf<MutableList<StrokeData>>(mutableListOf())
    var currentPageIndex = 0

    var currentPenColor = Color.BLACK
    var currentPenSize = 8f
    var isEraserMode = false
    private var currentPoints = StringBuilder()

    val paperWidth = 850f
    val paperHeight = 1100f

    var scaleFactor = 1.0f
    var translateX = 0f
    var translateY = 0f

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    var onStrokeAdded: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoomAndPan()
    }

    fun resetZoomAndPan() {
        if (width == 0 || height == 0) return

        val scaleX = width.toFloat() / paperWidth
        val scaleY = height.toFloat() / paperHeight
        scaleFactor = Math.min(scaleX, scaleY)
        
        translateX = (width.toFloat() - (paperWidth * scaleFactor)) / 2f
        translateY = (height.toFloat() - (paperHeight * scaleFactor)) / 2f
        
        invalidate()
    }

    // EXPOSED METHOD FOR BUTTON: Undo
    fun undoLastStroke(): Boolean {
        val activeList = pages.getOrNull(currentPageIndex)
        val redoList = redoPages.getOrNull(currentPageIndex)
        if (!activeList.isNullOrEmpty() && redoList != null) {
            val removed = activeList.removeAt(activeList.size - 1)
            redoList.add(removed)
            invalidate()
            onStrokeAdded?.invoke()
            return true
        }
        return false
    }

    // EXPOSED METHOD FOR BUTTON: Redo
    fun redoLastStroke(): Boolean {
        val targetRedoList = redoPages.getOrNull(currentPageIndex)
        val targetActiveList = pages.getOrNull(currentPageIndex)
        if (!targetRedoList.isNullOrEmpty() && targetActiveList != null) {
            val restoredStroke = targetRedoList.removeAt(targetRedoList.size - 1)
            targetActiveList.add(restoredStroke)
            invalidate()
            onStrokeAdded?.invoke()
            return true
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#E0E0E0"))

        canvas.save()
        transformMatrix.reset()
        transformMatrix.postScale(scaleFactor, scaleFactor)
        transformMatrix.postTranslate(translateX, translateY)
        canvas.concat(transformMatrix)

        val paperPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, paperWidth, paperHeight, paperPaint)

        val borderPaint = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, paperWidth, paperHeight, borderPaint)

        for (stroke in pages[currentPageIndex]) {
            val path = strokeToPath(stroke.pointsStr)
            val paint = createPaint(stroke.color, stroke.width)
            canvas.drawPath(path, paint)
        }

        if (currentPoints.isNotEmpty()) {
            val currentPath = strokeToPath(currentPoints.toString())
            val currentPaint = createPaint(currentPenColor, currentPenSize)
            canvas.drawPath(currentPath, currentPaint)
        }
        canvas.restore()

        drawViewportScrollbars(canvas)
    }

    private fun drawViewportScrollbars(canvas: Canvas) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val docW = paperWidth * scaleFactor
        val docH = paperHeight * scaleFactor

        val scrollPaint = Paint().apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val barThickness = 8f
        val padding = 4f

        if (docW > viewW) {
            val sizeRatio = viewW / docW
            val barWidth = viewW * sizeRatio
            val maxScrollable = docW - viewW
            val scrolledRatio = if (maxScrollable > 0) -translateX / maxScrollable else 0f
            val barLeft = padding + (scrolledRatio * (viewW - barWidth - (padding * 2)))
            canvas.drawRoundRect(barLeft, viewH - barThickness - padding, barLeft + barWidth, viewH - padding, 4f, 4f, scrollPaint)
        }

        if (docH > viewH) {
            val sizeRatio = viewH / docH
            val barHeight = viewH * sizeRatio
            val maxScrollable = docH - viewH
            val scrolledRatio = if (maxScrollable > 0) -translateY / maxScrollable else 0f
            val barTop = padding + (scrolledRatio * (viewH - barHeight - (padding * 2)))
            canvas.drawRoundRect(viewW - barThickness - padding, barTop, viewW - padding, barTop + barHeight, 4f, 4f, scrollPaint)
        }
    }

    private fun strokeToPath(pointsStr: String): Path {
        val path = Path()
        val tokens = pointsStr.split(",")
        if (tokens.size >= 2) {
            path.moveTo(tokens[0].toFloat(), tokens[1].toFloat())
            var i = 2
            while (i < tokens.size - 1) {
                path.lineTo(tokens[i].toFloat(), tokens[i+1].toFloat())
                i += 2
            }
        }
        return path
    }

    private fun createPaint(color: Int, width: Float): Paint {
        return Paint().apply {
            this.color = color
            this.strokeWidth = width
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        // Standard 2-finger panning and zooming
        if (pointerCount > 1) {
            isPanning = true
            currentPoints.setLength(0)
            
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_MOVE && pointerCount == 2) {
                if (!scaleDetector.isInProgress) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    translateX += x - lastTouchX
                    translateY += y - lastTouchY
                    invalidate()
                    lastTouchX = x
                    lastTouchY = y
                }
            } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                lastTouchX = event.getX(0)
                lastTouchY = event.getY(0)
            }
            return true
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            isPanning = false
            lastTouchX = event.x
            lastTouchY = event.y
        }

        if (isPanning) {
            if (event.action == MotionEvent.ACTION_UP) {
                isPanning = false
            }
            return true
        }

        transformMatrix.reset()
        transformMatrix.postScale(scaleFactor, scaleFactor)
        transformMatrix.postTranslate(translateX, translateY)
        transformMatrix.invert(inverseMatrix)

        val pts = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pts)
        val mappedX = pts[0]
        val mappedY = pts[1]

        val isInsidePaper = mappedX in 0f..paperWidth && mappedY in 0f..paperHeight

        if (isEraserMode) {
            if (isInsidePaper && (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN)) {
                val iterator = pages[currentPageIndex].iterator()
                var modified = false
                while (iterator.hasNext()) {
                    val stroke = iterator.next()
                    if (isPointNearStroke(mappedX, mappedY, stroke.pointsStr, stroke.width + 20f)) {
                        iterator.remove()
                        modified = true
                    }
                }
                if (modified) {
                    invalidate()
                    onStrokeAdded?.invoke()
                }
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInsidePaper) {
                    currentPoints.setLength(0)
                    currentPoints.append("$mappedX,$mappedY")
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPoints.isNotEmpty() && isInsidePaper) {
                    currentPoints.append(",$mappedX,$mappedY")
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (currentPoints.isNotEmpty()) {
                    pages[currentPageIndex].add(StrokeData(currentPoints.toString(), currentPenColor, currentPenSize))
                    redoPages[currentPageIndex].clear()
                    currentPoints.setLength(0)
                    invalidate()
                    onStrokeAdded?.invoke()
                }
            }
        }
        return true
    }

    private fun isPointNearStroke(x: Float, y: Float, pointsStr: String, threshold: Float): Boolean {
        val tokens = pointsStr.split(",")
        var i = 0
        while (i < tokens.size - 1) {
            val px = tokens[i].toFloat()
            val py = tokens[i+1].toFloat()
            val distance = Math.sqrt(((x - px) * (x - px) + (y - py) * (y - py)).toDouble()).toFloat()
            if (distance < threshold) return true
            i += 2
        }
        return false
    }

    fun addPage() {
        pages.add(mutableListOf())
        redoPages.add(mutableListOf())
        currentPageIndex = pages.size - 1
        resetZoomAndPan()
    }

    fun nextPage(): Boolean {
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            resetZoomAndPan()
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPageIndex > 0) {
            currentPageIndex--
            resetZoomAndPan()
            return true
        }
        return false
    }

    fun getPageCount(): Int = pages.size

    fun toSerializedString(): String {
        val sb = StringBuilder()
        for (p in 0 until pages.size) {
            if (p > 0) sb.append(";")
            for (s in 0 until pages[p].size) {
                if (s > 0) sb.append("|")
                val stroke = pages[p][s]
                sb.append("${stroke.color}:${stroke.width}:${stroke.pointsStr}")
            }
        }
        return sb.toString()
    }

    fun loadFromSerializedString(data: String) {
        pages.clear()
        redoPages.clear()
        if (data.isEmpty()) {
            pages.add(mutableListOf())
            redoPages.add(mutableListOf())
            currentPageIndex = 0
            resetZoomAndPan()
            return
        }
        val pTokens = data.split(";")
        for (pToken in pTokens) {
            val pageList = mutableListOf<StrokeData>()
            if (pToken.isNotEmpty()) {
                val sTokens = pToken.split("|")
                for (sToken in sTokens) {
                    if (sToken.isNotEmpty()) {
                        val parts = sToken.split(":", limit = 3)
                        if (parts.size == 3) {
                            pageList.add(StrokeData(parts[2], parts[0].toInt(), parts[1].toFloat()))
                        }
                    }
                }
            }
            pages.add(pageList)
            redoPages.add(mutableListOf())
        }
        currentPageIndex = 0
        resetZoomAndPan()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 15.0f))

            val focusX = detector.focusX
            val focusY = detector.focusY
            translateX = focusX - (focusX - translateX) * (scaleFactor / oldScale)
            translateY = focusY - (focusY - translateY) * (scaleFactor / oldScale)

            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetZoomAndPan()
            return true
        }
    }
}