package com.danny.dannyapp.ui.tournament

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.MatchDistancePreset
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
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
    private lateinit var scrollBoardPills: HorizontalScrollView
    private lateinit var chipGroupBoards: ChipGroup
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
    private lateinit var bracketViewActive: TournamentBracketView

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
    private lateinit var scrollReviewBoardPills: HorizontalScrollView
    private lateinit var chipGroupReviewBoards: ChipGroup
    private lateinit var rvReviewMatches: RecyclerView
    private lateinit var reviewMatchesAdapter: TournamentMatchAdapter
    private lateinit var bracketViewReview: TournamentBracketView
    private lateinit var layoutSimulatingOverlay: View

    private var tournamentId: Long = -1L
    private var careerId: Long = -1L
    private var careerName: String = ""
    private var currentPrizeMoneyItems: List<PrizeMoneyItem> = emptyList()
    private var selectedBoardFilterIndex: Int = -2

    private fun setupSwipeGestures() {
        val openDrawScrollView = (layoutOpenDrawInfo as? ViewGroup)?.children?.filterIsInstance<ScrollView>()?.firstOrNull()
        TournamentDetailUiHelper.attachHorizontalSwipe(
            views = listOfNotNull(layoutOpenDrawInfo, layoutEntrantsContainer, openDrawScrollView),
            recyclerViews = listOf(rvMatches, rvReviewMatches),
            onSwipeLeft = { onSwipeNextRound() },
            onSwipeRight = { onSwipePreviousRound() }
        )
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
        val currentIndex = when (val state = viewModel.uiState.value) {
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
            showMessage(getString(R.string.invalid_tournament_id), isError = true, duration = Snackbar.LENGTH_LONG)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onSettingsResumed()
        renderState(viewModel.uiState.value)
        if (::activeMatchesAdapter.isInitialized) activeMatchesAdapter.notifyItemRangeChanged(0, activeMatchesAdapter.itemCount)
        if (::reviewMatchesAdapter.isInitialized) reviewMatchesAdapter.notifyItemRangeChanged(0, reviewMatchesAdapter.itemCount)
        if (::bracketViewActive.isInitialized) bracketViewActive.refreshStyles()
        if (::bracketViewReview.isInitialized) bracketViewReview.refreshStyles()
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
        scrollBoardPills = findViewById(R.id.scrollBoardPills)
        chipGroupBoards = findViewById(R.id.chipGroupBoards)
        layoutGroupSelector = findViewById(R.id.layoutGroupSelector)
        chipGroupGroups = findViewById(R.id.chipGroupGroups)
        btnSimulateCpuMatches = findViewById(R.id.btnSimulateCpuMatches)
        btnSimulateComplete = findViewById(R.id.btnSimulateComplete)
        btnAdvanceRound = findViewById(R.id.btnAdvanceRound)
        rvMatches = findViewById(R.id.rvMatches)
        bracketViewActive = findViewById(R.id.bracketViewActive)
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
        scrollReviewBoardPills = findViewById(R.id.scrollReviewBoardPills)
        chipGroupReviewBoards = findViewById(R.id.chipGroupReviewBoards)
        rvReviewMatches = findViewById(R.id.rvReviewMatches)
        bracketViewReview = findViewById(R.id.bracketViewReview)

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

        bracketViewActive.onMatchClick = { matchModel ->
            if (!matchModel.isCompleted && matchModel.isUserMatch) {
                showUserMatchOptionsDialog(matchModel)
            }
        }
        bracketViewActive.onRoundSelected = { index ->
            viewModel.selectRound(index)
        }

        bracketViewReview.onMatchClick = { /* read-only */ }
        bracketViewReview.onRoundSelected = { index ->
            viewModel.selectRound(index)
        }
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
                    rootViewId = R.id.tournamentDetailMain
                )

                TournamentUiHelper.bindTournamentHeader(
                    this, tvActiveTitle, tvStatusBadgeActive, tvActiveTypeBadge, tvActiveHost,
                    state.tournament, state.activeRoundDisplayName, R.color.status_active, cardActiveControls
                )

                currentEvent = state.event

                btnSimulateComplete.visibility = if (debugMode) View.VISIBLE else View.GONE
                btnSimulateCpuMatches.visibility = if (!autoSimulateCpu && state.hasOpenCpuMatchesInActiveRound) View.VISIBLE else View.GONE
                btnAdvanceRound.visibility = if (!autoAdvanceRound && state.canAdvanceRound) View.VISIBLE else View.GONE
                btnAdvanceRound.isEnabled = state.canAdvanceRound
                btnAdvanceRound.setText(if (state.isFinalRound) R.string.btn_finish_tournament else R.string.btn_advance_round)

                currentPrizeMoneyItems = state.prizeMoneyItems

                // Round Chips setup
                TournamentDetailUiHelper.bindRoundChips(
                    chipGroup = chipGroupRounds,
                    scrollContainer = scrollRoundPills,
                    roundDisplayNames = state.availableRoundDisplayNames,
                    roundStatuses = state.roundStatuses,
                    selectedIndex = state.selectedRoundIndex,
                    onRoundSelected = { index -> viewModel.selectRound(index) }
                )

                // Group Chips setup
                if (state.availableGroups.isNotEmpty()) {
                    layoutGroupSelector.visibility = View.VISIBLE
                    chipGroupGroups.removeAllViews()
                    state.availableGroups.forEachIndexed { index, groupLetter ->
                        val chip = com.google.android.material.chip.Chip(this).apply {
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
                    scrollRoundPills.visibility = View.GONE
                    layoutGroupSelector.visibility = View.GONE
                    rvMatches.visibility = View.GONE
                    layoutOpenDrawInfo.visibility = View.GONE
                    scrollBoardPills.visibility = View.VISIBLE
                    bracketViewActive.visibility = View.VISIBLE
                    setupQualifierBoardFilterChips(state.event, state.allRoundsMatches, scrollBoardPills, chipGroupBoards) { filteredRounds ->
                        bracketViewActive.setRoundHeaders(state.availableRoundDisplayNames, state.roundStatuses)
                        bracketViewActive.setRounds(
                            filteredRounds,
                            state.selectedRoundIndex,
                            state.grandSlamGroupStandings,
                            state.prevRoundDisplayName,
                            state.openEntrantsGroups,
                            state.isOpenDrawInfoVisible,
                            state.openDrawInfoMap
                        )
                        bracketViewActive.selectRound(state.selectedRoundIndex)
                    }
                } else {
                    scrollBoardPills.visibility = View.GONE
                    scrollRoundPills.visibility = View.VISIBLE
                    bracketViewActive.visibility = View.GONE
                    if (state.isOpenDrawInfoVisible) {
                        rvMatches.visibility = View.GONE
                        layoutOpenDrawInfo.visibility = View.VISIBLE
                        tvDrawAfterRoundInfo.text = getString(R.string.draw_after_round_info, state.prevRoundDisplayName)
                        if (state.openEntrantsGroups.isNotEmpty()) {
                            TournamentDetailUiHelper.renderOpenDrawEntrants(this, layoutEntrantsContainer, state.openEntrantsGroups)
                        } else {
                            layoutEntrantsContainer.visibility = View.GONE
                        }
                    } else {
                        rvMatches.visibility = View.VISIBLE
                        layoutOpenDrawInfo.visibility = View.GONE
                        activeMatchesAdapter.qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromRounds(state.event, state.allRoundsMatches)
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
                    rootViewId = R.id.tournamentDetailMain
                )

                TournamentUiHelper.bindTournamentHeader(
                    this, tvReviewTitle, tvStatusBadgeReview, tvReviewTypeBadge, tvReviewHost,
                    state.tournament, getString(R.string.tournament_status_completed), R.color.status_completed, cardReviewSummary
                )
                UIHelper.formatFlagAndText(tvReviewHost, state.hostCountryCode, state.host)

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
                    UIHelper.bindSmartPlayerName(tvReviewWinner, state.winner)
                }
                tvReviewUserRound.text = getString(R.string.tournament_review_user_round, state.userReachedRoundText)
                tvReviewUserPrize.text = getString(R.string.tournament_review_user_prize, state.userEarnedPrizeMoneyText)

                currentPrizeMoneyItems = state.prizeMoneyItems

                // Round Chips
                TournamentDetailUiHelper.bindRoundChips(
                    chipGroup = chipGroupReviewRounds,
                    scrollContainer = scrollReviewRoundPills,
                    roundDisplayNames = state.availableRoundDisplayNames,
                    selectedIndex = state.selectedRoundIndex,
                    onRoundSelected = { index -> viewModel.selectRound(index) }
                )

                if (settingsManager.isBracketViewEnabled) {
                    scrollReviewRoundPills.visibility = View.GONE
                    rvReviewMatches.visibility = View.GONE
                    bracketViewReview.visibility = View.VISIBLE
                    val reviewRoundStatuses = List(state.availableRoundDisplayNames.size) { RoundStatus.COMPLETED }
                    setupQualifierBoardFilterChips(state.event, state.allRoundsMatches, scrollReviewBoardPills, chipGroupReviewBoards) { filteredRounds ->
                        bracketViewReview.setRoundHeaders(state.availableRoundDisplayNames, reviewRoundStatuses)
                        bracketViewReview.setRounds(filteredRounds, state.selectedRoundIndex, state.grandSlamGroupStandings)
                        bracketViewReview.selectRound(state.selectedRoundIndex)
                    }
                } else {
                    scrollReviewBoardPills.visibility = View.GONE
                    scrollReviewRoundPills.visibility = View.VISIBLE
                    bracketViewReview.visibility = View.GONE
                    rvReviewMatches.visibility = View.VISIBLE
                    reviewMatchesAdapter.qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromRounds(state.event, state.allRoundsMatches)
                    reviewMatchesAdapter.submitList(state.matches)
                }
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.VISIBLE
                showMessage(state.message, isError = true, duration = Snackbar.LENGTH_LONG)
            }
        }
    }

    private fun showPrizeMoneyDialog() {
        val state = viewModel.uiState.value
        val participantGroups = when (state) {
            is TournamentDetailState.Preview -> state.participantGroups
            is TournamentDetailState.Active -> state.participantGroups
            is TournamentDetailState.Review -> state.participantGroups
            else -> emptyList()
        }
        val isParticipantsFrozen = when (state) {
            is TournamentDetailState.Preview -> state.isParticipantsFrozen
            is TournamentDetailState.Active -> state.isParticipantsFrozen
            is TournamentDetailState.Review -> state.isParticipantsFrozen
            else -> false
        }
        val openQualifierSlotsText = when (state) {
            is TournamentDetailState.Preview -> state.openQualifierSlotsText
            is TournamentDetailState.Active -> state.openQualifierSlotsText
            is TournamentDetailState.Review -> state.openQualifierSlotsText
            else -> null
        }
        val matchDistancePreset = when (state) {
            is TournamentDetailState.Preview -> state.matchDistancePreset
            is TournamentDetailState.Active -> state.matchDistancePreset
            is TournamentDetailState.Review -> state.matchDistancePreset
            else -> MatchDistancePreset.DEFAULT
        }
        TournamentDialogs.showPrizeMoneyDialog(
            context = this,
            prizeMoneyItems = currentPrizeMoneyItems,
            event = currentEvent,
            participantGroups = participantGroups,
            isParticipantsFrozen = isParticipantsFrozen,
            openQualifierSlotsText = openQualifierSlotsText,
            matchDistancePreset = matchDistancePreset
        )
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

    private fun setupQualifierBoardFilterChips(
        event: TournamentEvent,
        allRoundsMatches: List<List<MatchUiModel>>,
        scrollView: HorizontalScrollView,
        chipGroup: ChipGroup,
        onFilterChanged: (filteredRounds: List<List<MatchUiModel>>) -> Unit
    ) {
        val isQualifier = event.isQualifier

        if (!isQualifier || !settingsManager.isBracketViewEnabled) {
            scrollView.visibility = View.GONE
            onFilterChanged(allRoundsMatches)
            return
        }

        scrollView.visibility = View.VISIBLE
        val boardCount = TournamentDetailUiHelper.getQualifierBoardCountFromRounds(event, allRoundsMatches)

        if (selectedBoardFilterIndex == -2) {
            selectedBoardFilterIndex = TournamentDetailUiHelper.findDefaultBoardIndex(allRoundsMatches, boardCount)
        }

        chipGroup.removeAllViews()

        val chipAll = com.google.android.material.chip.Chip(this).apply {
            text = getString(R.string.bracket_board_all)
            isCheckable = true
            isChecked = (selectedBoardFilterIndex == -1)
            setOnClickListener {
                selectedBoardFilterIndex = -1
                setupQualifierBoardFilterChips(event, allRoundsMatches, scrollView, chipGroup, onFilterChanged)
            }
        }
        chipGroup.addView(chipAll)

        for (boardIdx in 0 until boardCount) {
            val chipBoard = com.google.android.material.chip.Chip(this).apply {
                text = getString(R.string.bracket_board_format, boardIdx + 1)
                isCheckable = true
                isChecked = (selectedBoardFilterIndex == boardIdx)
                setOnClickListener {
                    selectedBoardFilterIndex = boardIdx
                    setupQualifierBoardFilterChips(event, allRoundsMatches, scrollView, chipGroup, onFilterChanged)
                }
            }
            chipGroup.addView(chipBoard)
        }

        val filtered = TournamentDetailUiHelper.filterMatchesForBoard(
            allRoundsMatches = allRoundsMatches,
            boardIndex = selectedBoardFilterIndex,
            boardCount = boardCount,
            qualificationUseCase = appContainer.tournamentQualificationUseCase
        )
        onFilterChanged(filtered)
    }

    private fun onFocusUserMatchClicked() {
        val state = viewModel.uiState.value
        if (settingsManager.isBracketViewEnabled) {
            val bracketView = when (state) {
                is TournamentDetailState.Active -> bracketViewActive
                is TournamentDetailState.Review -> bracketViewReview
                else -> return
            }
            val roundIndex = when (state) {
                is TournamentDetailState.Active -> state.selectedRoundIndex
                is TournamentDetailState.Review -> state.selectedRoundIndex
                else -> 0
            }
            bracketView.scrollToUserMatch(roundIndex)
        } else {
            val filteredMatches = when (state) {
                is TournamentDetailState.Active -> state.matches
                is TournamentDetailState.Review -> state.matches
                else -> emptyList()
            }
            val userMatchIndex = filteredMatches.indexOfFirst { matchUiModel -> matchUiModel.isUserMatch }
            if (userMatchIndex == -1) return
            val targetRecyclerView = when (state) {
                is TournamentDetailState.Active -> rvMatches
                is TournamentDetailState.Review -> rvReviewMatches
                else -> return
            }
            TournamentDetailUiHelper.scrollToMatchCentered(targetRecyclerView, userMatchIndex)
        }
    }

    private fun onToggleBracketViewClicked() {
        selectedBoardFilterIndex = -2
        settingsManager.isBracketViewEnabled = !settingsManager.isBracketViewEnabled
        updateBracketFabIcon()
        renderState(viewModel.uiState.value)
    }

    private fun updateBracketFabIcon() {
        if (settingsManager.isBracketViewEnabled) {
            fabToggleBracketView.setImageResource(R.drawable.ic_view_list)
            fabToggleBracketView.contentDescription = getString(R.string.cd_show_list_view)
        } else {
            fabToggleBracketView.setImageResource(R.drawable.ic_bracket_tree)
            fabToggleBracketView.contentDescription = getString(R.string.cd_show_bracket_view)
        }
    }

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
