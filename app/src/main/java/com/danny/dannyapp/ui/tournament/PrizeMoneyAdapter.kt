package com.danny.dannyapp.ui.tournament

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.model.PrizeMoneyItem

class PrizeMoneyAdapter : ListAdapter<PrizeMoneyItem, PrizeMoneyAdapter.ViewHolder>(PrizeMoneyDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPrizeRoundName: TextView = view.findViewById(R.id.tvPrizeRoundName)
        val tvPrizeMatchDistance: TextView = view.findViewById(R.id.tvPrizeMatchDistance)
        val tvPrizeMoney: TextView = view.findViewById(R.id.tvPrizeMoney)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prize_money, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvPrizeRoundName.text = item.roundName
        holder.tvPrizeMatchDistance.text = item.matchDistanceText
        holder.tvPrizeMoney.text = item.prizeMoneyText
    }

    private object PrizeMoneyDiffCallback : DiffUtil.ItemCallback<PrizeMoneyItem>() {
        override fun areItemsTheSame(oldItem: PrizeMoneyItem, newItem: PrizeMoneyItem): Boolean {
            return oldItem.roundName == newItem.roundName
        }

        override fun areContentsTheSame(oldItem: PrizeMoneyItem, newItem: PrizeMoneyItem): Boolean {
            return oldItem == newItem
        }
    }
}
