package io.github.domi04151309.alwayson.adapters

import android.graphics.drawable.Icon
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import io.github.domi04151309.alwayson.R

class NotificationGridAdapter(private val itemArray: ArrayList<Icon>) : RecyclerView.Adapter<NotificationGridAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.notification_grid_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.view.findViewById<ImageView>(R.id.drawable).setImageIcon(itemArray[position])
    }

    override fun getItemCount() = itemArray.size
}