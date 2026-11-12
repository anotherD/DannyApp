package com.danny.dannyapp.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
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
    private lateinit var spinnerRound: Spinner
    private lateinit var layoutGroupSelector: LinearLayout
    private lateinit var spinnerGroup: Spinner
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
    private lateinit var spinnerReviewRound: Spinner
    private lateinit var rvReviewMatches: RecyclerView
    private lateinit var reviewMatchesAdapter: TournamentMatchAdapter

    private var isSpinningRoundInternal = false
    private var isSpinningGroupInternal = false

    private var tournamentId: Long = -1L
    private var careerId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_detail)

        tournamentId = intent.getLongExtra(EXTRA_TOURNAMENT_ID, -1L)
        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.tournament_detail),
            subtitleText = "ID: $tournamentId",
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
        spinnerRound = findViewById(R.id.spinnerRound)
        layoutGroupSelector = findViewById(R.id.layoutGroupSelector)
        spinnerGroup = findViewById(R.id.spinnerGroup)
        btnSimulateCpuMatches = findViewById(R.id.btnSimulateCpuMatches)
        btnAdvanceRound = findViewById(R.id.btnAdvanceRound)
        rvMatches = findViewById(R.id.rvMatches)

        // Review
        layoutReviewContainer = findViewById(R.id.layoutReviewContainer)
        tvReviewWinner = findViewById(R.id.tvReviewWinner)
        tvReviewFinalScore = findViewById(R.id.tvReviewFinalScore)
        tvReviewUserRound = findViewById(R.id.tvReviewUserRound)
        tvReviewUserPrize = findViewById(R.id.tvReviewUserPrize)
        spinnerReviewRound = findViewById(R.id.spinnerReviewRound)
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

        spinnerRound.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinningRoundInternal) {
                    viewModel.selectRound(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerGroup.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinningGroupInternal) {
                    viewModel.selectGroup(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerReviewRound.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinningRoundInternal) {
                    viewModel.selectRound(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

                // Round Spinner setup
                isSpinningRoundInternal = true
                val roundAdapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    state.availableRoundDisplayNames
                )
                roundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerRound.adapter = roundAdapter
                if (state.selectedRoundIndex in state.availableRoundDisplayNames.indices) {
                    spinnerRound.setSelection(state.selectedRoundIndex, false)
                }
                isSpinningRoundInternal = false

                // Group Spinner setup
                if (state.availableGroups.isNotEmpty()) {
                    layoutGroupSelector.visibility = View.VISIBLE
                    isSpinningGroupInternal = true
                    val groupAdapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        state.availableGroups.map { "Gruppe $it" }
                    )
                    groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerGroup.adapter = groupAdapter
                    if (state.selectedGroupIndex in state.availableGroups.indices) {
                        spinnerGroup.setSelection(state.selectedGroupIndex, false)
                    }
                    isSpinningGroupInternal = false
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

                tvReviewWinner.text = "🏆 Turniersieger: ${state.winnerName}"
                tvReviewFinalScore.text = "Final-Ergebnis: ${state.finalScoreText}"
                tvReviewUserRound.text = "Deine erreichte Runde: ${state.userReachedRoundText}"
                tvReviewUserPrize.text = "Gewonnenes Preisgeld: ${state.userEarnedPrizeMoneyText}"

                // Round Spinner
                isSpinningRoundInternal = true
                val reviewRoundAdapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    state.availableRoundDisplayNames
                )
                reviewRoundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerReviewRound.adapter = reviewRoundAdapter
                if (state.selectedRoundIndex in state.availableRoundDisplayNames.indices) {
                    spinnerReviewRound.setSelection(state.selectedRoundIndex, false)
                }
                isSpinningRoundInternal = false

                reviewMatchesAdapter.submitList(state.matches)
            }

            is TournamentDetailState.Error -> {
                pbLoading.visibility = View.GONE
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showUserMatchOptionsDialog(matchModel: MatchUiModel) {
        val options = arrayOf(
            "1. Match simulieren (Skill/Zufall)",
            "2. Sieger festlegen (Sieg/Niederlage)",
            "3. Ergebnis manuell eingeben",
            "4. Live spielen (GameEngine)"
        )

        AlertDialog.Builder(this)
            .setTitle("User-Match Steuerung")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.simulateUserMatch(matchModel.match.id)
                    1 -> showSetWinnerDialog(matchModel)
                    2 -> showManualScoreInputDialog(matchModel)
                    3 -> showLiveGameEnginePlaceholder()
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
    }
}
