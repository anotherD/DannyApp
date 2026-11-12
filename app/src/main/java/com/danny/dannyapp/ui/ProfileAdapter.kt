package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.db.UserProfile

class ProfileAdapter(
    private val onItemClick: (UserProfile) -> Unit,
    private val onItemLongClick: (UserProfile) -> Unit
) : ListAdapter<UserProfile, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback) {

    class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProfileName: TextView = view.findViewById(R.id.tvProfileName)
        val tvPlayerNameAndFlag: TextView = view.findViewById(R.id.tvPlayerNameAndFlag)
        val tvProfileDescription: TextView = view.findViewById(R.id.tvProfileDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = getItem(position)
        holder.tvProfileName.text = profile.name

        val flagEmoji = Country.getFlagEmoji(profile.countryCode)
        holder.tvPlayerNameAndFlag.text = "$flagEmoji ${profile.playerName}"

        if (profile.description.isNotBlank()) {
            holder.tvProfileDescription.text = profile.description
            holder.tvProfileDescription.visibility = View.VISIBLE
        } else {
            holder.tvProfileDescription.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onItemClick(profile) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(profile)
            true
        }
    }

    private object ProfileDiffCallback : DiffUtil.ItemCallback<UserProfile>() {
        override fun areItemsTheSame(oldItem: UserProfile, newItem: UserProfile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserProfile, newItem: UserProfile): Boolean {
            return oldItem == newItem
        }
    }
}
