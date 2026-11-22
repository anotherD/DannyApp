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
import com.danny.dannyapp.ui.UIHelper
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
            val px = (10 * density).toInt()
            setPadding(px, px, px, px)
        }

        val tvInfo = TextView(context).apply {
            text = context.getString(R.string.draw_after_round_info, prevRoundName)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        mainContainer.addView(tvInfo)

        for ((groupIndex, group) in groups.withIndex()) {
            if (groupIndex > 0) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt()
                    ).apply {
                        topMargin = (6 * density).toInt()
                        bottomMargin = (6 * density).toInt()
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
                }
                mainContainer.addView(divider)
            }

            val tvGroupTitle = TextView(context).apply {
                text = group.title
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.rot_sehr_hell))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4 * density).toInt()
                }
            }
            mainContainer.addView(tvGroupTitle)

            val playerPairs = group.players.chunked(2)
            for (pair in playerPairs) {
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (2 * density).toInt()
                    }
                }

                for ((colIdx, player) in pair.withIndex()) {
                    val tvPlayer = TextView(context).apply {
                        textSize = 10f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }
                    UIHelper.bindSmartPlayerName(tvPlayer, player)
                    rowLayout.addView(tvPlayer)

                    if (colIdx == 0 && pair.size > 1) {
                        val spacer = View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                (4 * density).toInt(),
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                        rowLayout.addView(spacer)
                    }
                }
                mainContainer.addView(rowLayout)
            }
        }

        scrollView.addView(mainContainer)
        card.addView(scrollView)
        return card
    }

    /**
     * Erstellt eine Gruppenkarten-Ansicht für die Grand Slam Gruppenphase.
     */
    fun createGroupCardView(
        context: Context,
        density: Float,
        groupLetter: String,
        standings: List<GrandSlamGroupItem>
    ): View {
        val card = MaterialCardView(context).apply {
            radius = 6f * density
            cardElevation = 2f * density
            strokeWidth = (1f * density).toInt()
            strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
            useCompatPadding = false
        }

        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pxH = (6 * density).toInt()
            val pxV = (4 * density).toInt()
            setPadding(pxH, pxV, pxH, pxV)
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvTitle = TextView(context).apply {
            text = context.getString(R.string.group_label, groupLetter)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.white))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val widthPlayed = (28 * density).toInt()
        val widthDiff = (40 * density).toInt()
        val widthPoints = (32 * density).toInt()

        val tvHeaderPlayed = TextView(context).apply {
            text = context.getString(R.string.group_table_header_played)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            fontFeatureSettings = "tnum"
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = LinearLayout.LayoutParams(widthPlayed, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvHeaderDiff = TextView(context).apply {
            text = context.getString(R.string.group_table_header_diff)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            fontFeatureSettings = "tnum"
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = LinearLayout.LayoutParams(widthDiff, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tvHeaderPoints = TextView(context).apply {
            text = context.getString(R.string.group_table_header_points)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            fontFeatureSettings = "tnum"
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = LinearLayout.LayoutParams(widthPoints, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        headerLayout.addView(tvTitle)
        headerLayout.addView(tvHeaderPlayed)
        headerLayout.addView(tvHeaderDiff)
        headerLayout.addView(tvHeaderPoints)
        mainContainer.addView(headerLayout)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt()
            ).apply {
                topMargin = (2 * density).toInt()
                bottomMargin = (2 * density).toInt()
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
        }
        mainContainer.addView(divider)

        val displayItems = standings.take(3)
        for (i in 0 until 3) {
            val item = displayItems.getOrNull(i)
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((2 * density).toInt(), (1 * density).toInt(), (2 * density).toInt(), (1 * density).toInt())
                if (item != null && item.player.id == USER_ID) {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.rot_container_dunkel))
                }
            }

            val tvRank = TextView(context).apply {
                text = context.getString(R.string.group_table_rank_format, i + 1)
                textSize = 10f
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(
                    if (item != null && item.player.id == USER_ID) ContextCompat.getColor(context, R.color.rot_sehr_hell)
                    else if (i == 0) ContextCompat.getColor(context, R.color.white)
                    else ContextCompat.getColor(context, R.color.grau_text_gedaempft)
                )
                layoutParams = LinearLayout.LayoutParams((14 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvName = TextView(context).apply {
                textSize = 10f
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (item != null) {
                    UIHelper.bindSmartPlayerName(this, item.player)
                    if (item.player.id == USER_ID) {
                        setTextColor(ContextCompat.getColor(context, R.color.rot_sehr_hell))
                    } else if (i != 0) {
                        setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                    }
                } else {
                    text = "-"
                    setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                }
            }

            val statsTextColor = if (item != null && item.player.id == USER_ID) {
                ContextCompat.getColor(context, R.color.rot_sehr_hell)
            } else if (i == 0) {
                ContextCompat.getColor(context, R.color.white)
            } else {
                ContextCompat.getColor(context, R.color.grau_text_gedaempft)
            }

            val tvPlayed = TextView(context).apply {
                text = item?.played?.toString() ?: "0"
                textSize = 10f
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                if (i == 0) paint.isFakeBoldText = true
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                fontFeatureSettings = "tnum"
                setTextColor(statsTextColor)
                layoutParams = LinearLayout.LayoutParams(widthPlayed, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val diffStr = if (item != null) {
                if (item.legDiff > 0) "+${item.legDiff}" else "${item.legDiff}"
            } else {
                "0"
            }
            val tvDiff = TextView(context).apply {
                text = diffStr
                textSize = 10f
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                if (i == 0) paint.isFakeBoldText = true
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                fontFeatureSettings = "tnum"
                setTextColor(statsTextColor)
                layoutParams = LinearLayout.LayoutParams(widthDiff, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvPoints = TextView(context).apply {
                text = item?.points?.toString() ?: "0"
                textSize = 10f
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                if (i == 0) paint.isFakeBoldText = true
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                fontFeatureSettings = "tnum"
                setTextColor(statsTextColor)
                layoutParams = LinearLayout.LayoutParams(widthPoints, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            rowLayout.addView(tvRank)
            rowLayout.addView(tvName)
            rowLayout.addView(tvPlayed)
            rowLayout.addView(tvDiff)
            rowLayout.addView(tvPoints)
            mainContainer.addView(rowLayout)
        }

        card.addView(mainContainer)
        return card
    }
}
