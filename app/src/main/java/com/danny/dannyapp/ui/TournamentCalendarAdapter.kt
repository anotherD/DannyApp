package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.TournamentEvent

class TournamentCalendarAdapter(
    private val onTournamentClick: (TournamentUiModel) -> Unit,
    private val onStartTournamentClick: (TournamentUiModel) -> Unit
) : ListAdapter<TournamentUiModel, TournamentCalendarAdapter.TournamentViewHolder>(TournamentDiffCallback) {

    class TournamentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTournamentOrder: TextView = view.findViewById(R.id.tvTournamentOrder)
        val tvTournamentTypeBadge: TextView = view.findViewById(R.id.tvTournamentTypeBadge)
        val tvTournamentName: TextView = view.findViewById(R.id.tvTournamentName)
        val tvTournamentStatus: TextView = view.findViewById(R.id.tvTournamentStatus)
        val tvTournamentDetails: TextView = view.findViewById(R.id.tvTournamentDetails)
        val btnStartTournament: Button = view.findViewById(R.id.btnStartTournament)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TournamentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tournament, parent, false)
        return TournamentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TournamentViewHolder, position: Int) {
        val model = getItem(position)
        val tournament = model.tournament

        holder.tvTournamentOrder.text = "#${tournament.order}"
        holder.tvTournamentName.text = tournament.name

        val event = try { TournamentEvent.valueOf(tournament.type) } catch (e: Exception) { null }
        val isMajor = event in listOf(
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN, TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX, TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.GRAND_SLAM_OF_DARTS, TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS,
            TournamentEvent.WORLD_CHAMPIONSHIP
        )
        val isQualifier = event in listOf(
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM
        )

        if (isMajor) {
            holder.tvTournamentTypeBadge.visibility = View.VISIBLE
            holder.tvTournamentTypeBadge.text = "MAJOR"
            holder.tvTournamentTypeBadge.setBackgroundResource(R.drawable.bg_major_badge)
        } else if (isQualifier) {
            holder.tvTournamentTypeBadge.visibility = View.VISIBLE
            holder.tvTournamentTypeBadge.text = "QUALIFIER"
            holder.tvTournamentTypeBadge.setBackgroundResource(R.drawable.bg_qualifier_badge)
        } else {
            holder.tvTournamentTypeBadge.visibility = View.GONE
        }

        val statusText = when (model.status) {
            TournamentStatus.COMPLETED -> {
                val winner = if (model.winnerName != null) " • Sieger: ${model.winnerName}" else ""
                val userRound = if (!model.userReachedRound.isNullOrBlank()) " • Deine Runde: ${model.userReachedRound}" else ""
                "Abgeschlossen$winner$userRound"
            }
            TournamentStatus.ACTIVE -> "Aktiv"
            TournamentStatus.PLANNED -> "Geplant"
        }
        holder.tvTournamentStatus.text = statusText

        val host = tournament.hostCountry ?: "ENG"
        holder.tvTournamentDetails.text = "Austragungsland: $host • Typ: ${tournament.type}"

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            holder.btnStartTournament.visibility = View.VISIBLE
            holder.btnStartTournament.setOnClickListener { onStartTournamentClick(model) }
        } else {
            holder.btnStartTournament.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onTournamentClick(model) }
    }

    private object TournamentDiffCallback : DiffUtil.ItemCallback<TournamentUiModel>() {
        override fun areItemsTheSame(oldItem: TournamentUiModel, newItem: TournamentUiModel): Boolean {
            return oldItem.tournament.id == newItem.tournament.id
        }

        override fun areContentsTheSame(oldItem: TournamentUiModel, newItem: TournamentUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
