package com.danny.dannyapp.ui.tournament

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.GrandSlamGroupItem
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class TournamentBracketView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onMatchClick: ((MatchUiModel) -> Unit)? = null
    var onRoundSelected: ((Int) -> Unit)? = null

    private var grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>> = emptyMap()
    private var openEntrantsGroups: List<EntrantGroupInfo> = emptyList()
    private var prevRoundDisplayName: String = ""
    private var isOpenDrawInfoVisible: Boolean = false
    private var openDrawInfoMap: Map<Int, Pair<String, List<EntrantGroupInfo>>> = emptyMap()

    private var roundsMatches: List<List<MatchUiModel>> = emptyList()
    private var matchViews: List<List<View>> = emptyList()
    private var headerDisplayNames: List<String> = emptyList()
    private var headerStatuses: List<RoundStatus> = emptyList()
    private var headerViews: List<View> = emptyList()
    private var groupTableHeaderView: View? = null

    private fun isGrandSlamGroupStage(): Boolean {
        val firstMatchRoundKey = roundsMatches.firstOrNull()?.firstOrNull()?.match?.round
        return grandSlamGroupStandings.isNotEmpty() && (firstMatchRoundKey == TournamentRound.GROUP.name)
    }

    private val TILE_HEIGHT_DP = 68f
    private val SLOT_HEIGHT_DP = 76f

    private var selectedRoundIndex: Int = 0
    private var focusedRoundFloat: Float = 0f
    private var scrollYOffset: Float = 0f

    private var focusedAnimator: ValueAnimator? = null
    private val overScroller: OverScroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity: Int = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity: Int = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var isBeingDragged: Boolean = false
    private var dragDirection: Int = 0 // 0 = none, 1 = horizontal, 2 = vertical

    private val density: Float = resources.displayMetrics.density
    private val linePaintDefault = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.grau_rahmen_dunkel)
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
    }
    private val linePaintHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.rot_haupt)
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
    }

    init {
        setWillNotDraw(false)
    }

    /**
     * Setzt die Runden-Header (Namen und Status) für den Turnierbaum.
     */
    fun setRoundHeaders(displayNames: List<String>, statuses: List<RoundStatus>) {
        if (this.headerDisplayNames != displayNames || this.headerStatuses != statuses) {
            this.headerDisplayNames = displayNames
            this.headerStatuses = statuses
            rebuildHeaderViews()
            requestLayout()
            invalidate()
        } else {
            updateHeaderStyles()
        }
    }

    /**
     * Setzt die Runden- und Match-Daten des Turnierbaums.
     */
    fun setRounds(
        roundsMatches: List<List<MatchUiModel>>,
        initialRoundIndex: Int = 0,
        grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>> = emptyMap(),
        prevRoundDisplayName: String = "",
        openEntrantsGroups: List<EntrantGroupInfo> = emptyList(),
        isOpenDrawInfoVisible: Boolean = false,
        openDrawInfoMap: Map<Int, Pair<String, List<EntrantGroupInfo>>> = emptyMap()
    ) {
        val roundsChanged = (this.roundsMatches != roundsMatches)
        val standingsChanged = (this.grandSlamGroupStandings != grandSlamGroupStandings)
        val openDrawChanged = (this.openEntrantsGroups != openEntrantsGroups || this.prevRoundDisplayName != prevRoundDisplayName || this.isOpenDrawInfoVisible != isOpenDrawInfoVisible || this.openDrawInfoMap != openDrawInfoMap)
        
        this.grandSlamGroupStandings = grandSlamGroupStandings
        this.prevRoundDisplayName = prevRoundDisplayName
        this.openEntrantsGroups = openEntrantsGroups
        this.isOpenDrawInfoVisible = isOpenDrawInfoVisible
        this.openDrawInfoMap = openDrawInfoMap

        if (roundsChanged || standingsChanged || openDrawChanged) {
            this.roundsMatches = roundsMatches
            focusedAnimator?.cancel()
            overScroller.forceFinished(true)

            val targetRound = initialRoundIndex.coerceIn(0, (roundsMatches.size - 1).coerceAtLeast(0))
            selectedRoundIndex = targetRound
            focusedRoundFloat = targetRound.toFloat()
            if (roundsChanged) {
                scrollYOffset = 0f
            }

            rebuildViews()
            requestLayout()
            invalidate()
        } else {
            selectRound(initialRoundIndex, animate = false)
        }
    }

    /**
     * Wählt eine spezifische Runde im Turnierbaum aus und zentriert diese.
     */
    fun selectRound(roundIndex: Int, animate: Boolean = true) {
        if (roundsMatches.isEmpty()) return
        val targetIndex = roundIndex.coerceIn(0, roundsMatches.lastIndex)
        selectedRoundIndex = targetIndex
        updateHeaderStyles()

        val startFocus = focusedRoundFloat
        val targetFocus = targetIndex.toFloat()

        val targetMaxScrollY = computeMaxScrollYForFocus(targetFocus)
        val currentMaxScrollY = maxScrollYForCurrentFocus()
        val targetScrollY = if (currentMaxScrollY > 0) {
            (scrollYOffset / currentMaxScrollY) * targetMaxScrollY
        } else 0f
        val clampedTargetScrollY = targetScrollY.coerceIn(0f, targetMaxScrollY)

        focusedAnimator?.cancel()

        if (animate && abs(targetFocus - startFocus) > 0.001f) {
            val startScrollY = scrollYOffset
            focusedAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    focusedRoundFloat = startFocus + (targetFocus - startFocus) * fraction
                    scrollYOffset = startScrollY + (clampedTargetScrollY - startScrollY) * fraction
                    requestLayout()
                    invalidate()
                }
                start()
            }
        } else {
            focusedRoundFloat = targetFocus
            scrollYOffset = clampedTargetScrollY
            requestLayout()
            invalidate()
        }
    }

    /**
     * Scrollt den Turnierbaum auf das User-Match in der angegebenen Runde.
     * Sucht den Match-Index direkt in den gefilterten [roundsMatches], damit auch bei aktivem
     * Qualifier-Board-Filter die korrekte Kachel getroffen wird.
     * Runden-Schwenk und vertikale Zentrierung werden in einer gemeinsamen Animation ausgeführt.
     */
    fun scrollToUserMatch(targetRoundIndex: Int) {
        if (roundsMatches.isEmpty()) return
        val clampedRoundIndex = targetRoundIndex.coerceIn(0, roundsMatches.lastIndex)
        val userMatchIndex = roundsMatches.getOrNull(clampedRoundIndex)
            ?.indexOfFirst { matchUiModel -> matchUiModel.isUserMatch }
            ?: -1
        if (userMatchIndex == -1) return

        val startFocusedRound = focusedRoundFloat
        val targetFocusedRound = clampedRoundIndex.toFloat()
        val startScrollY = scrollYOffset

        selectedRoundIndex = clampedRoundIndex
        updateHeaderStyles()

        val targetCenterY = computeMatchCenterY(clampedRoundIndex, userMatchIndex, targetFocusedRound)
        val viewHeight = height.toFloat()
        val headerHeightPx = if (isGrandSlamGroupStage()) 60f * density else 40f * density
        val availableHeight = viewHeight - headerHeightPx - paddingTop.toFloat() - paddingBottom.toFloat()
        val maxScrollYAtTarget = computeMaxScrollYForFocus(targetFocusedRound)
        val targetScrollY = (targetCenterY - headerHeightPx - paddingTop.toFloat() - availableHeight / 2f)
            .coerceIn(0f, maxScrollYAtTarget)

        val roundNeedsAnimation = abs(targetFocusedRound - startFocusedRound) > 0.001f
        val scrollNeedsAnimation = abs(targetScrollY - startScrollY) > 1f

        focusedAnimator?.cancel()

        if (roundNeedsAnimation || scrollNeedsAnimation) {
            focusedAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    if (roundNeedsAnimation) {
                        focusedRoundFloat = startFocusedRound + (targetFocusedRound - startFocusedRound) * fraction
                    }
                    if (scrollNeedsAnimation) {
                        scrollYOffset = startScrollY + (targetScrollY - startScrollY) * fraction
                    }
                    requestLayout()
                    invalidate()
                }
                start()
            }
        } else {
            focusedRoundFloat = targetFocusedRound
            scrollYOffset = targetScrollY
            requestLayout()
            invalidate()
        }
    }

    /**
     * Aktualisiert die Kachel-Farbschemata ohne Neuaufbau.
     */
    fun refreshStyles() {
        for (roundIndex in matchViews.indices) {
            val views = matchViews[roundIndex]
            val models = roundsMatches.getOrNull(roundIndex) ?: continue
            for (matchIndex in views.indices) {
                val item = models.getOrNull(matchIndex) ?: continue
                bindMatchView(views[matchIndex], item)
            }
        }
        updateHeaderStyles()
        invalidate()
    }

    private fun rebuildViews() {
        for (list in matchViews) {
            for (childView in list) {
                removeView(childView)
            }
        }
        matchViews = emptyList()
        groupTableHeaderView?.let {
            removeView(it)
        }
        groupTableHeaderView = null

        val inflater = LayoutInflater.from(context)
        val newMatchViews = mutableListOf<List<View>>()
        var insertIndex = 0

        val isGsGroupStage = isGrandSlamGroupStage()

        for ((roundIdx, roundMatches) in roundsMatches.withIndex()) {
            val currentRoundViews = mutableListOf<View>()
            if (roundIdx == 0 && isGsGroupStage) {
                val groupLetters = ('A'..'P').map { it.toString() }
                for (groupLetter in groupLetters) {
                    val standings = grandSlamGroupStandings[groupLetter] ?: emptyList()
                    val cardView = TournamentBracketCardBuilder.createGroupCardView(context, density, groupLetter, standings, isLargeLayout = false, parent = this)
                    addView(cardView, insertIndex++)
                    currentRoundViews.add(cardView)
                }
            } else if (roundMatches.isEmpty() && (openDrawInfoMap.containsKey(roundIdx) || openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)) {
                val (prevName, groups) = openDrawInfoMap[roundIdx] ?: Pair(prevRoundDisplayName, openEntrantsGroups)
                val infoCardView = TournamentBracketCardBuilder.createOpenDrawInfoCardView(context, density, prevName, groups)
                addView(infoCardView, insertIndex++)
                currentRoundViews.add(infoCardView)
            } else {
                for (item in roundMatches) {
                    val cardView = inflater.inflate(R.layout.item_bracket_match, this, false)
                    bindMatchView(cardView, item)
                    addView(cardView, insertIndex++)
                    currentRoundViews.add(cardView)
                }
            }
            newMatchViews.add(currentRoundViews)
        }
        this.matchViews = newMatchViews

        if (isGsGroupStage) {
            val headerView = TournamentBracketCardBuilder.inflateBracketGroupHeaderView(context, this)
            addView(headerView, insertIndex++)
            groupTableHeaderView = headerView
        }
    }

    private fun rebuildHeaderViews() {
        for (headerView in headerViews) {
            removeView(headerView)
        }
        val newHeaderViews = mutableListOf<View>()
        for ((roundIndex, displayName) in headerDisplayNames.withIndex()) {
            val status = headerStatuses.getOrNull(roundIndex) ?: RoundStatus.PENDING
            val headerView = createHeaderView(roundIndex, displayName, status)
            addView(headerView)
            newHeaderViews.add(headerView)
        }
        headerViews = newHeaderViews
    }

    private fun createHeaderView(roundIndex: Int, displayName: String, status: RoundStatus): View {
        val textView = TextView(context).apply {
            text = displayName
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val pxPaddingH = (14 * density).toInt()
            val pxPaddingV = (6 * density).toInt()
            setPadding(pxPaddingH, pxPaddingV, pxPaddingH, pxPaddingV)
            minWidth = (72 * density).toInt()

            setOnClickListener {
                selectRound(roundIndex)
                onRoundSelected?.invoke(roundIndex)
            }
        }
        styleHeaderView(textView, roundIndex, status)
        return textView
    }

    private fun styleHeaderView(textView: TextView, roundIndex: Int, status: RoundStatus) {
        val isSelected = (roundIndex == selectedRoundIndex)
        val bgRes = when (status) {
            RoundStatus.COMPLETED -> R.color.status_completed
            RoundStatus.ACTIVE -> R.color.status_active
            RoundStatus.PENDING -> R.color.anthrazit_container_niedrig
        }
        val textRes = when {
            isSelected -> R.color.white
            status == RoundStatus.ACTIVE -> R.color.white
            else -> R.color.grau_text_gedaempft
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(ContextCompat.getColor(context, bgRes))
            if (isSelected) {
                setStroke((2f * density).toInt(), ContextCompat.getColor(context, R.color.white))
            } else {
                setStroke((1f * density).toInt(), ContextCompat.getColor(context, R.color.grau_rahmen_dunkel))
            }
        }
        textView.background = bgDrawable
        textView.setTextColor(ContextCompat.getColor(context, textRes))
    }

    private fun updateHeaderStyles() {
        for ((roundIndex, view) in headerViews.withIndex()) {
            val textView = view as? TextView ?: continue
            val status = headerStatuses.getOrNull(roundIndex) ?: RoundStatus.PENDING
            styleHeaderView(textView, roundIndex, status)
        }
    }

    private fun bindMatchView(view: View, item: MatchUiModel) {
        val card = view as? MaterialCardView ?: view.findViewById(R.id.cardBracketMatch)
        val tvBracketMatchIndex: TextView = view.findViewById(R.id.tvBracketMatchIndex)
        val tvPlayer1Name: TextView = view.findViewById(R.id.tvPlayer1Name)
        val tvPlayer1Score: TextView = view.findViewById(R.id.tvPlayer1Score)
        val tvPlayer2Name: TextView = view.findViewById(R.id.tvPlayer2Name)
        val tvPlayer2Score: TextView = view.findViewById(R.id.tvPlayer2Score)

        val match = item.match
        tvBracketMatchIndex.text = TournamentUiHelper.formatMatchBadgeText(context, match)

        UIHelper.bindSmartPlayerName(tvPlayer1Name, item.player1)
        UIHelper.bindSmartPlayerName(tvPlayer2Name, item.player2)

        UIHelper.applyMatchCardStyle(
            card = card,
            item = item,
            player1Name = tvPlayer1Name,
            player1Score = tvPlayer1Score,
            player2Name = tvPlayer2Name,
            player2Score = tvPlayer2Score
        )

        card.setOnClickListener {
            onMatchClick?.invoke(item)
        }
    }

    private fun getTargetMatchIndex(r: Int, m: Int): Int {
        return TournamentDetailUiHelper.resolveBracketTargetMatchIndex(roundsMatches, r, m)
    }

    private fun computeMatchCenterY(r: Int, m: Int, focus: Float): Float {
        val numRounds = roundsMatches.size
        if (numRounds == 0) return 0f

        val floorFocus = floor(focus).toInt().coerceIn(0, numRounds - 1)
        val ceilFocus = ceil(focus).toInt().coerceIn(0, numRounds - 1)
        val fraction = focus - floorFocus

        if (floorFocus == ceilFocus) {
            return computeIntegerFocusY(r, m, floorFocus)
        }
        val yFloor = computeIntegerFocusY(r, m, floorFocus)
        val yCeil = computeIntegerFocusY(r, m, ceilFocus)
        return yFloor + (yCeil - yFloor) * fraction
    }

    /**
     * Prüft, ob Runde [roundIndex] vertikal an die Folgerunde [roundIndex]+1 verankert werden soll.
     * Kriterium: [roundIndex] ist nicht die letzte Runde, die Folgerunde ist keine Open-Draw-Info-Karte
     * (`roundsMatches[roundIndex + 1].isNotEmpty()`), und die aktuelle Runde hat entweder weniger Kacheln
     * als die Folgerunde (`sizeCurr < sizeNext`, z. B. QUAL_PRELIM) oder die gleiche Kachelanzahl
     * (`sizeCurr == sizeNext`, z. B. L48 -> L32 oder bereits ausgeloste R1_160 -> R2_128 -> R3_96 -> L64)
     * mit gültigem Ziel-Match (`getTargetMatchIndex(roundIndex, 0) >= 0`).
     */
    private fun isAnchoredToNextRound(roundIndex: Int): Boolean {
        if (roundIndex >= roundsMatches.lastIndex) return false
        val nextRoundMatches = roundsMatches.getOrNull(roundIndex + 1) ?: return false
        if (nextRoundMatches.isEmpty()) return false

        val currentViewCount = matchViews.getOrNull(roundIndex)?.size ?: 0
        val nextViewCount = matchViews.getOrNull(roundIndex + 1)?.size ?: 0
        if (nextViewCount == 0) return false

        return if (currentViewCount < nextViewCount) {
            true
        } else if (currentViewCount == nextViewCount) {
            (0 until currentViewCount).any { getTargetMatchIndex(roundIndex, it) >= 0 }
        } else {
            false
        }
    }

    /**
     * Berechnet die vertikale Mitte (Y-Koordinate) einer Match-Kachel in Runde [roundIndex],
     * Index [matchIndex], wenn der Fokus auf ganzzahligem Rundenindex [focusInt] liegt.
     *
     * - **Spezialregel 1 (Verankerte Vorrunde/1:1-Runde):** Jede Runde mit [isAnchoredToNextRound]
     *   liegt immer exakt auf der Y-Position ihres Ziel-Matches in der Folgerunde – unabhängig
     *   von [focusInt].
     * - **Spezialregel 2 (Anker-Runde):** Die erste echte Runde nach einer 1:1-Kette (z. B. L64
     *   beim UK Open oder L32 bei der European Tour) bleibt stabil auf ihrem regulären Basis-Raster
     *   verankert.
     */
    private fun computeIntegerFocusY(roundIndex: Int, matchIndex: Int, focusInt: Int): Float {
        val tileHeight = TILE_HEIGHT_DP * density
        val baseSlotHeight = SLOT_HEIGHT_DP * density
        val headerHeight = if (isGrandSlamGroupStage()) 60f * density else 40f * density
        val paddingTopPx = paddingTop.toFloat() + headerHeight + 8f * density

        // ── Spezialregel 1: Verankerte Runde → immer auf Höhe des Ziel-Matches in der Folgerunde ──
        if (isAnchoredToNextRound(roundIndex)) {
            val targetMatchIndex = getTargetMatchIndex(roundIndex, matchIndex)
            if (targetMatchIndex >= 0) {
                return computeIntegerFocusY(roundIndex + 1, targetMatchIndex, focusInt)
            }
            return paddingTopPx + matchIndex * baseSlotHeight + tileHeight / 2f
        }

        if (roundIndex == focusInt) {
            return paddingTopPx + matchIndex * baseSlotHeight + tileHeight / 2f
        }

        if (roundIndex > focusInt) {
            val prevViewCount = matchViews.getOrNull(roundIndex - 1)?.size ?: 0
            val currentViewCount = matchViews.getOrNull(roundIndex)?.size ?: 0

            // ── Spezialregel 2: Erste echte Runde nach einer verankerten Kette → fester Raster-Anker ──
            val prevRoundWasAnchored = roundIndex > 0 && isAnchoredToNextRound(roundIndex - 1)
            if (prevRoundWasAnchored || prevViewCount < currentViewCount) {
                return paddingTopPx + matchIndex * baseSlotHeight + tileHeight / 2f
            }

            return when (prevViewCount) {
                currentViewCount * 2 -> {
                    val topChildY = computeIntegerFocusY(roundIndex - 1, 2 * matchIndex, focusInt)
                    val bottomChildY = computeIntegerFocusY(roundIndex - 1, 2 * matchIndex + 1, focusInt)
                    (topChildY + bottomChildY) / 2f
                }
                currentViewCount -> {
                    paddingTopPx + matchIndex * baseSlotHeight + tileHeight / 2f
                }
                else -> {
                    computeIntegerFocusY(roundIndex - 1, matchIndex, focusInt)
                }
            }
        } else {
            // roundIndex < focusInt
            val currentViewCount = matchViews.getOrNull(roundIndex)?.size ?: 0
            val nextViewCount = matchViews.getOrNull(roundIndex + 1)?.size ?: 0

            return if (nextViewCount * 2 == currentViewCount) {
                val parentY = computeIntegerFocusY(roundIndex + 1, matchIndex / 2, focusInt)
                val distanceToFocus = focusInt - roundIndex
                val spreadFactor = 0.5f.pow(distanceToFocus + 1)
                val verticalOffset = if (matchIndex % 2 == 0) -spreadFactor * SLOT_HEIGHT_DP * density else spreadFactor * SLOT_HEIGHT_DP * density
                parentY + verticalOffset
            } else {
                val targetMatchIndex = getTargetMatchIndex(roundIndex, matchIndex)
                computeIntegerFocusY(roundIndex + 1, targetMatchIndex, focusInt)
            }
        }
    }

    private fun maxScrollYForCurrentFocus(): Float {
        return computeMaxScrollYForFocus(focusedRoundFloat)
    }

    /**
     * Berechnet den maximalen vertikalen Scroll-Offset für den gegebenen Fokus-Wert [focus].
     *
     * In 1:1-Vorrunden (z. B. QUAL_PRELIM, L48) können Kacheln auf Slots liegen, die weit
     * unten in der Folgerunde verankert sind. Daher wird das Maximum über **alle** Kacheln
     * der fokussierten Runde ermittelt, damit man immer bis zur untersten Kachel scrollen kann.
     */
    private fun computeMaxScrollYForFocus(focus: Float): Float {
        val numRounds = roundsMatches.size
        if (numRounds == 0) return 0f
        val currentRoundInt = focus.roundToInt().coerceIn(0, numRounds - 1)
        val roundViews = matchViews.getOrNull(currentRoundInt) ?: return 0f
        if (roundViews.isEmpty()) return 0f

        val tileHeight = TILE_HEIGHT_DP * density
        // Maximum über alle Kacheln der Runde bilden, damit Vorrunden vollständig scrollbar sind
        val maxCenterY = roundViews.indices.maxOf { tileIndex ->
            computeMatchCenterY(currentRoundInt, tileIndex, focus)
        }
        val totalContentHeight = maxCenterY + tileHeight / 2f + paddingBottom.toFloat() + 16f * density
        return max(0f, totalContentHeight - height.toFloat())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        val isGsGroupStage = isGrandSlamGroupStage()
        val columnWidth = (viewWidth * 0.56f).toInt()
        val headerChildHeightPx = (32 * density).toInt()
        val headerAreaHeight = (if (isGsGroupStage) 60 else 40) * density
        val infoCardHeightPx = if (viewHeight > 0) viewHeight - headerAreaHeight.toInt() - (16 * density).toInt() else (500 * density).toInt()

        val childWidthSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY)
        val infoCardHeightSpec = MeasureSpec.makeMeasureSpec(infoCardHeightPx.coerceAtLeast((200 * density).toInt()), MeasureSpec.EXACTLY)

        val headerChildWidthSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.AT_MOST)
        val headerChildHeightSpec = MeasureSpec.makeMeasureSpec(headerChildHeightPx, MeasureSpec.EXACTLY)

        for ((roundIndex, list) in matchViews.withIndex()) {
            val roundMatches = roundsMatches.getOrNull(roundIndex)
            val isOpenDraw = roundMatches.isNullOrEmpty() && (openDrawInfoMap.containsKey(roundIndex) || openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)
            val tileH = (TILE_HEIGHT_DP * density).toInt()
            val childHeightSpec = MeasureSpec.makeMeasureSpec(tileH, MeasureSpec.EXACTLY)
            for (child in list) {
                if (isOpenDraw) {
                    child.measure(childWidthSpec, infoCardHeightSpec)
                } else {
                    child.measure(childWidthSpec, childHeightSpec)
                }
            }
        }
        for (headerChild in headerViews) {
            headerChild.measure(headerChildWidthSpec, headerChildHeightSpec)
        }
        groupTableHeaderView?.let {
            val groupHeaderHeightSpec = MeasureSpec.makeMeasureSpec((20 * density).toInt(), MeasureSpec.EXACTLY)
            it.measure(childWidthSpec, groupHeaderHeightSpec)
        }

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val viewWidth = right - left
        val viewHeight = bottom - top
        if (viewWidth <= 0 || viewHeight <= 0) return

        val isGsGroupStage = isGrandSlamGroupStage()
        val columnWidth = (viewWidth * 0.56f).toInt()
        val columnStepX = viewWidth * 0.60f
        val centerX = viewWidth / 2f
        val headerAreaHeight = (if (isGsGroupStage) 60 else 40) * density

        for (roundIndex in headerViews.indices) {
            val headerView = headerViews[roundIndex]
            val columnCenterX = centerX + (roundIndex - focusedRoundFloat) * columnStepX
            val measuredW = headerView.measuredWidth
            val measuredH = headerView.measuredHeight
            val hLeft = (columnCenterX - measuredW / 2f).toInt()
            val hRight = hLeft + measuredW
            val hTop = (paddingTop + (40f * density - measuredH) / 2f).toInt()
            val hBottom = hTop + measuredH
            headerView.layout(hLeft, hTop, hRight, hBottom)
        }

        groupTableHeaderView?.let { headerView ->
            val columnCenterX = centerX + (0 - focusedRoundFloat) * columnStepX
            val measuredW = headerView.measuredWidth
            val hLeft = (columnCenterX - measuredW / 2f).toInt()
            val hRight = hLeft + measuredW
            val hTop = (paddingTop + 40f * density).toInt()
            val hBottom = (paddingTop + 60f * density).toInt()
            headerView.layout(hLeft, hTop, hRight, hBottom)
        }

        val maxScroll = maxScrollYForCurrentFocus()
        scrollYOffset = scrollYOffset.coerceIn(0f, maxScroll)

        for (roundIndex in matchViews.indices) {
            val columnCenterX = centerX + (roundIndex - focusedRoundFloat) * columnStepX
            val childLeft = (columnCenterX - columnWidth / 2f).toInt()
            val childRight = childLeft + columnWidth

            val roundMatches = roundsMatches.getOrNull(roundIndex)
            val isOpenDraw = roundMatches.isNullOrEmpty() && (openDrawInfoMap.containsKey(roundIndex) || openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)
            val tileHeight = TILE_HEIGHT_DP * density

            val diff = focusedRoundFloat - roundIndex
            val scale = if (diff <= 0f) {
                1.0f
            } else {
                (1.0f - 0.54f * diff).coerceIn(0.46f, 1.0f)
            }

            val views = matchViews[roundIndex]
            for (matchIndex in views.indices) {
                val childView = views[matchIndex]
                if (isOpenDraw) {
                    val childTop = (paddingTop + headerAreaHeight + 8f * density).toInt()
                    val childBottom = childTop + childView.measuredHeight
                    childView.layout(childLeft, childTop, childRight, childBottom)
                } else {
                    val childCenterY = computeMatchCenterY(roundIndex, matchIndex, focusedRoundFloat) - scrollYOffset
                    val childTop = (childCenterY - tileHeight / 2f).toInt()
                    val childBottom = childTop + tileHeight.toInt()

                    childView.layout(childLeft, childTop, childRight, childBottom)
                }
                childView.pivotX = childView.width.toFloat()
                childView.pivotY = childView.height / 2f
                childView.scaleX = scale
                childView.scaleY = scale
            }
        }
    }

    /**
     * Zeichnet eine 3-teilige rechtwinklige Verbindungslinie zwischen Quell- und Zielkachel
     * (horizontal bis zum Mittelpunkt [midX], vertikal auf Höhe [targetY], horizontal zum Ziel [targetX]).
     */
    private fun drawBracketConnector(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        midX: Float,
        targetX: Float,
        targetY: Float,
        paint: Paint
    ) {
        canvas.drawLine(startX, startY, midX, startY, paint)
        canvas.drawLine(midX, startY, midX, targetY, paint)
        canvas.drawLine(midX, targetY, targetX, targetY, paint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val isGsGroupStage = isGrandSlamGroupStage()
        val headerHeightPx = if (isGsGroupStage) 60f * density else 40f * density
        val clipTop = paddingTop.toFloat() + headerHeightPx

        canvas.save()
        canvas.clipRect(0f, clipTop, width.toFloat(), height.toFloat())

        val numRounds = matchViews.size
        if (numRounds > 1) {
            for (roundIdx in 0 until numRounds - 1) {
                val roundMatchesR = roundsMatches.getOrNull(roundIdx) ?: continue
                val roundMatchesNext = roundsMatches.getOrNull(roundIdx + 1) ?: continue
                if (roundMatchesNext.isEmpty()) continue

                val currentRoundKey = roundMatchesR.firstOrNull()?.match?.round

                val viewsCurr = matchViews.getOrNull(roundIdx) ?: continue
                val viewsNext = matchViews.getOrNull(roundIdx + 1) ?: continue
                if (viewsNext.isEmpty()) continue

                // Stets die Anzahl der tatsächlich gerenderten Kacheln verwenden (nicht roundsMatches.size),
                // damit z. B. die 16 Gruppentabellen des Grand Slam korrekt als 2:1-Baum vor L16 verbunden werden.
                val renderedCountCurr = viewsCurr.size
                val renderedCountNext = viewsNext.size
                val isGrandSlamGroupRound = (currentRoundKey == TournamentRound.GROUP.name)

                for (groupOrMatchIdx in 0 until renderedCountCurr) {
                    val targetMatchIndex = if (renderedCountCurr == renderedCountNext * 2) {
                        groupOrMatchIdx / 2
                    } else {
                        getTargetMatchIndex(roundIdx, groupOrMatchIdx)
                    }

                    val viewCurr = viewsCurr.getOrNull(groupOrMatchIdx) ?: continue
                    val viewTarget = viewsNext.getOrNull(targetMatchIndex) ?: continue

                    if (renderedCountCurr == renderedCountNext * 2) {
                        // ── 2:1-Baum-Verbindungslinie (Standard & Grand-Slam-Gruppen → L16) ──
                        val pairIndex = groupOrMatchIdx / 2
                        val viewPairTop = viewsCurr.getOrNull(2 * pairIndex)
                        val viewPairBottom = viewsCurr.getOrNull(2 * pairIndex + 1)

                        if (groupOrMatchIdx % 2 != 0 || viewPairTop == null || viewPairBottom == null) continue

                        val xPairTop = viewPairTop.right.toFloat()
                        val yPairTop = viewPairTop.top + viewPairTop.height / 2f
                        val xPairBottom = viewPairBottom.right.toFloat()
                        val yPairBottom = viewPairBottom.top + viewPairBottom.height / 2f
                        val xTarget = viewTarget.left.toFloat()
                        val yTarget = viewTarget.top + viewTarget.height / 2f
                        val midX = (xPairTop + xTarget) / 2f

                        canvas.drawLine(xPairTop, yPairTop, midX, yPairTop, linePaintDefault)
                        canvas.drawLine(xPairBottom, yPairBottom, midX, yPairBottom, linePaintDefault)
                        canvas.drawLine(midX, min(yPairTop, yPairBottom), midX, max(yPairTop, yPairBottom), linePaintDefault)
                        canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintDefault)

                        if (isGrandSlamGroupRound) {
                            // Grand-Slam-Gruppenphase: Verbindungslinie rot hervorheben, falls der User
                            // bereits in das entsprechende Achtelfinal-Match (vTarget) eingezogen ist.
                            val matchNext = roundMatchesNext.getOrNull(pairIndex)
                            val userIsInTargetMatch = matchNext != null &&
                                (matchNext.isUserMatch || matchNext.match.player1Id == USER_ID || matchNext.match.player2Id == USER_ID)

                            if (userIsInTargetMatch) {
                                val topGroupLetter = ('A' + 2 * pairIndex).toString()
                                val bottomGroupLetter = ('A' + 2 * pairIndex + 1).toString()
                                val userInTopGroup = grandSlamGroupStandings[topGroupLetter]?.any { it.player.id == USER_ID } == true
                                val userInBottomGroup = grandSlamGroupStandings[bottomGroupLetter]?.any { it.player.id == USER_ID } == true

                                val highlightTop = userInTopGroup || !userInBottomGroup
                                val highlightBottom = userInBottomGroup || !userInTopGroup

                                if (highlightTop) {
                                    drawBracketConnector(canvas, xPairTop, yPairTop, midX, xTarget, yTarget, linePaintHighlight)
                                }
                                if (highlightBottom) {
                                    drawBracketConnector(canvas, xPairBottom, yPairBottom, midX, xTarget, yTarget, linePaintHighlight)
                                }
                            }
                        } else {
                            // Standard: Verbindungslinie des Siegers rot hervorheben, falls es der User ist
                            val matchTop = roundMatchesR.getOrNull(2 * pairIndex)
                            if (matchTop != null && matchTop.isCompleted && matchTop.winnerId == USER_ID) {
                                drawBracketConnector(canvas, xPairTop, yPairTop, midX, xTarget, yTarget, linePaintHighlight)
                            }
                            val matchBottom = roundMatchesR.getOrNull(2 * pairIndex + 1)
                            if (matchBottom != null && matchBottom.isCompleted && matchBottom.winnerId == USER_ID) {
                                drawBracketConnector(canvas, xPairBottom, yPairBottom, midX, xTarget, yTarget, linePaintHighlight)
                            }
                        }
                    } else {
                        // ── 1:1-Verbindungslinie (Vorrunden wie QUAL_PRELIM, L48) ──
                        if (targetMatchIndex < 0 || targetMatchIndex >= viewsNext.size) continue

                        val xCurr = viewCurr.right.toFloat()
                        val yCurr = viewCurr.top + viewCurr.height / 2f
                        val xNext = viewTarget.left.toFloat()
                        val yNext = viewTarget.top + viewTarget.height / 2f
                        val midX = (xCurr + xNext) / 2f

                        drawBracketConnector(canvas, xCurr, yCurr, midX, xNext, yNext, linePaintDefault)

                        val currentMatch = roundMatchesR.getOrNull(groupOrMatchIdx)
                        if (currentMatch != null && currentMatch.isCompleted && currentMatch.winnerId == USER_ID) {
                            drawBracketConnector(canvas, xCurr, yCurr, midX, xNext, yNext, linePaintHighlight)
                        }
                    }
                }
            }
        }

        val dTime = drawingTime
        for (list in matchViews) {
            for (child in list) {
                drawChild(canvas, child, dTime)
            }
        }

        canvas.restore()

        for (headerChild in headerViews) {
            drawChild(canvas, headerChild, dTime)
        }
        groupTableHeaderView?.let {
            drawChild(canvas, it, dTime)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isBeingDragged = false
                dragDirection = 0
                overScroller.forceFinished(true)
                focusedAnimator?.cancel()
                initOrResetVelocityTracker()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    isBeingDragged = true
                    dragDirection = if (dx > dy) 1 else 2
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        initOrResetVelocityTracker()
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isBeingDragged = false
                dragDirection = 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                if (!isBeingDragged) {
                    val totalDx = abs(event.x - downX)
                    val totalDy = abs(event.y - downY)
                    if (totalDx > touchSlop || totalDy > touchSlop) {
                        isBeingDragged = true
                        dragDirection = if (totalDx > totalDy) 1 else 2
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isBeingDragged) {
                    if (dragDirection == 1) {
                        val columnStepX = width * 0.60f
                        if (columnStepX > 0) {
                            val deltaFocus = -dx / columnStepX
                            focusedRoundFloat = (focusedRoundFloat + deltaFocus).coerceIn(0f, (roundsMatches.size - 1).coerceAtLeast(0).toFloat())
                            requestLayout()
                            invalidate()
                        }
                    } else if (dragDirection == 2) {
                        val maxScroll = maxScrollYForCurrentFocus()
                        scrollYOffset = (scrollYOffset - dy).coerceIn(0f, maxScroll)
                        requestLayout()
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isBeingDragged) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f

                    if (dragDirection == 1) {
                        val numRounds = roundsMatches.size
                        if (numRounds > 0) {
                            val currentFocus = focusedRoundFloat
                            val targetRound = when {
                                vx < -minFlingVelocity -> ceil(currentFocus).toInt().coerceAtMost(numRounds - 1)
                                vx > minFlingVelocity -> floor(currentFocus).toInt().coerceAtLeast(0)
                                else -> currentFocus.roundToInt().coerceIn(0, numRounds - 1)
                            }
                            val previousRoundIndex = selectedRoundIndex
                            selectRound(targetRound)
                            if (targetRound != previousRoundIndex) {
                                onRoundSelected?.invoke(targetRound)
                            }
                        }
                    } else if (dragDirection == 2) {
                        val maxScroll = maxScrollYForCurrentFocus().toInt()
                        if (maxScroll > 0) {
                            overScroller.fling(
                                0, scrollYOffset.toInt(),
                                0, -vy.toInt(),
                                0, 0,
                                0, maxScroll
                            )
                            postInvalidateOnAnimation()
                        }
                    }
                } else {
                    performClick()
                }
                recycleVelocityTracker()
                isBeingDragged = false
                dragDirection = 0
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                recycleVelocityTracker()
                isBeingDragged = false
                dragDirection = 0
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (overScroller.computeScrollOffset()) {
            scrollYOffset = overScroller.currY.toFloat()
            requestLayout()
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    private fun initOrResetVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
