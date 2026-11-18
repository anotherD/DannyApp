package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.EntrantGroupDomainData
import com.danny.dannyapp.domain.usecase.EntrantGroupType
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
import com.danny.dannyapp.ui.UiHelper
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.divider.MaterialDivider
import kotlin.math.abs

/**
 * UI helper class for tournament details to render open draw entrants,
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
                        (1 * context.resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(0, (12 * context.resources.displayMetrics.density).toInt(), 0, (12 * context.resources.displayMetrics.density).toInt())
                    }
                    dividerColor = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
                }
                container.addView(divider)
            }

            val tvTitle = TextView(context).apply {
                text = group.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
                setPadding(0, 0, 0, (6 * context.resources.displayMetrics.density).toInt())
            }
            container.addView(tvTitle)

            val chipGroup = ChipGroup(context).apply {
                chipSpacingHorizontal = (6 * context.resources.displayMetrics.density).toInt()
                chipSpacingVertical = (6 * context.resources.displayMetrics.density).toInt()
            }

            group.players.forEach { player ->
                val chip = Chip(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                    setChipBackgroundColorResource(R.color.anthrazit_container_hoch)
                    isClickable = false
                    isFocusable = false
                }
                UiHelper.bindSmartPlayerName(chip, player)
                chipGroup.addView(chip)
            }
            container.addView(chipGroup)
        }
    }

    /**
     * Maps domain entrant group data into UI [EntrantGroupInfo] list with localized titles and smart player names.
     */
    fun mapEntrantGroupsToUi(
        context: Context,
        domainGroups: List<EntrantGroupDomainData>
    ): List<EntrantGroupInfo> {
        return domainGroups.map { group ->
            val title = when (group.groupType) {
                EntrantGroupType.OOM_65_96 -> context.getString(R.string.entrant_reason_oom_65_96)
                EntrantGroupType.OOM_33_64 -> context.getString(R.string.entrant_reason_oom_33_64)
                EntrantGroupType.OOM_TOP_32 -> context.getString(R.string.entrant_reason_oom_top_32)
                EntrantGroupType.ROUND_WINNERS -> context.getString(R.string.entrant_reason_round_winners, group.prevRoundName ?: "")
            }
            val players = group.players.map { p ->
                PlayerUiInfo(
                    id = p.id,
                    name = p.name,
                    countryCode = p.countryCode,
                    seed = p.seed,
                    skill = p.skill
                )
            }
            EntrantGroupInfo(title = title, players = players)
        }
    }

    /**
     * Returns the board count for a qualifier event.
     */
    fun getQualifierBoardCount(event: TournamentEvent): Int {
        return if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4
    }

    /**
     * Finds the default board index containing the user match, or 0 if user is not found.
     */
    fun findDefaultBoardIndex(
        allRoundsMatches: List<List<MatchUiModel>>,
        boardCount: Int
    ): Int {
        val userMatch = allRoundsMatches.flatten().find { it.isUserMatch } ?: return 0
        val event = try { TournamentEvent.valueOf(userMatch.match.tournamentId.toString()) } catch (_: Exception) { null }
        val currentRound = try { TournamentRound.valueOf(userMatch.match.round) } catch (_: Exception) { null }

        return when {
            userMatch.match.round == TournamentRound.QUAL_PRELIM.name -> {
                userMatch.targetBoard?.minus(1) ?: 0
            }
            userMatch.match.round == TournamentRound.QUAL_R1.name -> {
                userMatch.match.matchIndex % boardCount
            }
            userMatch.match.round == TournamentRound.QUAL_R2.name -> {
                (userMatch.match.matchIndex * 2) % boardCount
            }
            userMatch.match.round == TournamentRound.QUAL_FINAL.name -> {
                (userMatch.match.matchIndex * 4) % boardCount
            }
            event != null && currentRound != null -> {
                AdvanceTournamentRoundUseCase.resolveNextRoundSlot(event, currentRound, userMatch.match.matchIndex).first % boardCount
            }
            else -> 0
        }
    }

    /**
     * Filters matches for all rounds based on the selected board index (-1 = ALL, >= 0 = specific board).
     */
    fun filterMatchesForBoard(
        allRoundsMatches: List<List<MatchUiModel>>,
        boardIndex: Int,
        boardCount: Int,
        qualificationUseCase: TournamentQualificationUseCase
    ): List<List<MatchUiModel>> {
        if (boardIndex < 0) return allRoundsMatches

        val qualR1Round = allRoundsMatches.flatten().filter { it.match.round == TournamentRound.QUAL_R1.name }

        return allRoundsMatches.map { roundMatches ->
            roundMatches.filter { model ->
                when (model.match.round) {
                    TournamentRound.QUAL_PRELIM.name -> {
                        val board = qualificationUseCase.resolvePrelimTargetBoard(
                            qualR1Matches = qualR1Round.map { it.match },
                            prelimMatchIndex = model.match.matchIndex,
                            winnerId = model.match.winnerId,
                            boardCount = boardCount
                        )
                        (board - 1) == boardIndex
                    }
                    TournamentRound.QUAL_R1.name -> {
                        (model.match.matchIndex % boardCount) == boardIndex
                    }
                    TournamentRound.QUAL_R2.name -> {
                        ((model.match.matchIndex * 2) % boardCount) == boardIndex ||
                            (((model.match.matchIndex * 2) + 1) % boardCount) == boardIndex
                    }
                    TournamentRound.QUAL_FINAL.name -> {
                        val startBoard = (model.match.matchIndex * 4) % boardCount
                        boardIndex in startBoard until (startBoard + 4).coerceAtMost(boardCount)
                    }
                    else -> true
                }
            }
        }
    }

    /**
     * Binds round pills/chips into a [ChipGroup] with status colors and auto-scrolls to the selected chip.
     */
    fun bindRoundChips(
        chipGroup: ChipGroup,
        scrollContainer: HorizontalScrollView,
        roundDisplayNames: List<String>,
        roundStatuses: List<RoundStatus>?,
        selectedIndex: Int,
        onRoundSelected: (Int) -> Unit
    ) {
        chipGroup.removeAllViews()
        val context = chipGroup.context
        roundDisplayNames.forEachIndexed { index, name ->
            val status = roundStatuses?.getOrNull(index) ?: RoundStatus.PENDING
            val chip = Chip(context).apply {
                text = name
                isCheckable = true
                isChecked = (index == selectedIndex)

                val (bgColorRes, textColorRes, strokeColorRes) = when (status) {
                    RoundStatus.COMPLETED -> Triple(R.color.anthrazit_flaeche, R.color.grau_text_hell, R.color.status_completed)
                    RoundStatus.ACTIVE -> Triple(R.color.rot_container_dunkel, R.color.white, R.color.status_active)
                    RoundStatus.PENDING -> Triple(R.color.anthrazit_flaeche, R.color.grau_text_gedaempft, R.color.grau_rahmen_dunkel)
                }

                chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, bgColorRes))
                setTextColor(ContextCompat.getColor(context, textColorRes))
                chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, strokeColorRes))
                chipStrokeWidth = (1.5f * context.resources.displayMetrics.density)

                setOnClickListener {
                    onRoundSelected(index)
                }
            }
            chipGroup.addView(chip)
        }

        scrollContainer.post {
            val selectedChip = chipGroup.getChildAt(selectedIndex) ?: return@post
            val scrollX = selectedChip.left - (scrollContainer.width / 2) + (selectedChip.width / 2)
            scrollContainer.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    /**
     * Attaches a horizontal swipe gesture detector to views to switch rounds left/right.
     */
    fun attachHorizontalSwipe(
        views: List<View>,
        recyclerViews: List<RecyclerView>,
        onSwipeLeft: () -> Unit,
        onSwipeRight: () -> Unit
    ) {
        val gestureDetector = GestureDetector(views.firstOrNull()?.context ?: return, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        onSwipeLeft()
                    } else {
                        onSwipeRight()
                    }
                    return true
                }
                return false
            }
        })

        val touchListener = View.OnTouchListener { v, event ->
            v.performClick()
            gestureDetector.onTouchEvent(event)
        }

        views.forEach { view ->
            view.setOnTouchListener(touchListener)
        }

        recyclerViews.forEach { rv ->
            rv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(e)
                    return false
                }
            })
        }
    }

    /**
     * Scrolls the given [RecyclerView] smoothly so that the item at [targetPosition] is centered vertically.
     */
    fun scrollToMatchCentered(recyclerView: RecyclerView, targetPosition: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
                val containerHeight = recyclerView.height
                val viewHeight = view.height
                val targetTop = view.top
                return targetTop - (containerHeight / 2) + (viewHeight / 2)
            }

            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
        }
        smoothScroller.targetPosition = targetPosition
        layoutManager.startSmoothScroll(smoothScroller)
    }
}
