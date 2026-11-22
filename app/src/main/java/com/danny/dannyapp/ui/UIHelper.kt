package com.danny.dannyapp.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

/**
 * Returns the corresponding color resource ID for a [SkillLevel].
 */
fun SkillLevel.toColorResId(): Int {
    return when (this) {
        SkillLevel.LEVEL_1 -> R.color.skill_level_1
        SkillLevel.LEVEL_2 -> R.color.skill_level_2
        SkillLevel.LEVEL_3 -> R.color.skill_level_3
        SkillLevel.LEVEL_4 -> R.color.skill_level_4
        SkillLevel.LEVEL_5 -> R.color.skill_level_5
        SkillLevel.LEVEL_6 -> R.color.skill_level_6
        SkillLevel.LEVEL_7 -> R.color.skill_level_7
        SkillLevel.LEVEL_8 -> R.color.skill_level_8
        SkillLevel.LEVEL_9 -> R.color.skill_level_9
        SkillLevel.LEVEL_10 -> R.color.skill_level_10
    }
}

object UIHelper {

    /**
     * Zeigt eine Material-3-Snackbar mit anpassbaren Farben und automatischer Mehrzeiligkeit (max. 3 Zeilen) an.
     */
    fun showSnackbar(
        anchorView: View,
        message: String,
        isError: Boolean = false,
        duration: Int = Snackbar.LENGTH_SHORT
    ) {
        val snackbar = Snackbar.make(anchorView, message, duration)
        val context = anchorView.context
        val bgColorRes = if (isError) R.color.rot_container_dunkel else R.color.anthrazit_container_hoch
        val textColorRes = if (isError) R.color.rot_sehr_hell else R.color.grau_text_hell

        snackbar.setBackgroundTint(ContextCompat.getColor(context, bgColorRes))
        snackbar.setTextColor(ContextCompat.getColor(context, textColorRes))

        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.maxLines = 3

        snackbar.show()
    }

    /**
     * Erstellt die Spieler-UI-Informationen für den Nutzer basierend auf der verknüpften Karriere und dem Profil.
     */
    fun createUserPlayerUiInfo(
        careerWithProfile: CareerWithProfile,
        seed: Int? = null
    ): PlayerUiInfo {
        return PlayerUiInfo(
            id = USER_ID,
            name = careerWithProfile.playerName,
            countryCode = careerWithProfile.countryCode,
            skill = SkillLevel.DEFAULT.value,
            seed = seed
        )
    }

    fun createCpuPlayerUiInfo(
        playerId: Long,
        player: Player?,
        seed: Int? = null
    ): PlayerUiInfo {
        val cpuPlayer = requireNotNull(player) { "CPU-Spieler mit ID $playerId nicht gefunden" }
        return PlayerUiInfo(
            id = cpuPlayer.id,
            name = cpuPlayer.name,
            countryCode = cpuPlayer.countryCode,
            skill = cpuPlayer.skill,
            seed = seed
        )
    }

    /**
     * Ermittelt die Spieler-UI-Informationen basierend auf der Spieler-ID (Nutzer oder CPU-Spieler).
     */
    fun resolvePlayerUiInfo(
        playerId: Long,
        player: Player?,
        careerWithProfile: CareerWithProfile,
        seed: Int? = null
    ): PlayerUiInfo {
        return if (playerId == USER_ID) {
            createUserPlayerUiInfo(careerWithProfile, seed)
        } else {
            createCpuPlayerUiInfo(playerId, player, seed)
        }
    }

