package com.darkred1145.geohud

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import coil3.load
import coil3.request.crossfade
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Built-in full-screen capture viewer; no external gallery app required. */
class ViewerActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var txtMeta: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_viewer)

        findViewById<View>(R.id.header).applySystemBarPadding()
        findViewById<View>(R.id.metaContainer).applySystemBarPadding()
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, R.string.toast_no_viewer, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        txtMeta = findViewById(R.id.txtMeta)
        findViewById<ImageButton>(R.id.btnOptions).setOnClickListener { showOptions(uri) }

        val image = findViewById<ImageView>(R.id.imgViewer)
        image.load(uri) {
            crossfade(true)
        }
        image.setOnClickListener {
            if (txtMeta.text.isNotEmpty()) {
                txtMeta.visibility =
                    if (txtMeta.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        loadMetadata(uri)
    }

    private fun showOptions(uri: Uri) {
        val options = arrayOf(
            getString(R.string.viewer_share),
            getString(R.string.viewer_open_with),
            getString(R.string.viewer_delete)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_capture)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareCapture(uri)
                    1 -> openExternally(uri)
                    else -> confirmDelete(uri)
                }
            }
            .show()
    }

    private fun shareCapture(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(contentResolver, "capture", uri)
        try {
            startActivity(Intent.createChooser(send, getString(R.string.viewer_share)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternally(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_delete)
            .setMessage(R.string.dialog_body_delete)
            .setPositiveButton(R.string.btn_delete) { _, _ -> deleteCapture(uri) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun deleteCapture(uri: Uri) {
        val localPath = intent.getStringExtra(EXTRA_LOCAL_PATH)
        val deleted = try {
            when {
                localPath != null -> File(localPath).delete()
                contentResolver.delete(uri, null, null) > 0 -> true
                else -> DocumentFile.fromSingleUri(this, uri)?.delete() == true
            }
        } catch (e: Exception) {
            false
        }

        if (deleted) {
            Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, R.string.toast_delete_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMetadata(uri: Uri) {
        scope.launch {
            val details = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val exif = ExifInterface(input)
                        val latLong = FloatArray(2)
                        if (!exif.getLatLong(latLong)) return@use null
                        Metadata(
                            latLong = latLong,
                            altitude = exif.getAltitude(Double.NaN),
                            taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: return@launch

            val text = buildString {
                append(
                    getString(
                        R.string.viewer_coords_format,
                        String.format(Locale.US, "%.6f", details.latLong[0]),
                        String.format(Locale.US, "%.6f", details.latLong[1])
                    )
                )
                if (!details.altitude.isNaN()) {
                    append('\n')
                    append(getString(R.string.meta_alt, "${details.altitude.roundToInt()} m"))
                }
                if (!details.taken.isNullOrBlank()) {
                    append('\n')
                    append(formatExifTime(details.taken))
                }
            }
            txtMeta.text = text
            txtMeta.visibility = View.VISIBLE
        }
    }

    private fun formatExifTime(raw: String): String {
        val parsed = runCatching {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)
        }.getOrNull() ?: return raw
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(parsed)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Metadata(
        val latLong: FloatArray,
        val altitude: Double,
        val taken: String?
    )

    companion object {
        const val EXTRA_LOCAL_PATH = "local_path"
    }
}
