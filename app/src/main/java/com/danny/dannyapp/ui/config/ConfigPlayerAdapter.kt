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
            val heightPx = (holder.tvPlayerName.textSize * 1.17f).toInt()
            val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
            } else {
                (heightPx * 1.5f).toInt()
            }
            drawable.setBounds(0, 0, widthPx, heightPx)
            // Verwenden eines angepassten ImageSpan mit vertikalem Offset (etwas tiefer)
            val imageSpan = VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
            spanBuilder.setSpan(imageSpan, spanBuilder.length - 1, spanBuilder.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            holder.tvPlayerName.text = spanBuilder
        } else {
            val flagEmoji = Country.getFlagEmoji(player.countryCode)
            holder.tvPlayerName.text = "${player.name} $flagEmoji"
        }
        holder.tvPlayerSkill.text = context.getString(R.string.player_skill_format, player.skillLevel)
        
        // 10-stufiges Farbschema für Level 1 bis 10 (Dark-Mode optimiert)
        val skillColor = getSkillColor(player.skillLevel)
        holder.tvPlayerSkill.setTextColor(skillColor)
        (holder.itemView as? com.google.android.material.card.MaterialCardView)?.strokeColor = skillColor

        holder.tvPlayerTourCard.text = if (player.hasTourCard) {
            context.getString(R.string.player_tour_card_yes)
        } else {
            context.getString(R.string.player_tour_card_no)
        }
    }

    private fun getSkillColor(level: Int): Int {
        val colors = intArrayOf(
            android.graphics.Color.parseColor("#4FC3F7"), // Level 1: Cyan
            android.graphics.Color.parseColor("#26A69A"), // Level 2: Teal
            android.graphics.Color.parseColor("#66BB6A"), // Level 3: Green
            android.graphics.Color.parseColor("#9CCC65"), // Level 4: Light Green
            android.graphics.Color.parseColor("#FFEE58"), // Level 5: Yellow
            android.graphics.Color.parseColor("#FFA726"), // Level 6: Orange
            android.graphics.Color.parseColor("#FF7043"), // Level 7: Deep Orange
            android.graphics.Color.parseColor("#FF1744"), // Level 8: Vivid Red (Stärkerer Kontrast zu Orange)
            android.graphics.Color.parseColor("#AB47BC"), // Level 9: Purple
            android.graphics.Color.parseColor("#FFD700")  // Level 10: Gold
        )
        val index = (level - 1).coerceIn(0, colors.size - 1)
        return colors[index]
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
            // y ist die Baseline des Textes. Wir verschieben das Bild um verticalOffsetPx nach unten.
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
