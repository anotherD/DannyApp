package com.danny.dannyapp.ui.tournament

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
import com.danny.dannyapp.ui.career.CareerDetailActivity
import com.danny.dannyapp.ui.career.CareerDetailState
import com.danny.dannyapp.ui.career.CareerDetailViewModel
import com.danny.dannyapp.ui.navigation.AppScreen
import kotlinx.coroutines.launch

class SeasonCalendarActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val calendarViewModel: TournamentCalendarViewModel by viewModels()

    private lateinit var layoutCareerSimulatingOverlay: View
    private lateinit var tvCareerSimulationProgress: TextView

    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun buildUpNavigationExtras(parentScreen: AppScreen, intent: Intent) {
        if (parentScreen == AppScreen.CAREER_OVERVIEW) {
            intent.putExtra(CareerDetailActivity.EXTRA_CAREER_ID, careerId)
            intent.putExtra(CareerDetailActivity.EXTRA_CAREER_NAME, careerName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_season_calendar)

        careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: ""

        setupCareerNavigationToolbar(AppScreen.SEASON_CALENDAR, careerName, rootViewId = R.id.seasonCalendarMain)

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
                .replace(R.id.fragmentContainerCalendar, TournamentCalendarFragment.newInstance(careerId, careerName))
                .commit()
        }

        careerViewModel.loadCareer(careerId)
        calendarViewModel.loadTournaments(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    careerViewModel.careerState.collect { state ->
                        when (state) {
                            is CareerDetailState.Success -> {
                                val career = state.careerWithProfile.career
                                careerName = career.name
                                setupCareerNavigationToolbar(AppScreen.SEASON_CALENDAR, careerName, career.season, rootViewId = R.id.seasonCalendarMain)
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
