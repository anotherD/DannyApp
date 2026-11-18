package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.ui.UiHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.google.android.material.card.MaterialCardView

object TournamentUiHelper {

    fun getDisplayRoundName(roundKey: String, context: Context): String {
        return TournamentRound.fromKey(roundKey)?.displayNameResId?.let { context.getString(it) } ?: roundKey
    }

    fun getTournamentCity(hostCountry: String?, hostCity: String? = null): String {
        if (!hostCity.isNullOrBlank()) {
            return hostCity
        }
        val countryCode = hostCountry ?: Country.DEFAULT_CODE
        return Country.getDisplayName(countryCode)
    }

    data class TypeBadgeInfo(val text: String, val colorResId: Int)

    fun getTournamentTypeBadgeInfo(context: Context, typeString: String): TypeBadgeInfo {
        val event = try { TournamentEvent.valueOf(typeString) } catch (_: Exception) { null }
        val isMajor = event?.isMajor == true
        val isQualifier = event?.isQualifier == true
        val isEtQualifier = event == TournamentEvent.QUALIFIER_ET
        val isMajorQualifier = isQualifier && !isEtQualifier
        val isEuropeanTour = event == TournamentEvent.EUROPEAN_TOUR

        val text = when {
            isMajor -> context.getString(R.string.tournament_type_major)
            isMajorQualifier -> context.getString(R.string.tournament_type_major_qualifier)
            isEtQualifier -> context.getString(R.string.tournament_type_et_qualifier)
            isEuropeanTour -> context.getString(R.string.tournament_type_european_tour)
            else -> context.getString(R.string.tournament_type_players_championship)
        }

        val colorResId = when {
            isMajor -> R.color.badge_major
            isMajorQualifier -> R.color.badge_major_qualifier
            isEtQualifier -> R.color.badge_et_qualifier
            isEuropeanTour -> R.color.badge_european_tour
            else -> R.color.badge_players_championship
        }

        return TypeBadgeInfo(text, colorResId)
    }

    fun bindTournamentHeader(
        context: Context,
        tvTitle: TextView,
        tvStatusBadge: TextView,
        tvTypeBadge: TextView,
        tvHost: TextView,
        tournament: Tournament,
        statusText: String,
        statusColorResId: Int,
        cardView: MaterialCardView? = null
    ) {
        tvTitle.text = tournament.name
        tvStatusBadge.text = statusText
        tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, statusColorResId))

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTypeBadge.text = typeBadgeInfo.text
        tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, typeBadgeInfo.colorResId))

        val hostText = getTournamentCity(tournament.hostCountry, tournament.hostCity)
        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        UiHelper.formatFlagAndText(tvHost, hostCountryCode, hostText)

        cardView?.strokeColor = ContextCompat.getColor(context, statusColorResId)
    }

    fun bindQualifierWinnersList(
        context: Context,
        headerView: TextView,
        container: android.widget.LinearLayout,
        winners: List<PlayerUiInfo>?,
        textSizeSp: Float = 14f
    ) {
        container.removeAllViews()
        if (winners.isNullOrEmpty()) {
            headerView.visibility = View.GONE
            container.visibility = View.GONE
            return
        }

        headerView.visibility = View.VISIBLE
        container.visibility = View.VISIBLE

        winners.forEach { winner ->
            val tv = TextView(context).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
                val layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.setMargins(0, 4, 0, 4)
                this.layoutParams = layoutParams
            }
            UiHelper.bindSmartPlayerName(tv, winner)
            container.addView(tv)
        }
    }

    fun bindTournamentTile(
        itemView: View,
        model: TournamentUiModel,
        onTournamentClick: (TournamentUiModel) -> Unit,
        onStartTournamentClick: (TournamentUiModel) -> Unit,
        onSimulateCompleteClick: (TournamentUiModel) -> Unit,
        simulatingTournamentId: Long? = null
    ) {
        val context = itemView.context
        val tvTournamentName = itemView.findViewById<TextView>(R.id.tvTournamentName)
        val tvTournamentStatus = itemView.findViewById<TextView>(R.id.tvTournamentStatus)
        val tvTournamentTypeBadge = itemView.findViewById<TextView>(R.id.tvTournamentTypeBadge)
        val tvTournamentDetails = itemView.findViewById<TextView>(R.id.tvTournamentDetails)
        val btnSimulateCompleteTournament = itemView.findViewById<Button>(R.id.btnSimulateCompleteTournament)
        val btnStartTournament = itemView.findViewById<Button>(R.id.btnStartTournament)

        val tournament = model.tournament
        val settingsManager = SettingsManager(context)
        val debugMode = settingsManager.debugMode

        tvTournamentName?.text = tournament.name

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTournamentTypeBadge?.text = typeBadgeInfo.text
        tvTournamentTypeBadge?.setBackgroundColor(ContextCompat.getColor(context, typeBadgeInfo.colorResId))

        val hostText = getTournamentCity(tournament.hostCountry, tournament.hostCity)
        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        if (tvTournamentDetails != null) {
            UiHelper.formatFlagAndText(tvTournamentDetails, hostCountryCode, hostText)
        }

        val isCurrentlySimulating = (simulatingTournamentId == tournament.id)

        when (model.status) {
            TournamentStatus.COMPLETED -> {
                val winnerText = model.winnerName?.let { context.getString(R.string.tournament_status_winner_format, it) }
                    ?: context.getString(R.string.tournament_status_completed)
                tvTournamentStatus?.text = winnerText
                tvTournamentStatus?.setBackgroundColor(ContextCompat.getColor(context, R.color.status_completed))

                btnStartTournament?.visibility = View.GONE
                btnSimulateCompleteTournament?.visibility = View.GONE
            }

            TournamentStatus.ACTIVE -> {
                val roundName = model.activeRoundName ?: context.getString(R.string.round_qual_r1)
                tvTournamentStatus?.text = "${context.getString(R.string.tournament_status_active)} • $roundName"
                tvTournamentStatus?.setBackgroundColor(ContextCompat.getColor(context, R.color.status_active))

                btnStartTournament?.visibility = View.GONE
                btnSimulateCompleteTournament?.visibility = if (debugMode && !isCurrentlySimulating) View.VISIBLE else View.GONE
            }

            TournamentStatus.PLANNED -> {
                tvTournamentStatus?.text = context.getString(R.string.tournament_status_planned)
                tvTournamentStatus?.setBackgroundColor(ContextCompat.getColor(context, R.color.status_planned))

                btnSimulateCompleteTournament?.visibility = View.GONE

                if (model.isNextPlanned && !isCurrentlySimulating) {
                    btnStartTournament?.visibility = View.VISIBLE
                } else {
                    btnStartTournament?.visibility = View.GONE
                }
            }
        }

        itemView.setOnClickListener {
            if (!isCurrentlySimulating) {
                onTournamentClick(model)
            }
        }

        btnStartTournament?.setOnClickListener {
            if (!isCurrentlySimulating) {
                onStartTournamentClick(model)
            }
        }

        btnSimulateCompleteTournament?.setOnClickListener {
            if (!isCurrentlySimulating) {
                onSimulateCompleteClick(model)
            }
        }
    }
}
