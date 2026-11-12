package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.google.android.material.card.MaterialCardView

class OrderOfMeritAdapter : ListAdapter<OrderOfMeritEntry, OrderOfMeritAdapter.OomViewHolder>(OomDiffCallback) {

    class OomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardOomItem)
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPrizeMoney: TextView = view.findViewById(R.id.tvPrizeMoney)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_of_merit, parent, false)
        return OomViewHolder(view)
    }

    override fun onBindViewHolder(holder: OomViewHolder, position: Int) {
        val entry = getItem(position)
        holder.tvRank.text = "#${entry.rank}"

        val context = holder.itemView.context
        val flagResId = Country.getFlagResId(entry.countryCode)
        if (flagResId != null) {
            val spanBuilder = android.text.SpannableStringBuilder("   ${entry.name}")
            val drawable = ContextCompat.getDrawable(context, flagResId)!!
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
            val flagEmoji = Country.getFlagEmoji(entry.countryCode)
            holder.tvPlayerName.text = "$flagEmoji ${entry.name}"
        }

        holder.tvPrizeMoney.text = "£%,.0f".format(entry.prizeMoney)

        if (entry.isUser) {
            holder.card.setCardBackgroundColor(context.getColor(R.color.rot_container_dunkel))
            holder.tvPlayerName.setTextColor(context.getColor(R.color.rot_sehr_hell))
        } else {
            holder.card.setCardBackgroundColor(context.getColor(R.color.anthrazit_flaeche))
            holder.tvPlayerName.setTextColor(context.getColor(R.color.grau_text_hell))
        }
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

    private object OomDiffCallback : DiffUtil.ItemCallback<OrderOfMeritEntry>() {
        override fun areItemsTheSame(oldItem: OrderOfMeritEntry, newItem: OrderOfMeritEntry): Boolean {
            return oldItem.playerId == newItem.playerId
        }

        override fun areContentsTheSame(oldItem: OrderOfMeritEntry, newItem: OrderOfMeritEntry): Boolean {
            return oldItem == newItem
        }
    }
}
