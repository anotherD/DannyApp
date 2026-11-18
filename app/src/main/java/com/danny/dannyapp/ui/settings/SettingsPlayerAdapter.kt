package com.danny.dannyapp.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.ui.UiHelper
import com.danny.dannyapp.ui.toColorResId

/**
 * RecyclerView adapter for displaying players in the settings configuration screen.
 */
class SettingsPlayerAdapter : ListAdapter<Player, SettingsPlayerAdapter.PlayerViewHolder>(PlayerDiffCallback) {

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
        val skillLevel = SkillLevel.fromValue(level)
        return ContextCompat.getColor(context, skillLevel.toColorResId())
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
