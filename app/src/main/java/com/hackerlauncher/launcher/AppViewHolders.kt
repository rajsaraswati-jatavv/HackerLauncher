package com.hackerlauncher.launcher

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.app_icon)
    val label: TextView = itemView.findViewById(R.id.app_label)
    val badge: TextView = itemView.findViewById(R.id.notification_badge)
    val checkbox: CheckBox = itemView.findViewById(R.id.app_checkbox)
}

class AppListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.app_icon)
    val name: TextView = itemView.findViewById(R.id.app_name)
    val pkg: TextView = itemView.findViewById(R.id.app_package)
    val size: TextView = itemView.findViewById(R.id.app_size)
    val badge: TextView = itemView.findViewById(R.id.notification_badge)
    val checkbox: CheckBox = itemView.findViewById(R.id.app_checkbox)
}

class AppDiffCallback(
    private val oldList: List<AppInfo>,
    private val newList: List<AppInfo>
) : androidx.recyclerview.widget.DiffUtil.Callback() {

    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos].packageName == newList[newPos].packageName &&
               oldList[oldPos].activityName == newList[newPos].activityName
    }

    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
        return oldList[oldPos] == newList[newPos]
    }
}
