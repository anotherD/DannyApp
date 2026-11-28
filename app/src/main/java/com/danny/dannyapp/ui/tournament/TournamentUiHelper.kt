package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.view.LayoutInflater
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object TournamentUiHelper {

    /**
     * Wandelt die Runden-Kennung [roundKey] in ein kurzes Präfix um.
     *
     * @param roundKey Die Runden-Kennung aus [TournamentRound].
     * @return Das entsprechende Runden-Präfix.
     */
    fun getRoundPrefix(roundKey: String): String {
        val qualNumber = TournamentRound.parseQualifierRoundNumber(roundKey)
        if (qualNumber != null) return "R$qualNumber"
        return when (roundKey) {
            TournamentRound.QUAL_PRELIM.name -> "PRE"
            TournamentRound.QUAL_R1.name, TournamentRound.R1_160.name -> "R1"
            TournamentRound.QUAL_R2.name, TournamentRound.R2_128.name -> "R2"
            TournamentRound.QUAL_R3.name, TournamentRound.R3_96.name -> "R3"
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
        return TournamentRound.fromKey(roundKey)?.displayNameResId?.let { context.getString(it) }
            ?: TournamentRound.parseQualifierRoundNumber(roundKey)?.let { context.getString(R.string.round_qualifier_numbered, it) }
            ?: roundKey
    }

    fun getTournamentCity(hostCity: String): String {
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
                val rowView = LayoutInflater.from(context).inflate(R.layout.item_entrant_row, containerLayout, false)
                val tvPlayerLeft = rowView.findViewById<TextView>(R.id.tvEntrantPlayerLeft)
                val tvPlayerRight = rowView.findViewById<TextView>(R.id.tvEntrantPlayerRight)

                if (rowWinners.isNotEmpty()) {
                    UIHelper.bindSmartPlayerName(tvPlayerLeft, rowWinners[0])
                }
                if (rowWinners.size > 1) {
                    UIHelper.bindSmartPlayerName(tvPlayerRight, rowWinners[1])
                    tvPlayerRight.visibility = View.VISIBLE
                } else {
                    tvPlayerRight.visibility = View.INVISIBLE
                }

                if (textColorResId != null) {
                    val color = ContextCompat.getColor(context, textColorResId)
                    tvPlayerLeft.setTextColor(color)
                    tvPlayerRight.setTextColor(color)
                }
                if (textSizeSp != 13f) {
                    val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
                    tvPlayerLeft.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                    tvPlayerRight.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
                }

                containerLayout.addView(rowView)
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
        val btnSimulateCompleteTournament: MaterialButton = itemView.findViewById(R.id.btnSimulateCompleteTournament)

        tvTournamentName.text = context.getString(R.string.tournament_order_name_format, tournament.order, tournament.name)

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTournamentTypeBadge.visibility = View.VISIBLE
        tvTournamentTypeBadge.text = typeBadgeInfo.text
        tvTournamentTypeBadge.background?.mutate()?.setTint(ContextCompat.getColor(context, typeBadgeInfo.colorResId))

        val statusBadgeInfo = getStatusBadgeInfo(context, model)
        tvTournamentStatus.text = statusBadgeInfo.text
        tvTournamentStatus.background?.mutate()?.setTint(ContextCompat.getColor(context, statusBadgeInfo.colorResId))

        val city = getTournamentCity(tournament.hostCity)
        UIHelper.formatFlagAndText(tvTournamentDetails, tournament.hostCountry, city)

        val event = TournamentEvent.valueOf(tournament.type)
        val cardView = itemView as? MaterialCardView
        val innerLayout = (itemView as? ViewGroup)?.getChildAt(0)

        when {
            model.status == TournamentStatus.COMPLETED -> {
                innerLayout?.alpha = 0.65f
                if (cardView != null) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_container_niedrig))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                    cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
                }
            }
            model.status == TournamentStatus.ACTIVE || model.isNextPlanned -> {
                innerLayout?.alpha = 1.0f
                if (cardView != null) {
                    applyTournamentEventCardStyle(context, cardView, event, isHighlighted = true)
                }
            }
            else -> {
                innerLayout?.alpha = 1.0f
                if (cardView != null) {
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                    cardView.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
                }
            }
        }

        if (cardView != null) {
            if (model.status == TournamentStatus.ACTIVE) {
                val elevationPx = context.resources.getDimension(R.dimen.sticky_card_elevation)
                cardView.cardElevation = elevationPx
                cardView.translationZ = elevationPx
            } else {
                cardView.cardElevation = 0f
                cardView.translationZ = 0f
            }
        }

        val paddingDimen = if (model.status == TournamentStatus.ACTIVE) {
            R.dimen.tournament_card_padding_active
        } else {
            R.dimen.tournament_card_padding
        }
        val paddingPx = context.resources.getDimensionPixelSize(paddingDimen)
        innerLayout?.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        val isSimulatingAny = simulatingTournamentId != null

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            btnStartTournament.visibility = View.VISIBLE
            btnStartTournament.isEnabled = !isSimulatingAny
            btnStartTournament.setOnClickListener { if (!isSimulatingAny) onStartTournamentClick(model) }
        } else {
            btnStartTournament.visibility = View.GONE
        }

        val debugMode = context.appContainer.settingsManager.debugMode
        val isEligibleForQuickSim = debugMode && model.status != TournamentStatus.COMPLETED
        if (isEligibleForQuickSim) {
            btnSimulateCompleteTournament.visibility = View.VISIBLE
            btnSimulateCompleteTournament.isEnabled = !isSimulatingAny
            btnSimulateCompleteTournament.setOnClickListener { if (!isSimulatingAny) onSimulateCompleteClick(model) }
        } else {
            btnSimulateCompleteTournament.visibility = View.GONE
        }

        itemView.setOnClickListener { onTournamentClick(model) }
    }

    private fun applyTournamentEventCardStyle(
        context: Context,
        card: MaterialCardView,
        event: TournamentEvent,
        isHighlighted: Boolean = true
    ) {
        val isMajor = event.isMajor
        val isQualifier = event.isQualifier
        val isEuropeanTour = event == TournamentEvent.EUROPEAN_TOUR
        val isEtQualifier = event == TournamentEvent.QUALIFIER_ET
        val isMajorQualifier = isQualifier && !isEtQualifier

        when {
            isMajor -> {
                card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                card.strokeColor = ContextCompat.getColor(context, R.color.badge_major)
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_major_tint))
            }
            isMajorQualifier -> {
                card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                card.strokeColor = ContextCompat.getColor(context, R.color.badge_major_qualifier)
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_major_tint))
            }
            isEuropeanTour -> {
                card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                card.strokeColor = ContextCompat.getColor(context, R.color.badge_european_tour)
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_et_tint))
            }
            isEtQualifier -> {
                card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                card.strokeColor = ContextCompat.getColor(context, R.color.badge_et_qualifier)
                card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_et_tint))
            }
            else -> {
                if (isHighlighted) {
                    card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                    card.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_karte_aktiv))
                } else {
                    card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
                    card.strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
                }
            }
        }
    }

    fun bindTournamentHeader(
        context: Context,
        headerCard: MaterialCardView,
        tournament: Tournament,
        statusText: String,
        statusColorResId: Int
    ) {
        val tvTitle: TextView = headerCard.findViewById(R.id.tvHeaderTitle)
        val tvStatusBadge: TextView = headerCard.findViewById(R.id.tvHeaderStatusBadge)
        val tvTypeBadge: TextView = headerCard.findViewById(R.id.tvHeaderTypeBadge)
        val tvHost: TextView = headerCard.findViewById(R.id.tvHeaderHost)

        tvTitle.text = tournament.name

        tvStatusBadge.text = statusText
        tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, statusColorResId)

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTypeBadge.visibility = View.VISIBLE
        tvTypeBadge.text = typeBadgeInfo.text
        tvTypeBadge.backgroundTintList = ContextCompat.getColorStateList(context, typeBadgeInfo.colorResId)

        val city = getTournamentCity(tournament.hostCity)
        UIHelper.formatFlagAndText(tvHost, tournament.hostCountry, city)

        val event = TournamentEvent.valueOf(tournament.type)
        applyTournamentEventCardStyle(context, headerCard, event)
    }
}
