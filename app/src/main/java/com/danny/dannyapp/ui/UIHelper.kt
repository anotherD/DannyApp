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

    fun formatFlagAndText(
        context: Context,
        textView: TextView,
        countryCode: String,
        text: String
//        prefix: String = "" // war nicht da
    ) {
        val flagResId = Country.getFlagResId(countryCode)
        // Sonderfall Nordirland (kein Country.getFlagEmoji(countryCode))
        if (flagResId != null) {
            val spanBuilder = SpannableStringBuilder("   $text")
//            val spanText = if (text.isEmpty()) "${prefix} " else "${prefix}   $text"
//            val spanBuilder = SpannableStringBuilder(spanText)
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
//                val start = prefix.length
//                val end = start + 1
//                spanBuilder.setSpan(imageSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                textView.text = spanBuilder
                return
            }
        }
        val flagEmoji = Country.getFlagEmoji(countryCode)
        textView.text = "$flagEmoji $text"
//        textView.text = if (text.isEmpty()) "$prefix$flagEmoji" else "$prefix$flagEmoji $text"
    }

    // Vornamen kürzen und mit Punkt verbinden
    fun abbreviateName(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        if (parts.size <= 1) return name
        val firstName = parts[0]
        val lastName = parts.subList(1, parts.size).joinToString(" ")
        return "${firstName.first()}.$lastName"
    }

    fun bindSmartPlayerName(
        textView: TextView,
        name: String,
        seed: Int? = null,
        isLeft: Boolean = true,
        countryCode: String? = null
    ) {
        val context = textView.context

        fun setFormattedText(playerText: String) {
            val textWithSeed = if (seed != null) {
                if (isLeft) context.getString(R.string.player_with_seed, seed, playerText)
                else context.getString(R.string.player_with_seed_right, playerText, seed)
            } else {
                playerText
            }
            if (!countryCode.isNullOrBlank()) {
                formatFlagAndText(context, textView, countryCode, textWithSeed)
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
                    paint.measureText("$flagEmoji ")
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
