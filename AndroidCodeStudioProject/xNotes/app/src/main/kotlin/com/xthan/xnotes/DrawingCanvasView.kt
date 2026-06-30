package com.xthan.xnotes

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class DrawingCanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class Stroke(val path: Path, val color: Int, val width: Float)

    private val pages = mutableListOf<MutableList<Stroke>>(mutableListOf())
    var currentPageIndex = 0
        private set

    var currentPenColor = Color.BLACK
    var currentPenSize = 8f
    private var currentPath = Path()

    private var scaleFactor = 1.0f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(transformMatrix)

        for (stroke in pages[currentPageIndex]) {
            val paint = createPaint(stroke.color, stroke.width)
            canvas.drawPath(stroke.path, paint)
        }

        val currentPaint = createPaint(currentPenColor, currentPenSize)
        canvas.drawPath(currentPath, currentPaint)
        canvas.restore()
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
            currentPath.reset()
            return true
        }

        transformMatrix.invert(inverseMatrix)
        val pts = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(pts)
        val mappedX = pts[0]
        val mappedY = pts[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> currentPath.moveTo(mappedX, mappedY)
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(mappedX, mappedY)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pages[currentPageIndex].add(Stroke(currentPath, currentPenColor, currentPenSize))
                currentPath = Path()
                invalidate()
            }
        }
        return true
    }

    fun addPage() {
        pages.add(mutableListOf())
        currentPageIndex = pages.size - 1
        invalidate()
    }

    fun nextPage(): Boolean {
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            invalidate()
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPageIndex > 0) {
            currentPageIndex--
            invalidate()
            return true
        }
        return false
    }

    fun getPageCount(): Int = pages.size

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 4.0f))
            transformMatrix.setScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    }
}