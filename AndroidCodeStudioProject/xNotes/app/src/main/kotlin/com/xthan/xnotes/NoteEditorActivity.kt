package com.xthan.xnotes

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var drawingCanvas: DrawingCanvasView
    private lateinit var txtPageIndicator: TextView
    private lateinit var txtBottomNotebookTitle: TextView
    
    private var notebookId: String = ""
    private var notebookTitle: String = ""
    private var notebookColor: Int = Color.BLACK

    // Page state control tracks
    private val pageDataList = mutableListOf<String>() // Holds raw structural rendering strings per page sheet
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        // Read intent extras from main deck router pass
        notebookId = intent.getStringExtra("NOTEBOOK_ID") ?: ""
        notebookTitle = intent.getStringExtra("NOTEBOOK_TITLE") ?: "Untitled Notebook"
        notebookColor = intent.getIntExtra("NOTEBOOK_COLOR", Color.BLACK)

        // Initialize UI Elements
        drawingCanvas = findViewById(R.id.drawingCanvas)
        txtPageIndicator = findViewById(R.id.txtPageIndicator)
        txtBottomNotebookTitle = findViewById(R.id.txtBottomNotebookTitle)

        // Assign initial metadata views
        txtBottomNotebookTitle.text = notebookTitle
        drawingCanvas.setStrokeColor(notebookColor)

        // Load page structures from persistence layers
        loadSavedNotePages()

        // Hook up structural top bar buttons
        findViewById<Button>(R.id.btnToolsMenu).setOnClickListener {
            // Placeholder path for brush style popups
            Toast.makeText(this, "Brush Tools Menu", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSizesMenu).setOnClickListener {
            // Placeholder path for stroke sizing options
            Toast.makeText(this, "Stroke Size Menu", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPrevPage).setOnClickListener {
            if (currentPageIndex > 0) {
                saveCurrentPageSnapshot()
                currentPageIndex--
                displayCurrentPageData()
            }
        }

        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            if (currentPageIndex < pageDataList.size - 1) {
                saveCurrentPageSnapshot()
                currentPageIndex++
                displayCurrentPageData()
            } else {
                Toast.makeText(this, "Click '+' to append a new page canvas sheet.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAddPage).setOnClickListener {
            saveCurrentPageSnapshot()
            pageDataList.add("") // Add an empty snapshot block string array slot
            currentPageIndex = pageDataList.size - 1
            displayCurrentPageData()
            Toast.makeText(this, "Page ${currentPageIndex + 1} Created", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDeletePage).setOnClickListener {
            if (pageDataList.size > 1) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Current Page")
                    .setMessage("Are you sure you want to completely erase page ${currentPageIndex + 1}?")
                    .setPositiveButton("Delete") { _, _ ->
                        pageDataList.removeAt(currentPageIndex)
                        if (currentPageIndex >= pageDataList.size) {
                            currentPageIndex = pageDataList.size - 1
                        }
                        displayCurrentPageData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "Cannot delete the last remaining page sheet.", Toast.LENGTH_SHORT).show()
            }
        }

        // New Feature: Quick Jump Page Navigator Wheel Setup
        findViewById<Button>(R.id.btnJumpPage).setOnClickListener {
            val totalPages = pageDataList.size
            if (totalPages <= 1) {
                Toast.makeText(this, "Only one page exists to display.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create an interactive system wheel selector dialogue view
            val numberPicker = NumberPicker(this).apply {
                minValue = 1
                maxValue = totalPages
                value = currentPageIndex + 1
                wrapSelectorWheel = false
            }

            AlertDialog.Builder(this)
                .setTitle("Jump to Page")
                .setView(numberPicker)
                .setPositiveButton("Go") { _, _ ->
                    val targetPage = numberPicker.value - 1
                    if (targetPage != currentPageIndex) {
                        saveCurrentPageSnapshot()
                        currentPageIndex = targetPage
                        displayCurrentPageData()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Hook up bottom layer controller targets
        findViewById<Button>(R.id.btnZoomReset).setOnClickListener {
            drawingCanvas.resetCanvasZoomScale() // Calls clear viewport scaling paths
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            drawingCanvas.undoLastStroke()
        }

        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            drawingCanvas.redoLastStroke()
        }
    }

    private fun saveCurrentPageSnapshot() {
        if (notebookId.isNotEmpty()) {
            val rawPageString = drawingCanvas.getSerializedDataString() ?: ""
            if (currentPageIndex in 0 until pageDataList.size) {
                pageDataList[currentPageIndex] = rawPageString
            }
        }
    }

    private fun displayCurrentPageData() {
        // Stop background loops and render structural path data strings cleanly
        drawingCanvas.clearCanvasViewElements()
        val pageString = pageDataList.getOrNull(currentPageIndex) ?: ""
        if (pageString.isNotEmpty()) {
            drawingCanvas.loadSerializedDataString(pageString)
        }
        
        // Update page string indicator readout display text fields safely
        txtPageIndicator.text = "${currentPageIndex + 1}/${pageDataList.size}"
    }

    private fun saveAllNotebookPagesToDisk() {
        if (notebookId.isEmpty()) return
        saveCurrentPageSnapshot()

        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val stringBuilder = StringBuilder()
        for (i in 0 until pageDataList.size) {
            stringBuilder.append(pageDataList[i])
            if (i < pageDataList.size - 1) {
                stringBuilder.append("===PAGE_BREAK===")
            }
        }
        prefs.edit().putString("note_data_$notebookId", stringBuilder.toString()).apply()
    }

    private fun loadSavedNotePages() {
        pageDataList.clear()
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val rawDocData = prefs.getString("note_data_$notebookId", "") ?: ""

        if (rawDocData.isNotEmpty()) {
            val structuralPages = rawDocData.split("===PAGE_BREAK===")
            for (page in structuralPages) {
                pageDataList.add(page)
            }
            currentPageIndex = 0
        } else {
            pageDataList.add("") // Default primary workbook index entry assignment initialization
            currentPageIndex = 0
        }
        displayCurrentPageData()
    }

    override fun onPause() {
        super.onPause()
        saveAllNotebookPagesToDisk() // Guarantee layout preservation when swapping activities out of background states
    }
}