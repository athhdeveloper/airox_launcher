package com.lib.airox.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lib.airox.launcher.databinding.ItemAppGridBinding
import com.lib.airox.launcher.model.AppInfo

/**
 * Draggable grid adapter for displaying apps with drag and drop support
 * Enhanced with long-press support for Infinity X style
 */
class DraggableAppGridAdapter(
    private var apps: MutableList<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongPress: (AppInfo, View) -> Unit,
    private val onAppMove: (Int, Int) -> Unit,
    private val iconSize: Int = 80
) : RecyclerView.Adapter<DraggableAppGridAdapter.AppGridViewHolder>() {

    private var isDragging = false

    class AppGridViewHolder(val binding: ItemAppGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppGridViewHolder {
        val binding = ItemAppGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppGridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppGridViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.appName.text = app.name
        holder.binding.appIcon.setImageDrawable(app.icon)



        holder.itemView.setOnClickListener {
            if (!isDragging) {
                onAppClick(app)
            }
        }

        holder.itemView.setOnLongClickListener {
            // Only show menu if not dragging
            if (!isDragging) {
                onAppLongPress(app, holder.itemView)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = apps.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        isDragging = true
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                val temp = apps[i]
                apps[i] = apps[i + 1]
                apps[i + 1] = temp
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                val temp = apps[i]
                apps[i] = apps[i - 1]
                apps[i - 1] = temp
            }
        }
        onAppMove(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }

    fun addApp(app: AppInfo, position: Int) {
        apps.add(position.coerceIn(0, apps.size), app)
        notifyItemInserted(position)
    }

    fun removeApp(position: Int): AppInfo? {
        if (position in 0 until apps.size) {
            val app = apps.removeAt(position)
            notifyItemRemoved(position)
            return app
        }
        return null
    }

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }

    fun getApps(): List<AppInfo> = apps.toList()
    
    fun getAppAt(position: Int): AppInfo? {
        return if (position in 0 until apps.size) apps[position] else null
    }
}
