package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.google.android.material.card.MaterialCardView

object TournamentUiHelper {

    /**
     * Wandelt die Runden-Kennung [roundKey] in ein kurzes Präfix um.
     *
     * @param roundKey Die Runden-Kennung aus [TournamentRound].
     * @return Das entsprechende Runden-Präfix.
     */
    fun getRoundPrefix(roundKey: String): String {
        return when (roundKey) {
            TournamentRound.QUAL_PRELIM.name -> "PRE"
            TournamentRound.QUAL_R1.name, TournamentRound.R1_160.name -> "R1"
            TournamentRound.QUAL_R2.name, TournamentRound.R2_128.name -> "R2"
            TournamentRound.R3_96.name -> "R3"
            TournamentRound.QUAL_FINAL.name -> "FIN"
            TournamentRound.L128.name -> "L128"
            TournamentRound.L64.name -> "L64"
            TournamentRound.L48.name -> "L48"
            TournamentRound.L32.name -> "L32"
            TournamentRound.L16.name -> "L16"
            TournamentRound.QF.name -> "QF"
            TournamentRound.SF.name -> "SF"
            TournamentRound.F.name -> "F"
            TournamentRound.GROUP.name -> "GRP"
            else -> roundKey
        }
    }

    /**
     * Formatiert den Badge-Text für ein Turnier-Match mit Runden-Präfix und Match-Nummer.
     *
     * @param context Der Context für String-Ressourcen.
     * @param match Das [TournamentMatch], dessen Badge-Text formatiert werden soll.
     * @return Der formatierte Badge-Text.
     */
    fun formatMatchBadgeText(context: Context, match: TournamentMatch): String {
        val prefix = getRoundPrefix(match.round)
        val number = match.matchIndex + 1
        return if (!match.groupName.isNullOrBlank()) {
            context.getString(R.string.tournament_match_group_index_format, match.groupName, prefix, number)
        } else {
            context.getString(R.string.tournament_match_index_format, prefix, number)
        }
    }

    fun getDisplayRoundName(roundKey: String, context: Context): String {
        return TournamentRound.fromKey(roundKey)?.displayNameResId?.let { context.getString(it) } ?: roundKey
    }

    fun getTournamentCity(hostCountry: String, hostCity: String): String {
        return hostCity
    }

    data class TypeBadgeInfo(val text: String, val colorResId: Int)

    fun getTournamentTypeBadgeInfo(context: Context, typeString: String): TypeBadgeInfo {
        val event = TournamentEvent.valueOf(typeString)
        val isMajor = event.isMajor
        val isQualifier = event.isQualifier
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

        val colorRes = when {
            isMajor -> R.color.badge_major
            isMajorQualifier -> R.color.badge_major_qualifier
            isEtQualifier -> R.color.badge_et_qualifier
            isEuropeanTour -> R.color.badge_european_tour
            else -> R.color.badge_players_championship
        }

        return TypeBadgeInfo(text, colorRes)
    }

    data class StatusBadgeInfo(val text: String, val colorResId: Int)

    fun getStatusBadgeInfo(context: Context, model: TournamentUiModel): StatusBadgeInfo {
        val event = TournamentEvent.valueOf(model.tournament.type)
        val isMultiWinnerQualifier = event.isQualifier
        val text = when (model.status) {
            TournamentStatus.COMPLETED -> {
                if (!isMultiWinnerQualifier && model.winnerName != null) {
                    context.getString(R.string.tournament_status_winner_format, model.winnerName)
                } else {
                    context.getString(R.string.tournament_status_completed)
                }
            }
            TournamentStatus.ACTIVE -> {
                if (!model.activeRoundName.isNullOrBlank()) {
                    model.activeRoundName
                } else {
                    context.getString(R.string.tournament_status_active)
                }
            }
            TournamentStatus.PLANNED -> context.getString(R.string.tournament_status_planned)
        }

        val colorRes = when (model.status) {
            TournamentStatus.COMPLETED -> R.color.status_completed
            TournamentStatus.ACTIVE -> R.color.status_active
            TournamentStatus.PLANNED -> R.color.status_planned
        }

        return StatusBadgeInfo(text, colorRes)
    }

    fun bindQualifierWinnersList(
        context: Context,
        headerTextView: TextView,
        containerLayout: android.widget.LinearLayout,
        winners: List<PlayerUiInfo>?,
        textSizeSp: Float = 13f,
        textColorResId: Int? = null
    ) {
        containerLayout.removeAllViews()
        if (winners.isNullOrEmpty()) {
            headerTextView.visibility = View.GONE
            containerLayout.visibility = View.GONE
        } else {
            headerTextView.visibility = View.VISIBLE
            containerLayout.visibility = View.VISIBLE
            winners.chunked(2).forEach { rowWinners ->
                val rowLayout = android.widget.LinearLayout(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * context.resources.displayMetrics.density).toInt()
                        bottomMargin = (2 * context.resources.displayMetrics.density).toInt()
                    }
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    weightSum = 2f
                }

                rowWinners.forEach { player ->
                    val tvRow = TextView(context).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        textSize = textSizeSp
                        if (textColorResId != null) {
                            setTextColor(ContextCompat.getColor(context, textColorResId))
                        } else {
                            val typedValue = android.util.TypedValue()
                            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                            if (typedValue.resourceId != 0) {
                                setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                            }
                        }
                    }
                    UIHelper.bindSmartPlayerName(tvRow, player)
                    rowLayout.addView(tvRow)
                }

