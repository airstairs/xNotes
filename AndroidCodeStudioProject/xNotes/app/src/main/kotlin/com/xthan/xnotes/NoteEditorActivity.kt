package com.xthan.xnotes

import com.xthan.xnotes.R
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var canvasView: DrawingCanvasView
    private lateinit var pageIndicator: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        canvasView = findViewById(R.id.drawingCanvas)
        pageIndicator = findViewById(R.id.txtPageIndicator)

        val title = intent.getStringExtra("NOTEBOOK_TITLE") ?: "Notebook"
        setTitle(title)

        findViewById<Button>(R.id.btnBlack).setOnClickListener { canvasView.currentPenColor = Color.BLACK }
        findViewById<Button>(R.id.btnRed).setOnClickListener { canvasView.currentPenColor = Color.RED }
        findViewById<Button>(R.id.btnBlue).setOnClickListener { canvasView.currentPenColor = Color.BLUE }

        findViewById<Button>(R.id.btnThin).setOnClickListener { canvasView.currentPenSize = 5f }
        findViewById<Button>(R.id.btnThick).setOnClickListener { canvasView.currentPenSize = 20f }

        findViewById<Button>(R.id.btnAddPage).setOnClickListener {
            canvasView.addPage()
            updatePageIndicator()
        }
        findViewById<Button>(R.id.btnPrevPage).setOnClickListener {
            if (canvasView.prevPage()) updatePageIndicator()
        }
        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            if (canvasView.nextPage()) updatePageIndicator()
        }

        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        val current = canvasView.currentPageIndex + 1
        val total = canvasView.getPageCount()
        pageIndicator.text = "Page $current/$total"
    }
}
