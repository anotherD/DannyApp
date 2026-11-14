package com.danny.dannyapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import androidx.core.view.doOnPreDraw
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.ui.model.PlayerUiInfo

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
                val transY =
                    (bottom - b.bounds.bottom) - paint.fontMetricsInt.descent / 2 + verticalOffsetPx
                translate(x, transY.toFloat())
                b.draw(this)
            }
        }
    }

    fun getWorldEmoji(): String {
        return Country.getWorldEmoji()
    }

    // baut Flagge und Text zusammen und setzt den text in die TextView, flagge immer links (TODO: parameter ergänzen)
    // Aktuell genutzt für:
    //   Anzeige des Profils in KarriereWidget
    //   das sticki Widget in OOM    -> TODO: wieso nicht bei den anderen spielern?
    //   den sieger in careerDetail  -> TODO: wieder einbauen, war buggy
    //   ein Match (TODO: da aber separat nur die flagge (nötig? dann die Flagge auslagern)
    //   den Spielort eines turniers -> TODO: prüfen warum 2mal
    fun formatFlagAndText(
        textView: TextView,
        countryCode: String,
        text: String,
        isLeft: Boolean = true
    ) {
        val flagResId = Country.getFlagResId(countryCode)
        // Sonderfall Nordirland (kein Country.getFlagEmoji(countryCode))
        if (flagResId != null) {
            // Hier einfach textView.context verwenden!
            val drawable = ContextCompat.getDrawable(textView.context, flagResId)
            if (drawable != null) {
                val spanBuilder = if (isLeft) {
                    SpannableStringBuilder("   $text")
                } else {
                    SpannableStringBuilder("$text   ")
                }
                val heightPx = (textView.textSize * 1.17f).toInt()
                val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                    (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
                } else {
                    (heightPx * 1.5f).toInt()
                }
                drawable.setBounds(0, 0, widthPx, heightPx)
                val imageSpan = VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
                if (isLeft) {
                    spanBuilder.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    spanBuilder.setSpan(imageSpan, spanBuilder.length - 1, spanBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                /* @SuppressLint("SetTextI18n") */
                textView.text = spanBuilder
                return
            }
        }
        /* @SuppressLint("SetTextI18n") */
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
    // anschließend wird der Text in das angegebene TextView gesetzt
    // Aktuell genutzt für:
    //   ein Match
    //   die Liste der Spieler in Lostöpfen: TournamentDetailActivity:530
    //   die siegerliste bei abgeschlossenen turnieren
    // TODO: Warum wird hier nicht der player übergeben? Weil er schon in den aufrufern nur als string zur verfügung steht-> refactor
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
                if (isLeft) context.getString(R.string.player_with_seed, seed, playerText)
                else context.getString(R.string.player_with_seed_right, playerText, seed)
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
                if (isLeft) "($seed) " else " ($seed)"
            } else ""
            val seedWidth = if (seed != null) paint.measureText(seedText) else 0f

            val flagExtraWidth = if (!countryCode.isNullOrBlank()) {
                // Sonderfall Nordirland (kein Country.getFlagEmoji(countryCode))
                val flagResId = Country.getFlagResId(countryCode)
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
                    if (isLeft) context.getString(R.string.player_with_seed, seed, playerText)
                    else context.getString(R.string.player_with_seed_right, playerText, seed)
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

}
