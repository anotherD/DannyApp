package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
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
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.EntrantGroupDomainData
import com.danny.dannyapp.domain.usecase.EntrantGroupType
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.divider.MaterialDivider
import kotlin.math.abs
import kotlin.math.min

/**
 * UI helper class for [TournamentDetailActivity] to render open draw entrants,
 * manage qualifier boards, filter matches, and handle horizontal swipe gestures.
 */
object TournamentDetailUiHelper {

    /**
     * Renders open draw entrant groups (UK Open / European Open) into the given container layout.
     */
    fun renderOpenDrawEntrants(
        context: Context,
        container: LinearLayout,
        groups: List<EntrantGroupInfo>,
        isPreliminary: Boolean = false
    ) {
        container.visibility = View.VISIBLE
        container.removeAllViews()
        groups.forEachIndexed { groupIndex, group ->
            val groupView = LayoutInflater.from(context).inflate(R.layout.item_entrant_group, container, false)
            val tvGroupTitle = groupView.findViewById<TextView>(R.id.tvEntrantGroupTitle)
            val divider = groupView.findViewById<View>(R.id.dividerEntrantGroup)

            tvGroupTitle.text = group.title
            if (groupIndex == 0) {
                divider.visibility = View.GONE
            } else {
                divider.visibility = View.VISIBLE
            }
            container.addView(groupView)

            group.players.chunked(2).forEach { rowPlayers ->
                val rowView = LayoutInflater.from(context).inflate(R.layout.item_entrant_row, container, false)
                val tvPlayerLeft = rowView.findViewById<TextView>(R.id.tvEntrantPlayerLeft)
                val tvPlayerRight = rowView.findViewById<TextView>(R.id.tvEntrantPlayerRight)

                if (rowPlayers.isNotEmpty()) {
                    tvPlayerLeft.alpha = if (isPreliminary) 0.72f else 1.0f
                    UIHelper.bindSmartPlayerName(tvPlayerLeft, rowPlayers[0])
                }
                if (rowPlayers.size > 1) {
                    tvPlayerRight.alpha = if (isPreliminary) 0.72f else 1.0f
                    UIHelper.bindSmartPlayerName(tvPlayerRight, rowPlayers[1])
                    tvPlayerRight.visibility = View.VISIBLE
                } else {
                    tvPlayerRight.visibility = View.INVISIBLE
                }

                container.addView(rowView)
            }
        }
    }

