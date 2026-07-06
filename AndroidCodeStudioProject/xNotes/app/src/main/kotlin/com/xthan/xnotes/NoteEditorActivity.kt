package com.xthan.xnotes

import com.xthan.xnotes.R
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
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
        setTitle(title)
        
        bottomTitleIndicator.text = title

        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedData = prefs.getString("note_data_$notebookId", "") ?: ""
        canvasView.loadFromSerializedString(savedData)

        canvasView.onStrokeAdded = { autoSaveData() }

        // Setup custom dynamic Recents Switcher card branding setup
        val noteColor = prefs.getInt("note_color_$notebookId", Color.GRAY)
        setupRecentsCardStyle(title, noteColor)

        // Floating Action Buttons Click Listeners
        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            canvasView.undoLastStroke()
        }

        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            canvasView.redoLastStroke()
        }

        findViewById<Button>(R.id.btnZoomReset).setOnClickListener {
            canvasView.resetZoomAndPan()
        }

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

        // Safe fallback check for layout integration linking
        val jumpResId = resources.getIdentifier("btnJumpPage", "id", packageName)
        if (jumpResId != 0) {
            findViewById<Button>(jumpResId).setOnClickListener {
                showJumpWithThumbnailsDialog()
            }
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

    private fun showJumpWithThumbnailsDialog() {
        val totalPages = canvasView.getPageCount()
        val listView = ListView(this)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Jump to Page Preview")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.adapter = PageThumbnailAdapter(this, totalPages, canvasView)
        listView.setOnItemClickListener { _, _, position, _ ->
            canvasView.currentPageIndex = position
            canvasView.resetZoomAndPan()
            updatePageIndicator()
            canvasView.invalidate()
            dialog.dismiss()
        }

        dialog.show()
    }

    private class PageThumbnailAdapter(
        private val context: Context,
        private val count: Int,
        private val canvasView: DrawingCanvasView
    ) : BaseAdapter() {

        override fun getCount(): Int = count
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(android.R.layout.activity_list_item, parent, false)

            val text = view.findViewById<TextView>(android.R.id.text1)
            val icon = view.findViewById<ImageView>(android.R.id.icon)

            text.text = "Page ${position + 1}"
            text.textSize = 18f

            // Create bitmap matching aspect ratio of paperWidth (850) and paperHeight (1100)
            val width = 120
            val height = 155
            val thumbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(thumbBitmap)

            // Canvas sheet styling backgrounds
            canvas.drawColor(Color.parseColor("#E0E0E0"))
            val innerPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRect(4f, 4f, (width - 4).toFloat(), (height - 4).toFloat(), innerPaint)

            // Extract stroke list targeting array index safely
            val originalPagePaths = canvasView.pages.getOrNull(position)
            if (originalPagePaths != null && originalPagePaths.isNotEmpty()) {
                // Map coordinates using hardcoded canvas base rules limits
                val scaleX = (width - 8).toFloat() / canvasView.paperWidth
                val scaleY = (height - 8).toFloat() / canvasView.paperHeight
                val minScale = Math.min(scaleX, scaleY)

                canvas.save()
                canvas.translate(4f, 4f)
                canvas.scale(minScale, minScale)

                val previewPaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }

                // Loop through strokes using the matching properties found in DrawingCanvasView
                for (stroke in originalPagePaths) {
                    previewPaint.color = stroke.color
                    previewPaint.strokeWidth = stroke.width
                    
                    // Direct stroke string parsing conversion matching drawing view architecture
                    val path = Path()
                    val tokens = stroke.pointsStr.split(",")
                    if (tokens.size >= 2) {
                        path.moveTo(tokens[0].toFloat(), tokens[1].toFloat())
                        var i = 2
                        while (i < tokens.size - 1) {
                            path.lineTo(tokens[i].toFloat(), tokens[i+1].toFloat())
                            i += 2
                        }
                    }
                    canvas.drawPath(path, previewPaint)
                }
                canvas.restore()
            } else {
                // If the page is blank, draw a clean subtle text placeholder
                val textPaint = Paint().apply {
                    color = Color.parseColor("#CCCCCC")
                    textSize = 28f
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    textAlign = Paint.Align.CENTER
                }
                val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText("Empty", width / 2f, textY, textPaint)
            }

            icon.setImageBitmap(thumbBitmap)
            icon.layoutParams.width = width
            icon.layoutParams.height = height
            icon.scaleType = ImageView.ScaleType.FIT_CENTER

            return view
        }
    }

    private fun createNotebookIcon(color: Int): Bitmap {
        val size = 96 
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val bgPaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)
        
        val circlePaint = Paint().apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size * 0.3f 
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        
        val textPaint = Paint().apply {
            this.color = Color.BLACK
            textSize = radius * 1.3f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText("x", centerX, textY, textPaint)
        
        return bitmap
    }

    private fun setupRecentsCardStyle(title: String, noteColor: Int) {
        val recentsIcon = createNotebookIcon(noteColor)
        @Suppress("DEPRECATION")
        val taskDesc = android.app.ActivityManager.TaskDescription(title, recentsIcon, noteColor)
        setTaskDescription(taskDesc)
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