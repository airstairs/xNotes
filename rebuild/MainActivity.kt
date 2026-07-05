package com.xthan.xnotes

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class MainActivity : AppCompatActivity() {

    data class NotebookItem(val id: String, var title: String, var colorInt: Int)

    private val notebookList = mutableListOf<NotebookItem>()
    private lateinit var adapter: NotebookAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewNotebooks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = NotebookAdapter(notebookList, 
            onItemClick = { notebook ->
                val intent = Intent(this, NoteEditorActivity::class.java).apply {
                    putExtra("NOTEBOOK_ID", notebook.id)
                    putExtra("NOTEBOOK_TITLE", notebook.title)
                }
                startActivity(intent)
            },
            onColorClick = { notebook, position ->
                showFullColorPickerDialog(notebook, position)
            }
        )
        recyclerView.adapter = adapter

        loadNotebookProfiles()

        findViewById<Button>(R.id.btnCreateNotebook).setOnClickListener {
            showCreateNotebookDialog()
        }
    }

    private fun showCreateNotebookDialog() {
        val input = EditText(this).apply {
            hint = "Enter notebook name..."
        }
        
        AlertDialog.Builder(this)
            .setTitle("Create New Card")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    // Assign a completely random color chosen from the entire 32-bit color possibility space
                    val randomColor = (0xFF000000 or (Math.random() * 0x00FFFFFF).toLong()).toInt()
                    
                    val newNote = NotebookItem(
                        id = UUID.randomUUID().toString(),
                        title = name,
                        colorInt = randomColor
                    )
                    notebookList.add(newNote)
                    saveNotebookProfiles()
                    adapter.notifyItemInserted(notebookList.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Comprehensive Native Color Selector Dialog System Matrix
    private fun showFullColorPickerDialog(notebook: NotebookItem, position: Int) {
        val colorOptions = intArrayOf(
            Color.parseColor("#F44336"), Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"),
            Color.parseColor("#673AB7"), Color.parseColor("#3F51B5"), Color.parseColor("#2196F3"),
            Color.parseColor("#00BCD4"), Color.parseColor("#009688"), Color.parseColor("#4CAF50"),
            Color.parseColor("#FFEB3B"), Color.parseColor("#FF9800"), Color.parseColor("#795548"),
            Color.parseColor("#9E9E9E"), Color.parseColor("#607D8B"), Color.parseColor("#000000")
        )

        val colorNames = arrayOf(
            "Crimson Red", "Vibrant Pink", "Deep Purple", 
            "Royal Violet", "Classic Indigo", "Electric Blue",
            "Teal Cyan", "Mint Turquoise", "Emerald Green", 
            "Bright Yellow", "Sunset Orange", "Earth Brown",
            "Slate Gray", "Steel Blue-Gray", "Pitch Black"
        )

        AlertDialog.Builder(this)
            .setTitle("Select Square Card Color")
            .setItems(colorNames) { _, which ->
                notebook.colorInt = colorOptions[which]
                saveNotebookProfiles()
                adapter.notifyItemChanged(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveNotebookProfiles() {
        val prefs = getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for (item in notebookList) {
            // Serialize data using structured safe splitting points separating structural context metadata
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
        loadNotebookProfiles() // Keep UI synced if titles or manifest structures shift
    }

    // Embedded Adapter pattern to render customized card templates seamlessly
    class NotebookAdapter(
        private val items: List<NotebookItem>,
        private val onItemClick: (NotebookItem) -> Unit,
        private val onColorClick: (NotebookItem, Int) -> Unit
    ) : RecyclerView.Adapter<NotebookAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val iconCard: FrameLayout = v.findViewById(R.id.notebookIconCard)
            val iconText: TextView = v.findViewById(R.id.notebookIconText)
            val titleText: TextView = v.findViewById(R.id.txtNotebookTitle)
            val colorTrigger: ImageView = v.findViewById(R.id.colorSquareTrigger)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notebook_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.titleText.text = item.title
            
            // Apply the customized notebook card background color dynamically
            holder.iconCard.setBackgroundColor(item.colorInt)
            
            // Ensure the black-on-white 'X' text layer stays constant
            holder.iconText.text = "X"
            holder.iconText.setTextColor(Color.BLACK)

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.colorTrigger.setOnClickListener { onColorClick(item, position) }
        }

        override fun getItemCount(): Int = items.size
    }
}