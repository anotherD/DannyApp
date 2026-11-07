package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Career

class CareerAdapter(
    private val onItemClick: (Career) -> Unit,
    private val onItemLongClick: (Career) -> Unit
) : ListAdapter<Career, CareerAdapter.CareerViewHolder>(CareerDiffCallback) {

    class CareerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCareerName: TextView = view.findViewById(R.id.tvCareerName)
        val tvCareerLevel: TextView = view.findViewById(R.id.tvCareerLevel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CareerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_career, parent, false)
        return CareerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CareerViewHolder, position: Int) {
        val career = getItem(position)
        holder.tvCareerName.text = career.name
        holder.tvCareerLevel.text = holder.itemView.context.getString(R.string.career_level_format, career.level)
        holder.itemView.setOnClickListener { onItemClick(career) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(career)
            true
        }
    }

    private object CareerDiffCallback : DiffUtil.ItemCallback<Career>() {
        override fun areItemsTheSame(oldItem: Career, newItem: Career): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Career, newItem: Career): Boolean {
            return oldItem == newItem
        }
    }
}
