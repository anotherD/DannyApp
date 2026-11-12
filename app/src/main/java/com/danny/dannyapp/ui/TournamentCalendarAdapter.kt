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

class TournamentCalendarAdapter(
    private val onTournamentClick: (TournamentUiModel) -> Unit,
    private val onStartTournamentClick: (TournamentUiModel) -> Unit
) : ListAdapter<TournamentUiModel, TournamentCalendarAdapter.TournamentViewHolder>(TournamentDiffCallback) {

    class TournamentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTournamentOrder: TextView = view.findViewById(R.id.tvTournamentOrder)
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

        val statusText = when (model.status) {
            TournamentStatus.COMPLETED -> {
                val winner = if (model.winnerName != null) " • Sieger: ${model.winnerName}" else ""
                "Abgeschlossen$winner"
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
