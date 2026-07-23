package com.xthan.xnotes

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WidgetActionActivity : AppCompatActivity() {

    private val colorOptions = arrayOf("Black", "Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Gray")
    private val colorValues = arrayOf(
        Color.BLACK, 
        Color.RED, 
        Color.BLUE, 
        Color.parseColor("#4CAF50"), 
        Color.parseColor("#FFEB3B"), 
        Color.parseColor("#9C27B0"), 
        Color.parseColor("#FF9800"), 
        Color.GRAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Determine which action was triggered from the widget
        val actionType = intent.getStringExtra("ACTION_TYPE")
        val targetNoteName = intent.getStringExtra("NOTE_NAME")

        when (actionType) {
            "CREATE_NOTE" -> showWidgetNameInputDialog()
            "CHANGE_COLOR" -> if (targetNoteName != null) showWidgetColorSelectionDialog(targetNoteName)
            else -> finish()
        }
    }

    private fun showWidgetNameInputDialog() {
        val input = EditText(this).apply { hint = "Notebook Name" }
        AlertDialog.Builder(this)
            .setTitle("Set New Notebook Name")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    showWidgetColorSelectionDialog(name, isReassigning = false)
                } else {
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showWidgetColorSelectionDialog(name: String, isReassigning: Boolean = true) {
        AlertDialog.Builder(this)
            .setTitle(if (isReassigning) "Change Color for $name" else "Select Notebook Color")
            .setItems(colorOptions) { _, which ->
                val selectedColor = colorValues[which]
                val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
                
                // Save color
                prefs.edit().putInt("note_color_$name", selectedColor).apply()

                if (!isReassigning) {
                    // Save title to list if it's a new note
                    val savedList = prefs.getStringSet("notes_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    savedList.add(name)
                    prefs.edit().putStringSet("notes_list", savedList).apply()
                }

                // Refresh all home screen widgets instantly
                DashboardWidgetProvider.refreshAllWidgets(this)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}