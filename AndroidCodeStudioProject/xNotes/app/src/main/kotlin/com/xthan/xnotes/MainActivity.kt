package com.xthan.xnotes

import com.xthan.xnotes.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnNotebook1).setOnClickListener {
            openNotebook("xNotes", "Open New Card")
        }

        /*
        findViewById<Button>(R.id.btnNotebook2).setOnClickListener {
            openNotebook("beta", "Notebook Beta")
        }
        */
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



