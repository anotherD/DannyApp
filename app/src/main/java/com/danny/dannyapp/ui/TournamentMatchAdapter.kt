package com.danny.dannyapp.ui

import android.content.res.ColorStateList
import android.graphics.Color
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
import com.danny.dannyapp.data.db.TournamentMatch
import com.google.android.material.card.MaterialCardView

data class MatchUiModel(
    val match: TournamentMatch,
    val player1Name: String,
    val player1Flag: String,
    val player1Skill: Int,
    val player2Name: String,
    val player2Flag: String,
    val player2Skill: Int,
    val isUserMatch: Boolean,
    val isCompleted: Boolean,
    val winnerId: Long?,
    val scoreText: String,
    val roundDisplayName: String
)

class TournamentMatchAdapter(
    private val onMatchClick: (MatchUiModel) -> Unit
) : ListAdapter<MatchUiModel, TournamentMatchAdapter.ViewHolder>(MatchDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMatch: MaterialCardView = view.findViewById(R.id.cardMatch)
        val tvMatchRoundIndex: TextView = view.findViewById(R.id.tvMatchRoundIndex)
        val tvMatchStatusBadge: TextView = view.findViewById(R.id.tvMatchStatusBadge)
        val tvPlayer1Flag: TextView = view.findViewById(R.id.tvPlayer1Flag)
        val tvPlayer1Name: TextView = view.findViewById(R.id.tvPlayer1Name)
        val tvPlayer1Score: TextView = view.findViewById(R.id.tvPlayer1Score)
        val tvPlayer2Flag: TextView = view.findViewById(R.id.tvPlayer2Flag)
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
        holder.tvMatchRoundIndex.text = "${groupPrefix}Match #${match.matchIndex + 1}"

        holder.tvPlayer1Flag.text = item.player1Flag
        holder.tvPlayer1Name.text = item.player1Name

        holder.tvPlayer2Flag.text = item.player2Flag
        holder.tvPlayer2Name.text = item.player2Name

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

            holder.tvMatchStatusBadge.visibility = View.VISIBLE
            holder.tvMatchStatusBadge.text = "Beendet"
            holder.tvMatchStatusBadge.setBackgroundResource(R.drawable.bg_skill_badge)
        } else {
            holder.tvPlayer1Score.text = "-"
            holder.tvPlayer2Score.text = "-"
            holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)

            if (item.isUserMatch) {
                holder.tvMatchStatusBadge.visibility = View.VISIBLE
                holder.tvMatchStatusBadge.text = "DEIN MATCH"
                holder.tvMatchStatusBadge.setBackgroundResource(R.drawable.bg_major_badge)
            } else {
                holder.tvMatchStatusBadge.visibility = View.GONE
            }
        }

        if (item.isUserMatch && !item.isCompleted) {
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
