package com.darkred1145.geohud

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var txtCount: TextView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    private val legacyReadLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        loadImages()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this) // Apply Tactical Theme
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_gallery)
        applyPanelStyle()

        val root = findViewById<View>(R.id.root)
        root.applySystemBarPadding()

        recyclerView = findViewById(R.id.recyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        txtCount = findViewById(R.id.txtCount)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        // Setup Grid
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyReadLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        loadImages()
    }

    private fun loadImages() {
        loadJob?.cancel()
        loadJob = scope.launch {
            val items = withContext(Dispatchers.IO) { loadItems() }
            render(items)
        }
    }

    private fun loadItems(): List<GalleryItem> {
        val sharedPref = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val savedTree = sharedPref.getString(Prefs.KEY_SAVE_DIR, null)

        if (savedTree != null) {
            val treeItems = try {
                loadTreeItems(savedTree.toUri())
            } catch (e: Exception) {
                null // Revoked/broken tree: fall through to the default location.
            }
            if (treeItems != null) return treeItems.sortedByDescending { it.dateMillis }
        }

        return loadDefaultItems().sortedByDescending { it.dateMillis }
    }

    private fun loadTreeItems(treeUri: Uri): List<GalleryItem> {
        val tree = DocumentFile.fromTreeUri(this, treeUri) ?: return emptyList()
        return tree.listFiles().mapNotNull { doc ->
            val isImage = doc.isFile && (
                doc.type?.startsWith("image/") == true ||
                    doc.name?.endsWith(".jpg", ignoreCase = true) == true
            )
            if (isImage) GalleryItem.Media(doc.uri, doc.lastModified()) else null
        }
    }

    @Suppress("DEPRECATION", "NewApi")
    private fun loadDefaultItems(): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()
        try {
            items += queryMediaStore()
        } catch (e: Exception) {
            // No storage permission or MediaStore hiccup: file scan below may still work.
        }

        // Legacy installs may have written into app-specific storage.
        val appPictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val appDir = appPictures?.let { File(it, "GeoHUD") }
        if (appDir?.isDirectory == true) {
            appDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("jpg", true) }
                ?.forEach { items += GalleryItem.Local(it, it.lastModified()) }
        }

        return items
    }

    @Suppress("NewApi")
    private fun queryMediaStore(): List<GalleryItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?" to
                arrayOf("Pictures/GeoHUD/%", packageName)
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/Pictures/GeoHUD/%")
        }

        return contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            buildList {
                while (cursor.moveToNext()) {
                    val taken = cursor.getLong(takenColumn)
                    val added = cursor.getLong(addedColumn) * 1000L
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    add(GalleryItem.Media(uri, if (taken > 0L) taken else added))
                }
            }
        } ?: emptyList()
    }

    private fun render(items: List<GalleryItem>) {
        val empty = items.isEmpty()
        txtCount.text = getString(R.string.count_captures, items.size)
        emptyStateLayout.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE

        if (!empty) {
            recyclerView.adapter = GalleryAdapter(items) { item ->
                openFullScreen(item)
            }
        }
    }

    private fun openFullScreen(item: GalleryItem) {
        val uri = when (item) {
            is GalleryItem.Media -> item.uri
            is GalleryItem.Local -> try {
                FileProvider.getUriForFile(
                    this,
                    "$packageName.provider", // Keep in sync with AndroidManifest
                    item.file
                )
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, R.string.toast_no_viewer, Toast.LENGTH_SHORT).show()
                return
            }
        }

        startActivity(
            Intent(this, ViewerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply {
                    if (item is GalleryItem.Local) {
                        putExtra(ViewerActivity.EXTRA_LOCAL_PATH, item.file.absolutePath)
                    }
                }
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

