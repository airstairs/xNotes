package com.xthan.xnotes

import com.xthan.xnotes.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var savesContainer: LinearLayout
    private val PICK_FILE_REQUEST_CODE = 1001

    private val colorOptions = arrayOf("Black", "Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Gray")
    private val colorValues = arrayOf(
        Color.BLACK, 
        Color.RED, 
        Color.BLUE, 
        Color.parseColor("#4CAF50"), // Clean Green
        Color.parseColor("#FFEB3B"), // Clean Yellow
        Color.parseColor("#9C27B0"), // Clean Purple
        Color.parseColor("#FF9800"), // Clean Orange
        Color.GRAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        savesContainer = findViewById(R.id.savesContainer)

        findViewById<Button>(R.id.btnNotebook1).setOnClickListener {
            showNameInputDialog()
        }

        findViewById<Button>(R.id.btnExportAll).setOnClickListener {
            showExportFileNameDialog()
        }

        findViewById<Button>(R.id.btnImportAll).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Select Backup File"), PICK_FILE_REQUEST_CODE)
        }

        findViewById<Button>(R.id.btnMassDelete).setOnClickListener {
            showMassDeleteDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSavedNotesList()
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

    private fun showNameInputDialog() {
        val input = EditText(this).apply { hint = "Note Name" }
        AlertDialog.Builder(this)
            .setTitle("New Note Name")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    showColorSelectionDialog(name, isReassigning = false)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showColorSelectionDialog(name: String, isReassigning: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (isReassigning) "Change Color for $name" else "Select Notebook Color")
            .setItems(colorOptions) { _, which ->
                val selectedColor = colorValues[which]
                saveNoteColor(name, selectedColor)
                if (!isReassigning) {
                    saveNoteTitleToList(name)
                    openNotebook(name, name)
                } else {
                    refreshSavedNotesList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNoteColor(name: String, color: Int) {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("note_color_$name", color).apply()
    }

    private fun saveNoteTitleToList(name: String) {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedList = prefs.getStringSet("notes_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        savedList.add(name)
        prefs.edit().putStringSet("notes_list", savedList).apply()
        refreshSavedNotesList()
    }

    private fun refreshSavedNotesList() {
        savesContainer.removeAllViews()
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedList = prefs.getStringSet("notes_list", emptySet()) ?: emptySet()

        for (noteName in savedList) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val noteColor = prefs.getInt("note_color_$noteName", Color.GRAY)

            val btnColorSquare = ImageButton(this).apply {
                val sizePx = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(0, 0, 8, 0)
                }
                setImageBitmap(createNotebookIcon(noteColor))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    showColorSelectionDialog(noteName, isReassigning = true)
                }
            }

            val btnOpen = Button(this).apply {
                text = noteName
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { openNotebook(noteName, noteName) }
            }

            val btnDelete = Button(this).apply {
                text = "X"
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Delete note?")
                        .setMessage("Are you sure you want to delete '$noteName'?")
                        .setPositiveButton("Delete") { _, _ ->
                            val currentList = prefs.getStringSet("notes_list", emptySet())?.toMutableSet() ?: mutableSetOf()
                            currentList.remove(noteName)
                            prefs.edit().putStringSet("notes_list", currentList)
                                .remove("note_data_$noteName")
                                .remove("note_color_$noteName")
                                .apply()
                            refreshSavedNotesList()
                        }
                    .setNegativeButton("Cancel", null)
                    .show()
                }
            }

            itemLayout.addView(btnColorSquare)
            itemLayout.addView(btnOpen)
            itemLayout.addView(btnDelete)
            savesContainer.addView(itemLayout)
        }
    }

    private fun showExportFileNameDialog() {
        val input = EditText(this).apply { hint = "Backup Name" }
        AlertDialog.Builder(this)
            .setTitle("Export System")
            .setMessage("Enter custom target name for output file:")
            .setView(input)
            .setPositiveButton("Export") { _, _ ->
                var fileName = input.text.toString().trim()
                if (fileName.isEmpty()) fileName = "Backup"
                
                if (!fileName.endsWith(".xNotesBackup")) {
                    fileName = "$fileName.xNotesBackup"
                }
                
                val combinedData = StringBuilder()
                val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                val savedList = prefs.getStringSet("notes_list", emptySet()) ?: emptySet()
    
                for ((index, noteName) in savedList.withIndex()) {
                    if (index > 0) combinedData.append("##NOTE_BREAK##")
                    val noteData = prefs.getString("note_data_$noteName", "") ?: ""
                    // Append color configuration payload to export file format
                    val noteColor = prefs.getInt("note_color_$noteName", Color.GRAY)
                    combinedData.append("$noteName##NOTE_SPLIT##$noteData##COLOR_SPLIT##$noteColor")
                }
    
                try {
                    val exportDir = File(cacheDir, "exports")
                    if (!exportDir.exists()) exportDir.mkdirs()
                    
                    val outputFile = File(exportDir, fileName)
                    FileOutputStream(outputFile).use { writer ->
                        writer.write(combinedData.toString().toByteArray())
                    }
    
                    val fileUri: Uri = FileProvider.getUriForFile(
                        this,
                        "com.xthan.xnotes.fileprovider",
                        outputFile
                    )
    
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        type = "application/octet-stream"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    startActivity(Intent.createChooser(sendIntent, "Save xNotes System State File:"))
                    
                } catch (e: Exception) {
                    Toast.makeText(this, "Local Export Creation Failed", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intentData: Intent?) {
        super.onActivityResult(requestCode, resultCode, intentData)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            intentData?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                    inputStream?.close()

                    val rawContent = stringBuilder.toString()
                    if (rawContent.isNotEmpty()) {
                        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        val currentList = prefs.getStringSet("notes_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

                        val segments = rawContent.split("##NOTE_BREAK##")
                        for (segment in segments) {
                            if (segment.contains("##NOTE_SPLIT##")) {
                                val parts = segment.split("##NOTE_SPLIT##", limit = 2)
                                val name = parts[0]
                                var dataAndColor = parts[1]
                                
                                var colorValue = Color.GRAY
                                if (dataAndColor.contains("##COLOR_SPLIT##")) {
                                    val colorParts = dataAndColor.split("##COLOR_SPLIT##", limit = 2)
                                    dataAndColor = colorParts[0]
                                    colorValue = colorParts[1].toInt()
                                }
                                
                                currentList.add(name)
                                editor.putString("note_data_$name", dataAndColor)
                                editor.putInt("note_color_$name", colorValue)
                            }
                        }
                        editor.putStringSet("notes_list", currentList).apply()
                        refreshSavedNotesList()
                        Toast.makeText(this, "Notes Imported Successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Import Failure: Parsing Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showMassDeleteDialog() {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val savedList = prefs.getStringSet("notes_list", emptySet())?.toList() ?: emptyList()

        if (savedList.isEmpty()) {
            Toast.makeText(this, "No saved cards available to delete.", Toast.LENGTH_SHORT).show()
            return
        }

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val selectAllLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 20)
        }
        val cbSelectAll = CheckBox(context)
        val tvSelectAll = TextView(context).apply { 
            text = "Select All Notes"
            textSize = 18f
            setPadding(10, 0, 0, 0)
        }
        selectAllLayout.addView(cbSelectAll)
        selectAllLayout.addView(tvSelectAll)
        layout.addView(selectAllLayout)

        val checkBoxes = mutableListOf<CheckBox>()
        for (noteName in savedList) {
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 10, 0, 10)
            }
            val cb = CheckBox(context)
            val tv = TextView(context).apply {
                text = noteName
                textSize = 16f
                setPadding(10, 0, 0, 0)
            }
            itemLayout.addView(cb)
            itemLayout.addView(tv)
            layout.addView(itemLayout)
            checkBoxes.add(cb)
        }

        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            for (cb in checkBoxes) {
                cb.isChecked = isChecked
            }
        }

        val scroll = ScrollView(context).apply { addView(layout) }

        AlertDialog.Builder(context)
            .setTitle("Thread Delete Manager")
            .setView(scroll)
            .setPositiveButton("Execute Trash") { _, _ ->
                val targetedToDelete = mutableListOf<String>()
                for (i in checkBoxes.indices) {
                    if (checkBoxes[i].isChecked) {
                        targetedToDelete.add(savedList[i])
                    }
                }

                if (targetedToDelete.isNotEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("Final Confirmation")
                        .setMessage("Are you absolutely sure you want to permanently delete ${targetedToDelete.size} note entries?")
                        .setPositiveButton("Yes, Delete") { _, _ ->
                            val currentList = prefs.getStringSet("notes_list", emptySet())?.toMutableSet() ?: mutableSetOf()
                            val editor = prefs.edit()
                            for (target in targetedToDelete) {
                                currentList.remove(target)
                                editor.remove("note_data_$target")
                                editor.remove("note_color_$target")
                            }
                            editor.putStringSet("notes_list", currentList).apply()
                            refreshSavedNotesList()
                            Toast.makeText(context, "Selected entries cleared.", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openNotebook(id: String, title: String) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("NOTEBOOK_ID", id)
            putExtra("NOTEBOOK_TITLE", title)
            data = Uri.parse("notebook://open/$id")
        }
        startActivity(intent)
    }
}