    /**
     * Returns the number of qualifier boards for the given tournament event.
     */
    fun getQualifierBoardCount(event: TournamentEvent): Int {
        if (!event.isQualifier) return 0
        return when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WORLD_MASTERS -> 8
            TournamentEvent.QUALIFIER_WM -> 2
            else -> 8
        }
    }

    fun getQualifierBoardCountFromMatches(event: TournamentEvent, allMatches: List<TournamentMatch>): Int {
        val qualFinal = allMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }
        if (qualFinal.isNotEmpty()) return qualFinal.size
        return getQualifierBoardCount(event)
    }

    fun getQualifierBoardCountFromRounds(event: TournamentEvent, allRoundsMatches: List<List<MatchUiModel>>): Int {
        val matches = allRoundsMatches.flatten().map { it.match }
        return getQualifierBoardCountFromMatches(event, matches)
    }

    /**
     * Determines the default board index for the user's match, or -1 if none found.
     */
    fun findDefaultBoardIndex(allRoundsMatches: List<List<MatchUiModel>>, boardCount: Int): Int {
        val userMatch = allRoundsMatches.flatten().find { it.isUserMatch }
        if (userMatch != null) {
            val roundKey = userMatch.match.round
            val mIdx = userMatch.match.matchIndex
            val qualR1Models = allRoundsMatches.firstOrNull { list ->
                list.firstOrNull()?.match?.round == TournamentRound.QUAL_R1.name
            } ?: emptyList()
            val qualR1Matches = qualR1Models.map { it.match }
            require(qualR1Matches.isNotEmpty() && boardCount > 0) {
                "Qualifier R1 matches must not be empty and boardCount must be > 0"
            }
            val r1MatchesPerBoard = qualR1Matches.size / boardCount
            val boardIdx = when {
                roundKey == TournamentRound.QUAL_FINAL.name -> mIdx
                else -> {
                    val roundNumber = TournamentRound.parseQualifierRoundNumber(roundKey) ?: return -1
                    val divisor = 1 shl (roundNumber - 1)
                    val matchesPerBoard = (r1MatchesPerBoard / divisor).coerceAtLeast(1)
                    mIdx / matchesPerBoard
                }
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
        boardCount: Int
    ): List<List<MatchUiModel>> {
        if (boardIndex !in 0 until boardCount) {
            return allRoundsMatches
        }

        val qualR1Models = allRoundsMatches.firstOrNull { list ->
            list.firstOrNull()?.match?.round == TournamentRound.QUAL_R1.name
        } ?: emptyList()
        val qualR1Matches = qualR1Models.map { it.match }
        require(qualR1Matches.isNotEmpty() && boardCount > 0) {
            "Qualifier R1 matches must not be empty and boardCount must be > 0"
        }
        val r1MatchesPerBoard = qualR1Matches.size / boardCount

        return allRoundsMatches.map { roundMatches ->
            if (roundMatches.isEmpty()) return@map emptyList()
            val roundKey = roundMatches.first().match.round
            when {
                roundKey == TournamentRound.QUAL_FINAL.name -> {
                    roundMatches.filter { it.match.matchIndex == boardIndex }
                }
                TournamentRound.parseQualifierRoundNumber(roundKey) != null -> {
                    val roundNumber = TournamentRound.parseQualifierRoundNumber(roundKey)!!
                    val divisor = 1 shl (roundNumber - 1)
                    val matchesPerBoard = (r1MatchesPerBoard / divisor).coerceAtLeast(1)
                    val startIdx = matchesPerBoard * boardIndex
                    val endIdx = startIdx + matchesPerBoard - 1
                    roundMatches.filter { it.match.matchIndex in startIdx..endIdx }
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
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
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
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
                return gestureDetector.onTouchEvent(event)
            }
        }
        recyclerViews.forEach { rv ->
            rv.addOnItemTouchListener(rvTouchListener)
        }

        views.forEach { view ->
            view.setOnTouchListener { targetView, event ->
                val handled = gestureDetector.onTouchEvent(event)
                if (!handled && event.action == MotionEvent.ACTION_UP) {
                    targetView.performClick()
                }
                handled
            }
        }
    }

    /**
     * Resolves the target match index in round [roundIndex] + 1 for match [matchIndex] in round [roundIndex].
     *
     * @param roundsMatches Full list of round match UI models.
     * @param roundIndex Current round index.
     * @param matchIndex Current match index within round [roundIndex].
     * @return Target match index in round [roundIndex] + 1.
     */
    fun resolveBracketTargetMatchIndex(roundsMatches: List<List<MatchUiModel>>, roundIndex: Int, matchIndex: Int): Int {
        val roundMatchesR = roundsMatches.getOrNull(roundIndex) ?: return matchIndex
        val roundMatchesNext = roundsMatches.getOrNull(roundIndex + 1) ?: return matchIndex
        val sizeCurr = roundMatchesR.size
        val sizeNext = roundMatchesNext.size
        val matchItem = roundMatchesR.getOrNull(matchIndex)

        val roundKey = matchItem?.match?.round ?: roundMatchesR.firstOrNull()?.match?.round
        val isOpenDrawRound = roundKey == TournamentRound.R1_160.name ||
            roundKey == TournamentRound.R2_128.name ||
            roundKey == TournamentRound.R3_96.name

        if (isOpenDrawRound && roundMatchesNext.isEmpty()) {
            return -1
        }

        return when {
            sizeCurr == sizeNext * 2 -> matchIndex / 2
            sizeCurr == sizeNext -> {
                if (isOpenDrawRound) {
                    val winnerId = matchItem?.winnerId
                    if (winnerId != null && (winnerId > 0L || winnerId == USER_ID)) {
                        val idx = roundMatchesNext.indexOfFirst {
                            it.player1.id == winnerId || it.player2.id == winnerId ||
                                it.match.player1Id == winnerId || it.match.player2Id == winnerId
                        }
                        if (idx >= 0) return idx
                    }
                    -1
                } else {
                    if (matchItem?.isCompleted == true && (matchItem.winnerId ?: 0L) > 0L) {
                        val idx = roundMatchesNext.indexOfFirst { it.player1.id == matchItem.winnerId || it.player2.id == matchItem.winnerId }
                        if (idx >= 0) {
                            return idx
                        }
                    }
                    val isL48Round = matchItem?.match?.round == TournamentRound.L48.name || roundMatchesR.firstOrNull()?.match?.round == TournamentRound.L48.name
                    if (isL48Round) {
                        val targetIdx = AdvanceTournamentRoundUseCase.resolveNextRoundSlot(TournamentEvent.EUROPEAN_TOUR, TournamentRound.L48, matchIndex).first
                        if (targetIdx >= 0) targetIdx else matchIndex
                    } else {
                        matchIndex
                    }
                }
            }
            sizeCurr < sizeNext -> {
                if (matchItem?.isCompleted == true && (matchItem.winnerId ?: 0L) > 0L) {
                    val idx = roundMatchesNext.indexOfFirst { it.player1.id == matchItem.winnerId || it.player2.id == matchItem.winnerId }
                    if (idx >= 0) return idx
                }

                if (sizeNext == 4) {
                    min(3, sizeNext - 1)
                } else {
                    (sizeNext - sizeCurr + matchIndex).coerceIn(0, sizeNext - 1)
                }
            }
            else -> matchIndex / 2
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
        val inflater = LayoutInflater.from(context)
        chipGroup.removeAllViews()
        var selectedChip: Chip? = null

        roundDisplayNames.forEachIndexed { index, displayName ->
            val status = roundStatuses?.getOrNull(index) ?: RoundStatus.COMPLETED
            val chip = (inflater.inflate(R.layout.item_control_chip, chipGroup, false) as Chip).apply {
                text = displayName
                isCheckable = true
                isChecked = (index == selectedIndex)

                UIHelper.styleSelectionChip(this, isChecked, status)

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
