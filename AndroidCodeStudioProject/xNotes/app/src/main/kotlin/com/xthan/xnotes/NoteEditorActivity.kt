package com.xthan.xnotes

import com.xthan.xnotes.R
import android.content.Context
import android.graphics.Color
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

        // Setup Dropdown Popup Menu for Tools & Colors with crisp square glyphs
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
            
            try {
                val fields = popup.javaClass.getDeclaredFields()
                for (field in fields) {
                    if ("mPopup" == field.name) {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popup)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

            try {
                val fields = popup.javaClass.getDeclaredFields()
                for (field in fields) {
                    if ("mPopup" == field.name) {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popup)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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