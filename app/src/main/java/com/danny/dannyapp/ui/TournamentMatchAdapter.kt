package com.danny.dannyapp.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.card.MaterialCardView

class TournamentMatchAdapter(
    private val onMatchClick: (MatchUiModel) -> Unit
) : ListAdapter<MatchUiModel, TournamentMatchAdapter.ViewHolder>(MatchDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMatch: MaterialCardView = view.findViewById(R.id.cardMatch)
        val tvMatchRoundIndex: TextView = view.findViewById(R.id.tvMatchRoundIndex)
        val tvPlayer1Name: TextView = view.findViewById(R.id.tvPlayer1Name)
        val tvPlayer1Score: TextView = view.findViewById(R.id.tvPlayer1Score)
        val tvPlayer2Name: TextView = view.findViewById(R.id.tvPlayer2Name)
        val tvPlayer2Score: TextView = view.findViewById(R.id.tvPlayer2Score)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tournament_match, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val match = item.match
        val context = holder.itemView.context

        val groupPrefix = if (!match.groupName.isNullOrBlank()) "Gruppe ${match.groupName} • " else ""
        holder.tvMatchRoundIndex.text = "${groupPrefix}#${match.matchIndex + 1}"

        UiHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1, isLeft = true)
        UiHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2, isLeft = false)

        if (item.isCompleted) {
            holder.tvPlayer1Score.text = match.player1Score.toString()
            holder.tvPlayer2Score.text = match.player2Score.toString()

            if (item.winnerId == match.player1Id) {
                holder.tvPlayer1Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Score.setTypeface(null, Typeface.NORMAL)
            } else if (item.winnerId == match.player2Id) {
                holder.tvPlayer2Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Score.setTypeface(null, Typeface.NORMAL)
            } else {
                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
            }
        } else {
            holder.tvPlayer1Score.text = "-"
            holder.tvPlayer2Score.text = "-"
            holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
        }

        if (item.isCompleted) {
            holder.cardMatch.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
        } else {
            holder.cardMatch.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_grau_heller))
        }

        if (item.isUserMatch) {
            holder.cardMatch.strokeColor = ContextCompat.getColor(context, R.color.rot_hell)
            holder.cardMatch.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        } else {
            holder.cardMatch.strokeColor = ContextCompat.getColor(context, R.color.anthrazit_container_hoechst)
            holder.cardMatch.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
        }

        holder.itemView.setOnClickListener {
            onMatchClick(item)
        }
    }

    private object MatchDiffCallback : DiffUtil.ItemCallback<MatchUiModel>() {
        override fun areItemsTheSame(oldItem: MatchUiModel, newItem: MatchUiModel): Boolean {
            return oldItem.match.id == newItem.match.id
        }

        override fun areContentsTheSame(oldItem: MatchUiModel, newItem: MatchUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
