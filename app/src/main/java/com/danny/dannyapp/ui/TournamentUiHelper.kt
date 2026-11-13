package com.danny.dannyapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.domain.model.TournamentEvent

object TournamentUiHelper {

    class VerticalCenteredImageSpan(
        drawable: Drawable,
        private val verticalOffsetPx: Int = 3
    ) : ImageSpan(drawable, ALIGN_BASELINE) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val b = drawable
            canvas.save()
            val transY = (bottom - b.bounds.bottom) - paint.fontMetricsInt.descent / 2 + verticalOffsetPx
            canvas.translate(x, transY.toFloat())
            b.draw(canvas)
            canvas.restore()
        }
    }

    fun getDisplayRoundName(roundKey: String, context: Context): String {
        return when (roundKey) {
            "WINNER" -> context.getString(R.string.round_winner)
            "QUAL_PRELIM" -> context.getString(R.string.round_qual_prelim)
            "QUAL_R1" -> context.getString(R.string.round_qual_r1)
            "QUAL_R2" -> context.getString(R.string.round_qual_r2)
            "QUAL_FINAL" -> context.getString(R.string.round_qual_final)
            "F" -> context.getString(R.string.round_f)
            "SF" -> context.getString(R.string.round_sf)
            "QF" -> context.getString(R.string.round_qf)
            "L16" -> context.getString(R.string.round_l16)
            "L32" -> context.getString(R.string.round_l32)
            "L48" -> context.getString(R.string.round_l48)
            "L64" -> context.getString(R.string.round_l64)
            "L128" -> context.getString(R.string.round_l128)
            "R3_96" -> context.getString(R.string.round_r3_96)
            "R2_128" -> context.getString(R.string.round_r2_128)
            "R1_160" -> context.getString(R.string.round_r1_160)
            "GROUP_2ND" -> context.getString(R.string.round_group_2nd)
            "GROUP_3RD" -> context.getString(R.string.round_group_3rd)
            "GROUP" -> context.getString(R.string.round_group)
            else -> roundKey
        }
    }

    fun getTournamentCity(hostCountry: String, tournamentName: String): String {
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

    data class TypeBadgeInfo(val text: String, val colorResId: Int)

    fun getTournamentTypeBadgeInfo(context: Context, typeString: String): TypeBadgeInfo {
        val event = try { TournamentEvent.valueOf(typeString) } catch (_: Exception) { null }
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
        val isMultiWinnerQualifier = model.tournament.type in listOf(
            "QUALIFIER_ET", "QUALIFIER_GRAND_SLAM", "QUALIFIER_WM"
        )
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

    fun formatFlagAndText(
        context: Context,
        textView: TextView,
        countryCode: String,
        text: String
    ) {
        val flagResId = Country.getFlagResId(countryCode)
        if (flagResId != null) {
            val spanBuilder = SpannableStringBuilder("   $text")
            val drawable = ContextCompat.getDrawable(context, flagResId)
            if (drawable != null) {
                val heightPx = (textView.textSize * 1.17f).toInt()
                val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
                } else {
                    (heightPx * 1.5f).toInt()
                }
                drawable.setBounds(0, 0, widthPx, heightPx)
                val imageSpan = VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
                spanBuilder.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                textView.text = spanBuilder
                return
            }
        }
        val flagEmoji = Country.getFlagEmoji(countryCode)
        textView.text = "$flagEmoji $text"
    }

    fun bindTournamentTile(
        itemView: View,
        model: TournamentUiModel,
        onTournamentClick: (TournamentUiModel) -> Unit,
        onStartTournamentClick: (TournamentUiModel) -> Unit
    ) {
        val context = itemView.context
        val tournament = model.tournament

        val tvTournamentName: TextView = itemView.findViewById(R.id.tvTournamentName)
        val tvTournamentTypeBadge: TextView = itemView.findViewById(R.id.tvTournamentTypeBadge)
        val tvQualifierWinners: TextView = itemView.findViewById(R.id.tvQualifierWinners)
        val tvTournamentStatus: TextView = itemView.findViewById(R.id.tvTournamentStatus)
        val tvTournamentDetails: TextView = itemView.findViewById(R.id.tvTournamentDetails)
        val btnStartTournament: Button = itemView.findViewById(R.id.btnStartTournament)

        tvTournamentName.text = "#${tournament.order} ${tournament.name}"

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTournamentTypeBadge.visibility = View.VISIBLE
        tvTournamentTypeBadge.text = typeBadgeInfo.text
        tvTournamentTypeBadge.background?.setTint(ContextCompat.getColor(context, typeBadgeInfo.colorResId))

        if (!model.qualifierWinners.isNullOrEmpty()) {
            tvQualifierWinners.visibility = View.VISIBLE
            tvQualifierWinners.text = context.getString(
                R.string.qualifier_winners_prefix,
                model.qualifierWinners.joinToString(", ")
            )
        } else {
            tvQualifierWinners.visibility = View.GONE
        }

        val statusBadgeInfo = getStatusBadgeInfo(context, model)
        tvTournamentStatus.text = statusBadgeInfo.text
        tvTournamentStatus.background?.setTint(ContextCompat.getColor(context, statusBadgeInfo.colorResId))

        val host = tournament.hostCountry ?: "ENG"
        val city = getTournamentCity(host, tournament.name)
        formatFlagAndText(context, tvTournamentDetails, host, city)

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            btnStartTournament.visibility = View.VISIBLE
            btnStartTournament.setOnClickListener { onStartTournamentClick(model) }
        } else {
            btnStartTournament.visibility = View.GONE
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
        statusColorResId: Int
    ) {
        tvTitle.text = tournament.name

        tvStatusBadge.text = statusText
        tvStatusBadge.backgroundTintList = ContextCompat.getColorStateList(context, statusColorResId)

        val typeBadgeInfo = getTournamentTypeBadgeInfo(context, tournament.type)
        tvTypeBadge.visibility = View.VISIBLE
        tvTypeBadge.text = typeBadgeInfo.text
        tvTypeBadge.backgroundTintList = ContextCompat.getColorStateList(context, typeBadgeInfo.colorResId)

        val host = tournament.hostCountry ?: "ENG"
        val city = getTournamentCity(host, tournament.name)
        formatFlagAndText(context, tvHost, host, city)
    }
}