                if (rowWinners.size == 1) {
                    val emptyView = View(context).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            0,
                            0,
                            1f
                        )
                    }
                    rowLayout.addView(emptyView)
                }

                containerLayout.addView(rowLayout)
            }
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
        val tournament = model.tournament

        val tvTournamentName: TextView = itemView.findViewById(R.id.tvTournamentName)
        val tvTournamentTypeBadge: TextView = itemView.findViewById(R.id.tvTournamentTypeBadge)
        val tvTournamentStatus: TextView = itemView.findViewById(R.id.tvTournamentStatus)
        val tvTournamentDetails: TextView = itemView.findViewById(R.id.tvTournamentDetails)
        val btnStartTournament: Button = itemView.findViewById(R.id.btnStartTournament)
        val btnSimulateCompleteTournament: Button = itemView.findViewById(R.id.btnSimulateCompleteTournament)

        tvTournamentName.text = context.getString(R.string.tournament_order_name_format, tournament.order, tournament.name)

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTournamentTypeBadge.visibility = View.VISIBLE
        tvTournamentTypeBadge.text = typeBadgeInfo.text
        tvTournamentTypeBadge.background?.mutate()?.setTint(ContextCompat.getColor(context, typeBadgeInfo.colorResId))

        val statusBadgeInfo = getStatusBadgeInfo(context, model)
        tvTournamentStatus.text = statusBadgeInfo.text
        tvTournamentStatus.background?.mutate()?.setTint(ContextCompat.getColor(context, statusBadgeInfo.colorResId))

        val city = getTournamentCity(tournament.hostCountry, tournament.hostCity)
        UIHelper.formatFlagAndText(tvTournamentDetails, tournament.hostCountry, city)

        val cardView = itemView as? MaterialCardView
        when {
            model.status == TournamentStatus.ACTIVE -> {
                cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_karte_aktiv))
                cardView?.strokeColor = ContextCompat.getColor(context, R.color.status_active)
                cardView?.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            }
            model.isNextPlanned -> {
                cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_karte_aktiv))
                cardView?.strokeColor = ContextCompat.getColor(context, R.color.status_planned)
                cardView?.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            }
            else -> {
                cardView?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
                cardView?.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                cardView?.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            }
        }

        val isSimulatingAny = simulatingTournamentId != null
        val isThisSimulating = simulatingTournamentId == tournament.id

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            btnStartTournament.visibility = View.VISIBLE
            btnStartTournament.isEnabled = !isSimulatingAny
            btnStartTournament.setOnClickListener { if (!isSimulatingAny) onStartTournamentClick(model) }
        } else {
            btnStartTournament.visibility = View.GONE
        }

        val settingsManager = SettingsManager(context)
        val debugMode = settingsManager.debugMode
        val isEligibleForQuickSim = debugMode && (model.status == TournamentStatus.ACTIVE || (model.status == TournamentStatus.PLANNED && model.isNextPlanned))
        if (isEligibleForQuickSim) {
            btnSimulateCompleteTournament.visibility = View.VISIBLE
            btnSimulateCompleteTournament.isEnabled = !isSimulatingAny
            if (isThisSimulating) {
                btnSimulateCompleteTournament.text = context.getString(R.string.simulating_short)
            } else {
                btnSimulateCompleteTournament.text = context.getString(R.string.tournament_simulate_complete)
            }
            btnSimulateCompleteTournament.setOnClickListener { if (!isSimulatingAny) onSimulateCompleteClick(model) }
        } else {
            btnSimulateCompleteTournament.visibility = View.GONE
        }

        itemView.setOnClickListener { onTournamentClick(model) }
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
        headerCard: MaterialCardView? = null
    ) {
        tvTitle.text = tournament.name

        tvStatusBadge.text = statusText
        tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, statusColorResId)

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTypeBadge.visibility = View.VISIBLE
        tvTypeBadge.text = typeBadgeInfo.text
        tvTypeBadge.backgroundTintList = ContextCompat.getColorStateList(context, typeBadgeInfo.colorResId)

        val city = getTournamentCity(tournament.hostCountry, tournament.hostCity)
        UIHelper.formatFlagAndText(tvHost, tournament.hostCountry, city)

        if (headerCard != null) {
            val event = TournamentEvent.valueOf(tournament.type)
            val isMajor = event.isMajor
            val isQualifier = event.isQualifier
            val isEuropeanTour = event == TournamentEvent.EUROPEAN_TOUR
            val isEtQualifier = event == TournamentEvent.QUALIFIER_ET
            val isMajorQualifier = isQualifier && !isEtQualifier

            val density = context.resources.displayMetrics.density
            when {
                isMajor -> {
                    headerCard.strokeWidth = (2 * density).toInt()
                    headerCard.strokeColor = ContextCompat.getColor(context, R.color.badge_major)
                    headerCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_major_tint))
                }
                isMajorQualifier -> {
                    headerCard.strokeWidth = (2 * density).toInt()
                    headerCard.strokeColor = ContextCompat.getColor(context, R.color.badge_major_qualifier)
                    headerCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_major_tint))
                }
                isEuropeanTour -> {
                    headerCard.strokeWidth = (2 * density).toInt()
                    headerCard.strokeColor = ContextCompat.getColor(context, R.color.badge_european_tour)
                    headerCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_et_tint))
                }
                isEtQualifier -> {
                    headerCard.strokeWidth = (2 * density).toInt()
                    headerCard.strokeColor = ContextCompat.getColor(context, R.color.badge_et_qualifier)
                    headerCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_et_tint))
                }
                else -> {
                    headerCard.strokeWidth = (1 * density).toInt()
                    headerCard.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                    headerCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
                }
            }
        }
    }
}
