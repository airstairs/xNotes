package com.xthan.xnotes

import com.xthan.xnotes.R
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var canvasView: DrawingCanvasView
    private lateinit var pageIndicator: TextView
    private lateinit var notebookId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        canvasView = findViewById(R.id.drawingCanvas)
        pageIndicator = findViewById(R.id.txtPageIndicator)

        notebookId = intent.getStringExtra("NOTEBOOK_ID") ?: "default"
        val title = intent.getStringExtra("NOTEBOOK_TITLE") ?: "Notebook"
        setTitle(title)

        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedData = prefs.getString("note_data_$notebookId", "") ?: ""
        canvasView.loadFromSerializedString(savedData)

        canvasView.onStrokeAdded = { autoSaveData() }

        val btnBlack = findViewById<Button>(R.id.btnBlack)
        val btnRed = findViewById<Button>(R.id.btnRed)
        val btnBlue = findViewById<Button>(R.id.btnBlue)
        val btnEraser = findViewById<Button>(R.id.btnEraser)

        btnBlack.setOnClickListener {
            canvasView.isEraserMode = false
            canvasView.currentPenColor = Color.BLACK
            updateModeButtons(btnBlack, btnRed, btnBlue, btnEraser)
        }
        btnRed.setOnClickListener {
            canvasView.isEraserMode = false
            canvasView.currentPenColor = Color.RED
            updateModeButtons(btnBlack, btnRed, btnBlue, btnEraser)
        }
        btnBlue.setOnClickListener {
            canvasView.isEraserMode = false
            canvasView.currentPenColor = Color.BLUE
            updateModeButtons(btnBlack, btnRed, btnBlue, btnEraser)
        }
        btnEraser.setOnClickListener {
            canvasView.isEraserMode = true
            updateModeButtons(btnBlack, btnRed, btnBlue, btnEraser)
        }

        findViewById<Button>(R.id.btnThin).setOnClickListener { canvasView.currentPenSize = 5f }
        findViewById<Button>(R.id.btnThick).setOnClickListener { canvasView.currentPenSize = 20f }

        findViewById<Button>(R.id.btnAddPage).setOnClickListener {
            canvasView.addPage()
            updatePageIndicator()
            autoSaveData()
        }
        findViewById<Button>(R.id.btnPrevPage).setOnClickListener {
            if (canvasView.prevPage()) updatePageIndicator()
        }
        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            if (canvasView.nextPage()) updatePageIndicator()
        }

        updatePageIndicator()
        updateModeButtons(btnBlack, btnRed, btnBlue, btnEraser)
    }

    private fun updateModeButtons(b: Button, r: Button, bl: Button, e: Button) {
        b.setBackgroundColor(if (!canvasView.isEraserMode && canvasView.currentPenColor == Color.BLACK) Color.LTGRAY else Color.TRANSPARENT)
        r.setBackgroundColor(if (!canvasView.isEraserMode && canvasView.currentPenColor == Color.RED) Color.LTGRAY else Color.TRANSPARENT)
        bl.setBackgroundColor(if (!canvasView.isEraserMode && canvasView.currentPenColor == Color.BLUE) Color.LTGRAY else Color.TRANSPARENT)
        e.setBackgroundColor(if (canvasView.isEraserMode) Color.DKGRAY else Color.TRANSPARENT)
    }

    private fun autoSaveData() {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("note_data_$notebookId", canvasView.toSerializedString()).apply()
    }

    override fun onPause() {
        super.onPause()
        autoSaveData()
    }

    private fun updatePageIndicator() {
        val current = canvasView.currentPageIndex + 1
        val total = canvasView.getPageCount()
        pageIndicator.text = "Page $current/$total"
    }
}