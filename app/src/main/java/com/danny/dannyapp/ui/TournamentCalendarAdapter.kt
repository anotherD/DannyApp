package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.domain.model.TournamentEvent

class TournamentCalendarAdapter(
    private val onTournamentClick: (TournamentUiModel) -> Unit,
    private val onStartTournamentClick: (TournamentUiModel) -> Unit
) : ListAdapter<TournamentUiModel, TournamentCalendarAdapter.TournamentViewHolder>(TournamentDiffCallback) {

    class TournamentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTournamentName: TextView = view.findViewById(R.id.tvTournamentName)
        val tvTournamentTypeBadge: TextView = view.findViewById(R.id.tvTournamentTypeBadge)
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
        val context = holder.itemView.context

        holder.tvTournamentName.text = "#${tournament.order} ${tournament.name}"

        val event = try { TournamentEvent.valueOf(tournament.type) } catch (e: Exception) { null }
        val isMajor = event in listOf(
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN, TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX, TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.GRAND_SLAM_OF_DARTS, TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS,
            TournamentEvent.WORLD_CHAMPIONSHIP
        )
        val isMajorQualifier = event in listOf(
            TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM
        )
        val isEtQualifier = event == TournamentEvent.QUALIFIER_ET
        val isEuropeanTour = event == TournamentEvent.EUROPEAN_TOUR

        val typeText = when {
            isMajor -> context.getString(R.string.tournament_type_major)
            isMajorQualifier -> context.getString(R.string.tournament_type_major_qualifier)
            isEtQualifier -> context.getString(R.string.tournament_type_et_qualifier)
            isEuropeanTour -> context.getString(R.string.tournament_type_european_tour)
            else -> context.getString(R.string.tournament_type_players_championship)
        }

        holder.tvTournamentTypeBadge.visibility = View.VISIBLE
        holder.tvTournamentTypeBadge.text = typeText
        val badgeColorRes = when {
            isMajor -> R.color.badge_major
            isMajorQualifier -> R.color.badge_major_qualifier
            isEtQualifier -> R.color.badge_et_qualifier
            isEuropeanTour -> R.color.badge_european_tour
            else -> R.color.badge_players_championship
        }
        holder.tvTournamentTypeBadge.background?.setTint(ContextCompat.getColor(context, badgeColorRes))

        val statusText = when (model.status) {
            TournamentStatus.COMPLETED -> {
                val winner = if (model.winnerName != null) " • Sieger: ${model.winnerName}" else ""
                "Abgeschlossen$winner"
            }
            TournamentStatus.ACTIVE -> {
                if (!model.activeRoundName.isNullOrBlank()) {
                    "Aktiv • ${model.activeRoundName}"
                } else {
                    "Aktiv"
                }
            }
            TournamentStatus.PLANNED -> "Geplant"
        }
        holder.tvTournamentStatus.text = statusText

        val statusBgColorRes = when (model.status) {
            TournamentStatus.COMPLETED -> R.color.status_completed
            TournamentStatus.ACTIVE -> R.color.status_active
            TournamentStatus.PLANNED -> R.color.status_planned
        }
        holder.tvTournamentStatus.background?.setTint(ContextCompat.getColor(context, statusBgColorRes))

        val host = tournament.hostCountry ?: "ENG"
        val flag = Country.getFlagEmoji(host)
        val city = getTournamentCity(host, tournament.name)
        holder.tvTournamentDetails.text = "$flag $city"

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            holder.btnStartTournament.visibility = View.VISIBLE
            holder.btnStartTournament.setOnClickListener { onStartTournamentClick(model) }
        } else {
            holder.btnStartTournament.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onTournamentClick(model) }
    }

    private fun getTournamentCity(hostCountry: String, tournamentName: String): String {
        val nameLower = tournamentName.lowercase()
        when {
            nameLower.contains("uk open") -> return "Minehead"
            nameLower.contains("world matchplay") -> return "Blackpool"
            nameLower.contains("world grand prix") -> return "Leicester"
            nameLower.contains("european championship") -> return "Dortmund"
            nameLower.contains("grand slam") -> return "Wolverhampton"
            nameLower.contains("players championship finals") -> return "Minehead"
            nameLower.contains("world championship") -> return "London"
            nameLower.contains("european open") -> return "Graz"
            nameLower.contains("baltic sea") -> return "Kiel"
            nameLower.contains("poland") -> return "Krakau"
            nameLower.contains("international darts open") -> return "Riesa"
            nameLower.contains("france") -> return "Liévin"
            nameLower.contains("german darts grand prix") -> return "München"
            nameLower.contains("european darts grand prix") -> return "Sindelfingen"
            nameLower.contains("european darts trophy") -> return "Göttingen"
            nameLower.contains("austrian darts open") -> return "Graz"
            nameLower.contains("czech") -> return "Prag"
            nameLower.contains("german darts open") -> return "Leverkusen"
            nameLower.contains("slovak") -> return "Bratislava"
            nameLower.contains("belgian") -> return "Gent"
            nameLower.contains("swiss") -> return "Basel"
            nameLower.contains("hungarian") -> return "Budapest"
            nameLower.contains("dutch") -> return "Maastricht"
        }
        return when (hostCountry.uppercase()) {
            "ENG" -> "Wigan"
            "DEU" -> "Hildesheim"
            "NLD" -> "Den Bosch"
            "AUT" -> "Graz"
            "CZE" -> "Prag"
            "SVK" -> "Bratislava"
            "BEL" -> "Gent"
            "SUI" -> "Basel"
            "HUN" -> "Budapest"
            "POL" -> "Krakau"
            "FRA" -> "Liévin"
            else -> hostCountry
        }
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
