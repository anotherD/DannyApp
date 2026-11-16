package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
            com.danny.dannyapp.domain.enums.TournamentRound.QUAL_R1.name,
            com.danny.dannyapp.domain.enums.TournamentRound.QUAL_R2.name,
            com.danny.dannyapp.domain.enums.TournamentRound.QUAL_FINAL.name
        )

        if (roundKey == com.danny.dannyapp.domain.enums.TournamentRound.QUAL_PRELIM.name) {
            val targetBoard = item.targetBoard
            val prevTargetBoard = if (position > 0) getItem(position - 1).targetBoard else null
            if (targetBoard != null && (position == 0 || targetBoard != prevTargetBoard)) {
                holder.layoutBoardHeader.visibility = View.VISIBLE
                holder.tvBoardHeader.text = context.getString(R.string.bracket_board_format, targetBoard + 1)
            } else {
                holder.layoutBoardHeader.visibility = View.GONE
            }
        } else if (isQualifierRound) {
            val matchesPerBoard = when (roundKey) {
                com.danny.dannyapp.domain.enums.TournamentRound.QUAL_R1.name -> 4
                com.danny.dannyapp.domain.enums.TournamentRound.QUAL_R2.name -> 2
                com.danny.dannyapp.domain.enums.TournamentRound.QUAL_FINAL.name -> 1
                else -> 4
            }
            if (match.matchIndex % matchesPerBoard == 0) {
                val boardIdx = match.matchIndex / matchesPerBoard
                holder.layoutBoardHeader.visibility = View.VISIBLE
                holder.tvBoardHeader.text = context.getString(R.string.bracket_board_format, boardIdx + 1)
            } else {
                holder.layoutBoardHeader.visibility = View.GONE
            }
        } else {
            holder.layoutBoardHeader.visibility = View.GONE
        }

        if (!match.groupName.isNullOrBlank()) {
            holder.tvMatchRoundIndex.text = context.getString(R.string.tournament_match_group_index_format, match.groupName, match.matchIndex + 1)
        } else {
            holder.tvMatchRoundIndex.text = context.getString(R.string.tournament_match_index_format, match.matchIndex + 1)
        }

        UiHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1, isLeft = true)
        UiHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2, isLeft = false)

        UiHelper.applyMatchCardStyle(
            card = holder.cardMatch,
            item = item,
            player1Name = holder.tvPlayer1Name,
            player1Score = holder.tvPlayer1Score,
            player2Name = holder.tvPlayer2Name,
            player2Score = holder.tvPlayer2Score,
            matchIndexBadge = holder.tvMatchRoundIndex
        )

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
