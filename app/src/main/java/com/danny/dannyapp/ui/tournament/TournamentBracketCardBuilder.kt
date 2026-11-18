package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.ui.UiHelper
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.GrandSlamGroupItem
import com.google.android.material.card.MaterialCardView

/**
 * Hilfsklasse zum Erstellen von programmatischen UI-Karten für den Turnierbaum (Bracket).
 */
object TournamentBracketCardBuilder {

    /**
     * Erstellt eine Info-Karte für offene Auslosungen nach einer Runde.
     */
    fun createOpenDrawInfoCardView(
        context: Context,
        density: Float,
        prevRoundName: String,
        groups: List<EntrantGroupInfo>
    ): View {
        val card = MaterialCardView(context).apply {
            radius = 8f * density
            cardElevation = 2f * density
            strokeWidth = (1f * density).toInt()
            strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
            useCompatPadding = false
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }

        val tvInfo = TextView(context).apply {
            text = context.getString(R.string.draw_after_round_info, prevRoundName)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        mainContainer.addView(tvInfo)

        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                val divider = com.google.android.material.divider.MaterialDivider(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        setMargins(0, (8 * density).toInt(), 0, (8 * density).toInt())
                    }
                    dividerColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                }
                mainContainer.addView(divider)
            }

            val tvGroupTitle = TextView(context).apply {
                text = group.title
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.rot_sehr_hell))
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
            mainContainer.addView(tvGroupTitle)

            val chipGroup = com.google.android.material.chip.ChipGroup(context).apply {
                chipSpacingHorizontal = (4 * density).toInt()
                chipSpacingVertical = (4 * density).toInt()
            }

            group.players.forEach { player ->
                val chip = com.google.android.material.chip.Chip(context).apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    setChipBackgroundColorResource(R.color.anthrazit_container_hoch)
                    isClickable = false
                    isFocusable = false
                }
                UiHelper.bindSmartPlayerName(chip, player)
                chipGroup.addView(chip)
            }
            mainContainer.addView(chipGroup)
        }

        scrollView.addView(mainContainer)
        card.addView(scrollView)
        return card
    }

    /**
     * Erstellt eine Tabelle für Grand-Slam-Gruppenergebnisse.
     */
    fun createGrandSlamGroupCardView(
        context: Context,
        density: Float,
        groupLetter: String,
        items: List<GrandSlamGroupItem>
    ): View {
        val card = MaterialCardView(context).apply {
            radius = 8f * density
            cardElevation = 2f * density
            strokeWidth = (1f * density).toInt()
            strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
            useCompatPadding = false
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
        }

        val tvTitle = TextView(context).apply {
            text = context.getString(R.string.group_label, groupLetter)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        container.addView(tvTitle)

        // Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (4 * density).toInt())
        }

        val tvPosHead = TextView(context).apply {
            text = "#"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvNameHead = TextView(context).apply {
            text = context.getString(R.string.config_players)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvPlayedHead = TextView(context).apply {
            text = "Sp"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvDiffHead = TextView(context).apply {
            text = "+/-"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvPtsHead = TextView(context).apply {
            text = "Pkt"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        headerRow.addView(tvPosHead)
        headerRow.addView(tvNameHead)
        headerRow.addView(tvPlayedHead)
        headerRow.addView(tvDiffHead)
        headerRow.addView(tvPtsHead)
        container.addView(headerRow)

        items.forEachIndexed { index, item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
                if (index < 2) {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.surface_karte_aktiv))
                }
            }

            val tvPos = TextView(context).apply {
                text = "${item.rank}."
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = if (index < 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (item.player.id == USER_ID) ContextCompat.getColor(context, R.color.rot_sehr_hell) else ContextCompat.getColor(context, R.color.grau_text_hell))
                layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val tvName = TextView(context).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = if (index < 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (item.player.id == USER_ID) ContextCompat.getColor(context, R.color.rot_sehr_hell) else ContextCompat.getColor(context, R.color.grau_text_hell))
                ellipsize = TextUtils.TruncateAt.END
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            UiHelper.bindSmartPlayerName(tvName, item.player)

            val tvPlayed = TextView(context).apply {
                text = item.played.toString()
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val tvDiff = TextView(context).apply {
                val diffStr = if (item.legDiff > 0) "+${item.legDiff}" else item.legDiff.toString()
                text = diffStr
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val tvPts = TextView(context).apply {
                text = item.points.toString()
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (index < 2) ContextCompat.getColor(context, R.color.rot_hell) else ContextCompat.getColor(context, R.color.grau_text_hell))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            row.addView(tvPos)
            row.addView(tvName)
            row.addView(tvPlayed)
            row.addView(tvDiff)
            row.addView(tvPts)
            container.addView(row)
        }

        card.addView(container)
        return card
    }
}
