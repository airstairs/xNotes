package com.xthan.xnotes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class MainActivity : AppCompatActivity() {

    data class NotebookItem(val id: String, var title: String, var colorInt: Int, var isSelected: Boolean = false)

    private val notebookList = mutableListOf<NotebookItem>()
    private lateinit var adapter: NotebookAdapter
    private lateinit var recyclerView: RecyclerView
    
    private lateinit var selectionContextBar: LinearLayout
    private var isSelectionMode = false

    companion object {
        private const val REQUEST_CODE_EXPORT = 4221
        private const val REQUEST_CODE_IMPORT = 4222
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectionContextBar = findViewById(R.id.selectionContextBar)
        recyclerView = findViewById(R.id.recyclerViewNotebooks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = NotebookAdapter(notebookList, 
            onItemClick = { notebook, position ->
                if (isSelectionMode) {
                    notebook.isSelected = !notebook.isSelected
                    adapter.notifyItemChanged(position)
                } else {
                    val intent = Intent(this, NoteEditorActivity::class.java).apply {
                        putExtra("NOTEBOOK_ID", notebook.id)
                        putExtra("NOTEBOOK_TITLE", notebook.title)
                        putExtra("NOTEBOOK_COLOR", notebook.colorInt)
                    }
                    startActivity(intent)
                }
            },
            onColorClick = { notebook, position ->
                if (!isSelectionMode) {
                    showVisualMixerColorPicker(notebook, position)
                }
            },
            onDeleteClick = { position ->
                confirmDeleteNotebook(position)
            }
        )
        recyclerView.adapter = adapter

        loadNotebookProfiles()

        findViewById<Button>(R.id.btnCreateNotebook).setOnClickListener {
            showCreateNotebookDialog()
        }

        findViewById<Button>(R.id.btnExportBackup).setOnClickListener {
            triggerSystemFileExport()
        }

        findViewById<Button>(R.id.btnImportBackup).setOnClickListener {
            triggerSystemFileImport()
        }

        findViewById<Button>(R.id.btnManageThreads).setOnClickListener {
            enterSelectionMode()
        }

        findViewById<Button>(R.id.btnCancelSelection).setOnClickListener {
            exitSelectionMode()
        }

        findViewById<Button>(R.id.btnSelectAll).setOnClickListener {
            val allChecked = notebookList.all { it.isSelected }
            for (item in notebookList) {
                item.isSelected = !allChecked
            }
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnConfirmMassDelete).setOnClickListener {
            val selectedItems = notebookList.filter { it.isSelected }
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "No notes selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Delete Selected Threads")
                .setMessage("Are you sure you want to delete ${selectedItems.size} selected notebooks?")
                .setPositiveButton("Delete") { _, _ ->
                    val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    for (item in selectedItems) {
                        editor.remove("note_data_${item.id}")
                        notebookList.remove(item)
                    }
                    editor.apply()
                    saveNotebookProfiles()
                    exitSelectionMode()
                    Toast.makeText(this, "Selected files deleted.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectionContextBar.visibility = View.VISIBLE
        for (item in notebookList) item.isSelected = false
        adapter.setSelectionModeEnabled(true)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectionContextBar.visibility = View.GONE
        for (item in notebookList) item.isSelected = false
        adapter.setSelectionModeEnabled(false)
    }

    private fun showCreateNotebookDialog() {
        val input = EditText(this).apply { hint = "Enter notebook name..." }
        AlertDialog.Builder(this)
            .setTitle("Create New Card")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val randomColor = (0xFF000000 or (Math.random() * 0x00FFFFFF).toLong()).toInt()
                    val newNote = NotebookItem(UUID.randomUUID().toString(), name, randomColor)
                    notebookList.add(newNote)
                    saveNotebookProfiles()
                    adapter.notifyItemInserted(notebookList.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteNotebook(position: Int) {
        val target = notebookList[position]
        AlertDialog.Builder(this)
            .setTitle("Delete Notebook")
            .setMessage("Are you sure you want to delete '${target.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("note_data_${target.id}").apply()
                notebookList.removeAt(position)
                saveNotebookProfiles()
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, notebookList.size)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVisualMixerColorPicker(notebook: NotebookItem, position: Int) {
        val mixerView = ColorMixerView(this)
        mixerView.setSelectedColor(notebook.colorInt)

        AlertDialog.Builder(this)
            .setTitle("Slide to Select Color")
            .setView(mixerView)
            .setPositiveButton("Select") { _, _ ->
                notebook.colorInt = mixerView.getSelectedColor()
                saveNotebookProfiles()
                adapter.notifyItemChanged(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Storage Access Framework Document Creators
    private fun triggerSystemFileExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "backup.xnotesbackup")
        }
        startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }

    private fun triggerSystemFileImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return

        val fileUri: Uri = data.data ?: return

        if (requestCode == REQUEST_CODE_EXPORT) {
            try {
                val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                val manifest = prefs.getString("notebook_manifest_list", "") ?: ""
                contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    outputStream.write(manifest.toByteArray())
                }
                Toast.makeText(this, "Backup saved to file successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving backup file", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQUEST_CODE_IMPORT) {
            try {
                contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                    val importedText = stringBuilder.toString().trim()
                    
                    if (importedText.contains("::") && importedText.contains("||")) {
                        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("notebook_manifest_list", importedText).apply()
                        loadNotebookProfiles()
                        Toast.makeText(this, "Import successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid backup file structure layout format", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error reading import file source", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveNotebookProfiles() {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for (item in notebookList) {
            sb.append("${item.id}::${item.title}::${item.colorInt}||")
        }
        prefs.edit().putString("notebook_manifest_list", sb.toString()).apply()
    }

    private fun loadNotebookProfiles() {
        notebookList.clear()
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val rawData = prefs.getString("notebook_manifest_list", "") ?: ""
        if (rawData.isNotEmpty()) {
            val tokens = rawData.split("||")
            for (token in tokens) {
                if (token.isNotEmpty()) {
                    val parts = token.split("::")
                    if (parts.size == 3) {
                        notebookList.add(NotebookItem(parts[0], parts[1], parts[2].toInt()))
                    }
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        if (!isSelectionMode) {
            loadNotebookProfiles()
        }
    }

    class NotebookAdapter(
        private val items: List<NotebookItem>,
        private val onItemClick: (NotebookItem, Int) -> Unit,
        private val onColorClick: (NotebookItem, Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<NotebookAdapter.ViewHolder>() {

        private var showCheckBoxes = false

        fun setSelectionModeEnabled(enabled: Boolean) {
            showCheckBoxes = enabled
            notifyDataSetChanged()
        }

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val iconCard: FrameLayout = v.findViewById(R.id.notebookIconCard)
            val iconText: TextView = v.findViewById(R.id.notebookIconText)
            val titleText: TextView = v.findViewById(R.id.txtNotebookTitle)
            val clickArea: View = v.findViewById(R.id.lnrTextClickArea)
            val checkBox: CheckBox = v.findViewById(R.id.notebookSelectionCheck)
            val deleteButton: Button = v.findViewById(R.id.btnDeleteNotebook)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notebook_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.titleText.text = item.title
            holder.iconCard.setBackgroundColor(item.colorInt)
            holder.iconText.text = "X"

            if (showCheckBoxes) {
                holder.checkBox.visibility = View.VISIBLE
                holder.checkBox.isChecked = item.isSelected
                holder.deleteButton.visibility = View.GONE
            } else {
                holder.checkBox.visibility = View.GONE
                holder.deleteButton.visibility = View.VISIBLE
            }

            holder.clickArea.setOnClickListener { onItemClick(item, position) }
            holder.iconCard.setOnClickListener { onColorClick(item, position) }
            holder.deleteButton.setOnClickListener { onDeleteClick(position) }
        }

        override fun getItemCount(): Int = items.size
    }

    // Complete Rebuilt Visual Spectrum Picker Component Block
    private class ColorMixerView(context: Context) : View(context) {
        private val hsv = floatArrayOf(0f, 1f, 1f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var selectX = -1f
        private var selectY = -1f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(ViewGroup.LayoutParams.MATCH_PARENT, 460)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            
            // Draw Main Horizontal Hue Gradient Wheel Track
            val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
            val hueShader = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
            paint.shader = hueShader
            canvas.drawRect(0f, 20f, w, 180f, paint)
            paint.shader = null

            // Draw Selector Thumb over Hue Ribbon
            if (selectX < 0f) selectX = (hsv[0] / 360f) * w
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawCircle(selectX, 100f, 24f, paint)
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawCircle(selectX, 100f, 26f, paint)

            // Draw Lower Value/Brightness Mod Track Bar
            val currentHueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            val valShader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Color.BLACK, currentHueColor, Color.WHITE), null, Shader.TileMode.CLAMP)
            paint.shader = valShader
            canvas.drawRect(0f, 240f, w, 360f, paint)
            paint.shader = null

            // Draw Value Selector Thumb over lower panel
            if (selectY < 0f) selectY = hsv[2] * w // Map value space roughly over width axis dynamically
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawCircle(selectY, 300f, 24f, paint)

            // Render Final Swatch Preview Plate Block Bottom Edge
            paint.style = Paint.Style.FILL
            paint.color = Color.HSVToColor(hsv)
            canvas.drawRect(0f, 400f, w, 450f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = Math.max(0f, Math.min(event.x, width.toFloat()))
                val y = event.y

                if (y in 0f..210f) {
                    selectX = x
                    hsv[0] = (selectX / width.toFloat()) * 360f
                } else if (y in 210f..380f) {
                    selectY = x
                    hsv[2] = selectY / width.toFloat() // Lightness distribution mapping modification
                }
                
                invalidate()
                return true
            }
            return super.onTouchEvent(event)
        }

        fun setSelectedColor(color: Int) {
            Color.colorToHSV(color, hsv)
            selectX = -1f
            selectY = -1f
            invalidate()
        }

        fun getSelectedColor(): Int = Color.HSVToColor(hsv)
    }
}