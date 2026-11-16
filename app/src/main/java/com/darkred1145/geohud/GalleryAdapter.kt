package com.darkred1145.geohud

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A capture that can live in MediaStore, a SAF tree, or app-specific storage. */
sealed interface GalleryItem {
    val dateMillis: Long

    data class Media(val uri: Uri, override val dateMillis: Long) : GalleryItem
    data class Local(val file: File, override val dateMillis: Long) : GalleryItem
}

class GalleryAdapter(
    private val images: List<GalleryItem>,
    private val onClick: (GalleryItem) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

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
        val source: Any = when (item) {
            is GalleryItem.Media -> item.uri
            is GalleryItem.Local -> item.file
        }

        // Use Coil to load image efficiently
        holder.imgThumbnail.load(source) {
            crossfade(true)
        }

        holder.txtDate.text = if (item.dateMillis > 0L) {
            dateFormat.format(Date(item.dateMillis))
        } else {
            holder.itemView.context.getString(R.string.date_unknown)
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = images.size
}
