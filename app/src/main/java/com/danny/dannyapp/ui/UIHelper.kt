package com.danny.dannyapp.ui

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.core.widget.NestedScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
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
        duration: Int = Snackbar.LENGTH_SHORT,
    ) {
        val actualDuration = if ((isError || duration == Snackbar.LENGTH_INDEFINITE)) Snackbar.LENGTH_INDEFINITE else duration
        val snackbar = Snackbar.make(anchorView, message, actualDuration)
        val context = anchorView.context
        val bgColorRes = if (isError) R.color.rot_container_dunkel else R.color.anthrazit_container_hoch
        val textColorRes = if (isError) R.color.rot_sehr_hell else R.color.grau_text_hell

        snackbar.setBackgroundTint(ContextCompat.getColor(context, bgColorRes))
        snackbar.setTextColor(ContextCompat.getColor(context, textColorRes))

        if (isError || duration == Snackbar.LENGTH_INDEFINITE) {
            snackbar.setAction(R.string.dialog_close) {
                snackbar.dismiss()
            }
            snackbar.setActionTextColor(ContextCompat.getColor(context, textColorRes))
        }

        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.maxLines = if (isError) 15 else 3

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
                cardStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                badgeStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                badgeStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
            } else {
                cardBg = ContextCompat.getColor(context, R.color.anthrazit_flaeche)
                cardStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                cardStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
                badgeStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                badgeStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
            }
        } else {
            cardBg = ContextCompat.getColor(context, if (!item.isCompleted) R.color.surface_grau_heller else R.color.anthrazit_flaeche)
            if (item.isUserMatch) {
                cardStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                cardStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
                badgeStrokeColor = ContextCompat.getColor(context, R.color.rot_hell)
                badgeStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thick)
            } else {
                cardStrokeColor = ContextCompat.getColor(context, R.color.anthrazit_container_hoechst)
                cardStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
                badgeStrokeColor = ContextCompat.getColor(context, R.color.grau_rahmen)
                badgeStrokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke_thin)
            }
        }

        card.setCardBackgroundColor(cardBg)
        card.strokeColor = cardStrokeColor
        card.strokeWidth = cardStrokeWidth
        card.cardElevation = if (useModern) context.resources.getDimension(R.dimen.card_elevation_modern) else 0f

        if (matchIndexBadge != null) {
            val background = matchIndexBadge.background
            if (background is GradientDrawable) {
                background.mutate()
                val badgeFillColor = ContextCompat.getColor(
                    context,
                    if (useModern) R.color.surface_et_tint else R.color.anthrazit_flaeche
                )
                background.setColor(badgeFillColor)
                background.setStroke(badgeStrokeWidth, badgeStrokeColor)
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

    /**
     * Wendet das einheitliche Styling für Selektions-Chips (Pillen) an.
     */
    fun styleSelectionChip(chip: Chip, isSelected: Boolean, status: RoundStatus? = null) {
        val context = chip.context
        val bgRes = when (status) {
            RoundStatus.COMPLETED -> R.color.status_completed
            RoundStatus.ACTIVE -> R.color.status_active
            RoundStatus.PENDING, null -> R.color.anthrazit_container_niedrig
        }
        chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, bgRes))

        if (isSelected) {
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            chip.chipStrokeWidth = chip.resources.getDimension(R.dimen.card_stroke_thick)
            chip.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
            chip.chipStrokeWidth = chip.resources.getDimension(R.dimen.card_stroke_thin)
            val textColorRes = if (status == RoundStatus.ACTIVE) R.color.white else R.color.grau_text_gedaempft
            chip.setTextColor(ContextCompat.getColor(context, textColorRes))
        }
    }

    /**
     * Tag-Key, der markiert, dass ein vertikaler Edge-Fade bereits an einer View registriert wurde.
     * Verhindert doppelte Registrierung bei mehrfachen Traversierungen.
     */
    private val TAG_EDGE_FADE_ATTACHED = "tag_edge_fade_attached".hashCode()

    private fun getVerticalScrollOffset(view: View): Int {
        return when (view) {
            is RecyclerView -> view.computeVerticalScrollOffset()
            is NestedScrollView -> view.scrollY
            is ScrollView -> view.scrollY
            else -> 0
        }
    }

    private fun getVerticalScrollRange(view: View): Int {
        return when (view) {
            is RecyclerView -> view.computeVerticalScrollRange()
            is NestedScrollView -> {
                val child = view.getChildAt(0)
                child?.height ?: view.height
            }
            is ScrollView -> {
                val child = view.getChildAt(0)
                child?.height ?: view.height
            }
            else -> view.height
        }
    }

    private fun getVerticalScrollExtent(view: View): Int {
        return when (view) {
            is RecyclerView -> view.computeVerticalScrollExtent()
            is NestedScrollView -> view.height - view.paddingTop - view.paddingBottom
            is ScrollView -> view.height - view.paddingTop - view.paddingBottom
            else -> view.height
        }
    }

    /**
     * Hängt einen einheitlichen weichen vertikalen Edge-Fade (3-Stopp-LinearGradient) an eine
     * scrollbare View an – **immer** über `view.overlay`, auch für [RecyclerView].
     *
     * Gradient-Kurve (oben, Positionen 0 → 0.45 → 1.0):
     *   colorSolid  →  midColor (~35 % Deckkraft)  →  transparent
     * Unten spiegelbildlich.
     *
     * Dynamische Ein-/Ausblendung:
     * - Oben: proportional über die ersten `fadePx` Scroll-Offset.
     * - Unten: proportional über die letzten `fadePx` vor dem Scroll-Ende.
     *
     * Idempotent: Mehrfache Aufrufe für dieselbe View haben keine Auswirkung.
     */
    fun attachVerticalEdgeFade(scrollableView: View) {
        // Idempotenz-Check
        if (scrollableView.getTag(TAG_EDGE_FADE_ATTACHED) == true) return
        scrollableView.setTag(TAG_EDGE_FADE_ATTACHED, true)

        // Natives Fading deaktivieren
        scrollableView.isVerticalFadingEdgeEnabled = false

        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        /**
         * Erstellt den Fade-Drawable, der über `scrollableView.overlay` gezeichnet wird.
         * Bounds werden beim ersten Layout und bei jedem Layout-Change aktuell gehalten.
         */
        val fadeDrawable = object : Drawable() {

            override fun draw(canvas: Canvas) {
                if (scrollableView.width <= 0 || scrollableView.height <= 0) return

                // Bounds direkt vor dem Zeichnen explizit aktualisieren
                setBounds(0, 0, scrollableView.width, scrollableView.height)

                val fadePx = scrollableView.resources
                    .getDimensionPixelSize(R.dimen.fading_edge_length).toFloat()
                val w = scrollableView.width.toFloat()
                val h = scrollableView.height.toFloat()

                val colorSolid = ContextCompat.getColor(scrollableView.context, R.color.anthrazit_hintergrund)
                val midColor   = ColorUtils.setAlphaComponent(colorSolid, 90)   // ~35 % Deckkraft
                val colorTransp = ColorUtils.setAlphaComponent(colorSolid, 0)

                val translateY = if (scrollableView is ScrollView || scrollableView is NestedScrollView) scrollableView.scrollY.toFloat() else 0f

                canvas.withTranslation(y = translateY) {
                    // ── Oberer Verlauf ──────────────────────────────────────────────────────────
                    if (scrollableView.canScrollVertically(-1)) {
                        val scrollOffset = getVerticalScrollOffset(scrollableView).toFloat()
                        val alphaFraction = (scrollOffset / fadePx).coerceIn(0f, 1f)

                        fadePaint.shader = LinearGradient(
                            0f, 0f, 0f, fadePx,
                            intArrayOf(colorSolid, midColor, colorTransp),
                            floatArrayOf(0f, 0.45f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        fadePaint.alpha = (255 * alphaFraction).toInt()
                        canvas.drawRect(0f, 0f, w, fadePx, fadePaint)
                    }

                    // ── Unterer Verlauf ─────────────────────────────────────────────────────────
                    if (scrollableView.canScrollVertically(1)) {
                        val scrollRange  = getVerticalScrollRange(scrollableView).toFloat()
                        val scrollExtent = getVerticalScrollExtent(scrollableView).toFloat()
                        val scrollOffset = getVerticalScrollOffset(scrollableView).toFloat()
                        val remaining = (scrollRange - scrollExtent - scrollOffset).coerceAtLeast(0f)
                        val alphaFraction = (remaining / fadePx).coerceIn(0f, 1f)

                        fadePaint.shader = LinearGradient(
                            0f, h - fadePx, 0f, h,
                            intArrayOf(colorTransp, midColor, colorSolid),
                            floatArrayOf(0f, 0.45f, 1f),
                            Shader.TileMode.CLAMP
                        )
                        fadePaint.alpha = (255 * alphaFraction).toInt()
                        canvas.drawRect(0f, h - fadePx, w, h, fadePaint)
                    }
                }

                // Alpha zurücksetzen, damit nachfolgende draw-Aufrufe nicht beeinflusst werden
                fadePaint.alpha = 255
            }

            override fun setAlpha(alpha: Int) { /* nicht benötigt */ }
            override fun setColorFilter(cf: ColorFilter?) { /* nicht benötigt */ }
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }

        // Vor dem Hinzufügen des Overlays explizit die Bounds auf (0, 0, width, height) setzen
        fadeDrawable.setBounds(0, 0, scrollableView.width, scrollableView.height)
        scrollableView.overlay.add(fadeDrawable)

        // Bounds bei jedem OnLayoutChangeListener aktualisieren und invalidieren
        scrollableView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            fadeDrawable.setBounds(0, 0, scrollableView.width, scrollableView.height)
            fadeDrawable.invalidateSelf()
        }

        // Scroll-Listener je View-Typ
        when (scrollableView) {
            is RecyclerView -> {
                scrollableView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        fadeDrawable.invalidateSelf()
                    }
                })
            }
            is ScrollView, is NestedScrollView -> {
                scrollableView.setOnScrollChangeListener { _, _, _, _, _ ->
                    fadeDrawable.invalidateSelf()
                }
            }
        }
    }

    /**
     * Durchläuft [rootView] rekursiv und wendet [attachVerticalEdgeFade] automatisch
     * auf alle vertikalen [RecyclerView]s, [ScrollView]s und [NestedScrollView]s an.
     */
    fun applyVerticalEdgeFadeToHierarchy(rootView: View) {
        when (rootView) {
            is RecyclerView -> {
                // Nur vertikale RecyclerViews erfassen
                val lm = rootView.layoutManager
                val isVertical = lm == null ||
                    (lm is androidx.recyclerview.widget.LinearLayoutManager && lm.orientation == androidx.recyclerview.widget.LinearLayoutManager.VERTICAL) ||
                    lm is androidx.recyclerview.widget.GridLayoutManager
                if (isVertical) attachVerticalEdgeFade(rootView)
            }
            is ScrollView, is NestedScrollView -> attachVerticalEdgeFade(rootView)
            is ViewGroup -> {
                for (i in 0 until rootView.childCount) {
                    applyVerticalEdgeFadeToHierarchy(rootView.getChildAt(i))
                }
            }
        }
    }

    /**
     * Scrollt eine View innerhalb eines HorizontalScrollView mittig in den sichtbaren Bereich.
     */
    fun centerViewInHorizontalScroll(scrollView: HorizontalScrollView, targetView: View) {
        targetView.post {
            val scrollX = targetView.left - (scrollView.width - targetView.width) / 2
            scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }
}
