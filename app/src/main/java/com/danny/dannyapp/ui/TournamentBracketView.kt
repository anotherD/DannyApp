package com.danny.dannyapp.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
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

    private var roundsMatches: List<List<MatchUiModel>> = emptyList()
    private var roundViews: List<List<View>> = emptyList()

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
     * Setzt die Runden- und Match-Daten des Turnierbaums.
     */
    fun setRounds(roundsMatches: List<List<MatchUiModel>>, initialRoundIndex: Int = 0) {
        this.roundsMatches = roundsMatches
        focusedAnimator?.cancel()
        overScroller.forceFinished(true)

        val targetRound = initialRoundIndex.coerceIn(0, (roundsMatches.size - 1).coerceAtLeast(0))
        selectedRoundIndex = targetRound
        focusedRoundFloat = targetRound.toFloat()
        scrollYOffset = 0f

        rebuildViews()
        requestLayout()
        invalidate()
    }

    /**
     * Wählt eine spezifische Runde im Turnierbaum aus und zentriert diese.
     */
    fun selectRound(roundIndex: Int, animate: Boolean = true) {
        if (roundsMatches.isEmpty()) return
        val targetIndex = roundIndex.coerceIn(0, roundsMatches.lastIndex)
        selectedRoundIndex = targetIndex

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
        val targetScrollY = (targetCenterY - viewHeight / 2f).coerceIn(0f, computeMaxScrollYForFocus(r.toFloat()))

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
        for (r in roundViews.indices) {
            val views = roundViews[r]
            val models = roundsMatches.getOrNull(r) ?: continue
            for (m in views.indices) {
                val item = models.getOrNull(m) ?: continue
                bindMatchView(views[m], item)
            }
        }
        invalidate()
    }

    private fun rebuildViews() {
        removeAllViews()
        val newRoundViews = mutableListOf<List<View>>()

        val inflater = LayoutInflater.from(context)
        for ((r, roundMatches) in roundsMatches.withIndex()) {
            val currentRoundViews = mutableListOf<View>()
            for (item in roundMatches) {
                val cardView = inflater.inflate(R.layout.item_bracket_match, this, false)
                bindMatchView(cardView, item)
                addView(cardView)
                currentRoundViews.add(cardView)
            }
            newRoundViews.add(currentRoundViews)
        }
        this.roundViews = newRoundViews
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
        val minMargin = 8f * density
        val minSlotHeight = tileHeight + minMargin
        val baseSlotHeight = minSlotHeight

        val paddingTopPx = paddingTop.toFloat() + 8f * density

        if (r == focusInt) {
            return paddingTopPx + m * baseSlotHeight + tileHeight / 2f
        }

        if (r > focusInt) {
            val sizePrev = roundsMatches.getOrNull(r - 1)?.size ?: 0
            val sizeCurr = roundsMatches.getOrNull(r)?.size ?: 0
            return if (sizePrev == sizeCurr * 2) {
                val y1 = computeIntegerFocusY(r - 1, 2 * m, focusInt)
                val y2 = computeIntegerFocusY(r - 1, 2 * m + 1, focusInt)
                (y1 + y2) / 2f
            } else {
                computeIntegerFocusY(r - 1, m, focusInt)
            }
        } else {
            val sizeCurr = roundsMatches.getOrNull(r)?.size ?: 0
            val sizeNext = roundsMatches.getOrNull(r + 1)?.size ?: 0
            val parentY = computeIntegerFocusY(r + 1, m / 2, focusInt)
            return if (sizeNext * 2 == sizeCurr) {
                val slotH = max(minSlotHeight, baseSlotHeight * 2.0.pow((r - focusInt).toDouble()).toFloat())
                val offset = if (m % 2 == 0) -0.5f * slotH else 0.5f * slotH
                parentY + offset
            } else {
                parentY
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

        val columnWidth = (viewWidth * 0.56f).toInt()
        val tileHeightPx = (68 * density).toInt()

        val childWidthSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(tileHeightPx, MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(childWidthSpec, childHeightSpec)
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

        val maxScroll = maxScrollYForCurrentFocus()
        scrollYOffset = scrollYOffset.coerceIn(0f, maxScroll)

        for (r in roundViews.indices) {
            val columnCenterX = centerX + (r - focusedRoundFloat) * columnStepX
            val childLeft = (columnCenterX - columnWidth / 2f).toInt()
            val childRight = childLeft + columnWidth

            val views = roundViews[r]
            for (m in views.indices) {
                val childView = views[m]
                val childCenterY = computeMatchCenterY(r, m, focusedRoundFloat) - scrollYOffset
                val childTop = (childCenterY - tileHeight / 2f).toInt()
                val childBottom = childTop + tileHeight.toInt()

                childView.layout(childLeft, childTop, childRight, childBottom)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val numRounds = roundViews.size
        if (numRounds > 1) {
            for (r in 0 until numRounds - 1) {
                val roundMatchesR = roundsMatches.getOrNull(r) ?: continue
                val roundMatchesNext = roundsMatches.getOrNull(r + 1) ?: continue
                val viewsNext = roundViews.getOrNull(r + 1) ?: continue
                val viewsCurr = roundViews.getOrNull(r) ?: continue

                for (m in viewsNext.indices) {
                    val viewTarget = viewsNext[m]

                    if (roundMatchesR.size == roundMatchesNext.size * 2) {
                        val viewF1 = viewsCurr.getOrNull(2 * m)
                        val viewF2 = viewsCurr.getOrNull(2 * m + 1)
                        if (viewF1 != null && viewF2 != null) {
                            val x1 = viewF1.right.toFloat()
                            val y1 = viewF1.top + viewF1.height / 2f
                            val x2 = viewF2.right.toFloat()
                            val y2 = viewF2.top + viewF2.height / 2f
                            val xTarget = viewTarget.left.toFloat()
                            val yTarget = viewTarget.top + viewTarget.height / 2f
                            val midX = (x1 + xTarget) / 2f

                            canvas.drawLine(x1, y1, midX, y1, linePaintDefault)
                            canvas.drawLine(x2, y2, midX, y2, linePaintDefault)
                            canvas.drawLine(midX, min(y1, y2), midX, max(y1, y2), linePaintDefault)
                            canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintDefault)

                            val matchF1 = roundMatchesR.getOrNull(2 * m)
                            if (matchF1 != null && matchF1.isCompleted && matchF1.winnerId == USER_ID) {
                                canvas.drawLine(x1, y1, midX, y1, linePaintHighlight)
                                canvas.drawLine(midX, y1, midX, yTarget, linePaintHighlight)
                                canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintHighlight)
                            }

                            val matchF2 = roundMatchesR.getOrNull(2 * m + 1)
                            if (matchF2 != null && matchF2.isCompleted && matchF2.winnerId == USER_ID) {
                                canvas.drawLine(x2, y2, midX, y2, linePaintHighlight)
                                canvas.drawLine(midX, y2, midX, yTarget, linePaintHighlight)
                                canvas.drawLine(midX, yTarget, xTarget, yTarget, linePaintHighlight)
                            }
                        }
                    } else if (roundMatchesR.size == roundMatchesNext.size) {
                        val viewF = viewsCurr.getOrNull(m)
                        if (viewF != null) {
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
        super.dispatchDraw(canvas)
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
                            selectRound(targetRound, animate = true)
                            if (targetRound != selectedRoundIndex) {
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
