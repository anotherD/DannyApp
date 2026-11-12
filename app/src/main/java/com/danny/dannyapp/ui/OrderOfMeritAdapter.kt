package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.google.android.material.card.MaterialCardView

class OrderOfMeritAdapter : ListAdapter<OrderOfMeritEntry, OrderOfMeritAdapter.OomViewHolder>(OomDiffCallback) {

    class OomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardOomItem)
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvCountry: TextView = view.findViewById(R.id.tvCountry)
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
        holder.tvCountry.text = entry.countryCode
        holder.tvPlayerName.text = entry.name
        holder.tvPrizeMoney.text = "£%,.0f".format(entry.prizeMoney)

        val context = holder.itemView.context
        if (entry.isUser) {
            holder.card.setCardBackgroundColor(context.getColor(R.color.rot_container_dunkel))
            holder.tvPlayerName.setTextColor(context.getColor(R.color.rot_sehr_hell))
        } else {
            holder.card.setCardBackgroundColor(context.getColor(R.color.anthrazit_flaeche))
            holder.tvPlayerName.setTextColor(context.getColor(R.color.grau_text_hell))
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
