package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import kotlinx.coroutines.launch

class TournamentCalendarActivity : BaseActivity() {

    private val viewModel: TournamentCalendarViewModel by viewModels()
    private lateinit var adapter: TournamentCalendarAdapter
    private lateinit var rvTournaments: RecyclerView
    private lateinit var tvEmptyTournaments: TextView
    private lateinit var btnSimulateSeason: Button

    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament_calendar)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: "Karriere"

        setupToolbar(
            titleText = getString(R.string.tournament_calendar),
            subtitleText = getString(R.string.breadcrumb_format, careerName),
            showBack = true,
            rootViewId = R.id.tournamentCalendarMain
        )

        rvTournaments = findViewById(R.id.rvTournaments)
        tvEmptyTournaments = findViewById(R.id.tvEmptyTournaments)
        btnSimulateSeason = findViewById(R.id.btnSimulateSeason)

        rvTournaments.layoutManager = LinearLayoutManager(this)
        adapter = TournamentCalendarAdapter(
            onTournamentClick = { model ->
                val intent = Intent(this, TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, model.tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            },
            onStartTournamentClick = { model ->
                viewModel.startTournament(model.tournament.id, careerId)
            },
            onSimulateTournamentClick = { model ->
                viewModel.simulateTournament(model.tournament.id, careerId)
            }
        )
        rvTournaments.adapter = adapter

        btnSimulateSeason.setOnClickListener {
            if (careerId > 0L) {
                viewModel.simulateCompleteSeason(careerId)
            }
        }

        observeState()

        if (careerId > 0L) {
            viewModel.loadTournaments(careerId)
        }
    }

    override fun onResume() {
        super.onResume()
        btnSimulateSeason.visibility = if (debugMode) View.VISIBLE else View.GONE
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is TournamentCalendarState.Loading -> {
                            tvEmptyTournaments.text = getString(R.string.loading)
                            tvEmptyTournaments.visibility = TextView.VISIBLE
                            rvTournaments.visibility = RecyclerView.GONE
                        }
                        is TournamentCalendarState.Success -> {
                            adapter.submitList(state.tournaments)
                            if (state.tournaments.isEmpty()) {
                                tvEmptyTournaments.text = "Keine Turniere vorhanden."
                                tvEmptyTournaments.visibility = TextView.VISIBLE
                                rvTournaments.visibility = RecyclerView.GONE
                            } else {
                                tvEmptyTournaments.visibility = TextView.GONE
                                rvTournaments.visibility = RecyclerView.VISIBLE
                            }
                        }
                        is TournamentCalendarState.Error -> {
                            tvEmptyTournaments.text = state.message
                            tvEmptyTournaments.visibility = TextView.VISIBLE
                            rvTournaments.visibility = RecyclerView.GONE
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
