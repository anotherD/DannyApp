package com.danny.dannyapp.ui.tournament

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.career.CareerDetailActivity
import com.danny.dannyapp.ui.career.CareerDetailState
import com.danny.dannyapp.ui.career.CareerDetailViewModel
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.danny.dannyapp.ui.navigation.AppScreen
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class SeasonCalendarActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val calendarViewModel: TournamentCalendarViewModel by viewModels()

    private lateinit var adapter: TournamentCalendarAdapter
    private lateinit var rvTournaments: RecyclerView
    private lateinit var tvEmptyTournaments: TextView
    private lateinit var fabFocusCurrentTournament: FloatingActionButton

    private lateinit var chipFilterMajor: Chip
    private lateinit var chipFilterEt: Chip
    private lateinit var chipFilterPc: Chip

    private lateinit var layoutCareerSimulatingOverlay: View
    private lateinit var tvCareerSimulationProgress: TextView

    private var careerId: Long = -1L
    private var careerName: String = ""
    private var hasScrolledToActive = false
    private var allTournaments: List<TournamentUiModel> = emptyList()

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
        tvCareerSimulationProgress = findViewById(R.id.tvOverlayMessage)
        rvTournaments = findViewById(R.id.rvTournaments)
        tvEmptyTournaments = findViewById(R.id.tvEmptyTournaments)
        fabFocusCurrentTournament = findViewById(R.id.fabFocusCurrentTournament)

        chipFilterMajor = findViewById(R.id.chipFilterMajor)
        chipFilterEt = findViewById(R.id.chipFilterEt)
        chipFilterPc = findViewById(R.id.chipFilterPc)

        val chipCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            applyFiltersAndSubmit()
        }
        chipFilterMajor.setOnCheckedChangeListener(chipCheckedChangeListener)
        chipFilterEt.setOnCheckedChangeListener(chipCheckedChangeListener)
        chipFilterPc.setOnCheckedChangeListener(chipCheckedChangeListener)

        fabFocusCurrentTournament.setOnClickListener {
            val targetIndex = findTargetTournamentIndex(adapter.currentList)
            if (targetIndex != -1) {
                TournamentDetailUiHelper.scrollToMatchCentered(rvTournaments, targetIndex)
            }
        }

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
            onSimulateCompleteClick = { model ->
                lifecycleScope.launch {
                    calendarViewModel.simulateUpToTournament(model.tournament.id)
                }
            }
        )
        rvTournaments.adapter = adapter

        val simulationBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Block back navigation during active simulation
            }
        }
        onBackPressedDispatcher.addCallback(this, simulationBackPressedCallback)

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
                        when (state) {
                            is TournamentCalendarState.Loading -> {
                                tvEmptyTournaments.text = getString(R.string.loading)
                                tvEmptyTournaments.visibility = View.VISIBLE
                                rvTournaments.visibility = View.GONE
                                fabFocusCurrentTournament.visibility = View.GONE
                            }
                            is TournamentCalendarState.Success -> {
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

                                allTournaments = state.tournaments
                                adapter.simulatingTournamentId = if (state.simulationProgress != null) -1L else null
                                applyFiltersAndSubmit(scrollToTargetOnFirstLoad = true)
                            }
                            is TournamentCalendarState.Error -> {
                                tvEmptyTournaments.text = state.message
                                tvEmptyTournaments.visibility = View.VISIBLE
                                rvTournaments.visibility = View.GONE
                                fabFocusCurrentTournament.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findTargetTournamentIndex(tournaments: List<TournamentUiModel>): Int {
        return tournaments.indexOfFirst { it.status == TournamentStatus.ACTIVE || it.isNextPlanned }
    }

    private fun applyFiltersAndSubmit(scrollToTargetOnFirstLoad: Boolean = false) {
        val showMajor = chipFilterMajor.isChecked
        val showEt = chipFilterEt.isChecked
        val showPc = chipFilterPc.isChecked

        val filteredList = allTournaments.filter { model ->
            val event = TournamentEvent.valueOf(model.tournament.type)
            val isMajorCategory = event.isMajor || (event.isQualifier && event != TournamentEvent.QUALIFIER_ET)
            val isEtCategory = event == TournamentEvent.EUROPEAN_TOUR || event == TournamentEvent.QUALIFIER_ET
            val isPcCategory = event == TournamentEvent.PLAYERS_CHAMPIONSHIP

            (isMajorCategory && showMajor) || (isEtCategory && showEt) || (isPcCategory && showPc)
        }

        adapter.submitList(filteredList) {
            if (scrollToTargetOnFirstLoad && !hasScrolledToActive && filteredList.isNotEmpty()) {
                val targetIndex = findTargetTournamentIndex(filteredList)
                if (targetIndex != -1) {
                    rvTournaments.post {
                        if (rvTournaments.height > 0) {
                            val lm = rvTournaments.layoutManager as? LinearLayoutManager
                            val offset = if (targetIndex > 0) rvTournaments.height / 3 else 0
                            lm?.scrollToPositionWithOffset(targetIndex, offset)
                            hasScrolledToActive = true
                        }
                    }
                }
            }
        }

        val targetIndex = findTargetTournamentIndex(filteredList)
        if (filteredList.isEmpty()) {
            tvEmptyTournaments.text = if (allTournaments.isEmpty()) {
                getString(R.string.no_tournaments_available)
            } else {
                getString(R.string.no_tournaments_matching_filter)
            }
            tvEmptyTournaments.visibility = View.VISIBLE
            rvTournaments.visibility = View.GONE
            fabFocusCurrentTournament.visibility = View.GONE
        } else {
            tvEmptyTournaments.visibility = View.GONE
            rvTournaments.visibility = View.VISIBLE
            fabFocusCurrentTournament.visibility = if (targetIndex != -1) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && adapter.itemCount > 0) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
