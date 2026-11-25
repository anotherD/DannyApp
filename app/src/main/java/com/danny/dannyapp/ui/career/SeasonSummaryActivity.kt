package com.danny.dannyapp.ui.career

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.domain.model.UserTourCardOutcome
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.navigation.AppScreen
import com.danny.dannyapp.ui.tournament.TournamentUiHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class SeasonSummaryActivity : BaseActivity() {

    private val viewModel: SeasonSummaryViewModel by viewModels()

    private var careerId: Long = -1L
    private var careerName: String = ""

    private lateinit var tvSeasonSummaryHeader: TextView
    private lateinit var tvSummaryUserPlayer: TextView
    private lateinit var tvSummaryOom: TextView
    private lateinit var tvSummaryPtOom: TextView
    private lateinit var tvSummaryPcOom: TextView
    private lateinit var tvSummaryEtOom: TextView
    private lateinit var tvSummaryMatches: TextView
    private lateinit var tvSummaryBestResult: TextView
    private lateinit var tvSummaryTourCard: TextView

    private lateinit var layoutSummaryWorldChampionContainer: View
    private lateinit var tvSummaryWorldChampion: TextView
    private lateinit var layoutSummaryOomLeaderContainer: View
    private lateinit var tvSummaryOomLeader: TextView
    private lateinit var layoutSummaryTopClimberContainer: View
    private lateinit var tvSummaryTopClimber: TextView
    private lateinit var layoutMajorWinnersList: LinearLayout

    private lateinit var btnStartNextSeason: Button
    private lateinit var layoutSeasonSummaryLoadingOverlay: View
    private lateinit var tvSeasonSummaryLoadingText: TextView

    override fun buildUpNavigationExtras(parentScreen: AppScreen, intent: Intent) {
        if (parentScreen == AppScreen.CAREER_OVERVIEW) {
            intent.putExtra(EXTRA_CAREER_ID, careerId)
            intent.putExtra(EXTRA_CAREER_NAME, careerName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_season_summary)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: ""

        setupCareerNavigationToolbar(
            screen = AppScreen.SEASON_SUMMARY,
            careerName = careerName,
            rootViewId = R.id.seasonSummaryMain
        )

        tvSeasonSummaryHeader = findViewById(R.id.tvSeasonSummaryHeader)
        tvSummaryUserPlayer = findViewById(R.id.tvSummaryUserPlayer)
        tvSummaryOom = findViewById(R.id.tvSummaryOom)
        tvSummaryPtOom = findViewById(R.id.tvSummaryPtOom)
        tvSummaryPcOom = findViewById(R.id.tvSummaryPcOom)
        tvSummaryEtOom = findViewById(R.id.tvSummaryEtOom)
        tvSummaryMatches = findViewById(R.id.tvSummaryMatches)
        tvSummaryBestResult = findViewById(R.id.tvSummaryBestResult)
        tvSummaryTourCard = findViewById(R.id.tvSummaryTourCard)

        layoutSummaryWorldChampionContainer = findViewById(R.id.layoutSummaryWorldChampionContainer)
        tvSummaryWorldChampion = findViewById(R.id.tvSummaryWorldChampion)
        layoutSummaryOomLeaderContainer = findViewById(R.id.layoutSummaryOomLeaderContainer)
        tvSummaryOomLeader = findViewById(R.id.tvSummaryOomLeader)
        layoutSummaryTopClimberContainer = findViewById(R.id.layoutSummaryTopClimberContainer)
        tvSummaryTopClimber = findViewById(R.id.tvSummaryTopClimber)
        layoutMajorWinnersList = findViewById(R.id.layoutMajorWinnersList)

        btnStartNextSeason = findViewById(R.id.btnStartNextSeason)
        layoutSeasonSummaryLoadingOverlay = findViewById(R.id.layoutSeasonSummaryLoadingOverlay)
        tvSeasonSummaryLoadingText = findViewById(R.id.tvSeasonSummaryLoadingText)

        val summaryBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Block back navigation during season advance
            }
        }
        onBackPressedDispatcher.addCallback(this, summaryBackPressedCallback)

        btnStartNextSeason.setOnClickListener {
            val summary = (viewModel.uiState.value as? SeasonSummaryUiState.Ready)?.summary ?: return@setOnClickListener
            val numberPicker = NumberPicker(this).apply {
                minValue = Career.MIN_LEVEL
                maxValue = Career.MAX_LEVEL
                value = summary.currentCareerLevel
                wrapSelectorWheel = false
                displayedValues = Array(Career.MAX_LEVEL - Career.MIN_LEVEL + 1) { i -> getString(R.string.career_level_format, Career.MIN_LEVEL + i) }
            }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(numberPicker, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.season_summary_level_dialog_title)
                .setMessage(getString(R.string.season_summary_level_dialog_message, summary.currentCareerLevel))
                .setView(container)
                .setPositiveButton(R.string.season_summary_level_dialog_confirm) { _, _ ->
                    viewModel.startNextSeason(careerId, numberPicker.value)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewModel.loadSummary(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SeasonSummaryUiState.Loading -> {
                            layoutSeasonSummaryLoadingOverlay.visibility = View.VISIBLE
                            tvSeasonSummaryLoadingText.text = getString(R.string.loading)
                            btnStartNextSeason.isEnabled = false
                            summaryBackPressedCallback.isEnabled = false
                        }
                        is SeasonSummaryUiState.Advancing -> {
                            layoutSeasonSummaryLoadingOverlay.visibility = View.VISIBLE
                            tvSeasonSummaryLoadingText.text = getString(R.string.season_summary_advancing_text)
                            btnStartNextSeason.isEnabled = false
                            summaryBackPressedCallback.isEnabled = true
                        }
                        is SeasonSummaryUiState.Ready -> {
                            layoutSeasonSummaryLoadingOverlay.visibility = View.GONE
                            btnStartNextSeason.isEnabled = true
                            summaryBackPressedCallback.isEnabled = false
                            val summary = state.summary
                            if (careerName.isBlank()) {
                                careerName = summary.careerName
                            }
                            setupCareerNavigationToolbar(
                                screen = AppScreen.SEASON_SUMMARY,
                                careerName = careerName,
                                seasonNumber = summary.completedSeasonNumber,
                                rootViewId = R.id.seasonSummaryMain
                            )

                            tvSeasonSummaryHeader.text = getString(R.string.season_summary_header_format, summary.completedSeasonNumber)
                            btnStartNextSeason.text = getString(R.string.season_summary_start_next_season_format, summary.nextSeasonNumber)

                            UIHelper.formatFlagAndText(tvSummaryUserPlayer, summary.userCountryCode, summary.userName)

                            tvSummaryOom.text = getString(R.string.career_overview_ranking_item_format, getString(R.string.rank_format, summary.oomRank), getString(R.string.prize_money_format, summary.oomPrizeMoney))
                            tvSummaryPtOom.text = getString(R.string.career_overview_ranking_item_format, getString(R.string.rank_format, summary.ptOomRank), getString(R.string.prize_money_format, summary.ptOomPrizeMoney))
                            tvSummaryPcOom.text = getString(R.string.career_overview_ranking_item_format, getString(R.string.rank_format, summary.pcOomRank), getString(R.string.prize_money_format, summary.pcOomPrizeMoney))
                            tvSummaryEtOom.text = getString(R.string.career_overview_ranking_item_format, getString(R.string.rank_format, summary.etOomRank), getString(R.string.prize_money_format, summary.etOomPrizeMoney))

                            tvSummaryMatches.text = getString(
                                R.string.season_summary_matches_value_format,
                                summary.matchesWon,
                                summary.matchesLost,
                                summary.tournamentsPlayed
                            )

                            if (summary.bestTournamentName != null && summary.bestReachedRoundKey != null) {
                                val roundName = TournamentUiHelper.getDisplayRoundName(summary.bestReachedRoundKey, this@SeasonSummaryActivity)
                                tvSummaryBestResult.text = getString(R.string.season_summary_best_result_format, summary.bestTournamentName, roundName)
                            } else {
                                tvSummaryBestResult.text = getString(R.string.season_summary_best_result_none)
                            }

                            tvSummaryTourCard.text = when (summary.tourCardOutcome) {
                                UserTourCardOutcome.RETAINED_TOP_64 -> getString(R.string.season_summary_tour_card_top64)
                                UserTourCardOutcome.SECOND_YEAR_ACTIVE -> getString(R.string.season_summary_tour_card_second_year)
                                UserTourCardOutcome.LOST_AND_REGAINED_Q_SCHOOL -> getString(R.string.season_summary_tour_card_qschool)
                            }

                            if (summary.worldChampion != null) {
                                layoutSummaryWorldChampionContainer.visibility = View.VISIBLE
                                UIHelper.formatFlagAndText(tvSummaryWorldChampion, summary.worldChampion.winnerCountryCode, summary.worldChampion.winnerName)
                            } else {
                                layoutSummaryWorldChampionContainer.visibility = View.GONE
                            }

                            if (summary.oomLeader != null) {
                                layoutSummaryOomLeaderContainer.visibility = View.VISIBLE
                                val leaderPrize = getString(R.string.prize_money_format, summary.oomLeader.prizeMoney)
                                val leaderText = getString(R.string.season_summary_oom_leader_format, summary.oomLeader.winnerName, leaderPrize)
                                UIHelper.formatFlagAndText(tvSummaryOomLeader, summary.oomLeader.winnerCountryCode, leaderText)
                            } else {
                                layoutSummaryOomLeaderContainer.visibility = View.GONE
                            }

                            if (summary.topClimber != null) {
                                layoutSummaryTopClimberContainer.visibility = View.VISIBLE
                                val climberPrize = getString(R.string.prize_money_format, summary.topClimber.prizeMoneyDelta)
                                val climberText = getString(R.string.season_summary_top_climber_format, summary.topClimber.playerName, summary.topClimber.rankDelta, climberPrize)
                                UIHelper.formatFlagAndText(tvSummaryTopClimber, summary.topClimber.countryCode, climberText)
                            } else {
                                layoutSummaryTopClimberContainer.visibility = View.GONE
                            }

                            layoutMajorWinnersList.removeAllViews()
                            if (summary.majorWinners.isEmpty()) {
                                val tvNone = TextView(this@SeasonSummaryActivity).apply {
                                    text = "-"
                                    textSize = 14f
                                    setTextColor(ContextCompat.getColor(this@SeasonSummaryActivity, R.color.grau_text_gedaempft))
                                }
                                layoutMajorWinnersList.addView(tvNone)
                            } else {
                                for (mw in summary.majorWinners) {
                                    val row = LinearLayout(this@SeasonSummaryActivity).apply {
                                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                            bottomMargin = (4 * resources.displayMetrics.density).toInt()
                                        }
                                        orientation = LinearLayout.HORIZONTAL
                                        weightSum = 2f
                                        isBaselineAligned = false
                                    }
                                    val tvTour = TextView(this@SeasonSummaryActivity).apply {
                                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                        text = mw.tournamentName
                                        textSize = 14f
                                        setTextColor(ContextCompat.getColor(this@SeasonSummaryActivity, R.color.grau_text_hell))
                                    }
                                    val tvWinner = TextView(this@SeasonSummaryActivity).apply {
                                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                        gravity = Gravity.END
                                        textSize = 14f
                                        setTextColor(ContextCompat.getColor(this@SeasonSummaryActivity, R.color.grau_text_hell))
                                        setTypeface(null, Typeface.BOLD)
                                    }
                                    UIHelper.formatFlagAndText(tvWinner, mw.winnerCountryCode, mw.winnerName, isLeft = true)
                                    row.addView(tvTour)
                                    row.addView(tvWinner)
                                    layoutMajorWinnersList.addView(row)
                                }
                            }
                        }
                        is SeasonSummaryUiState.Completed -> {
                            val intent = Intent(this@SeasonSummaryActivity, CareerDetailActivity::class.java).apply {
                                putExtra(EXTRA_CAREER_ID, careerId)
                                putExtra(EXTRA_CAREER_NAME, careerName)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                            finish()
                        }
                        is SeasonSummaryUiState.Error -> {
                            layoutSeasonSummaryLoadingOverlay.visibility = View.GONE
                            btnStartNextSeason.isEnabled = true
                            showMessage(state.message, isError = true)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
