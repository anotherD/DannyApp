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
        val tvCareerSeason: TextView = view.findViewById(R.id.tvCareerSeason)
        val tvCareerProfile: TextView = view.findViewById(R.id.tvCareerProfile)
        val tvCareerPlayer: TextView = view.findViewById(R.id.tvCareerPlayer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CareerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_career, parent, false)
        return CareerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CareerViewHolder, position: Int) {
        val careerWithProfile = getItem(position)
        val career = careerWithProfile.career
        val context = holder.itemView.context
        holder.tvCareerName.text = career.name
        holder.tvCareerLevel.text = context.getString(R.string.career_level_format, career.level)
        holder.tvCareerSeason.text = context.getString(R.string.season_format, career.season)
        if (!careerWithProfile.profileName.isNullOrBlank()) {
            holder.tvCareerProfile.text = context.getString(R.string.career_profile_format, careerWithProfile.profileName)
            holder.tvCareerProfile.visibility = View.VISIBLE
        } else {
            holder.tvCareerProfile.visibility = View.GONE
        }
        if (!careerWithProfile.playerName.isNullOrBlank()) {
            val countryCode = careerWithProfile.countryCode ?: "DEU"
            TournamentUiHelper.formatFlagAndText(context, holder.tvCareerPlayer, countryCode, careerWithProfile.playerName)
            holder.tvCareerPlayer.visibility = View.VISIBLE
        } else {
            holder.tvCareerPlayer.visibility = View.GONE
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
