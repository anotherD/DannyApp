package com.danny.dannyapp.ui.tournament

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
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
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.GrandSlamGroupItem
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.danny.dannyapp.ui.navigation.AppScreen
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class TournamentDetailActivity : BaseActivity() {

    private val viewModel: TournamentDetailViewModel by viewModels()

    private lateinit var pbLoading: ProgressBar

    // Floating Action Buttons (FABs)
    private lateinit var layoutFabContainer: View
    private lateinit var fabFocusUserMatch: FloatingActionButton
    private lateinit var fabToggleBracketView: FloatingActionButton

    // Common Header Card
    private lateinit var cardTournamentHeader: MaterialCardView
    private lateinit var layoutActiveActions: View
    private lateinit var tvPreviewPrizeFund: TextView
    private lateinit var btnStartTournamentPreview: Button
    private var currentEvent: TournamentEvent? = null

    // Common Content
    private lateinit var layoutTournamentContent: View
    private lateinit var scrollRoundPills: HorizontalScrollView
    private lateinit var chipGroupRounds: ChipGroup
    private lateinit var scrollBoardPills: HorizontalScrollView
    private lateinit var chipGroupBoards: ChipGroup
    private lateinit var layoutGroupSelector: View
    private lateinit var layoutGroupRows: LinearLayout
    private lateinit var rvMatches: RecyclerView
    private lateinit var matchesAdapter: TournamentMatchAdapter
    private lateinit var bracketView: TournamentBracketView
    private lateinit var layoutOpenDrawInfo: View
    private lateinit var tvDrawAfterRoundInfo: TextView
    private lateinit var layoutEntrantsContainer: LinearLayout
    private lateinit var containerGroupTable: ViewGroup

    // Review / Active internal views in cardTournamentHeader
    private lateinit var tvReviewWinnerLabel: TextView
    private lateinit var tvReviewWinner: TextView
    private lateinit var tvReviewQualifierHeader: TextView
    private lateinit var layoutReviewQualifierWinnersList: LinearLayout
    private lateinit var tvReviewUserRound: TextView
    private lateinit var tvReviewUserPrize: TextView
    private lateinit var btnSimulateCpuMatches: Button
    private lateinit var btnSimulateComplete: Button
    private lateinit var btnAdvanceRound: Button

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
            recyclerViews = listOf(rvMatches),
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

    override fun buildUpNavigationExtras(parentScreen: AppScreen, intent: Intent) {
        if (parentScreen == AppScreen.SEASON_CALENDAR) {
            intent.putExtra(SeasonCalendarActivity.EXTRA_CAREER_ID, careerId)
            intent.putExtra(SeasonCalendarActivity.EXTRA_CAREER_NAME, careerName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        tournamentId = intent.getLongExtra(EXTRA_TOURNAMENT_ID, -1L)
        val intentCareerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        if (intentCareerId > 0L) {
            careerId = intentCareerId
        }
        val intentCareerName = intent.getStringExtra(EXTRA_CAREER_NAME)
        if (!intentCareerName.isNullOrBlank()) {
            careerName = intentCareerName
        }

        setupCareerNavigationToolbar(AppScreen.TOURNAMENT_DETAIL, careerName, rootViewId = R.id.tournamentDetailMain)

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
        if (::matchesAdapter.isInitialized) matchesAdapter.notifyItemRangeChanged(0, matchesAdapter.itemCount)
        if (::bracketView.isInitialized) bracketView.refreshStyles()
    }

    private fun initViews() {
        pbLoading = findViewById(R.id.pbLoading)
        layoutSimulatingOverlay = findViewById(R.id.layoutSimulatingOverlay)
        layoutSimulatingOverlay.findViewById<TextView>(R.id.tvOverlayMessage).setText(R.string.simulating_tournament)

        // Common Header Card
        cardTournamentHeader = findViewById(R.id.cardTournamentHeader)
        tvPreviewPrizeFund = findViewById(R.id.tvPreviewPrizeFund)
        btnStartTournamentPreview = findViewById(R.id.btnStartTournamentPreview)
        layoutActiveActions = findViewById(R.id.layoutActiveActions)
        btnSimulateCpuMatches = findViewById(R.id.btnSimulateCpuMatches)
        btnSimulateComplete = findViewById(R.id.btnSimulateComplete)
        btnAdvanceRound = findViewById(R.id.btnAdvanceRound)

        tvReviewWinnerLabel = findViewById(R.id.tvReviewWinnerLabel)
        tvReviewWinner = findViewById(R.id.tvReviewWinner)
        tvReviewQualifierHeader = findViewById(R.id.tvReviewQualifierHeader)
        layoutReviewQualifierWinnersList = findViewById(R.id.layoutReviewQualifierWinnersList)
        tvReviewUserRound = findViewById(R.id.tvReviewUserRound)
        tvReviewUserPrize = findViewById(R.id.tvReviewUserPrize)

        // Common Content
        layoutTournamentContent = findViewById(R.id.layoutTournamentContent)
        scrollRoundPills = findViewById(R.id.scrollRoundPills)
        chipGroupRounds = findViewById(R.id.chipGroupRounds)
        scrollBoardPills = findViewById(R.id.scrollBoardPills)
        chipGroupBoards = findViewById(R.id.chipGroupBoards)
        layoutGroupSelector = findViewById(R.id.layoutGroupSelector)
        layoutGroupRows = findViewById(R.id.layoutGroupRows)
        rvMatches = findViewById(R.id.rvMatches)
        bracketView = findViewById(R.id.bracketView)
        layoutOpenDrawInfo = findViewById(R.id.layoutOpenDrawInfo)
        tvDrawAfterRoundInfo = findViewById(R.id.tvDrawAfterRoundInfo)
        layoutEntrantsContainer = findViewById(R.id.layoutEntrantsContainer)
        containerGroupTable = findViewById(R.id.containerGroupTable)

        // FABs
        layoutFabContainer = findViewById(R.id.layoutFabContainer)
        fabFocusUserMatch = findViewById(R.id.fabFocusUserMatch)
        fabToggleBracketView = findViewById(R.id.fabToggleBracketView)
    }

    private fun setupAdapters() {
        matchesAdapter = TournamentMatchAdapter { matchModel ->
            val state = viewModel.uiState.value
            if (state is TournamentDetailState.Active && !matchModel.isCompleted && matchModel.isUserMatch) {
                showUserMatchOptionsDialog(matchModel)
            }
        }
        rvMatches.layoutManager = LinearLayoutManager(this)
        rvMatches.adapter = matchesAdapter

        bracketView.onMatchClick = { matchModel ->
            val state = viewModel.uiState.value
            if (state is TournamentDetailState.Active && !matchModel.isCompleted && matchModel.isUserMatch) {
                showUserMatchOptionsDialog(matchModel)
            }
        }
        bracketView.onRoundSelected = { index ->
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

        cardTournamentHeader.findViewById<View>(R.id.btnHeaderPrizeInfo).setOnClickListener {
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
                cardTournamentHeader.visibility = View.GONE
                btnStartTournamentPreview.visibility = View.GONE
                layoutTournamentContent.visibility = View.GONE
            }

            is TournamentDetailState.Preview -> {
                pbLoading.visibility = View.GONE
                cardTournamentHeader.visibility = View.VISIBLE
                tvPreviewPrizeFund.visibility = View.VISIBLE
                layoutActiveActions.visibility = View.GONE
                tvReviewWinnerLabel.visibility = View.GONE
                tvReviewWinner.visibility = View.GONE
                tvReviewQualifierHeader.visibility = View.GONE
                layoutReviewQualifierWinnersList.visibility = View.GONE
                tvReviewUserRound.visibility = View.GONE
                tvReviewUserPrize.visibility = View.GONE
                layoutTournamentContent.visibility = View.GONE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupCareerNavigationToolbar(AppScreen.TOURNAMENT_DETAIL, activeCareerName, state.seasonNumber, titleOverride = state.tournament.name, rootViewId = R.id.tournamentDetailMain)

                TournamentUiHelper.bindTournamentHeader(
                    this, cardTournamentHeader,
                    state.tournament, getString(R.string.tournament_status_planned), R.color.status_planned
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
                cardTournamentHeader.visibility = View.VISIBLE
                tvPreviewPrizeFund.visibility = View.GONE
                layoutActiveActions.visibility = View.VISIBLE
                tvReviewWinnerLabel.visibility = View.GONE
                tvReviewWinner.visibility = View.GONE
                tvReviewQualifierHeader.visibility = View.GONE
                layoutReviewQualifierWinnersList.visibility = View.GONE
                tvReviewUserRound.visibility = View.GONE
                tvReviewUserPrize.visibility = View.GONE
                btnStartTournamentPreview.visibility = View.GONE
                layoutTournamentContent.visibility = View.VISIBLE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupCareerNavigationToolbar(AppScreen.TOURNAMENT_DETAIL, activeCareerName, state.seasonNumber, titleOverride = state.tournament.name, rootViewId = R.id.tournamentDetailMain)

                TournamentUiHelper.bindTournamentHeader(
                    this, cardTournamentHeader,
                    state.tournament, state.activeRoundDisplayName, R.color.status_active
                )

                currentEvent = state.event

                btnSimulateComplete.visibility = if (debugMode) View.VISIBLE else View.GONE
                btnSimulateCpuMatches.visibility = if (!autoSimulateCpu && state.hasOpenCpuMatchesInActiveRound) View.VISIBLE else View.GONE
                btnAdvanceRound.visibility = if (!autoAdvanceRound && state.canAdvanceRound) View.VISIBLE else View.GONE
                btnAdvanceRound.isEnabled = state.canAdvanceRound
                btnAdvanceRound.setText(if (state.isFinalRound) R.string.btn_finish_tournament else R.string.btn_advance_round)

                currentPrizeMoneyItems = state.prizeMoneyItems

                renderTournamentContent(
                    event = state.event,
                    availableRoundDisplayNames = state.availableRoundDisplayNames,
                    roundStatuses = state.roundStatuses,
                    selectedRoundIndex = state.selectedRoundIndex,
                    availableGroups = state.availableGroups,
                    selectedGroupIndex = state.selectedGroupIndex,
                    matches = state.matches,
                    allRoundsMatches = state.allRoundsMatches,
                    grandSlamGroupStandings = state.grandSlamGroupStandings,
                    isOpenDrawInfoVisible = state.isOpenDrawInfoVisible,
                    prevRoundDisplayName = state.prevRoundDisplayName,
                    openEntrantsGroups = state.openEntrantsGroups,
                    openDrawInfoMap = state.openDrawInfoMap
                )
            }

            is TournamentDetailState.Review -> {
                pbLoading.visibility = View.GONE
                cardTournamentHeader.visibility = View.VISIBLE
                tvPreviewPrizeFund.visibility = View.GONE
                layoutActiveActions.visibility = View.GONE
                tvReviewUserRound.visibility = View.VISIBLE
                tvReviewUserPrize.visibility = View.VISIBLE
                btnStartTournamentPreview.visibility = View.GONE
                layoutTournamentContent.visibility = View.VISIBLE

                val activeCareerName = state.careerName.ifBlank { careerName }
                setupCareerNavigationToolbar(AppScreen.TOURNAMENT_DETAIL, activeCareerName, state.seasonNumber, titleOverride = state.tournament.name, rootViewId = R.id.tournamentDetailMain)

                TournamentUiHelper.bindTournamentHeader(
                    this, cardTournamentHeader,
                    state.tournament, getString(R.string.tournament_status_completed), R.color.status_completed
                )

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

                val reviewRoundStatuses = List(state.availableRoundDisplayNames.size) { RoundStatus.COMPLETED }

                renderTournamentContent(
                    event = state.event,
                    availableRoundDisplayNames = state.availableRoundDisplayNames,
                    roundStatuses = reviewRoundStatuses,
                    selectedRoundIndex = state.selectedRoundIndex,
                    availableGroups = state.availableGroups,
                    selectedGroupIndex = state.selectedGroupIndex,
                    matches = state.matches,
                    allRoundsMatches = state.allRoundsMatches,
                    grandSlamGroupStandings = state.grandSlamGroupStandings
                )
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.VISIBLE
                cardTournamentHeader.visibility = View.GONE
                btnStartTournamentPreview.visibility = View.GONE
                layoutTournamentContent.visibility = View.GONE
                layoutTournamentContent.visibility = View.GONE
                showMessage(state.message, isError = true, duration = Snackbar.LENGTH_LONG)
            }
        }
    }

    private fun renderTournamentContent(
        event: TournamentEvent,
        availableRoundDisplayNames: List<String>,
        roundStatuses: List<RoundStatus>,
        selectedRoundIndex: Int,
        availableGroups: List<String>,
        selectedGroupIndex: Int,
        matches: List<MatchUiModel>,
        allRoundsMatches: List<List<MatchUiModel>>,
        grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>>,
        isOpenDrawInfoVisible: Boolean = false,
        prevRoundDisplayName: String = "",
        openEntrantsGroups: List<EntrantGroupInfo> = emptyList(),
        openDrawInfoMap: Map<Int, Pair<String, List<EntrantGroupInfo>>> = emptyMap()
    ) {
        layoutTournamentContent.visibility = View.VISIBLE

        // Round Chips setup
        TournamentDetailUiHelper.bindRoundChips(
            chipGroup = chipGroupRounds,
            scrollContainer = scrollRoundPills,
            roundDisplayNames = availableRoundDisplayNames,
            roundStatuses = roundStatuses,
            selectedIndex = selectedRoundIndex,
            onRoundSelected = { index -> viewModel.selectRound(index) }
        )

        // Group Chips setup (8 equal-width pills per row)
        if (availableGroups.isNotEmpty()) {
            layoutGroupSelector.visibility = View.VISIBLE
            layoutGroupRows.removeAllViews()
            val density = resources.displayMetrics.density
            val spacingPx = (4 * density).toInt()

            availableGroups.chunked(8).forEachIndexed { rowIndex, rowGroups ->
                val rowLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (rowIndex > 0) topMargin = spacingPx
                    }
                }

                rowGroups.forEachIndexed { colIndex, groupLetter ->
                    val globalIndex = rowIndex * 8 + colIndex
                    val isSelected = (globalIndex == selectedGroupIndex)
                    val chip = (layoutInflater.inflate(R.layout.item_control_chip, rowLayout, false) as Chip).apply {
                        text = getString(R.string.group_label_nav, groupLetter)
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        isCheckable = true
                        isChecked = isSelected
                        setEnsureMinTouchTargetSize(false)
                        minWidth = 0
                        minimumWidth = 0
                        chipStartPadding = 0f
                        chipEndPadding = 0f
                        UIHelper.styleSelectionChip(this, isSelected)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (colIndex > 0) marginStart = spacingPx
                        }
                        setOnClickListener {
                            viewModel.selectGroup(globalIndex)
                        }
                    }
                    rowLayout.addView(chip)
                }
                layoutGroupRows.addView(rowLayout)
            }
        } else {
            layoutGroupSelector.visibility = View.GONE
        }

        val selectedGroupLetter = availableGroups.getOrNull(selectedGroupIndex)
        if (selectedGroupLetter != null && !settingsManager.isBracketViewEnabled) {
            containerGroupTable.visibility = View.VISIBLE
            containerGroupTable.removeAllViews()
            val standings = grandSlamGroupStandings[selectedGroupLetter] ?: emptyList()
            val groupCard = TournamentBracketCardBuilder.createGroupCardView(
                this,
                resources.displayMetrics.density,
                selectedGroupLetter,
                standings,
                isLargeLayout = true
            )
            containerGroupTable.addView(groupCard)
        } else {
            containerGroupTable.visibility = View.GONE
            containerGroupTable.removeAllViews()
        }

        if (settingsManager.isBracketViewEnabled) {
            scrollRoundPills.visibility = View.GONE
            layoutGroupSelector.visibility = View.GONE
            rvMatches.visibility = View.GONE
            layoutOpenDrawInfo.visibility = View.GONE
            scrollBoardPills.visibility = View.VISIBLE
            bracketView.visibility = View.VISIBLE
            containerGroupTable.visibility = View.GONE
            setupQualifierBoardFilterChips(event, allRoundsMatches, scrollBoardPills, chipGroupBoards) { filteredRounds ->
                bracketView.setRoundHeaders(availableRoundDisplayNames, roundStatuses)
                bracketView.setRounds(
                    filteredRounds,
                    selectedRoundIndex,
                    grandSlamGroupStandings,
                    prevRoundDisplayName,
                    openEntrantsGroups,
                    isOpenDrawInfoVisible,
                    openDrawInfoMap
                )
                bracketView.selectRound(selectedRoundIndex)
            }
        } else {
            scrollBoardPills.visibility = View.GONE
            scrollRoundPills.visibility = View.VISIBLE
            bracketView.visibility = View.GONE
            if (isOpenDrawInfoVisible) {
                rvMatches.visibility = View.GONE
                layoutOpenDrawInfo.visibility = View.VISIBLE
                tvDrawAfterRoundInfo.text = getString(R.string.draw_after_round_info, prevRoundDisplayName)
                if (openEntrantsGroups.isNotEmpty()) {
                    TournamentDetailUiHelper.renderOpenDrawEntrants(this, layoutEntrantsContainer, openEntrantsGroups)
                } else {
                    layoutEntrantsContainer.visibility = View.GONE
                }
            } else {
                rvMatches.visibility = View.VISIBLE
                layoutOpenDrawInfo.visibility = View.GONE
                matchesAdapter.qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromRounds(event, allRoundsMatches)
                matchesAdapter.submitList(matches)
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

        var selectedBoardChip: Chip? = null

        val isAllSelected = (selectedBoardFilterIndex == -1)
        val chipAll = (layoutInflater.inflate(R.layout.item_control_chip, chipGroup, false) as Chip).apply {
            text = getString(R.string.bracket_board_all)
            isCheckable = true
            isChecked = isAllSelected
            UIHelper.styleSelectionChip(this, isAllSelected)
            setOnClickListener {
                selectedBoardFilterIndex = -1
                setupQualifierBoardFilterChips(event, allRoundsMatches, scrollView, chipGroup, onFilterChanged)
            }
        }
        chipGroup.addView(chipAll)
        if (isAllSelected) {
            selectedBoardChip = chipAll
        }

        for (boardIdx in 0 until boardCount) {
            val isBoardSelected = (selectedBoardFilterIndex == boardIdx)
            val chipBoard = (layoutInflater.inflate(R.layout.item_control_chip, chipGroup, false) as Chip).apply {
                text = getString(R.string.bracket_board_format, boardIdx + 1)
                isCheckable = true
                isChecked = isBoardSelected
                UIHelper.styleSelectionChip(this, isBoardSelected)
                setOnClickListener {
                    selectedBoardFilterIndex = boardIdx
                    setupQualifierBoardFilterChips(event, allRoundsMatches, scrollView, chipGroup, onFilterChanged)
                }
            }
            chipGroup.addView(chipBoard)
            if (isBoardSelected) {
                selectedBoardChip = chipBoard
            }
        }

        selectedBoardChip?.let {
            UIHelper.centerViewInHorizontalScroll(scrollView, it)
        }

        val filtered = TournamentDetailUiHelper.filterMatchesForBoard(
            allRoundsMatches = allRoundsMatches,
            boardIndex = selectedBoardFilterIndex,
            boardCount = boardCount
        )
        onFilterChanged(filtered)
    }

    private fun onFocusUserMatchClicked() {
        val state = viewModel.uiState.value
        val roundIndex = when (state) {
            is TournamentDetailState.Active -> state.selectedRoundIndex
            is TournamentDetailState.Review -> state.selectedRoundIndex
            else -> return
        }
        val matches = when (state) {
            is TournamentDetailState.Active -> state.matches
            is TournamentDetailState.Review -> state.matches
            else -> emptyList()
        }

        if (settingsManager.isBracketViewEnabled) {
            bracketView.scrollToUserMatch(roundIndex)
        } else {
            val userMatchIndex = matches.indexOfFirst { matchUiModel -> matchUiModel.isUserMatch }
            if (userMatchIndex == -1) return
            TournamentDetailUiHelper.scrollToMatchCentered(rvMatches, userMatchIndex)
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
