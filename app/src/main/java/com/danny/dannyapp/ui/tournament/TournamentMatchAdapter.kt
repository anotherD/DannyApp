package com.danny.dannyapp.ui.tournament

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.ui.UiHelper
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
        val layoutBoardHeader: View = view.findViewById(R.id.layoutBoardHeader)
        val tvBoardHeader: TextView = view.findViewById(R.id.tvBoardHeader)
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

        val roundKey = match.round
        val isQualifierRound = roundKey in listOf(
            TournamentRound.QUAL_R1.name,
            TournamentRound.QUAL_R2.name,
            TournamentRound.QUAL_FINAL.name
        )

        if (roundKey == TournamentRound.QUAL_PRELIM.name) {
            val targetBoard = item.targetBoard
            val prevTargetBoard = if (position > 0) getItem(position - 1).targetBoard else null
            if (targetBoard != null && (position == 0 || targetBoard != prevTargetBoard)) {
                holder.layoutBoardHeader.visibility = View.VISIBLE
                holder.tvBoardHeader.text = context.getString(R.string.bracket_board_format, targetBoard)
            } else {
                holder.layoutBoardHeader.visibility = View.GONE
            }
        } else {
            holder.layoutBoardHeader.visibility = View.GONE
        }

        if (match.groupName != null) {
            holder.tvMatchRoundIndex.text = context.getString(
                R.string.tournament_match_group_index_format,
                match.groupName,
                match.matchIndex + 1
            )
        } else {
            holder.tvMatchRoundIndex.text = context.getString(
                R.string.tournament_match_index_format,
                match.matchIndex + 1
            )
        }

        UiHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1)
        UiHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2)

        if (match.isCompleted) {
            holder.tvPlayer1Score.text = match.player1Score.toString()
            holder.tvPlayer2Score.text = match.player2Score.toString()

            if (match.winnerId == item.player1.id) {
                holder.tvPlayer1Name.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.tvPlayer1Score.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.tvPlayer2Name.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.tvPlayer2Score.setTypeface(null, android.graphics.Typeface.NORMAL)
            } else if (match.winnerId == item.player2.id) {
                holder.tvPlayer2Name.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.tvPlayer2Score.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.tvPlayer1Name.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.tvPlayer1Score.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        } else {
            holder.tvPlayer1Score.text = "-"
            holder.tvPlayer2Score.text = "-"
            holder.tvPlayer1Name.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvPlayer1Score.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvPlayer2Name.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvPlayer2Score.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        if (item.isUserMatch) {
            holder.cardMatch.setCardBackgroundColor(context.getColor(R.color.rot_container_dunkel))
            holder.cardMatch.strokeColor = context.getColor(R.color.rot_haupt)
            holder.cardMatch.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        } else {
            holder.cardMatch.setCardBackgroundColor(context.getColor(R.color.anthrazit_flaeche))
            holder.cardMatch.strokeColor = context.getColor(R.color.grau_rahmen_dunkel)
            holder.cardMatch.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
        }

        holder.cardMatch.setOnClickListener {
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
