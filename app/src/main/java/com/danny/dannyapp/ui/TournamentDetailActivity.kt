package com.danny.dannyapp.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class TournamentDetailActivity : BaseActivity() {

    private val viewModel: TournamentDetailViewModel by viewModels()

    private lateinit var pbLoading: ProgressBar

    // Floating Action Buttons (FABs)
    private lateinit var layoutFabContainer: View
    private lateinit var fabFocusUserMatch: FloatingActionButton
    private lateinit var fabToggleBracketView: FloatingActionButton

    // Preview
    private lateinit var cardPreviewHeader: MaterialCardView
    private lateinit var layoutPreviewContainer: View
    private lateinit var tvPreviewTitle: TextView
    private lateinit var tvStatusBadgePreview: TextView
    private lateinit var tvPreviewTypeBadge: TextView
    private lateinit var tvPreviewHost: TextView
    private lateinit var tvPreviewPrizeFund: TextView
    private lateinit var btnPrizeInfoPreview: ImageView
    private lateinit var btnStartTournamentPreview: Button
    private var currentEvent: TournamentEvent? = null

    // Active
    private lateinit var cardActiveControls: MaterialCardView
    private lateinit var layoutActiveContainer: View
    private lateinit var tvActiveTitle: TextView
    private lateinit var btnPrizeInfoActive: View
    private lateinit var tvStatusBadgeActive: TextView
    private lateinit var tvActiveTypeBadge: TextView
    private lateinit var tvActiveHost: TextView
    private lateinit var scrollRoundPills: HorizontalScrollView
    private lateinit var chipGroupRounds: ChipGroup
    private lateinit var layoutGroupSelector: View
    private lateinit var chipGroupGroups: ChipGroup
    private lateinit var btnSimulateCpuMatches: Button
    private lateinit var btnSimulateComplete: Button
    private lateinit var btnAdvanceRound: Button
    private lateinit var rvMatches: RecyclerView
    private lateinit var layoutOpenDrawInfo: View
    private lateinit var tvDrawAfterRoundInfo: TextView
    private lateinit var layoutEntrantsContainer: LinearLayout
    private lateinit var activeMatchesAdapter: TournamentMatchAdapter
    private lateinit var vpActiveBracket: ViewPager2
    private lateinit var activeBracketAdapter: BracketRoundAdapter

    // Review
    private lateinit var cardReviewSummary: MaterialCardView
    private lateinit var layoutReviewContainer: View
    private lateinit var tvReviewTitle: TextView
    private lateinit var tvReviewTypeBadge: TextView
    private lateinit var tvReviewHost: TextView
    private lateinit var tvReviewWinnerLabel: TextView
    private lateinit var tvReviewWinner: TextView
    private lateinit var tvReviewQualifierHeader: TextView
    private lateinit var layoutReviewQualifierWinnersList: LinearLayout
    private lateinit var btnPrizeInfoReview: View
    private lateinit var tvStatusBadgeReview: TextView
    private lateinit var tvReviewUserRound: TextView
    private lateinit var tvReviewUserPrize: TextView
    private lateinit var scrollReviewRoundPills: HorizontalScrollView
    private lateinit var chipGroupReviewRounds: ChipGroup
    private lateinit var rvReviewMatches: RecyclerView
    private lateinit var reviewMatchesAdapter: TournamentMatchAdapter
    private lateinit var vpReviewBracket: ViewPager2
    private lateinit var reviewBracketAdapter: BracketRoundAdapter
    private lateinit var layoutSimulatingOverlay: View
    private lateinit var gestureDetector: GestureDetector

    private var tournamentId: Long = -1L
    private var careerId: Long = -1L
    private var careerName: String = ""
    private var currentPrizeMoneyItems: List<PrizeMoneyItem> = emptyList()

    private fun setupSwipeGestures() {
        val density = resources.displayMetrics.density
        val swipeThreshold = (60 * density).toInt()
        val swipeVelocityThreshold = (100 * density).toInt()

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
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
                            onSwipeNextRound()
                        } else {
                            onSwipePreviousRound()
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
        rvMatches.addOnItemTouchListener(rvTouchListener)
        rvReviewMatches.addOnItemTouchListener(rvTouchListener)

        val genericTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
        layoutOpenDrawInfo.setOnTouchListener(genericTouchListener)
        layoutEntrantsContainer.setOnTouchListener(genericTouchListener)
        val openDrawScrollView = (layoutOpenDrawInfo as? ViewGroup)?.children?.filterIsInstance<ScrollView>()?.firstOrNull()
        openDrawScrollView?.setOnTouchListener(genericTouchListener)
    }

    private fun onSwipeNextRound() {
        val state = viewModel.uiState.value
        val (currentIndex, maxIndex) = when (state) {
            is TournamentDetailState.Active -> state.selectedRoundIndex to state.availableRounds.lastIndex
            is TournamentDetailState.Review -> state.selectedRoundIndex to state.availableRounds.lastIndex
            else -> return
        }
        if (currentIndex < maxIndex) {
            viewModel.selectRound(currentIndex + 1)
        }
    }

    private fun onSwipePreviousRound() {
        val state = viewModel.uiState.value
        val currentIndex = when (state) {
            is TournamentDetailState.Active -> state.selectedRoundIndex
            is TournamentDetailState.Review -> state.selectedRoundIndex
            else -> return
        }
        if (currentIndex > 0) {
            viewModel.selectRound(currentIndex - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        tournamentId = intent.getLongExtra(EXTRA_TOURNAMENT_ID, -1L)
        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: ""

        setupToolbar(
            titleText = getString(R.string.tournament_detail),
            subtitleText = getString(R.string.breadcrumb_format, careerName.ifBlank { getString(R.string.career) }),
            showBack = true,
            rootViewId = R.id.tournamentDetailMain
        )

        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Block back press while simulating batch
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initViews()
        setupAdapters()
        setupListeners()
        setupSwipeGestures()
        observeState(onBackPressedCallback)

        if (tournamentId > 0L) {
            viewModel.loadTournament(tournamentId, careerId)
        } else {
            Toast.makeText(this, getString(R.string.invalid_tournament_id), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btnSimulateCpuMatches.isInitialized && viewModel.uiState.value is TournamentDetailState.Active) {
            val state = viewModel.uiState.value as TournamentDetailState.Active
            val canPlay = state.isSelectedRoundPlayable
            btnSimulateCpuMatches.visibility = if (debugMode && canPlay) View.VISIBLE else View.GONE
            btnSimulateComplete.visibility = if (debugMode && canPlay) View.VISIBLE else View.GONE
        }
    }

    private fun initViews() {
        pbLoading = findViewById(R.id.pbLoading)
        layoutSimulatingOverlay = findViewById(R.id.layoutSimulatingOverlay)

        // Preview
        cardPreviewHeader = findViewById(R.id.cardPreviewHeader)
        layoutPreviewContainer = findViewById(R.id.layoutPreviewContainer)
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle)
        tvStatusBadgePreview = findViewById(R.id.tvStatusBadgePreview)
        tvPreviewTypeBadge = findViewById(R.id.tvPreviewTypeBadge)
        tvPreviewHost = findViewById(R.id.tvPreviewHost)
        tvPreviewPrizeFund = findViewById(R.id.tvPreviewPrizeFund)
        btnPrizeInfoPreview = findViewById(R.id.btnPrizeInfoPreview)
        btnStartTournamentPreview = findViewById(R.id.btnStartTournamentPreview)

        // Active
        cardActiveControls = findViewById(R.id.cardActiveControls)
        layoutActiveContainer = findViewById(R.id.layoutActiveContainer)
        tvActiveTitle = findViewById(R.id.tvActiveTitle)
        btnPrizeInfoActive = findViewById(R.id.btnPrizeInfoActive)
        tvStatusBadgeActive = findViewById(R.id.tvStatusBadgeActive)
        tvActiveTypeBadge = findViewById(R.id.tvActiveTypeBadge)
        tvActiveHost = findViewById(R.id.tvActiveHost)
        scrollRoundPills = findViewById(R.id.scrollRoundPills)
        chipGroupRounds = findViewById(R.id.chipGroupRounds)
        layoutGroupSelector = findViewById(R.id.layoutGroupSelector)
        chipGroupGroups = findViewById(R.id.chipGroupGroups)
        btnSimulateCpuMatches = findViewById(R.id.btnSimulateCpuMatches)
        btnSimulateComplete = findViewById(R.id.btnSimulateComplete)
        btnAdvanceRound = findViewById(R.id.btnAdvanceRound)
        rvMatches = findViewById(R.id.rvMatches)
        vpActiveBracket = findViewById(R.id.vpActiveBracket)
        layoutOpenDrawInfo = findViewById(R.id.layoutOpenDrawInfo)
        tvDrawAfterRoundInfo = findViewById(R.id.tvDrawAfterRoundInfo)
        layoutEntrantsContainer = findViewById(R.id.layoutEntrantsContainer)

        // Review
        cardReviewSummary = findViewById(R.id.cardReviewSummary)
        layoutReviewContainer = findViewById(R.id.layoutReviewContainer)
        tvReviewTitle = findViewById(R.id.tvReviewTitle)
        tvReviewTypeBadge = findViewById(R.id.tvReviewTypeBadge)
        tvReviewHost = findViewById(R.id.tvReviewHost)
        tvReviewWinnerLabel = findViewById(R.id.tvReviewWinnerLabel)
        tvReviewWinner = findViewById(R.id.tvReviewWinner)
        tvReviewQualifierHeader = findViewById(R.id.tvReviewQualifierHeader)
        layoutReviewQualifierWinnersList = findViewById(R.id.layoutReviewQualifierWinnersList)
        btnPrizeInfoReview = findViewById(R.id.btnPrizeInfoReview)
        tvStatusBadgeReview = findViewById(R.id.tvStatusBadgeReview)
        tvReviewUserRound = findViewById(R.id.tvReviewUserRound)
        tvReviewUserPrize = findViewById(R.id.tvReviewUserPrize)
        scrollReviewRoundPills = findViewById(R.id.scrollReviewRoundPills)
        chipGroupReviewRounds = findViewById(R.id.chipGroupReviewRounds)
        rvReviewMatches = findViewById(R.id.rvReviewMatches)
        vpReviewBracket = findViewById(R.id.vpReviewBracket)

        // FABs
        layoutFabContainer = findViewById(R.id.layoutFabContainer)
        fabFocusUserMatch = findViewById(R.id.fabFocusUserMatch)
        fabToggleBracketView = findViewById(R.id.fabToggleBracketView)
    }

    private fun setupAdapters() {
        activeMatchesAdapter = TournamentMatchAdapter { matchModel ->
            if (!matchModel.isCompleted && matchModel.isUserMatch) {
                showUserMatchOptionsDialog(matchModel)
            }
        }
        rvMatches.layoutManager = LinearLayoutManager(this)
        rvMatches.adapter = activeMatchesAdapter

        reviewMatchesAdapter = TournamentMatchAdapter { /* read-only */ }
        rvReviewMatches.layoutManager = LinearLayoutManager(this)
        rvReviewMatches.adapter = reviewMatchesAdapter

        activeBracketAdapter = BracketRoundAdapter { matchModel ->
            if (!matchModel.isCompleted && matchModel.isUserMatch) {
                showUserMatchOptionsDialog(matchModel)
            }
        }
        reviewBracketAdapter = BracketRoundAdapter { /* read-only */ }

        val screenWidth = resources.displayMetrics.widthPixels
        val sidePadding = (screenWidth * 0.22).toInt()

        fun setupViewPager(viewPager: ViewPager2, adapter: BracketRoundAdapter) {
            viewPager.adapter = adapter
            viewPager.clipToPadding = false
            viewPager.clipChildren = false
            viewPager.offscreenPageLimit = 1
            viewPager.setPadding(sidePadding, 0, sidePadding, 0)
            (viewPager.getChildAt(0) as? RecyclerView)?.apply {
                clipToPadding = false
                clipChildren = false
            }
        }

        setupViewPager(vpActiveBracket, activeBracketAdapter)
        setupViewPager(vpReviewBracket, reviewBracketAdapter)

        vpActiveBracket.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val state = viewModel.uiState.value
                if (state is TournamentDetailState.Active && state.selectedRoundIndex != position) {
                    viewModel.selectRound(position)
                }
            }
        })

        vpReviewBracket.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val state = viewModel.uiState.value
                if (state is TournamentDetailState.Review && state.selectedRoundIndex != position) {
                    viewModel.selectRound(position)
                }
            }
        })
    }

    private fun setupListeners() {
        btnStartTournamentPreview.setOnClickListener {
            viewModel.startTournament()
        }

        btnSimulateCpuMatches.setOnClickListener {
            viewModel.simulateCpuMatches()
        }

        btnSimulateComplete.setOnClickListener {
            viewModel.simulateCompleteTournament()
        }

        btnAdvanceRound.setOnClickListener {
            viewModel.advanceRound()
        }

        btnPrizeInfoPreview.setOnClickListener {
            showPrizeMoneyDialog()
        }

        btnPrizeInfoActive.setOnClickListener {
            showPrizeMoneyDialog()
        }

        btnPrizeInfoReview.setOnClickListener {
            showPrizeMoneyDialog()
        }

        fabFocusUserMatch.setOnClickListener {
            onFocusUserMatchClicked()
        }

        fabToggleBracketView.setOnClickListener {
            onToggleBracketViewClicked()
        }
    }

    private fun observeState(onBackPressedCallback: androidx.activity.OnBackPressedCallback) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    viewModel.isSimulatingBatch.collect { simulating ->
                        layoutSimulatingOverlay.visibility = if (simulating) View.VISIBLE else View.GONE
                        onBackPressedCallback.isEnabled = simulating
                        if (simulating) {
                            layoutFabContainer.visibility = View.GONE
                        } else {
                            updateFabState(viewModel.uiState.value)
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: TournamentDetailState) {
        updateFabState(state)
        when (state) {
            is TournamentDetailState.Loading -> {
                pbLoading.visibility = View.VISIBLE
                layoutPreviewContainer.visibility = View.GONE
                layoutActiveContainer.visibility = View.GONE
                layoutReviewContainer.visibility = View.GONE
            }

            is TournamentDetailState.Preview -> {
                pbLoading.visibility = View.GONE
                layoutPreviewContainer.visibility = View.VISIBLE
                layoutActiveContainer.visibility = View.GONE
                layoutReviewContainer.visibility = View.GONE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_career_season_format, activeCareerName, state.seasonNumber),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                TournamentUiHelper.bindTournamentHeader(
                    this, tvPreviewTitle, tvStatusBadgePreview, tvPreviewTypeBadge, tvPreviewHost,
                    state.tournament, getString(R.string.tournament_status_planned), R.color.status_planned, cardPreviewHeader
                )

                tvPreviewPrizeFund.text = state.totalPrizeFundText
                currentEvent = state.event
                currentPrizeMoneyItems = state.prizeMoneyItems

                if (state.isNextPlanned) {
                    btnStartTournamentPreview.visibility = View.VISIBLE
                } else {
                    btnStartTournamentPreview.visibility = View.GONE
                }
            }

            is TournamentDetailState.Active -> {
                pbLoading.visibility = View.GONE
                layoutPreviewContainer.visibility = View.GONE
                layoutActiveContainer.visibility = View.VISIBLE
                layoutReviewContainer.visibility = View.GONE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_career_season_format, activeCareerName, state.seasonNumber),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                TournamentUiHelper.bindTournamentHeader(
                    this, tvActiveTitle, tvStatusBadgeActive, tvActiveTypeBadge, tvActiveHost,
                    state.tournament, state.activeRoundDisplayName, R.color.status_active, cardActiveControls
                )

                currentEvent = state.event

                val canPlay = state.isSelectedRoundPlayable
                btnSimulateCpuMatches.visibility = if (debugMode && canPlay) View.VISIBLE else View.GONE
                btnSimulateComplete.visibility = if (debugMode && canPlay) View.VISIBLE else View.GONE
                btnAdvanceRound.visibility = if (state.canAdvanceRound) View.VISIBLE else View.GONE
                btnAdvanceRound.isEnabled = state.canAdvanceRound

                currentPrizeMoneyItems = state.prizeMoneyItems

                // Round Chips setup
                chipGroupRounds.removeAllViews()
                var selectedChip: Chip? = null
                state.availableRoundDisplayNames.forEachIndexed { index, displayName ->
                    val status = state.roundStatuses.getOrNull(index) ?: RoundStatus.PENDING
                    val chip = Chip(this).apply {
                        text = displayName
                        isCheckable = true
                        isChecked = (index == state.selectedRoundIndex)

                        val (bgRes, textRes) = when (status) {
                            RoundStatus.COMPLETED -> R.color.status_completed to R.color.grau_text_gedaempft
                            RoundStatus.ACTIVE -> R.color.status_active to R.color.white
                            RoundStatus.PENDING -> R.color.anthrazit_container_niedrig to R.color.grau_text_gedaempft
                        }

                        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@TournamentDetailActivity, bgRes))
                        setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, textRes))

                        if (isChecked) {
                            chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@TournamentDetailActivity, R.color.white))
                            chipStrokeWidth = 2 * resources.displayMetrics.density
                        } else {
                            chipStrokeWidth = 0f
                        }

                        setOnClickListener {
                            viewModel.selectRound(index)
                        }
                    }
                    chipGroupRounds.addView(chip)
                    if (index == state.selectedRoundIndex) {
                        selectedChip = chip
                    }
                }
                selectedChip?.let { chip ->
                    chip.post {
                        val scrollX = chip.left - (scrollRoundPills.width - chip.width) / 2
                        scrollRoundPills.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    }
                }

                // Group Chips setup
                if (state.availableGroups.isNotEmpty()) {
                    layoutGroupSelector.visibility = View.VISIBLE
                    chipGroupGroups.removeAllViews()
                    state.availableGroups.forEachIndexed { index, groupLetter ->
                        val chip = Chip(this).apply {
                            text = getString(R.string.group_label, groupLetter)
                            isCheckable = true
                            isChecked = (index == state.selectedGroupIndex)
                            setOnClickListener {
                                viewModel.selectGroup(index)
                            }
                        }
                        chipGroupGroups.addView(chip)
                    }
                } else {
                    layoutGroupSelector.visibility = View.GONE
                }

                if (settingsManager.isBracketViewEnabled) {
                    rvMatches.visibility = View.GONE
                    layoutOpenDrawInfo.visibility = View.GONE
                    vpActiveBracket.visibility = View.VISIBLE
                    state.allRoundsMatches.forEachIndexed { index, roundMatches ->
                        activeBracketAdapter.setRoundMatches(index, roundMatches, state.allRoundsMatches.size)
                    }
                    if (vpActiveBracket.currentItem != state.selectedRoundIndex) {
                        vpActiveBracket.setCurrentItem(state.selectedRoundIndex, true)
                    }
                } else {
                    vpActiveBracket.visibility = View.GONE
                    if (state.isOpenDrawInfoVisible) {
                        rvMatches.visibility = View.GONE
                        layoutOpenDrawInfo.visibility = View.VISIBLE
                        tvDrawAfterRoundInfo.text = getString(R.string.draw_after_round_info, state.prevRoundDisplayName)
                        if (state.openEntrantsGroups.isNotEmpty()) {
                            layoutEntrantsContainer.visibility = View.VISIBLE
                            layoutEntrantsContainer.removeAllViews()
                            state.openEntrantsGroups.forEachIndexed { groupIndex, group ->
                                if (groupIndex > 0) {
                                    val divider = com.google.android.material.divider.MaterialDivider(this).apply {
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            topMargin = (8 * resources.displayMetrics.density).toInt()
                                            bottomMargin = (8 * resources.displayMetrics.density).toInt()
                                        }
                                    }
                                    layoutEntrantsContainer.addView(divider)
                                }
                                val tvGroupTitle = TextView(this).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        bottomMargin = (4 * resources.displayMetrics.density).toInt()
                                    }
                                    text = group.title
                                    textSize = 14f
                                    setTypeface(null, Typeface.BOLD)
                                    gravity = Gravity.CENTER_HORIZONTAL
                                    val typedValue = android.util.TypedValue()
                                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                                    if (typedValue.resourceId != 0) {
                                        setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, typedValue.resourceId))
                                    }
                                }
                                layoutEntrantsContainer.addView(tvGroupTitle)

                                group.players.chunked(2).forEach { rowPlayers ->
                                    val rowLayout = LinearLayout(this).apply {
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            topMargin = (2 * resources.displayMetrics.density).toInt()
                                            bottomMargin = (2 * resources.displayMetrics.density).toInt()
                                        }
                                        orientation = LinearLayout.HORIZONTAL
                                        weightSum = 2f
                                    }

                                    rowPlayers.forEach { player ->
                                        val tvPlayer = TextView(this).apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                0,
                                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                                1f
                                            )
                                            textSize = 13f
                                            val typedValue = android.util.TypedValue()
                                            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                                            if (typedValue.resourceId != 0) {
                                                setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, typedValue.resourceId))
                                            }
                                        }
                                        UiHelper.bindSmartPlayerName(tvPlayer, player, isLeft = true)
                                        rowLayout.addView(tvPlayer)
                                    }

                                    if (rowPlayers.size == 1) {
                                        val emptyView = View(this).apply {
                                            layoutParams = LinearLayout.LayoutParams(
                                                0,
                                                0,
                                                1f
                                            )
                                        }
                                        rowLayout.addView(emptyView)
                                    }

                                    layoutEntrantsContainer.addView(rowLayout)
                                }
                            }
                        } else {
                            layoutEntrantsContainer.visibility = View.GONE
                        }
                    } else {
                        rvMatches.visibility = View.VISIBLE
                        layoutOpenDrawInfo.visibility = View.GONE
                        activeMatchesAdapter.submitList(state.matches)
                    }
                }
            }

            is TournamentDetailState.Review -> {
                pbLoading.visibility = View.GONE
                layoutPreviewContainer.visibility = View.GONE
                layoutActiveContainer.visibility = View.GONE
                layoutReviewContainer.visibility = View.VISIBLE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_career_season_format, activeCareerName, state.seasonNumber),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                TournamentUiHelper.bindTournamentHeader(
                    this, tvReviewTitle, tvStatusBadgeReview, tvReviewTypeBadge, tvReviewHost,
                    state.tournament, getString(R.string.tournament_status_completed), R.color.status_completed, cardReviewSummary
                )
                UiHelper.formatFlagAndText(tvReviewHost, state.hostCountryCode, state.host)

                currentEvent = state.event

                if (state.isQualifier) {
                    tvReviewQualifierHeader.visibility = View.VISIBLE
                    layoutReviewQualifierWinnersList.visibility = View.VISIBLE
                    TournamentUiHelper.bindQualifierWinnersList(
                        this, tvReviewQualifierHeader, layoutReviewQualifierWinnersList, state.qualifierWinners, textSizeSp = 14f
                    )
                    tvReviewWinnerLabel.visibility = View.GONE
                    tvReviewWinner.visibility = View.GONE
                } else {
                    tvReviewQualifierHeader.visibility = View.GONE
                    layoutReviewQualifierWinnersList.visibility = View.GONE
                    tvReviewWinnerLabel.visibility = View.VISIBLE
                    tvReviewWinner.visibility = View.VISIBLE
                    UiHelper.bindSmartPlayerName(tvReviewWinner, state.winner)
                }
                tvReviewUserRound.text = getString(R.string.tournament_review_user_round, state.userReachedRoundText)
                tvReviewUserPrize.text = getString(R.string.tournament_review_user_prize, state.userEarnedPrizeMoneyText)

                currentPrizeMoneyItems = state.prizeMoneyItems

                // Round Chips
                chipGroupReviewRounds.removeAllViews()
                var selectedReviewChip: Chip? = null
                state.availableRoundDisplayNames.forEachIndexed { index, displayName ->
                    val chip = Chip(this).apply {
                        text = displayName
                        isCheckable = true
                        isChecked = (index == state.selectedRoundIndex)

                        chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@TournamentDetailActivity, R.color.status_completed))
                        setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, R.color.grau_text_gedaempft))

                        if (isChecked) {
                            chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@TournamentDetailActivity, R.color.white))
                            chipStrokeWidth = 2 * resources.displayMetrics.density
                        } else {
                            chipStrokeWidth = 0f
                        }

                        setOnClickListener {
                            viewModel.selectRound(index)
                        }
                    }
                    chipGroupReviewRounds.addView(chip)
                    if (index == state.selectedRoundIndex) {
                        selectedReviewChip = chip
                    }
                }
                selectedReviewChip?.let { chip ->
                    chip.post {
                        val scrollX = chip.left - (scrollReviewRoundPills.width - chip.width) / 2
                        scrollReviewRoundPills.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    }
                }

                if (settingsManager.isBracketViewEnabled) {
                    rvReviewMatches.visibility = View.GONE
                    vpReviewBracket.visibility = View.VISIBLE
                    state.allRoundsMatches.forEachIndexed { index, roundMatches ->
                        reviewBracketAdapter.setRoundMatches(index, roundMatches, state.allRoundsMatches.size)
                    }
                    if (vpReviewBracket.currentItem != state.selectedRoundIndex) {
                        vpReviewBracket.setCurrentItem(state.selectedRoundIndex, true)
                    }
                } else {
                    vpReviewBracket.visibility = View.GONE
                    rvReviewMatches.visibility = View.VISIBLE
                    reviewMatchesAdapter.submitList(state.matches)
                }
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.VISIBLE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPrizeMoneyDialog() {
        TournamentDialogs.showPrizeMoneyDialog(this, currentPrizeMoneyItems, currentEvent)
    }

    private fun showUserMatchOptionsDialog(matchModel: MatchUiModel) {
        TournamentDialogs.showUserMatchOptionsDialog(
            context = this,
            matchModel = matchModel,
            debugMode = debugMode,
            onSetWinner = { showSetWinnerDialog(it) },
            onManualScore = { showManualScoreInputDialog(it) },
            onLiveGame = { showLiveGameEnginePlaceholder() }
        )
    }

    private fun showSetWinnerDialog(matchModel: MatchUiModel) {
        TournamentDialogs.showSetWinnerDialog(this, matchModel) { matchId, userWins ->
            viewModel.setUserMatchWinner(matchId, userWins)
        }
    }

    private fun showManualScoreInputDialog(matchModel: MatchUiModel) {
        TournamentDialogs.showManualScoreInputDialog(
            context = this,
            matchModel = matchModel,
            coroutineScope = lifecycleScope,
            onSaveScore = { matchId, p1, p2 ->
                viewModel.setManualUserMatchScore(matchId, p1, p2)
            }
        )
    }

    private fun showLiveGameEnginePlaceholder() {
        TournamentDialogs.showLiveGameEnginePlaceholder(this)
    }

    /**
     * Scrollt die Match-Liste oder den Turnierbaum zentriert auf das Match des Users in der aktuellen Runde.
     */
    private fun onFocusUserMatchClicked() {
        val state = viewModel.uiState.value
        val matches = when (state) {
            is TournamentDetailState.Active -> state.matches
            is TournamentDetailState.Review -> state.matches
            else -> emptyList()
        }
        val userMatchIndex = matches.indexOfFirst { it.isUserMatch }
        if (userMatchIndex != -1) {
            if (settingsManager.isBracketViewEnabled) {
                val viewPager = when (state) {
                    is TournamentDetailState.Active -> vpActiveBracket
                    is TournamentDetailState.Review -> vpReviewBracket
                    else -> return
                }
                val adapter = when (state) {
                    is TournamentDetailState.Active -> activeBracketAdapter
                    is TournamentDetailState.Review -> reviewBracketAdapter
                    else -> return
                }
                val roundIndex = when (state) {
                    is TournamentDetailState.Active -> state.selectedRoundIndex
                    is TournamentDetailState.Review -> state.selectedRoundIndex
                    else -> 0
                }
                adapter.scrollToMatch(viewPager, roundIndex, userMatchIndex)
            } else {
                val targetRecyclerView = when (state) {
                    is TournamentDetailState.Active -> rvMatches
                    is TournamentDetailState.Review -> rvReviewMatches
                    else -> return
                }
                scrollToMatchCentered(targetRecyclerView, userMatchIndex)
            }
        }
    }

    /**
     * Scrollt eine [RecyclerView] zentriert auf die angegebene Position.
     */
    private fun scrollToMatchCentered(recyclerView: RecyclerView, position: Int) {
        if (position < 0) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val smoothScroller = object : androidx.recyclerview.widget.LinearSmoothScroller(recyclerView.context) {
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
     * Schaltet zwischen Rundenliste und Turnierbaum-Ansicht um.
     */
    private fun onToggleBracketViewClicked() {
        settingsManager.isBracketViewEnabled = !settingsManager.isBracketViewEnabled
        updateBracketFabIcon()
        renderState(viewModel.uiState.value)
    }

    /**
     * Aktualisiert das Icon und die Content-Description des Ansichts-Wechsel-Buttons basierend auf [SettingsManager.isBracketViewEnabled].
     */
    private fun updateBracketFabIcon() {
        if (settingsManager.isBracketViewEnabled) {
            fabToggleBracketView.setImageResource(R.drawable.ic_view_list)
            fabToggleBracketView.contentDescription = getString(R.string.cd_show_list_view)
        } else {
            fabToggleBracketView.setImageResource(R.drawable.ic_bracket_tree)
            fabToggleBracketView.contentDescription = getString(R.string.cd_show_bracket_view)
        }
    }

    /**
     * Aktualisiert die Sichtbarkeit und den Zustand der Schwebe-Buttons (FABs).
     */
    private fun updateFabState(state: TournamentDetailState) {
        when (state) {
            is TournamentDetailState.Active -> {
                layoutFabContainer.visibility = View.VISIBLE
                updateUserMatchFocusFab(state.matches)
            }
            is TournamentDetailState.Review -> {
                layoutFabContainer.visibility = View.VISIBLE
                updateUserMatchFocusFab(state.matches)
            }
            else -> {
                layoutFabContainer.visibility = View.GONE
            }
        }
        updateBracketFabIcon()
    }

    /**
     * Aktiviert oder deaktiviert den Button [fabFocusUserMatch] abhängig davon, ob ein User-Match in der Liste vorhanden ist.
     */
    private fun updateUserMatchFocusFab(matches: List<MatchUiModel>) {
        val hasUserMatch = matches.any { it.isUserMatch }
        fabFocusUserMatch.isEnabled = hasUserMatch
        fabFocusUserMatch.alpha = if (hasUserMatch) 1.0f else 0.38f
    }

    companion object {
        const val EXTRA_TOURNAMENT_ID = "extra_tournament_id"
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
