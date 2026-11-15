package com.danny.dannyapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import androidx.core.view.doOnPreDraw
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.card.MaterialCardView

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

object UiHelper {

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
            canvas.withSave {
                val transY = y + (paint.fontMetricsInt.ascent + paint.fontMetricsInt.descent - b.bounds.bottom) / 2
                translate(x, transY.toFloat())
                b.draw(this)
            }
        }
    }

    fun getWorldEmoji(): String {
        return Country.getWorldEmoji()
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

        textView.doOnPreDraw { _ ->
            val width = textView.width - textView.paddingStart - textView.paddingEnd
            if (width <= 0) return@doOnPreDraw
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
            if (totalWidth(name) <= width) {
                return@doOnPreDraw
            }

            // 2. Abbreviated name fits?
            val abbrName = abbreviateName(name)
            if (totalWidth(abbrName) <= width) {
                setFormattedText(abbrName)
                return@doOnPreDraw
            }

            // 3. Ellipsize abbreviated name part only so seed is never cut off
            val availableForName = (width - seedWidth - flagExtraWidth).coerceAtLeast(0f)
            val ellipsizedName = TextUtils.ellipsize(abbrName, paint, availableForName, TextUtils.TruncateAt.END).toString()
            setFormattedText(ellipsizedName)
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
        val settingsManager = SettingsManager(context)
        val useModern = settingsManager.useModernMatchCardStyle

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

            if (item.winnerId == match.player1Id) {
                player1Name.setTypeface(null, Typeface.BOLD)
                player1Score.setTypeface(null, Typeface.BOLD)
                player1Name.setTextColor(colorTextHell)
                player1Score.setTextColor(colorTextHell)

                player2Name.setTypeface(null, Typeface.NORMAL)
                player2Score.setTypeface(null, Typeface.NORMAL)
                player2Name.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                player2Score.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
            } else if (item.winnerId == match.player2Id) {
                player2Name.setTypeface(null, Typeface.BOLD)
                player2Score.setTypeface(null, Typeface.BOLD)
                player2Name.setTextColor(colorTextHell)
                player2Score.setTextColor(colorTextHell)

                player1Name.setTypeface(null, Typeface.NORMAL)
                player1Score.setTypeface(null, Typeface.NORMAL)
                player1Name.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
                player1Score.setTextColor(if (useModern) colorTextGedaempft else colorTextHell)
            } else {
                player1Name.setTypeface(null, Typeface.NORMAL)
                player1Score.setTypeface(null, Typeface.NORMAL)
                player1Name.setTextColor(colorTextHell)
                player1Score.setTextColor(colorTextHell)

                player2Name.setTypeface(null, Typeface.NORMAL)
                player2Score.setTypeface(null, Typeface.NORMAL)
                player2Name.setTextColor(colorTextHell)
                player2Score.setTextColor(colorTextHell)
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
