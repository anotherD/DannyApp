package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
     * Erstellt einen gemeinsamen Spaltenkopf für Gruppentabellen.
     *
     * @param isLargeLayout `true` für die große Tabellenkarte in der Listenansicht,
     *                      `false` für die kompakte Darstellung im Turnierbaum.
     */
    fun createGroupTableHeaderView(
        context: Context,
        density: Float,
        isLargeLayout: Boolean = false,
        groupLetter: String? = null
    ): View {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (isLargeLayout) {
            val pxH = (12 * density).toInt()
            val pxV = (6 * density).toInt()
            headerLayout.setPadding(pxH, pxV, pxH, pxV)

            val widthPlayed = (36 * density).toInt()
            val widthDiff = (48 * density).toInt()
            val widthPoints = (40 * density).toInt()

            val tvTitle = TextView(context).apply {
                text = if (!groupLetter.isNullOrEmpty()) context.getString(R.string.group_label, groupLetter) else ""
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.white))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvHeaderPlayed = TextView(context).apply {
                text = context.getString(R.string.group_table_header_played)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                fontFeatureSettings = "tnum"
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                layoutParams = LinearLayout.LayoutParams(widthPlayed, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvHeaderDiff = TextView(context).apply {
                text = context.getString(R.string.group_table_header_diff)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
                fontFeatureSettings = "tnum"
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                layoutParams = LinearLayout.LayoutParams(widthDiff, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvHeaderPoints = TextView(context).apply {
                text = context.getString(R.string.group_table_header_points)
                textSize = 12f
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
        } else {
            val pxH = (6 * density).toInt()
            headerLayout.setPadding(pxH, 0, pxH, 0)

            val leftSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((21 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            }
            headerLayout.addView(leftSpacer)

            val rightContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            val widthRank = (14 * density).toInt()
            val widthPlayed = (26 * density).toInt()
            val widthDiff = (36 * density).toInt()
            val widthPoints = (28 * density).toInt()

            val rankSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(widthRank, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            rightContainer.addView(rankSpacer)

            val nameSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            rightContainer.addView(nameSpacer)

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

            rightContainer.addView(tvHeaderPlayed)
            rightContainer.addView(tvHeaderDiff)
            rightContainer.addView(tvHeaderPoints)
            headerLayout.addView(rightContainer)
        }

        return headerLayout
    }

    /**
     * Erstellt eine Gruppenkarten-Ansicht für die Grand Slam Gruppenphase.
     *
     * @param isLargeLayout `true` für die größere Darstellung in der Listenansicht (analog zu Match-Karten),
     *                      `false` für die kompakte Darstellung im Turnierbaum.
     */
    fun createGroupCardView(
        context: Context,
        density: Float,
        groupLetter: String,
        standings: List<GrandSlamGroupItem>,
        isLargeLayout: Boolean = false
    ): View {
        if (!isLargeLayout) {
            return createCompactGroupCardView(context, density, groupLetter, standings)
        }

        val cardRadius = 12f * density
        val pxH = (12 * density).toInt()
        val pxV = (10 * density).toInt()
        val dividerMargin = (6 * density).toInt()
        val rowPadV = (4 * density).toInt()
        val widthRank = (22 * density).toInt()
        val widthPlayed = (36 * density).toInt()
        val widthDiff = (48 * density).toInt()
        val widthPoints = (40 * density).toInt()
        val rowTextSize = 14f

        val card = MaterialCardView(context).apply {
            radius = cardRadius
            cardElevation = 2f * density
            strokeWidth = (1f * density).toInt()
            strokeColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
            useCompatPadding = false
        }

        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pxH, pxV, pxH, pxV)
        }

        val headerView = createGroupTableHeaderView(context, density, isLargeLayout = true, groupLetter = groupLetter)
        mainContainer.addView(headerView)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt()
            ).apply {
                topMargin = dividerMargin
                bottomMargin = dividerMargin
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
                setPadding((2 * density).toInt(), rowPadV, (2 * density).toInt(), rowPadV)
                if (item != null && item.player.id == USER_ID) {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.rot_container_dunkel))
                }
            }

            val tvRank = TextView(context).apply {
                text = context.getString(R.string.group_table_rank_format, i + 1)
                textSize = rowTextSize
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(
                    if (item != null && item.player.id == USER_ID) ContextCompat.getColor(context, R.color.rot_sehr_hell)
                    else if (i == 0) ContextCompat.getColor(context, R.color.white)
                    else ContextCompat.getColor(context, R.color.grau_text_gedaempft)
                )
                layoutParams = LinearLayout.LayoutParams(widthRank, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvName = TextView(context).apply {
                textSize = rowTextSize
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
                textSize = rowTextSize
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
                textSize = rowTextSize
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
                textSize = rowTextSize
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

    private fun createCompactGroupCardView(
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

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val leftFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (20 * density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvGroupIndex = TextView(context).apply {
            text = context.getString(R.string.group_label, groupLetter)
            textSize = 10f
            gravity = Gravity.CENTER
            rotation = -90f
            maxLines = 1
            setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        leftFrame.addView(tvGroupIndex)
        rootLayout.addView(leftFrame)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (1 * density).toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
        }
        rootLayout.addView(divider)

        val rightContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            val pxH = (6 * density).toInt()
            val pxV = (3 * density).toInt()
            setPadding(pxH, pxV, pxH, pxV)
        }

        val widthRank = (14 * density).toInt()
        val widthPlayed = (26 * density).toInt()
        val widthDiff = (36 * density).toInt()
        val widthPoints = (28 * density).toInt()
        val rowPadV = (2 * density).toInt()

        val displayItems = standings.take(3)
        for (i in 0 until 3) {
            val item = displayItems.getOrNull(i)
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    bottomMargin = rowPadV
                }
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
                layoutParams = LinearLayout.LayoutParams(widthRank, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            rightContainer.addView(rowLayout)
        }

        rootLayout.addView(rightContainer)
        card.addView(rootLayout)
        return card
    }
}
