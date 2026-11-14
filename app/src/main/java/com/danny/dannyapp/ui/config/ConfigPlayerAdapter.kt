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
import com.danny.dannyapp.ui.UiHelper

class ConfigPlayerAdapter : ListAdapter<Player, ConfigPlayerAdapter.PlayerViewHolder>(PlayerDiffCallback) {

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPlayerSkill: TextView = view.findViewById(R.id.tvPlayerSkill)
        val layoutTourCards: View = view.findViewById(R.id.layoutTourCards)
        val tvTourCardSecond: View = view.findViewById(R.id.tvTourCardSecond)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = getItem(position)
        val context = holder.itemView.context

        UiHelper.formatFlagAndText(holder.tvPlayerName, player.countryCode, player.name)

        holder.tvPlayerSkill.text = "L${player.skill}"
        
        val skillColor = getSkillColor(context, player.skill)
        holder.tvPlayerSkill.setTextColor(skillColor)
        (holder.itemView as? com.google.android.material.card.MaterialCardView)?.strokeColor = skillColor

        if (player.hasTourCard) {
            holder.layoutTourCards.visibility = View.VISIBLE
            if (player.tourCardYear == 1) {
                holder.tvTourCardSecond.visibility = View.VISIBLE
            } else {
                holder.tvTourCardSecond.visibility = View.GONE
            }
        } else {
            holder.layoutTourCards.visibility = View.GONE
        }
    }

    private fun getSkillColor(context: android.content.Context, level: Int): Int {
        val resId = when (level.coerceIn(1, 10)) {
            1 -> R.color.skill_level_1
            2 -> R.color.skill_level_2
            3 -> R.color.skill_level_3
            4 -> R.color.skill_level_4
            5 -> R.color.skill_level_5
            6 -> R.color.skill_level_6
            7 -> R.color.skill_level_7
            8 -> R.color.skill_level_8
            9 -> R.color.skill_level_9
            else -> R.color.skill_level_10
        }
        return androidx.core.content.ContextCompat.getColor(context, resId)
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
