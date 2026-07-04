package com.xthan.xnotes

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class DrawingCanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class StrokeData(val pointsStr: String, val color: Int, val width: Float)

    val pages = mutableListOf<MutableList<StrokeData>>(mutableListOf())
    var currentPageIndex = 0

    var currentPenColor = Color.BLACK
    var currentPenSize = 8f
    var isEraserMode = false
    private var currentPoints = StringBuilder()

    // Canvas Paper Sizing
    private var paperWidth = 0f
    private var paperHeight = 0f

    // Zoom and Pan coordinates
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    var onStrokeAdded: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Force the canvas paper size to always lock onto Portrait dimensions
        if (paperWidth == 0f || paperHeight == 0f) {
            paperWidth = Math.min(w, h).toFloat()
            paperHeight = Math.max(w, h).toFloat()
        }
    }

    fun resetZoomAndPan() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        
        // Build view transformation around center baseline
        transformMatrix.reset()
        transformMatrix.postTranslate(translateX, translateY)
        transformMatrix.postScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        canvas.concat(transformMatrix)

        // Draw the explicit Paper boundary block
        val paperPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, paperWidth, paperHeight, paperPaint)

        // Draw lines or borders if screen is rotated out of default scale
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(0f, 0f, paperWidth, paperHeight, borderPaint)

        // Draw vector strokes
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
        scaleDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            isPanning = true
            currentPoints.setLength(0)
            
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_MOVE) {
                val x = event.getX(0)
                val y = event.getY(0)
                if (!scaleDetector.isInProgress) {
                    translateX += x - lastTouchX
                    translateY += y - lastTouchY
                    invalidate()
                }
                lastTouchX = x
                lastTouchY = y
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                lastTouchX = event.getX(0)
                lastTouchY = event.getY(0)
            }
            return true
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            isPanning = false
        }

        if (isPanning) {
            if (event.action == MotionEvent.ACTION_UP) {
                isPanning = false
            }
            return true
        }

        transformMatrix.reset()
        transformMatrix.postTranslate(translateX, translateY)
        transformMatrix.postScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        transformMatrix.invert(inverseMatrix)

        val pts = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pts)
        val mappedX = pts[0]
        val mappedY = pts[1]

        if (isEraserMode) {
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
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
                currentPoints.setLength(0)
                currentPoints.append("$mappedX,$mappedY")
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentPoints.isNotEmpty()) {
                    currentPoints.append(",$mappedX,$mappedY")
                }
                invalidate()
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (currentPoints.isNotEmpty()) {
                    pages[currentPageIndex].add(StrokeData(currentPoints.toString(), currentPenColor, currentPenSize))
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
        if (data.isEmpty()) {
            pages.add(mutableListOf())
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
        }
        currentPageIndex = 0
        resetZoomAndPan()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            // Capped at 1.0f minimum to stay snapped to paper size, boosted to 15.0f maximum for high-detail zoom
            scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 15.0f))
            invalidate()
            return true
        }
    }
}