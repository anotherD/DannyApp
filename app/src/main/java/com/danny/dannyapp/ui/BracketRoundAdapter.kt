package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.card.MaterialCardView

/**
 * ViewPager2 adapter displaying tournament rounds, where each page represents a single round.
 *
 * @param onMatchClick Callback invoked when a match item in any round is clicked.
 */
class BracketRoundAdapter(
    private val onMatchClick: (MatchUiModel) -> Unit
) : RecyclerView.Adapter<BracketRoundAdapter.RoundViewHolder>() {

    private val roundMatchesMap = mutableMapOf<Int, List<MatchUiModel>>()
    private var totalRounds: Int = 0

    /**
     * Updates the number of rounds and matches for the specified round index.
     */
    fun setRoundMatches(roundIndex: Int, matches: List<MatchUiModel>, count: Int) {
        val countChanged = totalRounds != count
        totalRounds = count
        roundMatchesMap[roundIndex] = matches
        if (countChanged) {
            notifyDataSetChanged()
        } else {
            notifyItemChanged(roundIndex)
        }
    }

    /**
     * Smoothly scrolls the specified round's match list to center the match at [matchIndex].
     */
    fun scrollToMatch(viewPager: ViewPager2, roundIndex: Int, matchIndex: Int) {
        val pagerRv = viewPager.getChildAt(0) as? RecyclerView
        val roundViewHolder = pagerRv?.findViewHolderForAdapterPosition(roundIndex) as? RoundViewHolder
        roundViewHolder?.scrollToMatch(matchIndex)
    }

    override fun getItemCount(): Int = totalRounds

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoundViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bracket_round_page, parent, false)
        return RoundViewHolder(view, onMatchClick)
    }

    override fun onBindViewHolder(holder: RoundViewHolder, position: Int) {
        val matches = roundMatchesMap[position] ?: emptyList()
        holder.bind(matches)
    }

    /**
     * ViewHolder holding a RecyclerView for matches within a single tournament round page.
     */
    class RoundViewHolder(
        view: View,
        onMatchClick: (MatchUiModel) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val rvBracketRoundMatches: RecyclerView = view.findViewById(R.id.rvBracketRoundMatches)
        private val adapter = BracketMatchAdapter(onMatchClick)

        init {
            rvBracketRoundMatches.layoutManager = LinearLayoutManager(view.context)
            rvBracketRoundMatches.adapter = adapter
        }

        fun bind(matches: List<MatchUiModel>) {
            adapter.submitList(matches)
            adapter.notifyDataSetChanged()
        }

        /**
         * Smoothly scrolls to center the match at [matchIndex].
         */
        fun scrollToMatch(matchIndex: Int) {
            if (matchIndex < 0) return
            val layoutManager = rvBracketRoundMatches.layoutManager as? LinearLayoutManager ?: return
            val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(rvBracketRoundMatches.context) {
                override fun calculateDtToFit(
                    viewStart: Int,
                    viewEnd: Int,
                    boxStart: Int,
                    boxEnd: Int,
                    snapPreference: Int
                ): Int {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                }
            }
            smoothScroller.targetPosition = matchIndex
            layoutManager.startSmoothScroll(smoothScroller)
        }
    }

    /**
     * Adapter for individual match tiles inside a bracket round page.
     */
    private class BracketMatchAdapter(
        private val onMatchClick: (MatchUiModel) -> Unit
    ) : ListAdapter<MatchUiModel, BracketMatchAdapter.MatchViewHolder>(MatchDiffCallback) {

        class MatchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardBracketMatch: MaterialCardView = view.findViewById(R.id.cardBracketMatch)
            val tvBracketMatchIndex: TextView = view.findViewById(R.id.tvBracketMatchIndex)
            val tvPlayer1Name: TextView = view.findViewById(R.id.tvPlayer1Name)
            val tvPlayer1Score: TextView = view.findViewById(R.id.tvPlayer1Score)
            val tvPlayer2Name: TextView = view.findViewById(R.id.tvPlayer2Name)
            val tvPlayer2Score: TextView = view.findViewById(R.id.tvPlayer2Score)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bracket_match, parent, false)
            return MatchViewHolder(view)
        }

        override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
            val item = getItem(position)
            val match = item.match
            val context = holder.itemView.context

            if (!match.groupName.isNullOrBlank()) {
                holder.tvBracketMatchIndex.text = context.getString(R.string.tournament_match_group_index_format, match.groupName, match.matchIndex + 1)
            } else {
                holder.tvBracketMatchIndex.text = context.getString(R.string.tournament_match_index_format, match.matchIndex + 1)
            }

            UiHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1, isLeft = true)
            UiHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2, isLeft = true)

            UiHelper.applyMatchCardStyle(
                card = holder.cardBracketMatch,
                item = item,
                player1Name = holder.tvPlayer1Name,
                player1Score = holder.tvPlayer1Score,
                player2Name = holder.tvPlayer2Name,
                player2Score = holder.tvPlayer2Score,
                matchIndexBadge = null
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
}