    class VerticalCenteredImageSpan(
        drawable: Drawable
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
            val boundDrawable = drawable
            canvas.withSave {
                val transY = y + (paint.fontMetricsInt.ascent + paint.fontMetricsInt.descent - boundDrawable.bounds.bottom) / 2
                translate(x, transY.toFloat())
                boundDrawable.draw(this)
            }
        }
    }

    // baut Flagge und Text zusammen und setzt den text in die TextView, flagge immer links
    fun formatFlagAndText(
        textView: TextView,
        countryCode: String,
        text: String,
        isLeft: Boolean = true
    ) {
        val flagResId = if (Country.hasCustomFlag(countryCode)) R.drawable.ic_flag_nir else null
        // Sonderfall Nordirland
        if (flagResId != null) {
            val drawable = ContextCompat.getDrawable(textView.context, flagResId)
            if (drawable != null) {
                val spanBuilder = if (isLeft) {
                    SpannableStringBuilder("  $text")
                } else {
                    SpannableStringBuilder("$text ")
                }
                val heightPx = (textView.textSize * 1.17f).toInt()
                val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
                } else {
                    (heightPx * 1.5f).toInt()
                }
                val sidePaddingPx = 2
                if (isLeft) {
                    drawable.setBounds(sidePaddingPx, 0, widthPx + sidePaddingPx, heightPx)
                } else {
                    drawable.setBounds(0, 0, widthPx, heightPx)
                }
                val imageSpan = VerticalCenteredImageSpan(drawable)
                if (isLeft) {
                    spanBuilder.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    spanBuilder.setSpan(imageSpan, spanBuilder.length - 1, spanBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                textView.text = spanBuilder
                return
            }
        }
        textView.text = if (isLeft) {
            "${Country.getFlagEmoji(countryCode)} $text"
        } else {
            "$text ${Country.getFlagEmoji(countryCode)}"
        }
    }

    // Vornamen kürzen und mit Punkt verbinden
    fun abbreviateName(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        if (parts.size <= 1) return name
        val firstName = parts[0]
        val lastName = parts.subList(1, parts.size).joinToString(" ")
        return "${firstName.first()}.$lastName"
    }

    // generiert eine Spielerzeile aus Name, Flagge und Setzlistenposition
    fun bindSmartPlayerName(
        textView: TextView,
        player: PlayerUiInfo,
        isLeft: Boolean = true,
    ) {
        val context = textView.context
        val name = player.name
        val countryCode = player.countryCode
        val seed = player.seed

        fun setFormattedText(playerText: String) {
            val textWithSeed = if (seed != null) {
                if (isLeft) context.getString(R.string.player_with_seed_right, playerText, seed)
                else context.getString(R.string.player_with_seed, seed, playerText)
            } else {
                playerText
            }
            if (!countryCode.isNullOrBlank()) {
                formatFlagAndText(textView, countryCode, textWithSeed, isLeft)
            } else {
                textView.text = textWithSeed
            }
        }

        setFormattedText(name)

        fun applySmartTruncation() {
            val width = textView.width - textView.paddingStart - textView.paddingEnd
            if (width <= 0) return
            val safetyBufferPx = 4f * context.resources.displayMetrics.density
            val effectiveWidth = (width - safetyBufferPx).coerceAtLeast(0f)
            val paint = textView.paint

            val seedText = if (seed != null) {
                if (isLeft) " ($seed)" else "($seed) "
            } else ""
            val seedWidth = if (seed != null) paint.measureText(seedText) else 0f

            val flagExtraWidth = if (!countryCode.isNullOrBlank()) {
                // Sonderfall Nordirland
                val flagResId = if (Country.hasCustomFlag(countryCode)) R.drawable.ic_flag_nir else null
                if (flagResId != null) {
                    val heightPx = (textView.textSize * 1.17f).toInt()
                    val drawable = ContextCompat.getDrawable(context, flagResId)
                    val widthPx = if (drawable != null && drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
                    } else {
                        (heightPx * 1.5f).toInt()
                    }
                    widthPx.toFloat() + paint.measureText("  ")
                } else {
                    val flagEmoji = Country.getFlagEmoji(countryCode)
                    paint.measureText(if (isLeft) "$flagEmoji " else " $flagEmoji")
                }
            } else 0f

            fun totalWidth(playerText: String): Float {
                val textWithSeed = if (seed != null) {
                    if (isLeft) context.getString(R.string.player_with_seed_right, playerText, seed)
                    else context.getString(R.string.player_with_seed, seed, playerText)
                } else {
                    playerText
                }
                return paint.measureText(textWithSeed) + flagExtraWidth
            }

            // 1. Full name fits?
            if (totalWidth(name) <= effectiveWidth) {
                setFormattedText(name)
                return
            }

            // 2. Abbreviated name fits?
            val abbrName = abbreviateName(name)
            if (totalWidth(abbrName) <= effectiveWidth) {
                setFormattedText(abbrName)
                return
            }

            // 3. Ellipsize abbreviated name part only so seed is never cut off
            val availableForName = (effectiveWidth - seedWidth - flagExtraWidth).coerceAtLeast(0f)
            val ellipsizedName = TextUtils.ellipsize(abbrName, paint, availableForName, TextUtils.TruncateAt.END).toString()
            setFormattedText(ellipsizedName)
        }

        val oldListener = textView.tag as? View.OnLayoutChangeListener
        if (oldListener != null) {
            textView.removeOnLayoutChangeListener(oldListener)
        }

        val layoutListener = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft || textView.width > 0) {
                applySmartTruncation()
            }
        }

        textView.addOnLayoutChangeListener(layoutListener)
        textView.tag = layoutListener

        if (textView.width > 0) {
            applySmartTruncation()
        }
    }

    /**
     * Applies the match card background, border stroke, badge stroke, player names text styles,
     * and scores according to the Modern or Classic style setting.
     */
    fun applyMatchCardStyle(
        card: MaterialCardView,
        item: MatchUiModel,
        player1Name: TextView,
        player1Score: TextView,
        player2Name: TextView,
        player2Score: TextView,
        matchIndexBadge: TextView? = null
    ) {
        val context = card.context
        val match = item.match
        val useModern = context.appContainer.settingsManager.useModernMatchCardStyle

        val cardBg: Int
        val cardStrokeColor: Int
        val cardStrokeWidth: Int
        val badgeStrokeColor: Int
        val badgeStrokeWidth: Int

        val colorTextHell = ContextCompat.getColor(context, R.color.grau_text_hell)
        val colorTextGedaempft = ContextCompat.getColor(context, R.color.grau_text_gedaempft)

        if (useModern) {
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
            cardBg = ContextCompat.getColor(context, if (!item.isCompleted) R.color.surface_grau_heller else R.color.anthrazit_flaeche)
            if (item.isUserMatch) {
                cardStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                cardStrokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                badgeStrokeWidth = 2
            } else {
                cardStrokeColor = ContextCompat.getColor(context, R.color.anthrazit_container_hoechst)
                cardStrokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                badgeStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                badgeStrokeWidth = 1
            }
        }

        card.setCardBackgroundColor(cardBg)
        card.strokeColor = cardStrokeColor
        card.strokeWidth = cardStrokeWidth
        card.cardElevation = if (useModern) 2f * context.resources.displayMetrics.density else 0f

        if (matchIndexBadge != null) {
            val background = matchIndexBadge.background
            if (background is GradientDrawable) {
                background.mutate()
                val badgeFillColor = ContextCompat.getColor(
                    context,
                    if (useModern) R.color.surface_et_tint else R.color.anthrazit_flaeche
                )
                background.setColor(badgeFillColor)
                background.setStroke((badgeStrokeWidth * context.resources.displayMetrics.density).toInt(), badgeStrokeColor)
            }
        }

        if (item.isCompleted) {
            player1Score.text = match.player1Score.toString()
            player2Score.text = match.player2Score.toString()

            when (item.winnerId) {
                match.player1Id -> {
                    player1Name.setTypeface(null, Typeface.BOLD)
                    player1Score.setTypeface(null, Typeface.BOLD)
                    player1Name.setTextColor(colorTextHell)
                    player1Score.setTextColor(colorTextHell)

                    player2Name.setTypeface(null, Typeface.NORMAL)
                    player2Score.setTypeface(null, Typeface.NORMAL)
                    player2Name.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                    player2Score.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                }
                match.player2Id -> {
                    player2Name.setTypeface(null, Typeface.BOLD)
                    player2Score.setTypeface(null, Typeface.BOLD)
                    player2Name.setTextColor(colorTextHell)
                    player2Score.setTextColor(colorTextHell)

                    player1Name.setTypeface(null, Typeface.NORMAL)
                    player1Score.setTypeface(null, Typeface.NORMAL)
                    player1Name.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                    player1Score.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                }
                else -> {
                    player1Name.setTypeface(null, Typeface.NORMAL)
                    player1Score.setTypeface(null, Typeface.NORMAL)
                    player1Name.setTextColor(colorTextHell)
                    player1Score.setTextColor(colorTextHell)

                    player2Name.setTypeface(null, Typeface.NORMAL)
                    player2Score.setTypeface(null, Typeface.NORMAL)
                    player2Name.setTextColor(colorTextHell)
                    player2Score.setTextColor(colorTextHell)
                }
            }
        } else {
            player1Score.text = "-"
            player2Score.text = "-"

            player1Name.setTypeface(null, Typeface.NORMAL)
            player1Score.setTypeface(null, Typeface.NORMAL)
            player1Name.setTextColor(colorTextHell)
            player1Score.setTextColor(colorTextHell)

            player2Name.setTypeface(null, Typeface.NORMAL)
            player2Score.setTypeface(null, Typeface.NORMAL)
            player2Name.setTextColor(colorTextHell)
            player2Score.setTextColor(colorTextHell)
        }
    }
}
