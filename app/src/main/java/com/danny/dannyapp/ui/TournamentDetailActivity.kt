package com.danny.dannyapp.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class TournamentDetailActivity : BaseActivity() {

    private val viewModel: TournamentDetailViewModel by viewModels()

    private lateinit var pbLoading: ProgressBar

    // Preview
    private lateinit var layoutPreviewContainer: ScrollView
    private lateinit var tvPreviewTitle: TextView
    private lateinit var tvPreviewHostAndType: TextView
    private lateinit var tvPreviewPrizeFund: TextView
    private lateinit var rvPreviewPrizeMoney: RecyclerView
    private lateinit var btnStartTournamentPreview: Button
    private lateinit var prizeMoneyAdapter: PrizeMoneyAdapter

    // Active
    private lateinit var layoutActiveContainer: View
    private lateinit var chipGroupRounds: ChipGroup
    private lateinit var layoutGroupSelector: View
    private lateinit var chipGroupGroups: ChipGroup
    private lateinit var btnSimulateCpuMatches: Button
    private lateinit var btnAdvanceRound: Button
    private lateinit var rvMatches: RecyclerView
    private lateinit var activeMatchesAdapter: TournamentMatchAdapter

    // Review
    private lateinit var layoutReviewContainer: View
    private lateinit var tvReviewWinner: TextView
    private lateinit var tvReviewFinalScore: TextView
    private lateinit var tvReviewUserRound: TextView
    private lateinit var tvReviewUserPrize: TextView
    private lateinit var chipGroupReviewRounds: ChipGroup
    private lateinit var rvReviewMatches: RecyclerView
    private lateinit var reviewMatchesAdapter: TournamentMatchAdapter

    private var tournamentId: Long = -1L
    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        tournamentId = intent.getLongExtra(EXTRA_TOURNAMENT_ID, -1L)
        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: "Karriere"

        setupToolbar(
            titleText = getString(R.string.tournament_detail),
            subtitleText = getString(R.string.breadcrumb_format, careerName),
            showBack = true,
            rootViewId = R.id.tournamentDetailMain
        )

        initViews()
        setupAdapters()
        setupListeners()
        observeState()

        if (tournamentId > 0L) {
            viewModel.loadTournament(tournamentId, careerId)
        } else {
            Toast.makeText(this, "Ungültige Turnier-ID", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btnSimulateCpuMatches.isInitialized) {
            btnSimulateCpuMatches.visibility = if (debugMode) View.VISIBLE else View.GONE
        }
    }

    private fun initViews() {
        pbLoading = findViewById(R.id.pbLoading)

        // Preview
        layoutPreviewContainer = findViewById(R.id.layoutPreviewContainer)
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle)
        tvPreviewHostAndType = findViewById(R.id.tvPreviewHostAndType)
        tvPreviewPrizeFund = findViewById(R.id.tvPreviewPrizeFund)
        rvPreviewPrizeMoney = findViewById(R.id.rvPreviewPrizeMoney)
        btnStartTournamentPreview = findViewById(R.id.btnStartTournamentPreview)

        // Active
        layoutActiveContainer = findViewById(R.id.layoutActiveContainer)
        chipGroupRounds = findViewById(R.id.chipGroupRounds)
        layoutGroupSelector = findViewById(R.id.layoutGroupSelector)
        chipGroupGroups = findViewById(R.id.chipGroupGroups)
        btnSimulateCpuMatches = findViewById(R.id.btnSimulateCpuMatches)
        btnAdvanceRound = findViewById(R.id.btnAdvanceRound)
        rvMatches = findViewById(R.id.rvMatches)

        // Review
        layoutReviewContainer = findViewById(R.id.layoutReviewContainer)
        tvReviewWinner = findViewById(R.id.tvReviewWinner)
        tvReviewFinalScore = findViewById(R.id.tvReviewFinalScore)
        tvReviewUserRound = findViewById(R.id.tvReviewUserRound)
        tvReviewUserPrize = findViewById(R.id.tvReviewUserPrize)
        chipGroupReviewRounds = findViewById(R.id.chipGroupReviewRounds)
        rvReviewMatches = findViewById(R.id.rvReviewMatches)
    }

    private fun setupAdapters() {
        prizeMoneyAdapter = PrizeMoneyAdapter()
        rvPreviewPrizeMoney.layoutManager = LinearLayoutManager(this)
        rvPreviewPrizeMoney.adapter = prizeMoneyAdapter

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

        btnAdvanceRound.setOnClickListener {
            viewModel.advanceRound()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
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

                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_format, careerName),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                tvPreviewTitle.text = state.tournament.name
                tvPreviewHostAndType.text = state.hostCountryText
                tvPreviewPrizeFund.text = state.totalPrizeFundText
                prizeMoneyAdapter.submitList(state.prizeMoneyItems)

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

                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_format, careerName),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                btnSimulateCpuMatches.visibility = if (debugMode) View.VISIBLE else View.GONE

                // Round Chips setup
                chipGroupRounds.removeAllViews()
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

                activeMatchesAdapter.submitList(state.matches)
            }

            is TournamentDetailState.Review -> {
                pbLoading.visibility = View.GONE
                layoutPreviewContainer.visibility = View.GONE
                layoutActiveContainer.visibility = View.GONE
                layoutReviewContainer.visibility = View.VISIBLE

                setupToolbar(
                    titleText = state.tournament.name,
                    subtitleText = getString(R.string.breadcrumb_format, careerName),
                    showBack = true,
                    rootViewId = R.id.tournamentDetailMain
                )

                tvReviewWinner.text = getString(R.string.tournament_review_winner, state.winnerFlag, state.winnerName)
                tvReviewFinalScore.text = getString(R.string.tournament_review_final_score, state.finalScoreText, state.opponentFlag, state.opponentName)
                tvReviewUserRound.text = getString(R.string.tournament_review_user_round, state.userReachedRoundText)
                tvReviewUserPrize.text = getString(R.string.tournament_review_user_prize, state.userEarnedPrizeMoneyText)

                // Round Chips
                chipGroupReviewRounds.removeAllViews()
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
                }

                reviewMatchesAdapter.submitList(state.matches)
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUserMatchOptionsDialog(matchModel: MatchUiModel) {
        val options = if (debugMode) {
            arrayOf(
                "Match simulieren (Skill/Zufall)",
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
                    selectedOption.startsWith("Match simulieren") -> viewModel.simulateUserMatch(matchModel.match.id)
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
