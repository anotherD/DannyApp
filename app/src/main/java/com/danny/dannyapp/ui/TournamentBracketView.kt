package com.danny.dannyapp.ui

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
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.domain.enums.TournamentRound
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
     * Scrollt auf das angegebene Match des Users in der Zielrunde.
     */
    fun scrollToUserMatch(roundIndex: Int, matchIndex: Int) {
        if (roundsMatches.isEmpty()) return
        val r = roundIndex.coerceIn(0, roundsMatches.lastIndex)
        if (r != selectedRoundIndex) {
            selectRound(r, animate = true)
        }
        val targetCenterY = computeMatchCenterY(r, matchIndex, r.toFloat())
        val viewHeight = height.toFloat()
        val headerHeight = 40f * density
        val availableHeight = viewHeight - headerHeight - paddingTop.toFloat() - paddingBottom.toFloat()
        val targetScrollY = (targetCenterY - headerHeight - paddingTop.toFloat() - availableHeight / 2f).coerceIn(0f, computeMaxScrollYForFocus(r.toFloat()))

        val startScrollY = scrollYOffset
        if (abs(targetScrollY - startScrollY) > 1f) {
            focusedAnimator?.cancel()
            focusedAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedValue as Float
                    scrollYOffset = startScrollY + (targetScrollY - startScrollY) * fraction
                    requestLayout()
                    invalidate()
                }
                start()
            }
        }
    }

    /**
     * Aktualisiert die Kachel-Farbschemata ohne Neuaufbau.
     */
    fun refreshStyles() {
        for (r in matchViews.indices) {
            val views = matchViews[r]
            val models = roundsMatches.getOrNull(r) ?: continue
            for (m in views.indices) {
                val item = models.getOrNull(m) ?: continue
                bindMatchView(views[m], item)
            }
        }
        updateHeaderStyles()
        invalidate()
    }

    private fun rebuildViews() {
        for (list in matchViews) {
            for (v in list) {
                removeView(v)
            }
        }
        matchViews = emptyList()

        val inflater = LayoutInflater.from(context)
        val newMatchViews = mutableListOf<List<View>>()
        var insertIndex = 0

        val firstMatchRoundKey = roundsMatches.firstOrNull()?.firstOrNull()?.match?.round
        val isGsGroupStage = grandSlamGroupStandings.isNotEmpty() && (firstMatchRoundKey == TournamentRound.GROUP.name)

        for ((roundIdx, roundMatches) in roundsMatches.withIndex()) {
            val currentRoundViews = mutableListOf<View>()
            if (roundIdx == 0 && isGsGroupStage) {
                val groupLetters = ('A'..'P').map { it.toString() }
                for (groupLetter in groupLetters) {
                    val standings = grandSlamGroupStandings[groupLetter] ?: emptyList()
                    val cardView = TournamentBracketCardBuilder.createGroupCardView(context, density, groupLetter, standings)
                    addView(cardView, insertIndex++)
                    currentRoundViews.add(cardView)
                }
            } else if (roundMatches.isEmpty() && openDrawInfoMap.containsKey(roundIdx)) {
                val (prevName, groups) = openDrawInfoMap[roundIdx]!!
                val infoCardView = TournamentBracketCardBuilder.createOpenDrawInfoCardView(context, density, prevName, groups)
                addView(infoCardView, insertIndex++)
                currentRoundViews.add(infoCardView)
            } else if (roundMatches.isEmpty() && (openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)) {
                val infoCardView = TournamentBracketCardBuilder.createOpenDrawInfoCardView(context, density, prevRoundDisplayName, openEntrantsGroups)
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
    }

    private fun rebuildHeaderViews() {
        for (v in headerViews) {
            removeView(v)
        }
        val newHeaderViews = mutableListOf<View>()
        for ((r, displayName) in headerDisplayNames.withIndex()) {
            val status = headerStatuses.getOrNull(r) ?: RoundStatus.PENDING
            val headerView = createHeaderView(r, displayName, status)
            addView(headerView)
            newHeaderViews.add(headerView)
        }
        headerViews = newHeaderViews
    }

    private fun createHeaderView(r: Int, displayName: String, status: RoundStatus): View {
        val textView = TextView(context).apply {
            text = displayName
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val pxPaddingH = (12 * density).toInt()
            val pxPaddingV = (4 * density).toInt()
            setPadding(pxPaddingH, pxPaddingV, pxPaddingH, pxPaddingV)

            setOnClickListener {
                selectRound(r, animate = true)
                onRoundSelected?.invoke(r)
            }
        }
        styleHeaderView(textView, r, status)
        return textView
    }

    private fun styleHeaderView(textView: TextView, r: Int, status: RoundStatus) {
        val isSelected = (r == selectedRoundIndex)
        val bgRes = when (status) {
            RoundStatus.COMPLETED -> R.color.status_completed
            RoundStatus.ACTIVE -> R.color.status_active
            RoundStatus.PENDING -> R.color.anthrazit_container_niedrig
        }
        val textRes = when (status) {
            RoundStatus.ACTIVE -> R.color.white
            else -> R.color.grau_text_gedaempft
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(ContextCompat.getColor(context, bgRes))
            if (isSelected) {
                setStroke((2f * density).toInt(), ContextCompat.getColor(context, R.color.white))
            } else {
                setStroke(0, 0)
            }
        }
        textView.background = bgDrawable
        textView.setTextColor(ContextCompat.getColor(context, textRes))
    }

    private fun updateHeaderStyles() {
        for ((r, view) in headerViews.withIndex()) {
            val textView = view as? TextView ?: continue
            val status = headerStatuses.getOrNull(r) ?: RoundStatus.PENDING
            styleHeaderView(textView, r, status)
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
        if (!match.groupName.isNullOrBlank()) {
            tvBracketMatchIndex.text = context.getString(R.string.tournament_match_group_index_format, match.groupName, match.matchIndex + 1)
        } else {
            tvBracketMatchIndex.text = context.getString(R.string.tournament_match_index_format, match.matchIndex + 1)
        }

        UiHelper.bindSmartPlayerName(tvPlayer1Name, item.player1, isLeft = true)
        UiHelper.bindSmartPlayerName(tvPlayer2Name, item.player2, isLeft = true)

        UiHelper.applyMatchCardStyle(
            card = card,
            item = item,
            player1Name = tvPlayer1Name,
            player1Score = tvPlayer1Score,
            player2Name = tvPlayer2Name,
            player2Score = tvPlayer2Score,
            matchIndexBadge = null
        )

        card.setOnClickListener {
            onMatchClick?.invoke(item)
        }
    }

    private fun getTargetMatchIndex(r: Int, m: Int): Int {
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
                        idx
                    } else if (headerDisplayNames.getOrNull(r)?.contains("L48") == true) {
                        val targetIdx = com.danny.dannyapp.domain.usecase.BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(sizeNext - 1 - m)
                        if (targetIdx >= 0) targetIdx else m
                    } else {
                        m
                    }
                } else {
                    if (headerDisplayNames.getOrNull(r)?.contains("L48") == true) {
                        val targetIdx = com.danny.dannyapp.domain.usecase.BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(sizeNext - 1 - m)
                        if (targetIdx >= 0) targetIdx else m
                    } else {
                        m
                    }
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

    private fun computeIntegerFocusY(r: Int, m: Int, focusInt: Int): Float {
        val tileHeight = 68f * density
        val baseSlotHeight = 76f * density
        val headerHeight = 40f * density
        val paddingTopPx = paddingTop.toFloat() + headerHeight + 8f * density

        if (r == focusInt) {
            return paddingTopPx + m * baseSlotHeight + tileHeight / 2f
        }

        if (r > focusInt) {
            val sizePrev = roundsMatches.getOrNull(r - 1)?.size ?: 0
            val sizeCurr = roundsMatches.getOrNull(r)?.size ?: 0
            return if (sizePrev < sizeCurr) {
                paddingTopPx + m * baseSlotHeight + tileHeight / 2f
            } else if (sizePrev == sizeCurr * 2) {
                val y1 = computeIntegerFocusY(r - 1, 2 * m, focusInt)
                val y2 = computeIntegerFocusY(r - 1, 2 * m + 1, focusInt)
                (y1 + y2) / 2f
            } else if (sizePrev == sizeCurr) {
                paddingTopPx + m * baseSlotHeight + tileHeight / 2f
            } else {
                computeIntegerFocusY(r - 1, m, focusInt)
            }
        } else {
            val sizeCurr = roundsMatches.getOrNull(r)?.size ?: 0
            val sizeNext = roundsMatches.getOrNull(r + 1)?.size ?: 0
            return if (sizeCurr < sizeNext) {
                val targetM = getTargetMatchIndex(r, m)
                val targetY = computeIntegerFocusY(r + 1, targetM, focusInt)
                val fallbackY = paddingTopPx + m * baseSlotHeight + tileHeight / 2f
                max(targetY, fallbackY)
            } else if (sizeNext == sizeCurr) {
                val targetM = getTargetMatchIndex(r, m)
                computeIntegerFocusY(r + 1, targetM, focusInt)
            } else if (sizeNext * 2 == sizeCurr) {
                val parentY = computeIntegerFocusY(r + 1, m / 2, focusInt)
                val distanceRound = focusInt - r
                val factor = 0.5f.pow(distanceRound + 1)
                val offset = if (m % 2 == 0) -factor * baseSlotHeight else factor * baseSlotHeight
                parentY + offset
            } else {
                val targetM = getTargetMatchIndex(r, m)
                computeIntegerFocusY(r + 1, targetM, focusInt)
            }
        }
    }

    private fun maxScrollYForCurrentFocus(): Float {
        return computeMaxScrollYForFocus(focusedRoundFloat)
    }

    private fun computeMaxScrollYForFocus(focus: Float): Float {
        val numRounds = roundsMatches.size
        if (numRounds == 0) return 0f
        val currentRoundInt = focus.roundToInt().coerceIn(0, numRounds - 1)
        val roundMatches = roundsMatches.getOrNull(currentRoundInt) ?: return 0f
        if (roundMatches.isEmpty()) return 0f

        val tileHeight = 68f * density
        val lastIndex = roundMatches.lastIndex
        val lastCenterY = computeMatchCenterY(currentRoundInt, lastIndex, focus)
        val totalContentHeight = lastCenterY + tileHeight / 2f + paddingBottom.toFloat() + 16f * density
        return max(0f, totalContentHeight - height.toFloat())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = MeasureSpec.getSize(heightMeasureSpec)

        val columnWidth = (viewWidth * 0.56f).toInt()
        val tileHeightPx = (68 * density).toInt()
        val headerChildHeightPx = (32 * density).toInt()
        val headerAreaHeight = (40 * density).toInt()
        val infoCardHeightPx = if (viewHeight > 0) viewHeight - headerAreaHeight - (16 * density).toInt() else (500 * density).toInt()

        val childWidthSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(tileHeightPx, MeasureSpec.EXACTLY)
        val infoCardHeightSpec = MeasureSpec.makeMeasureSpec(infoCardHeightPx.coerceAtLeast((200 * density).toInt()), MeasureSpec.EXACTLY)

        val headerChildWidthSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.AT_MOST)
        val headerChildHeightSpec = MeasureSpec.makeMeasureSpec(headerChildHeightPx, MeasureSpec.EXACTLY)

        for ((r, list) in matchViews.withIndex()) {
            val roundMatches = roundsMatches.getOrNull(r)
            val isOpenDraw = roundMatches.isNullOrEmpty() && (openDrawInfoMap.containsKey(r) || openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)
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

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val viewWidth = right - left
        val viewHeight = bottom - top
        if (viewWidth <= 0 || viewHeight <= 0) return

        val columnWidth = (viewWidth * 0.56f).toInt()
        val columnStepX = viewWidth * 0.60f
        val centerX = viewWidth / 2f
        val tileHeight = 68f * density
        val headerAreaHeight = 40f * density

        for (r in headerViews.indices) {
            val headerView = headerViews[r]
            val columnCenterX = centerX + (r - focusedRoundFloat) * columnStepX
            val measuredW = headerView.measuredWidth
            val measuredH = headerView.measuredHeight
            val hLeft = (columnCenterX - measuredW / 2f).toInt()
            val hRight = hLeft + measuredW
            val hTop = (paddingTop + (headerAreaHeight - measuredH) / 2f).toInt()
            val hBottom = hTop + measuredH
            headerView.layout(hLeft, hTop, hRight, hBottom)
        }

        val maxScroll = maxScrollYForCurrentFocus()
        scrollYOffset = scrollYOffset.coerceIn(0f, maxScroll)

        for (r in matchViews.indices) {
            val columnCenterX = centerX + (r - focusedRoundFloat) * columnStepX
            val childLeft = (columnCenterX - columnWidth / 2f).toInt()
            val childRight = childLeft + columnWidth

            val roundMatches = roundsMatches.getOrNull(r)
            val isOpenDraw = roundMatches.isNullOrEmpty() && (openDrawInfoMap.containsKey(r) || openEntrantsGroups.isNotEmpty() || isOpenDrawInfoVisible)

            val diff = focusedRoundFloat - r
            val scale = if (diff <= 0f) {
                1.0f
            } else {
                (1.0f - 0.54f * diff).coerceIn(0.46f, 1.0f)
            }

            val views = matchViews[r]
            for (m in views.indices) {
                val childView = views[m]
                if (isOpenDraw) {
                    val childTop = (paddingTop + headerAreaHeight + 8f * density).toInt()
                    val childBottom = childTop + childView.measuredHeight
                    childView.layout(childLeft, childTop, childRight, childBottom)
                } else {
                    val childCenterY = computeMatchCenterY(r, m, focusedRoundFloat) - scrollYOffset
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

    override fun dispatchDraw(canvas: Canvas) {
        val headerHeightPx = 40f * density
        val clipTop = paddingTop.toFloat() + headerHeightPx

        canvas.save()
        canvas.clipRect(0f, clipTop, width.toFloat(), height.toFloat())

        val numRounds = matchViews.size
        if (numRounds > 1) {
            for (r in 0 until numRounds - 1) {
                val roundMatchesR = roundsMatches.getOrNull(r) ?: continue
                val roundMatchesNext = roundsMatches.getOrNull(r + 1) ?: continue
                if (roundMatchesNext.isEmpty()) continue
                val viewsNext = matchViews.getOrNull(r + 1) ?: continue
                val viewsCurr = matchViews.getOrNull(r) ?: continue
                val sizeCurr = roundMatchesR.size
                val sizeNext = roundMatchesNext.size

                for (m in 0 until sizeCurr) {
                    val viewTarget = viewsNext.getOrNull(getTargetMatchIndex(r, m)) ?: continue
                    val viewF = viewsCurr.getOrNull(m) ?: continue

                    if (sizeCurr == sizeNext * 2) {
                        val mPair = m / 2
                        val viewF1 = viewsCurr.getOrNull(2 * mPair)
                        val viewF2 = viewsCurr.getOrNull(2 * mPair + 1)
                        if (m % 2 == 0 && viewF1 != null && viewF2 != null) {
                            val x1 = viewF1.right.toFloat()
                            val y1 = viewF1.top + viewF1.height / 2f
                            val x2 = viewF2.right.toFloat()
                            val y2 = viewF2.top + viewF2.height / 2f
                            val targetIdx = getTargetMatchIndex(r, 2 * mPair)
                            val vTarget = viewsNext.getOrNull(targetIdx) ?: continue
                            val xTarget = vTarget.left.toFloat()
                            val yTarget = vTarget.top + vTarget.height / 2f
                            val midX = (x1 + xTarget) / 2f

                            canvas.drawLine(x1, y1, midX, y1, linePaintDefault)
                            canvas.drawLine(x2, y2, midX, y2, linePaintDefault)
                            canvas.drawLine(midX, min(y1, y2), midX, max(y1, y2), linePaintDefault)
                            canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintDefault)

                            val matchF1 = roundMatchesR.getOrNull(2 * mPair)
                            if (matchF1 != null && matchF1.isCompleted && matchF1.winnerId == USER_ID) {
                                canvas.drawLine(x1, y1, midX, y1, linePaintHighlight)
                                canvas.drawLine(midX, y1, midX, yTarget, linePaintHighlight)
                                canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintHighlight)
                            }
                            val matchF2 = roundMatchesR.getOrNull(2 * mPair + 1)
                            if (matchF2 != null && matchF2.isCompleted && matchF2.winnerId == USER_ID) {
                                canvas.drawLine(x2, y2, midX, y2, linePaintHighlight)
                                canvas.drawLine(midX, y2, midX, yTarget, linePaintHighlight)
                                canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintHighlight)
                            }
                        }
                    } else {
                        val targetIdx = getTargetMatchIndex(r, m)
                        if (targetIdx >= 0 && targetIdx < viewsNext.size) {
                            val x1 = viewF.right.toFloat()
                            val y1 = viewF.top + viewF.height / 2f
                            val xTarget = viewTarget.left.toFloat()
                            val yTarget = viewTarget.top + viewTarget.height / 2f
                            val midX = (x1 + xTarget) / 2f

                            canvas.drawLine(x1, y1, midX, y1, linePaintDefault)
                            canvas.drawLine(midX, min(y1, yTarget), midX, max(y1, yTarget), linePaintDefault)
                            canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintDefault)

                            val matchF = roundMatchesR.getOrNull(m)
                            if (matchF != null && matchF.isCompleted && matchF.winnerId == USER_ID) {
                                canvas.drawLine(x1, y1, midX, y1, linePaintHighlight)
                                canvas.drawLine(midX, min(y1, yTarget), midX, max(y1, yTarget), linePaintHighlight)
                                canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintHighlight)
                            }
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
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isBeingDragged = false
                dragDirection = 0
                overScroller.forceFinished(true)
                focusedAnimator?.cancel()
                initOrResetVelocityTracker()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    isBeingDragged = true
                    dragDirection = if (dx > dy) 1 else 2
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        initOrResetVelocityTracker()
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isBeingDragged = false
                dragDirection = 0
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - lastTouchX
                val dy = ev.y - lastTouchY
                lastTouchX = ev.x
                lastTouchY = ev.y

                if (!isBeingDragged) {
                    val totalDx = abs(ev.x - downX)
                    val totalDy = abs(ev.y - downY)
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
                            val f = focusedRoundFloat
                            val targetRound = when {
                                vx < -minFlingVelocity -> ceil(f).toInt().coerceAtMost(numRounds - 1)
                                vx > minFlingVelocity -> floor(f).toInt().coerceAtLeast(0)
                                else -> f.roundToInt().coerceIn(0, numRounds - 1)
                            }
                            val previousRoundIndex = selectedRoundIndex
                            selectRound(targetRound, animate = true)
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
        return super.onTouchEvent(ev)
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
