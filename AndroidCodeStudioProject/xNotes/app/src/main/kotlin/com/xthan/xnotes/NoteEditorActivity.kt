package com.xthan.xnotes

import com.xthan.xnotes.R
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var canvasView: DrawingCanvasView
    private lateinit var pageIndicator: TextView
    private lateinit var bottomTitleIndicator: TextView
    private lateinit var notebookId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        canvasView = findViewById(R.id.drawingCanvas)
        pageIndicator = findViewById(R.id.txtPageIndicator)
        bottomTitleIndicator = findViewById(R.id.txtBottomNotebookTitle)

        notebookId = intent.getStringExtra("NOTEBOOK_ID") ?: "default"
        val title = intent.getStringExtra("NOTEBOOK_TITLE") ?: "Notebook"
        val noteColor = intent.getIntExtra("NOTEBOOK_COLOR", Color.DKGRAY)
        
        setTitle(title)
        bottomTitleIndicator.text = title

        // Dynamic System Recents UI Switcher Custom Card Mask Generator
        updateSystemTaskDescriptionCard(title, noteColor)

        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedData = prefs.getString("note_data_$notebookId", "") ?: ""
        canvasView.loadFromSerializedString(savedData)

        canvasView.onStrokeAdded = { autoSaveData() }

        findViewById<Button>(R.id.btnUndo).setOnClickListener { canvasView.undoLastStroke() }
        findViewById<Button>(R.id.btnRedo).setOnClickListener { canvasView.redoLastStroke() }
        findViewById<Button>(R.id.btnZoomReset).setOnClickListener { canvasView.resetZoomAndPan() }

        // Setup Dropdown Popup Menu for Tools & Colors
        val btnToolsMenu = findViewById<Button>(R.id.btnToolsMenu)
        btnToolsMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "⬛ Black Pencil")
            popup.menu.add(0, 2, 0, "🟥 Red Pencil")
            popup.menu.add(0, 3, 0, "🟦 Blue Pencil")
            popup.menu.add(0, 4, 0, "🧼 Eraser Mode")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        canvasView.isEraserMode = false
                        canvasView.currentPenColor = Color.BLACK
                        btnToolsMenu.text = "⬛ Black"
                    }
                    2 -> {
                        canvasView.isEraserMode = false
                        canvasView.currentPenColor = Color.RED
                        btnToolsMenu.text = "🟥 Red"
                    }
                    3 -> {
                        canvasView.isEraserMode = false
                        canvasView.currentPenColor = Color.BLUE
                        btnToolsMenu.text = "🟦 Blue"
                    }
                    4 -> {
                        canvasView.isEraserMode = true
                        btnToolsMenu.text = "🧼 Eraser"
                    }
                }
                true
            }
            popup.show()
        }

        // Setup Dropdown Popup Menu for Stroke Size Thickness profiles
        val btnSizesMenu = findViewById<Button>(R.id.btnSizesMenu)
        btnSizesMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "📐 Extra Thin (2px)")
            popup.menu.add(0, 2, 0, "📐 Thin (5px)")
            popup.menu.add(0, 3, 0, "📐 Thick (20px)")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        canvasView.currentPenSize = 2f
                        btnSizesMenu.text = "📐 2px"
                    }
                    2 -> {
                        canvasView.currentPenSize = 5f
                        btnSizesMenu.text = "📐 5px"
                    }
                    3 -> {
                        canvasView.currentPenSize = 20f
                        btnSizesMenu.text = "📐 20px"
                    }
                }
                true
            }
            popup.show()
        }

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

        findViewById<Button>(R.id.btnDeletePage).setOnClickListener {
            val targetIdx = canvasView.currentPageIndex
            AlertDialog.Builder(this)
                .setTitle("Delete Page")
                .setMessage("Delete Page ${targetIdx + 1}? This action cannot be reversed.")
                .setPositiveButton("Delete") { _, _ ->
                    if (canvasView.getPageCount() <= 1) {
                        canvasView.pages[0].clear()
                        canvasView.redoPages[0].clear()
                        canvasView.resetZoomAndPan()
                    } else {
                        canvasView.pages.removeAt(targetIdx)
                        canvasView.redoPages.removeAt(targetIdx)
                        if (canvasView.currentPageIndex >= canvasView.getPageCount()) {
                            canvasView.currentPageIndex = canvasView.getPageCount() - 1
                        }
                        canvasView.resetZoomAndPan()
                    }
                    updatePageIndicator()
                    autoSaveData()
                    Toast.makeText(this, "Page deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updatePageIndicator()
    }

    private fun updateSystemTaskDescriptionCard(title: String, color: Int) {
        try {
            // Generate a custom launcher bitmap matching the chosen note color
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            
            // Draw solid square profile background
            paint.color = color
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            
            // Draw standard central white safety badge
            paint.color = Color.WHITE
            canvas.drawCircle(size / 2f, size / 2f, 16f, paint)
            
            // Draw clear black inner tracking letter symbol
            paint.color = Color.BLACK
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            
            val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText("X", size / 2f, yPos, paint)

            // Replaced builder with high-compatibility standard constructor
            @Suppress("DEPRECATION")
            val taskDesc = ActivityManager.TaskDescription(title, bitmap, color)
            setTaskDescription(taskDesc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        pageIndicator.text = "$current/$total"
    }
}