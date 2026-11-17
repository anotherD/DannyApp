package com.danny.dannyapp.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.divider.MaterialDivider

/**
 * UI helper class for [TournamentDetailActivity] to render open draw entrants,
 * manage qualifier boards, filter matches, and handle horizontal swipe gestures.
 */
object TournamentDetailUiHelper {

    /**
     * Renders open draw entrant groups (UK Open / European Open) into the given container layout.
     */
    fun renderOpenDrawEntrants(context: Context, container: LinearLayout, groups: List<EntrantGroupInfo>) {
        container.visibility = View.VISIBLE
        container.removeAllViews()
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                val divider = MaterialDivider(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (8 * context.resources.displayMetrics.density).toInt()
                        bottomMargin = (8 * context.resources.displayMetrics.density).toInt()
                    }
                }
                container.addView(divider)
            }
            val tvGroupTitle = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4 * context.resources.displayMetrics.density).toInt()
                }
                text = group.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_HORIZONTAL
                val typedValue = TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                if (typedValue.resourceId != 0) {
                    setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                }
            }
            container.addView(tvGroupTitle)

            group.players.chunked(2).forEach { rowPlayers ->
                val rowLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * context.resources.displayMetrics.density).toInt()
                        bottomMargin = (2 * context.resources.displayMetrics.density).toInt()
                    }
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                }

                rowPlayers.forEach { player ->
                    val tvPlayer = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        textSize = 13f
                        val typedValue = TypedValue()
                        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                        if (typedValue.resourceId != 0) {
                            setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                        }
                    }
                    UiHelper.bindSmartPlayerName(tvPlayer, player, isLeft = true)
                    rowLayout.addView(tvPlayer)
                }

                if (rowPlayers.size == 1) {
                    val emptyView = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            0,
                            1f
                        )
                    }
                    rowLayout.addView(emptyView)
                }

                container.addView(rowLayout)
            }
        }
    }

    /**
     * Returns the number of qualifier boards for the given tournament event.
     */
    fun getQualifierBoardCount(event: TournamentEvent): Int {
        return when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
            TournamentEvent.QUALIFIER_WM -> 4
            else -> 4
        }
    }

    /**
     * Determines the default board index for the user's match, or -1 if none found.
     */
    fun findDefaultBoardIndex(allRoundsMatches: List<List<MatchUiModel>>, boardCount: Int): Int {
        val userMatch = allRoundsMatches.flatten().find { it.isUserMatch }
        if (userMatch != null) {
            val roundKey = userMatch.match.round
            val mIdx = userMatch.match.matchIndex
            val boardIdx = when (roundKey) {
                TournamentRound.QUAL_R1.name -> mIdx / 4
                TournamentRound.QUAL_R2.name -> mIdx / 2
                TournamentRound.QUAL_FINAL.name -> mIdx
                else -> -1
            }
            return if (boardIdx in 0 until boardCount) boardIdx else -1
        }
        return -1
    }

    /**
     * Filters all tournament rounds matches for the selected board index.
     */
    fun filterMatchesForBoard(
        allRoundsMatches: List<List<MatchUiModel>>,
        boardIndex: Int,
        boardCount: Int,
        qualificationUseCase: TournamentQualificationUseCase
    ): List<List<MatchUiModel>> {
        if (boardIndex < 0 || boardIndex >= boardCount) {
            return allRoundsMatches
        }

        val qualR1Models = allRoundsMatches.firstOrNull { list ->
            list.firstOrNull()?.match?.round == TournamentRound.QUAL_R1.name
        } ?: emptyList()
        val qualR1Matches = qualR1Models.map { it.match }

        return allRoundsMatches.map { roundMatches ->
            if (roundMatches.isEmpty()) return@map emptyList()
            val roundKey = roundMatches.first().match.round
            when (roundKey) {
                TournamentRound.QUAL_PRELIM.name -> {
                    roundMatches.filter { matchModel ->
                        val targetBoard = matchModel.targetBoard ?: qualificationUseCase.resolvePrelimTargetBoard(
                            qualR1Matches = qualR1Matches,
                            prelimMatchIndex = matchModel.match.matchIndex,
                            winnerId = matchModel.match.winnerId,
                            boardCount = boardCount
                        )
                        targetBoard == boardIndex
                    }
                }
                TournamentRound.QUAL_R1.name -> {
                    roundMatches.filter { it.match.matchIndex in (4 * boardIndex)..(4 * boardIndex + 3) }
                }
                TournamentRound.QUAL_R2.name -> {
                    roundMatches.filter { it.match.matchIndex in (2 * boardIndex)..(2 * boardIndex + 1) }
                }
                TournamentRound.QUAL_FINAL.name -> {
                    roundMatches.filter { it.match.matchIndex == boardIndex }
                }
                else -> roundMatches
            }
        }
    }

    /**
     * Attaches horizontal swipe gesture detection to given views and recyclerViews.
     */
    fun attachHorizontalSwipe(
        views: List<View>,
        recyclerViews: List<RecyclerView>,
        onSwipeLeft: () -> Unit,
        onSwipeRight: () -> Unit
    ) {
        if (views.isEmpty() && recyclerViews.isEmpty()) return
        val context = views.firstOrNull()?.context ?: recyclerViews.firstOrNull()?.context ?: return
        val density = context.resources.displayMetrics.density
        val swipeThreshold = (60 * density).toInt()
        val swipeVelocityThreshold = (100 * density).toInt()

        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > swipeThreshold && Math.abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX < 0) {
                            onSwipeLeft()
                        } else {
                            onSwipeRight()
                        }
                        return true
                    }
                }
                return false
            }
        })

        val rvTouchListener = object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return gestureDetector.onTouchEvent(e)
            }
        }
        recyclerViews.forEach { rv ->
            rv.addOnItemTouchListener(rvTouchListener)
        }

        val genericTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
        views.forEach { view ->
            view.setOnTouchListener(genericTouchListener)
        }
    }
}
