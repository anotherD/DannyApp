package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
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
     * Inflatiert das XML-Layout des Gruppen-Spaltenkopfs für den Turnierbaum.
     */
    fun inflateBracketGroupHeaderView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.include_bracket_group_header, parent, false)
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
        isLargeLayout: Boolean = false,
        parent: ViewGroup? = null
    ): View {
        val layoutRes = if (isLargeLayout) R.layout.item_group_table_large else R.layout.item_bracket_group
        val view = LayoutInflater.from(context).inflate(layoutRes, parent, false)

        if (isLargeLayout) {
            val tvGroupTitle: TextView = view.findViewById(R.id.tvGroupTitle)
            tvGroupTitle.text = context.getString(R.string.group_label, groupLetter)
        } else {
            val tvBracketGroupLabel: TextView = view.findViewById(R.id.tvBracketGroupLabel)
            tvBracketGroupLabel.text = context.getString(R.string.group_label, groupLetter)
        }

        bindGroupRows(view, context, standings)
        return view
    }

    private fun bindGroupRows(
        view: View,
        context: Context,
        standings: List<GrandSlamGroupItem>
    ) {
        val rowViews = listOf(
            GroupRowViews(
                rowLayout = view.findViewById(R.id.rowGroupPlayer1),
                tvRank = view.findViewById(R.id.tvGroupRank1),
                tvName = view.findViewById(R.id.tvGroupName1),
                tvPlayed = view.findViewById(R.id.tvGroupPlayed1),
                tvDiff = view.findViewById(R.id.tvGroupDiff1),
                tvPoints = view.findViewById(R.id.tvGroupPoints1)
            ),
            GroupRowViews(
                rowLayout = view.findViewById(R.id.rowGroupPlayer2),
                tvRank = view.findViewById(R.id.tvGroupRank2),
                tvName = view.findViewById(R.id.tvGroupName2),
                tvPlayed = view.findViewById(R.id.tvGroupPlayed2),
                tvDiff = view.findViewById(R.id.tvGroupDiff2),
                tvPoints = view.findViewById(R.id.tvGroupPoints2)
            ),
            GroupRowViews(
                rowLayout = view.findViewById(R.id.rowGroupPlayer3),
                tvRank = view.findViewById(R.id.tvGroupRank3),
                tvName = view.findViewById(R.id.tvGroupName3),
                tvPlayed = view.findViewById(R.id.tvGroupPlayed3),
                tvDiff = view.findViewById(R.id.tvGroupDiff3),
                tvPoints = view.findViewById(R.id.tvGroupPoints3)
            )
        )

        val displayItems = standings.take(3)
        for (i in 0 until 3) {
            val row = rowViews[i]
            val item = displayItems.getOrNull(i)

            if (item != null && item.player.id == USER_ID) {
                row.rowLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.rot_container_dunkel))
            } else {
                row.rowLayout.background = null
            }

            row.tvRank.text = context.getString(R.string.group_table_rank_format, i + 1)
            row.tvRank.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
            row.tvRank.setTextColor(
                if (item != null && item.player.id == USER_ID) ContextCompat.getColor(context, R.color.rot_sehr_hell)
                else if (i == 0) ContextCompat.getColor(context, R.color.white)
                else ContextCompat.getColor(context, R.color.grau_text_gedaempft)
            )

            row.tvName.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
            if (item != null) {
                UIHelper.bindSmartPlayerName(row.tvName, item.player)
                if (item.player.id == USER_ID) {
                    row.tvName.setTextColor(ContextCompat.getColor(context, R.color.rot_sehr_hell))
                } else if (i != 0) {
                    row.tvName.setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                } else {
                    row.tvName.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
            } else {
                row.tvName.text = "-"
                row.tvName.setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
            }

            val statsTextColor = if (item != null && item.player.id == USER_ID) {
                ContextCompat.getColor(context, R.color.rot_sehr_hell)
            } else if (i == 0) {
                ContextCompat.getColor(context, R.color.white)
            } else {
                ContextCompat.getColor(context, R.color.grau_text_gedaempft)
            }

            row.tvPlayed.text = item?.played?.toString() ?: "0"
            row.tvPlayed.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
            if (i == 0) row.tvPlayed.paint.isFakeBoldText = true

            val diffStr = if (item != null) {
                if (item.legDiff > 0) "+${item.legDiff}" else "${item.legDiff}"
            } else {
                "0"
            }
            row.tvDiff.text = diffStr
            row.tvDiff.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
            if (i == 0) row.tvDiff.paint.isFakeBoldText = true

            row.tvPoints.text = item?.points?.toString() ?: "0"
            row.tvPoints.setTypeface(null, if (i == 0) Typeface.BOLD else Typeface.NORMAL)
            if (i == 0) row.tvPoints.paint.isFakeBoldText = true

            row.tvPlayed.setTextColor(statsTextColor)
            row.tvDiff.setTextColor(statsTextColor)
            row.tvPoints.setTextColor(statsTextColor)
        }
    }

    private data class GroupRowViews(
        val rowLayout: LinearLayout,
        val tvRank: TextView,
        val tvName: TextView,
        val tvPlayed: TextView,
        val tvDiff: TextView,
        val tvPoints: TextView
    )
}
