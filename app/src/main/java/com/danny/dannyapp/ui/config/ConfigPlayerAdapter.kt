package com.danny.dannyapp.ui.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Player

class ConfigPlayerAdapter : ListAdapter<Player, ConfigPlayerAdapter.PlayerViewHolder>(PlayerDiffCallback) {

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPlayerSkill: TextView = view.findViewById(R.id.tvPlayerSkill)
        val tvPlayerTourCard: TextView = view.findViewById(R.id.tvPlayerTourCard)
        val tvPlayerCountry: TextView = view.findViewById(R.id.tvPlayerCountry)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = getItem(position)
        val context = holder.itemView.context
        holder.tvPlayerName.text = player.name
        holder.tvPlayerSkill.text = context.getString(R.string.player_skill_format, player.skillLevel)
        holder.tvPlayerTourCard.text = if (player.hasTourCard) {
            context.getString(R.string.player_tour_card_yes)
        } else {
            context.getString(R.string.player_tour_card_no)
        }
        holder.tvPlayerCountry.text = context.getString(R.string.player_country_format, player.countryCode)
    }

    private object PlayerDiffCallback : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(oldItem: Player, newItem: Player): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Player, newItem: Player): Boolean {
            return oldItem == newItem
        }
    }
}
