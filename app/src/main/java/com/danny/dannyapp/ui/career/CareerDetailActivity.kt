package com.danny.dannyapp.ui.career

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.ui.BaseActivity
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.danny.dannyapp.ui.navigation.AppScreen
import com.danny.dannyapp.ui.oom.OrderOfMeritActivity
import com.danny.dannyapp.ui.oom.OrderOfMeritViewModel
import com.danny.dannyapp.ui.tournament.SeasonCalendarActivity
import com.danny.dannyapp.ui.tournament.TournamentCalendarState
import com.danny.dannyapp.ui.tournament.TournamentCalendarViewModel
import com.danny.dannyapp.ui.tournament.TournamentDetailActivity
import com.danny.dannyapp.ui.tournament.TournamentUiHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class CareerDetailActivity : BaseActivity() {

    private val careerViewModel: CareerDetailViewModel by viewModels()
    private val calendarViewModel: TournamentCalendarViewModel by viewModels()
    private val oomViewModel: OrderOfMeritViewModel by viewModels()

    private lateinit var layoutCareerSimulatingOverlay: View
    private lateinit var tvCareerSimulationProgress: TextView

    private lateinit var tvCareerOverviewPlayer: TextView
    private lateinit var tvCareerOverviewMeta: TextView
    private lateinit var layoutOverviewRankOom: LinearLayout
    private lateinit var layoutOverviewRankPt: LinearLayout
    private lateinit var layoutOverviewRankPc: LinearLayout
    private lateinit var layoutOverviewRankEt: LinearLayout
    private lateinit var containerActiveTournament: FrameLayout
    private lateinit var tvNoActiveTournament: TextView
    private lateinit var btnEndSeason: Button
    private lateinit var cardShortcutCalendar: MaterialCardView

    private var careerId: Long = -1L
    private var careerName: String = ""
    private var boundTournamentModel: TournamentUiModel? = null
    private var boundSimulatingTournamentId: Long? = null

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
        tvCareerSimulationProgress = findViewById(R.id.tvOverlayMessage)

        tvCareerOverviewPlayer = findViewById(R.id.tvCareerOverviewPlayer)
        tvCareerOverviewMeta = findViewById(R.id.tvCareerOverviewMeta)
        layoutOverviewRankOom = findViewById(R.id.layoutOverviewRankOom)
        layoutOverviewRankPt = findViewById(R.id.layoutOverviewRankPt)
        layoutOverviewRankPc = findViewById(R.id.layoutOverviewRankPc)
        layoutOverviewRankEt = findViewById(R.id.layoutOverviewRankEt)

        layoutOverviewRankOom.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_oom)
        layoutOverviewRankPt.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_pt)
        layoutOverviewRankPc.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_pc)
        layoutOverviewRankEt.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_et)
        
        layoutOverviewRankOom.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_oom)
        layoutOverviewRankPt.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_pt)
        layoutOverviewRankPc.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_pc)
        layoutOverviewRankEt.findViewById<TextView>(R.id.tvRankingLabel).text = getString(R.string.oom_tab_et)
        containerActiveTournament = findViewById(R.id.containerActiveTournament)
        tvNoActiveTournament = findViewById(R.id.tvNoActiveTournament)
        btnEndSeason = findViewById(R.id.btnEndSeason)
        cardShortcutCalendar = findViewById(R.id.cardShortcutCalendar)

        val simulationBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // Block back navigation during active simulation
            }
        }
        onBackPressedDispatcher.addCallback(this, simulationBackPressedCallback)

        layoutOverviewRankOom.setOnClickListener { careerViewModel.requestOpenRanking(OrderOfMeritType.OOM) }
        layoutOverviewRankPt.setOnClickListener { careerViewModel.requestOpenRanking(OrderOfMeritType.PT_OOM) }
        layoutOverviewRankPc.setOnClickListener { careerViewModel.requestOpenRanking(OrderOfMeritType.PC_OOM) }
        layoutOverviewRankEt.setOnClickListener { careerViewModel.requestOpenRanking(OrderOfMeritType.ET_OOM) }
        cardShortcutCalendar.setOnClickListener { careerViewModel.requestOpenCalendar() }
        btnEndSeason.setOnClickListener {
            val intent = Intent(this, SeasonSummaryActivity::class.java).apply {
                putExtra(SeasonSummaryActivity.EXTRA_CAREER_ID, careerId)
                putExtra(SeasonSummaryActivity.EXTRA_CAREER_NAME, careerName)
            }
            startActivity(intent)
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
                                val careerWithProfile = state.careerWithProfile
                                val career = careerWithProfile.career
                                careerName = career.name
                                setupNavigationToolbar(
                                    screen = AppScreen.CAREER_OVERVIEW,
                                    titleOverride = career.name,
                                    rootViewId = R.id.careerDetailMain
                                )

                                val profileText = getString(R.string.career_profile_format, careerWithProfile.profileName)
                                val seasonText = getString(R.string.season_format, career.season)
                                val levelText = getString(R.string.career_level_format, career.level)
                                tvCareerOverviewMeta.text = getString(
                                    R.string.career_overview_meta_full_format,
                                    profileText,
                                    seasonText,
                                    levelText
                                )

                                UIHelper.formatFlagAndText(tvCareerOverviewPlayer, careerWithProfile.countryCode, careerWithProfile.playerName)
                            }
                            is CareerDetailState.Error -> {
                                showMessage(state.message, isError = true)
                            }
                            is CareerDetailState.InvalidId -> {
                                showMessage(getString(R.string.error_invalid_career_id), isError = true)
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

                            val targetModel = state.tournaments.find { it.status == TournamentStatus.ACTIVE }
                                ?: state.tournaments.find { it.isNextPlanned }
                                ?: state.tournaments.firstOrNull { !it.tournament.isCompleted }

                            if (targetModel != null) {
                                tvNoActiveTournament.visibility = View.GONE
                                btnEndSeason.visibility = View.GONE
                                containerActiveTournament.visibility = View.VISIBLE
                                boundTournamentModel = targetModel
                                boundSimulatingTournamentId = if (simulating) targetModel.tournament.id else null
                                bindActiveTournament(containerActiveTournament, targetModel, boundSimulatingTournamentId)
                            } else {
                                boundTournamentModel = null
                                boundSimulatingTournamentId = null
                                containerActiveTournament.removeAllViews()
                                containerActiveTournament.visibility = View.GONE
                                tvNoActiveTournament.visibility = View.VISIBLE
                                btnEndSeason.visibility = if (simulating) View.GONE else View.VISIBLE
                            }
                        }
                    }
                }
                launch {
                    oomViewModel.oomEntries.collect { entries ->
                        bindRankingEntry(layoutOverviewRankOom, entries)
                    }
                }
                launch {
                    oomViewModel.ptOomEntries.collect { entries ->
                        bindRankingEntry(layoutOverviewRankPt, entries)
                    }
                }
                launch {
                    oomViewModel.pcOomEntries.collect { entries ->
                        bindRankingEntry(layoutOverviewRankPc, entries)
                    }
                }
                launch {
                    oomViewModel.etOomEntries.collect { entries ->
                        bindRankingEntry(layoutOverviewRankEt, entries)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val model = boundTournamentModel
        if (model != null) {
            containerActiveTournament.visibility = View.VISIBLE
            tvNoActiveTournament.visibility = View.GONE
            btnEndSeason.visibility = View.GONE
            bindActiveTournament(containerActiveTournament, model, boundSimulatingTournamentId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        careerId = intent.getLongExtra(EXTRA_CAREER_ID, careerId)
        careerName = intent.getStringExtra(EXTRA_CAREER_NAME) ?: careerName
        careerViewModel.loadCareer(careerId)
        calendarViewModel.loadTournaments(careerId)
        oomViewModel.loadOrderOfMerit(careerId)
    }

    private fun bindRankingEntry(targetCellView: View, entries: List<OrderOfMeritEntry>) {
        val tvRankingValue = targetCellView.findViewById<TextView>(R.id.tvRankingValue)
        val userEntry = entries.find { entry -> entry.isUser }
        if (userEntry != null) {
            val rankText = getString(R.string.rank_format, userEntry.rank)
            val prizeText = getString(R.string.prize_money_format, userEntry.prizeMoney)
            tvRankingValue.text = getString(R.string.career_overview_ranking_item_format, rankText, prizeText)
        } else {
            tvRankingValue.text = getString(R.string.career_overview_rank_unranked)
        }
    }

    private fun bindActiveTournament(container: FrameLayout, model: TournamentUiModel, simulatingTournamentId: Long?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val itemView = inflater.inflate(R.layout.item_tournament, container, false)

        TournamentUiHelper.bindTournamentTile(
            itemView = itemView,
            model = model,
            onTournamentClick = { uiModel ->
                val intent = Intent(this, TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, uiModel.tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            },
            onStartTournamentClick = { uiModel ->
                lifecycleScope.launch {
                    val started = calendarViewModel.startTournament(uiModel.tournament.id)
                    if (started) {
                        val intent = Intent(this@CareerDetailActivity, TournamentDetailActivity::class.java).apply {
                            putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, uiModel.tournament.id)
                            putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                        }
                        startActivity(intent)
                    }
                }
            },
            onSimulateCompleteClick = { uiModel ->
                lifecycleScope.launch {
                    calendarViewModel.simulateUpToTournament(uiModel.tournament.id)
                }
            },
            simulatingTournamentId = simulatingTournamentId
        )

        container.addView(itemView)
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
        const val EXTRA_CAREER_NAME = "extra_career_name"
    }
}
