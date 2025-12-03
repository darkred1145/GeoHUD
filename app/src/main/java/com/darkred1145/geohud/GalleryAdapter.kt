package com.darkred1145.geohud

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class GalleryAdapter(
    private val images: List<Any>, // Can be File or Uri
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
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

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = images.size
}