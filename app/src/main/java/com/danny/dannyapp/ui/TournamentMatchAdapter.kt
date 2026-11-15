package com.danny.dannyapp.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
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

        if (!match.groupName.isNullOrBlank()) {
            holder.tvMatchRoundIndex.text = context.getString(R.string.tournament_match_group_index_format, match.groupName, match.matchIndex + 1)
        } else {
            holder.tvMatchRoundIndex.text = context.getString(R.string.tournament_match_index_format, match.matchIndex + 1)
        }

        val settingsManager = SettingsManager(context)
        val useModern = settingsManager.useModernMatchCardStyle

        val cardBg: Int
        val cardStrokeColor: Int
        val cardStrokeWidth: Int
        val badgeStrokeColor: Int
        val badgeStrokeWidth: Int

        val colorTextHell: Int
        val colorTextGedaempft: Int

        if (useModern) {
            colorTextHell = ContextCompat.getColor(context, R.color.grau_text_hell)
            colorTextGedaempft = ContextCompat.getColor(context, R.color.grau_text_gedaempft)
            if (item.isUserMatch) {
                cardBg = ContextCompat.getColor(context, R.color.rot_container_dunkel)
                cardStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                cardStrokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                badgeStrokeWidth = 2
            } else {
                cardBg = ContextCompat.getColor(context, R.color.anthrazit_flaeche)
                cardStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                cardStrokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                badgeStrokeWidth = 1
            }
        } else {
            colorTextHell = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, R.color.grau_text_hell))
            colorTextGedaempft = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            if (item.isUserMatch) {
                cardBg = com.google.android.material.color.MaterialColors.getColor(context, R.attr.colorErrorContainer, ContextCompat.getColor(context, R.color.rot_dunkel))
                cardStrokeColor = com.google.android.material.color.MaterialColors.getColor(context, R.attr.colorError, ContextCompat.getColor(context, R.color.rot_hell))
                cardStrokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = com.google.android.material.color.MaterialColors.getColor(context, R.attr.colorError, ContextCompat.getColor(context, R.color.rot_hell))
                badgeStrokeWidth = 2
            } else {
                cardBg = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, ContextCompat.getColor(context, R.color.anthrazit_container_hoch))
                cardStrokeColor = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
                cardStrokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                badgeStrokeWidth = 1
            }
        }

        holder.cardMatch.setCardBackgroundColor(cardBg)
        holder.cardMatch.strokeColor = cardStrokeColor
        holder.cardMatch.strokeWidth = cardStrokeWidth

        val background = holder.tvMatchRoundIndex.background
        if (background is GradientDrawable) {
            background.mutate()
            background.setStroke((badgeStrokeWidth * context.resources.displayMetrics.density).toInt(), badgeStrokeColor)
        }

        UiHelper.bindSmartPlayerName(holder.tvPlayer1Name, item.player1, isLeft = true)
        UiHelper.bindSmartPlayerName(holder.tvPlayer2Name, item.player2, isLeft = false)

        if (item.isCompleted) {
            holder.tvPlayer1Score.text = match.player1Score.toString()
            holder.tvPlayer2Score.text = match.player2Score.toString()

            if (item.winnerId == match.player1Id) {
                holder.tvPlayer1Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Name.setTextColor(colorTextHell)
                holder.tvPlayer1Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Score.setTextColor(colorTextHell)

                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Name.setTextColor(colorTextGedaempft)
                holder.tvPlayer2Score.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Score.setTextColor(colorTextGedaempft)
            } else if (item.winnerId == match.player2Id) {
                holder.tvPlayer2Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Name.setTextColor(colorTextHell)
                holder.tvPlayer2Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Score.setTextColor(colorTextHell)

                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Name.setTextColor(colorTextGedaempft)
                holder.tvPlayer1Score.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Score.setTextColor(colorTextGedaempft)
            } else {
                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Name.setTextColor(colorTextHell)
                holder.tvPlayer1Score.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Score.setTextColor(colorTextHell)

                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Name.setTextColor(colorTextHell)
                holder.tvPlayer2Score.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Score.setTextColor(colorTextHell)
            }
        } else {
            holder.tvPlayer1Score.text = "-"
            holder.tvPlayer2Score.text = "-"
            holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer1Name.setTextColor(colorTextHell)
            holder.tvPlayer1Score.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer1Score.setTextColor(colorTextHell)
            holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer2Name.setTextColor(colorTextHell)
            holder.tvPlayer2Score.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer2Score.setTextColor(colorTextHell)
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
