package com.danny.dannyapp.ui.career

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.oom.OrderOfMeritActivity
import com.danny.dannyapp.ui.oom.OrderOfMeritViewModel
import com.danny.dannyapp.ui.tournament.SeasonCalendarActivity
import com.danny.dannyapp.ui.tournament.TournamentCalendarState
import com.danny.dannyapp.ui.tournament.TournamentCalendarViewModel
import com.danny.dannyapp.ui.navigation.AppScreen
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val calendarViewModel: TournamentCalendarViewModel by viewModels()
    private val oomViewModel: OrderOfMeritViewModel by viewModels()

    private lateinit var layoutCareerSimulatingOverlay: View
    private lateinit var tvCareerSimulationProgress: TextView

    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: ""

        setupNavigationToolbar(
            screen = AppScreen.CAREER_OVERVIEW,
            titleOverride = getString(R.string.loading),
            rootViewId = R.id.careerDetailMain
        )

        layoutCareerSimulatingOverlay = findViewById(R.id.layoutCareerSimulatingOverlay)
        tvCareerSimulationProgress = findViewById(R.id.tvCareerSimulationProgress)

        val simulationBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Block back navigation during active simulation
            }
        }
        onBackPressedDispatcher.addCallback(this, simulationBackPressedCallback)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerCareerOverview, CareerOverviewFragment.newInstance(careerId, careerName))
                .commit()
        }

        careerViewModel.loadCareer(careerId)
        calendarViewModel.loadTournaments(careerId)
        oomViewModel.loadOrderOfMerit(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    careerViewModel.openRankingEvent.collect { type ->
                        val intent = Intent(this@CareerDetailActivity, OrderOfMeritActivity::class.java).apply {
                            putExtra(OrderOfMeritActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(OrderOfMeritActivity.EXTRA_CAREER_NAME, careerName)
                            putExtra(OrderOfMeritActivity.EXTRA_OOM_TYPE, type.name)
                        }
                        startActivity(intent)
                    }
                }
                launch {
                    careerViewModel.openCalendarEvent.collect {
                        val intent = Intent(this@CareerDetailActivity, SeasonCalendarActivity::class.java).apply {
                            putExtra(SeasonCalendarActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(SeasonCalendarActivity.EXTRA_CAREER_NAME, careerName)
                        }
                        startActivity(intent)
                    }
                }
                launch {
                    careerViewModel.careerState.collect { state ->
                        when (state) {
                            is CareerDetailState.Success -> {
                                val career = state.careerWithProfile.career
                                careerName = career.name
                                setupNavigationToolbar(
                                    screen = AppScreen.CAREER_OVERVIEW,
                                    titleOverride = career.name,
                                    rootViewId = R.id.careerDetailMain
                                )
                            }
                            else -> {}
                        }
                    }
                }
                launch {
                    calendarViewModel.uiState.collect { state ->
                        if (state is TournamentCalendarState.Success) {
                            val progress = state.simulationProgress
                            val simulating = progress != null
                            layoutCareerSimulatingOverlay.visibility = if (simulating) View.VISIBLE else View.GONE
                            simulationBackPressedCallback.isEnabled = simulating
                            if (progress != null) {
                                if (progress.tournamentName.isNotBlank()) {
                                    tvCareerSimulationProgress.text = getString(
                                        R.string.simulating_season_progress_format,
                                        progress.current,
                                        progress.total,
                                        progress.tournamentName
                                    )
                                } else {
                                    tvCareerSimulationProgress.text = getString(R.string.simulating_tournament)
                                }
                            }
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
