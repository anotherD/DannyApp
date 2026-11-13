package com.danny.dannyapp.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ImageView
import com.google.android.material.color.MaterialColors
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class TournamentDetailActivity : BaseActivity() {

    private val viewModel: TournamentDetailViewModel by viewModels()

    private lateinit var pbLoading: ProgressBar

    // Preview
    private lateinit var layoutPreviewContainer: View
    private lateinit var tvPreviewTitle: TextView
    private lateinit var tvStatusBadgePreview: TextView
    private lateinit var tvPreviewTypeBadge: TextView
    private lateinit var tvPreviewHost: TextView
    private lateinit var tvPreviewPrizeFund: TextView
    private lateinit var btnPrizeInfoPreview: ImageView
    private lateinit var btnStartTournamentPreview: Button
    private var currentEvent: com.danny.dannyapp.domain.model.TournamentEvent? = null

    // Active
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
    private lateinit var tvQualifiedEntrantsHeader: TextView
    private lateinit var layoutEntrantsContainer: LinearLayout
    private lateinit var activeMatchesAdapter: TournamentMatchAdapter

    // Review
    private lateinit var layoutReviewContainer: View
    private lateinit var tvReviewTitle: TextView
    private lateinit var tvReviewTypeBadge: TextView
    private lateinit var tvReviewHost: TextView
    private lateinit var tvReviewWinner: TextView
    private lateinit var tvReviewQualifierHeader: TextView
    private lateinit var layoutReviewQualifierWinnersList: LinearLayout
    private lateinit var btnPrizeInfoReview: View
    private lateinit var tvStatusBadgeReview: TextView
    private lateinit var tvReviewFinalScore: TextView
    private lateinit var tvReviewUserRound: TextView
    private lateinit var tvReviewUserPrize: TextView
    private lateinit var scrollReviewRoundPills: HorizontalScrollView
    private lateinit var chipGroupReviewRounds: ChipGroup
    private lateinit var rvReviewMatches: RecyclerView
    private lateinit var reviewMatchesAdapter: TournamentMatchAdapter
    private lateinit var layoutSimulatingOverlay: View

    private var tournamentId: Long = -1L
    private var careerId: Long = -1L
    private var careerName: String = ""
    private var currentPrizeMoneyItems: List<PrizeMoneyItem> = emptyList()

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
        observeState(onBackPressedCallback)

        if (tournamentId > 0L) {
            viewModel.loadTournament(tournamentId, careerId)
        } else {
            Toast.makeText(this, "Ungültige Turnier-ID", Toast.LENGTH_SHORT).show()
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
        layoutPreviewContainer = findViewById(R.id.layoutPreviewContainer)
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle)
        tvStatusBadgePreview = findViewById(R.id.tvStatusBadgePreview)
        tvPreviewTypeBadge = findViewById(R.id.tvPreviewTypeBadge)
        tvPreviewHost = findViewById(R.id.tvPreviewHost)
        tvPreviewPrizeFund = findViewById(R.id.tvPreviewPrizeFund)
        btnPrizeInfoPreview = findViewById(R.id.btnPrizeInfoPreview)
        btnStartTournamentPreview = findViewById(R.id.btnStartTournamentPreview)

        // Active
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
        layoutOpenDrawInfo = findViewById(R.id.layoutOpenDrawInfo)
        tvDrawAfterRoundInfo = findViewById(R.id.tvDrawAfterRoundInfo)
        tvQualifiedEntrantsHeader = findViewById(R.id.tvQualifiedEntrantsHeader)
        layoutEntrantsContainer = findViewById(R.id.layoutEntrantsContainer)

        // Review
        layoutReviewContainer = findViewById(R.id.layoutReviewContainer)
        tvReviewTitle = findViewById(R.id.tvReviewTitle)
        tvReviewTypeBadge = findViewById(R.id.tvReviewTypeBadge)
        tvReviewHost = findViewById(R.id.tvReviewHost)
        tvReviewWinner = findViewById(R.id.tvReviewWinner)
        tvReviewQualifierHeader = findViewById(R.id.tvReviewQualifierHeader)
        layoutReviewQualifierWinnersList = findViewById(R.id.layoutReviewQualifierWinnersList)
        btnPrizeInfoReview = findViewById(R.id.btnPrizeInfoReview)
        tvStatusBadgeReview = findViewById(R.id.tvStatusBadgeReview)
        tvReviewFinalScore = findViewById(R.id.tvReviewFinalScore)
        tvReviewUserRound = findViewById(R.id.tvReviewUserRound)
        tvReviewUserPrize = findViewById(R.id.tvReviewUserPrize)
        scrollReviewRoundPills = findViewById(R.id.scrollReviewRoundPills)
        chipGroupReviewRounds = findViewById(R.id.chipGroupReviewRounds)
        rvReviewMatches = findViewById(R.id.rvReviewMatches)
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
                    }
                }
            }
        }
    }

    private fun renderState(state: TournamentDetailState) {
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
                    state.tournament, state.activeRoundDisplayName, R.color.status_active
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
                    val chip = Chip(this).apply {
                        text = displayName
                        isCheckable = true
                        isChecked = (index == state.selectedRoundIndex)
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

                if (state.isOpenDrawInfoVisible) {
                    rvMatches.visibility = View.GONE
                    layoutOpenDrawInfo.visibility = View.VISIBLE
                    tvDrawAfterRoundInfo.text = getString(R.string.draw_after_round_info)
                    if (state.openEntrantsGroups.isNotEmpty()) {
                        tvQualifiedEntrantsHeader.visibility = View.VISIBLE
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
                                val typedValue = android.util.TypedValue()
                                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                                if (typedValue.resourceId != 0) {
                                    setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, typedValue.resourceId))
                                }
                            }
                            layoutEntrantsContainer.addView(tvGroupTitle)

                            group.players.forEach { player ->
                                val tvPlayer = TextView(this).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = (2 * resources.displayMetrics.density).toInt()
                                        bottomMargin = (2 * resources.displayMetrics.density).toInt()
                                    }
                                    textSize = 13f
                                    val typedValue = android.util.TypedValue()
                                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                                    if (typedValue.resourceId != 0) {
                                        setTextColor(ContextCompat.getColor(this@TournamentDetailActivity, typedValue.resourceId))
                                    }
                                }
                                val displayText = if (player.seed != null) {
                                    getString(R.string.player_with_seed, player.seed, player.name)
                                } else {
                                    player.name
                                }
                                TournamentUiHelper.formatFlagAndText(this, tvPlayer, player.countryCode, displayText)
                                layoutEntrantsContainer.addView(tvPlayer)
                            }
                        }
                    } else {
                        tvQualifiedEntrantsHeader.visibility = View.GONE
                        layoutEntrantsContainer.visibility = View.GONE
                    }
                } else {
                    rvMatches.visibility = View.VISIBLE
                    layoutOpenDrawInfo.visibility = View.GONE
                    activeMatchesAdapter.submitList(state.matches)
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
                    state.tournament, getString(R.string.tournament_status_completed), R.color.status_completed
                )

                currentEvent = state.event

                if (state.isQualifier) {
                    tvReviewQualifierHeader.visibility = View.VISIBLE
                    layoutReviewQualifierWinnersList.visibility = View.VISIBLE
                    TournamentUiHelper.bindQualifierWinnersList(
                        this, tvReviewQualifierHeader, layoutReviewQualifierWinnersList, state.qualifierWinners, textSizeSp = 14f
                    )
                    tvReviewWinner.visibility = View.GONE
                    tvReviewFinalScore.visibility = View.GONE
                } else {
                    tvReviewQualifierHeader.visibility = View.GONE
                    layoutReviewQualifierWinnersList.visibility = View.GONE
                    tvReviewWinner.visibility = View.VISIBLE
                    tvReviewWinner.text = getString(R.string.tournament_review_winner, state.winnerFlag, state.winnerName)
                    tvReviewFinalScore.visibility = View.VISIBLE
                    tvReviewFinalScore.text = getString(R.string.tournament_review_final_score, state.finalScoreText, state.opponentFlag, state.opponentName)
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

                reviewMatchesAdapter.submitList(state.matches)
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.VISIBLE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPrizeMoneyDialog() {
        if (currentPrizeMoneyItems.isEmpty()) return
        val event = currentEvent
        val totalPrize = if (event != null && event.totalPrizeFund > 0) {
            getString(R.string.tournament_total_prize_fund_format, event.totalPrizeFund.toLong())
        } else {
            getString(R.string.tournament_qualifying_no_prize)
        }

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val tvHeader = TextView(context).apply {
            text = totalPrize
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.rot_haupt))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvHeader)

        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PrizeMoneyAdapter().apply { submitList(currentPrizeMoneyItems) }
        }
        layout.addView(rv)

        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.tournament_preview_prizes_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_close), null)
            .show()
    }

    private fun showUserMatchOptionsDialog(matchModel: MatchUiModel) {
        val options = if (debugMode) {
            arrayOf(
                "Sieger festlegen (Sieg/Niederlage)",
                "Ergebnis manuell eingeben",
                "Live spielen (GameEngine)"
            )
        } else {
            arrayOf(
                "Ergebnis manuell eingeben",
                "Live spielen (GameEngine)"
            )
        }

        AlertDialog.Builder(this)
            .setTitle("User-Match Steuerung")
            .setItems(options) { _, which ->
                val selectedOption = options[which]
                when {
                    selectedOption.startsWith("Sieger festlegen") -> showSetWinnerDialog(matchModel)
                    selectedOption.startsWith("Ergebnis manuell") -> showManualScoreInputDialog(matchModel)
                    selectedOption.startsWith("Live spielen") -> showLiveGameEnginePlaceholder()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showSetWinnerDialog(matchModel: MatchUiModel) {
        val choices = arrayOf("Sieg (Du gewinnst)", "Niederlage (Gegner gewinnt)")

        AlertDialog.Builder(this)
            .setTitle("Sieger festlegen")
            .setItems(choices) { _, which ->
                val userWins = (which == 0)
                viewModel.setUserMatchWinner(matchModel.match.id, userWins)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showManualScoreInputDialog(matchModel: MatchUiModel) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val tvInfo = TextView(this).apply {
            text = "${matchModel.player1Name} vs ${matchModel.player2Name}\nRunde: ${matchModel.roundDisplayName}"
            setPadding(0, 0, 0, 20)
            textSize = 14f
        }

        val etP1 = EditText(this).apply {
            hint = "${matchModel.player1Name} Score"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val etP2 = EditText(this).apply {
            hint = "${matchModel.player2Name} Score"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(tvInfo)
        layout.addView(etP1)
        layout.addView(etP2)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Ergebnis manuell eingeben")
            .setView(layout)
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = etP1.text.toString().toIntOrNull() ?: -1
                val p2 = etP2.text.toString().toIntOrNull() ?: -1
                if (p1 < 0 || p2 < 0) {
                    Toast.makeText(this, "Bitte gültige Zahlen eingeben.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val error = viewModel.setManualUserMatchScore(matchModel.match.id, p1, p2)
                    if (error != null) {
                        Toast.makeText(this@TournamentDetailActivity, error, Toast.LENGTH_LONG).show()
                    } else {
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showLiveGameEnginePlaceholder() {
        AlertDialog.Builder(this)
            .setTitle("Live spielen (GameEngine)")
            .setMessage("Die automatische Scolia- und GameEngine-Anbindung wird in der nächsten Ausbaustufe für Live-Matches verknüpft.")
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        const val EXTRA_TOURNAMENT_ID = "extra_tournament_id"
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
