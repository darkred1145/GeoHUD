package com.darkred1145.geohud

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryAdapter(
    private val images: List<Any>, // Can be File or Uri
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = images[position]

        // Use Coil to load image efficiently
        holder.imgThumbnail.load(item) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
        }

        holder.txtDate.text = formatDate(holder.itemView.context, item)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatDate(context: Context, item: Any): String {
        val millis: Long = when (item) {
            is File -> item.lastModified()
            is Uri -> retrieveDateTaken(context, item)
                ?: DocumentFile.fromSingleUri(context, item)?.lastModified()
                ?: 0L
            else -> 0L
        }
        return if (millis > 0L) {
            SimpleDateFormat("MMM dd", Locale.US).format(Date(millis))
        } else {
            context.getString(R.string.date_unknown)
        }
    }

    private fun retrieveDateTaken(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATE_TAKEN),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0).takeIf { it > 0L }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun getItemCount() = images.size
}