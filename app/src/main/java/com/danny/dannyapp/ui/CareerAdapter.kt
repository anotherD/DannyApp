package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.CareerWithProfile

class CareerAdapter(
    private val onItemClick: (CareerWithProfile) -> Unit,
    private val onItemLongClick: (CareerWithProfile) -> Unit
) : ListAdapter<CareerWithProfile, CareerAdapter.CareerViewHolder>(CareerDiffCallback) {

    class CareerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCareerName: TextView = view.findViewById(R.id.tvCareerName)
        val tvCareerLevel: TextView = view.findViewById(R.id.tvCareerLevel)
        val tvCareerProfile: TextView = view.findViewById(R.id.tvCareerProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CareerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_career, parent, false)
        return CareerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CareerViewHolder, position: Int) {
        val careerWithProfile = getItem(position)
        val career = careerWithProfile.career
        holder.tvCareerName.text = career.name
        holder.tvCareerLevel.text = holder.itemView.context.getString(R.string.career_level_format, career.level)
        if (!careerWithProfile.profileName.isNullOrBlank()) {
            holder.tvCareerProfile.text = holder.itemView.context.getString(R.string.career_profile_format, careerWithProfile.profileName)
            holder.tvCareerProfile.visibility = View.VISIBLE
        } else {
            holder.tvCareerProfile.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onItemClick(careerWithProfile) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(careerWithProfile)
            true
        }
    }

    private object CareerDiffCallback : DiffUtil.ItemCallback<CareerWithProfile>() {
        override fun areItemsTheSame(oldItem: CareerWithProfile, newItem: CareerWithProfile): Boolean {
            return oldItem.career.id == newItem.career.id
        }

        override fun areContentsTheSame(oldItem: CareerWithProfile, newItem: CareerWithProfile): Boolean {
            return oldItem == newItem
        }
    }
}
