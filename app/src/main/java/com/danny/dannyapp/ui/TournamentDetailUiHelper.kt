package com.danny.dannyapp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
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
import com.danny.dannyapp.domain.usecase.BracketGenerator
import com.danny.dannyapp.domain.usecase.EntrantGroupDomainData
import com.danny.dannyapp.domain.usecase.EntrantGroupType
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.divider.MaterialDivider
import kotlin.math.min

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
        return if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4
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

    /**
     * Resolves the target match index in round [r] + 1 for match [m] in round [r].
     *
     * @param roundsMatches Full list of round match UI models.
     * @param r Current round index.
     * @param m Current match index within round [r].
     * @return Target match index in round [r] + 1.
     */
    fun resolveBracketTargetMatchIndex(roundsMatches: List<List<MatchUiModel>>, r: Int, m: Int): Int {
        val roundMatchesR = roundsMatches.getOrNull(r) ?: return m
        val roundMatchesNext = roundsMatches.getOrNull(r + 1) ?: return m
        val sizeCurr = roundMatchesR.size
        val sizeNext = roundMatchesNext.size
        val matchItem = roundMatchesR.getOrNull(m)

        return when {
            sizeCurr == sizeNext * 2 -> m / 2
            sizeCurr == sizeNext -> {
                if (matchItem?.isCompleted == true && (matchItem.winnerId ?: 0L) > 0L) {
                    val idx = roundMatchesNext.indexOfFirst { it.player1.id == matchItem.winnerId || it.player2.id == matchItem.winnerId }
                    if (idx >= 0) {
                        return idx
                    }
                }
                val isL48Round = matchItem?.match?.round == TournamentRound.L48.name || roundMatchesR.firstOrNull()?.match?.round == TournamentRound.L48.name
                if (isL48Round) {
                    val targetIdx = BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(sizeNext - 1 - m)
                    if (targetIdx >= 0) targetIdx else m
                } else {
                    m
                }
            }
            sizeCurr < sizeNext -> {
                if (matchItem?.isCompleted == true && (matchItem.winnerId ?: 0L) > 0L) {
                    val idx = roundMatchesNext.indexOfFirst { it.player1.id == matchItem.winnerId || it.player2.id == matchItem.winnerId }
                    if (idx >= 0) return idx
                }
                if (matchItem != null) {
                    val targetBoard = matchItem.targetBoard
                    if (targetBoard != null) {
                        val targetGlobalIndex = 4 * targetBoard + 3
                        val idx = roundMatchesNext.indexOfFirst { it.match.matchIndex == targetGlobalIndex }
                        if (idx >= 0) return idx
                    }
                }
                if (sizeNext == 4) {
                    min(3, sizeNext - 1)
                } else {
                    (sizeNext - sizeCurr + m).coerceIn(0, sizeNext - 1)
                }
            }
            else -> m / 2
        }
    }

    /**
     * Dynamically builds round filter [Chip] views into [chipGroup] and smooth-scrolls [scrollContainer]
     * to keep the selected chip centered.
     *
     * @param chipGroup The container layout for the round chips.
     * @param scrollContainer The parent [HorizontalScrollView] containing the chip group.
     * @param roundDisplayNames List of display names for each round.
     * @param roundStatuses Optional list of [RoundStatus] per round index (defaults to [RoundStatus.COMPLETED]).
     * @param selectedIndex Currently selected round index.
     * @param onRoundSelected Callback invoked when a round chip is clicked.
     */
    fun bindRoundChips(
        chipGroup: ChipGroup,
        scrollContainer: HorizontalScrollView,
        roundDisplayNames: List<String>,
        roundStatuses: List<RoundStatus>? = null,
        selectedIndex: Int,
        onRoundSelected: (Int) -> Unit
    ) {
        val context = chipGroup.context
        chipGroup.removeAllViews()
        var selectedChip: Chip? = null

        roundDisplayNames.forEachIndexed { index, displayName ->
            val status = roundStatuses?.getOrNull(index) ?: RoundStatus.COMPLETED
            val chip = Chip(context).apply {
                text = displayName
                isCheckable = true
                isChecked = (index == selectedIndex)

                val (bgRes, textRes) = when (status) {
                    RoundStatus.COMPLETED -> R.color.status_completed to R.color.grau_text_gedaempft
                    RoundStatus.ACTIVE -> R.color.status_active to R.color.white
                    RoundStatus.PENDING -> R.color.anthrazit_container_niedrig to R.color.grau_text_gedaempft
                }

                chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(context, bgRes))
                setTextColor(ContextCompat.getColor(context, textRes))

                if (isChecked) {
                    chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
                    chipStrokeWidth = 2 * context.resources.displayMetrics.density
                } else {
                    chipStrokeWidth = 0f
                }

                setOnClickListener {
                    onRoundSelected(index)
                }
            }
            chipGroup.addView(chip)
            if (index == selectedIndex) {
                selectedChip = chip
            }
        }

        selectedChip?.let { chip ->
            chip.post {
                val scrollX = chip.left - (scrollContainer.width - chip.width) / 2
                scrollContainer.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }
    }

    /**
     * Smoothly scrolls a [RecyclerView] to center the match item at [position].
     *
     * @param recyclerView Target RecyclerView.
     * @param position Zero-based adapter position of the target item.
     */
    fun scrollToMatchCentered(recyclerView: RecyclerView, position: Int) {
        if (position < 0) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }
        }
        smoothScroller.targetPosition = position
        layoutManager.startSmoothScroll(smoothScroller)
    }

    /**
     * Maps domain entrant groups to UI entrant group information.
     */
    fun mapEntrantGroupsToUi(context: Context, domainGroups: List<EntrantGroupDomainData>): List<EntrantGroupInfo> {
        return domainGroups.map { domainGroup ->
            val title = when (domainGroup.groupType) {
                EntrantGroupType.OOM_65_96 -> context.getString(R.string.entrant_reason_oom_65_96)
                EntrantGroupType.OOM_33_64 -> context.getString(R.string.entrant_reason_oom_33_64)
                EntrantGroupType.OOM_TOP_32 -> context.getString(R.string.entrant_reason_oom_top_32)
                EntrantGroupType.ROUND_WINNERS -> {
                    val prevRoundDisplayName = TournamentUiHelper.getDisplayRoundName(
                        domainGroup.prevRoundName ?: "",
                        context
                    )
                    context.getString(R.string.entrant_reason_round_winners, prevRoundDisplayName)
                }
            }
            val players = domainGroup.players.map { player ->
                PlayerUiInfo(
                    id = player.id,
                    name = player.name,
                    countryCode = player.countryCode,
                    seed = player.seed,
                    skill = player.skill
                )
            }
            EntrantGroupInfo(title = title, players = players)
        }
    }
}
