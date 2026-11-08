package com.danny.dannyapp.ui.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.db.Player

class ConfigPlayerAdapter : ListAdapter<Player, ConfigPlayerAdapter.PlayerViewHolder>(PlayerDiffCallback) {

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPlayerSkill: TextView = view.findViewById(R.id.tvPlayerSkill)
        val tvPlayerTourCard: TextView = view.findViewById(R.id.tvPlayerTourCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_config_player, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val player = getItem(position)
        val context = holder.itemView.context
        val flagResId = Country.getFlagResId(player.countryCode)
        if (flagResId != null) {
            // Sonderfall für Nordirland (NIR): Da Android keine native Emoji-Flagge dafür hat,
            // wird ein eigenes Flaggen-Bild per ImageSpan inline hinter dem Namen gerendert.
            val spanBuilder = android.text.SpannableStringBuilder("${player.name}  ")
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, flagResId)!!
            val heightPx = (holder.tvPlayerName.textSize * 1.1f).toInt()
            val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
            } else {
                (heightPx * 1.5f).toInt()
            }
            drawable.setBounds(0, 0, widthPx, heightPx)
            val imageSpan = android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_BASELINE)
            spanBuilder.setSpan(imageSpan, spanBuilder.length - 1, spanBuilder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.tvPlayerName.text = spanBuilder
        } else {
            val flagEmoji = Country.getFlagEmoji(player.countryCode)
            holder.tvPlayerName.text = "${player.name} $flagEmoji"
        }
        holder.tvPlayerSkill.text = context.getString(R.string.player_skill_format, player.skillLevel)
        holder.tvPlayerTourCard.text = if (player.hasTourCard) {
            context.getString(R.string.player_tour_card_yes)
        } else {
            context.getString(R.string.player_tour_card_no)
        }
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
