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
        val flagResId = Country.getFlagResId(player.countryCode)
        if (flagResId != null) {
            val spanBuilder = android.text.SpannableStringBuilder("   ${player.name}")
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, flagResId)!!
            val heightPx = (holder.tvPlayerName.textSize * 1.17f).toInt()
            val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
            } else {
                (heightPx * 1.5f).toInt()
            }
            drawable.setBounds(0, 0, widthPx, heightPx)
            val imageSpan = VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
            spanBuilder.setSpan(imageSpan, 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.tvPlayerName.text = spanBuilder
        } else {
            val flagEmoji = Country.getFlagEmoji(player.countryCode)
            holder.tvPlayerName.text = "$flagEmoji ${player.name}"
        }
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

    private class VerticalCenteredImageSpan(
        drawable: android.graphics.drawable.Drawable,
        private val verticalOffsetPx: Int = 3
    ) : android.text.style.ImageSpan(drawable, ALIGN_BASELINE) {
        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint
        ) {
            val b = drawable
            canvas.save()
            canvas.translate(x, (y - b.bounds.height() + verticalOffsetPx).toFloat())
            b.draw(canvas)
            canvas.restore()
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
