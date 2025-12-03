package com.darkred1145.terratag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this) // Apply Tactical Theme
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recyclerView = findViewById(R.id.recyclerView)
        txtEmpty = findViewById(R.id.txtEmpty)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        // Setup Grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        loadImages()
    }

    private fun loadImages() {
        val sharedPref = getSharedPreferences("TerraTagPrefs", MODE_PRIVATE)
        val savedUriString = sharedPref.getString("save_directory", null)

        val imageList = mutableListOf<Any>()

        if (savedUriString != null) {
            // 1. CUSTOM DIRECTORY (From Settings)
            try {
                val treeUri = savedUriString.toUri()
                val docFile = DocumentFile.fromTreeUri(this, treeUri)

                docFile?.listFiles()?.forEach { file ->
                    if (file.type?.startsWith("image/") == true) {
                        imageList.add(file.uri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // 2. DEFAULT DIRECTORY (Internal Storage)
            val mediaDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "TerraTag")
            // Also check the standard Pictures/TerraTag folder if we saved there
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TerraTag")

            if (publicDir.exists()) {
                publicDir.listFiles()?.filter { it.extension.equals("jpg", true) }
                    ?.forEach { imageList.add(it) }
            }
            // Add internal ones too if any
            if (mediaDir.exists()) {
                mediaDir.listFiles()?.filter { it.extension.equals("jpg", true) }
                    ?.forEach { imageList.add(it) }
            }
        }

        // Update UI
        if (imageList.isEmpty()) {
            txtEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            txtEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Reverse list to show newest first
            imageList.reverse()

            recyclerView.adapter = GalleryAdapter(imageList) { item ->
                openFullScreen(item)
            }
        }
    }

    private fun openFullScreen(item: Any) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val uri = when (item) {
            is File -> androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.provider", // Ensure this matches Manifest
                item
            )
            is Uri -> item
            else -> return
        }

        intent.setDataAndType(uri, "image/*")
        startActivity(intent)
    }
}