package com.danny.dannyapp.ui.tournament

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.card.MaterialCardView

class TournamentMatchAdapter(
    private val onMatchClick: (MatchUiModel) -> Unit
) : ListAdapter<MatchUiModel, TournamentMatchAdapter.ViewHolder>(MatchDiffCallback) {

    var qualifierBoardCount: Int = 1

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
            com.danny.dannyapp.domain.enums.TournamentRound.QUAL_R3.name,
            com.danny.dannyapp.domain.enums.TournamentRound.QUAL_FINAL.name
        )

        if (isQualifierRound) {
            val boardCount = qualifierBoardCount.coerceAtLeast(1)
            val matchesPerBoard = (currentList.size / boardCount).coerceAtLeast(1)
            if (matchesPerBoard > 0 && match.matchIndex % matchesPerBoard == 0) {
                val boardIdx = match.matchIndex / matchesPerBoard
                holder.layoutBoardHeader.visibility = View.VISIBLE
                holder.tvBoardHeader.text = context.getString(R.string.bracket_board_format, boardIdx + 1)
            } else {
                holder.layoutBoardHeader.visibility = View.GONE
            }
        } else {
            holder.layoutBoardHeader.visibility = View.GONE
        }

        holder.tvMatchRoundIndex.text = TournamentUiHelper.formatMatchBadgeText(context, match)

        UIHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1)
        UIHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2, isLeft = false)

        UIHelper.applyMatchCardStyle(
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
