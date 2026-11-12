package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.AppDatabase
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CareerDetailActivity : BaseActivity() {

    private val viewModel: CareerDetailViewModel by viewModels()
    private var careerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career_detail)

        val careerId = intent.getLongExtra(EXTRA_CAREER_ID, -1L)

        setupToolbar(
            titleText = getString(R.string.loading),
            subtitleText = getString(R.string.career),
            showBack = true,
            rootViewId = R.id.careerDetailMain,
        )

        val tvDetails: TextView = findViewById(R.id.tvCareerDetails)
        val tabLayoutCareer: TabLayout = findViewById(R.id.tabLayoutCareer)

        tabLayoutCareer.addTab(tabLayoutCareer.newTab().setText(getString(R.string.tab_calendar)))
        tabLayoutCareer.addTab(tabLayoutCareer.newTab().setText(getString(R.string.tab_tournament_view)))
        tabLayoutCareer.addTab(tabLayoutCareer.newTab().setText(getString(R.string.tab_oom)))

        tabLayoutCareer.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        val intent = Intent(this@CareerDetailActivity, TournamentCalendarActivity::class.java).apply {
                            putExtra(TournamentCalendarActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(TournamentCalendarActivity.EXTRA_CAREER_NAME, careerName)
                        }
                        startActivity(intent)
                    }
                    1 -> {
                        lifecycleScope.launch {
                            val tournamentId = findActiveOrNextTournamentId(careerId)
                            if (tournamentId != -1L) {
                                val intent = Intent(this@CareerDetailActivity, TournamentDetailActivity::class.java).apply {
                                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, tournamentId)
                                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                                }
                                startActivity(intent)
                            }
                        }
                    }
                    2 -> {
                        val intent = Intent(this@CareerDetailActivity, OrderOfMeritActivity::class.java).apply {
                            putExtra(OrderOfMeritActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(OrderOfMeritActivity.EXTRA_CAREER_NAME, careerName)
                        }
                        startActivity(intent)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                onTabSelected(tab)
            }
        })

        viewModel.loadCareer(careerId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.careerState.collect { state ->
                    when (state) {
                        is CareerDetailState.Loading -> {
                            tvDetails.text = getString(R.string.loading)
                        }
                        is CareerDetailState.InvalidId -> {
                            tvDetails.text = getString(R.string.career_invalid_id)
                        }
                        is CareerDetailState.NotFound -> {
                            tvDetails.text = getString(R.string.career_not_found)
                        }
                        is CareerDetailState.Success -> {
                            val careerWithProfile = state.careerWithProfile
                            val career = careerWithProfile.career
                            careerName = career.name
                            setupToolbar(
                                titleText = career.name,
                                subtitleText = getString(R.string.career),
                                showBack = true,
                                rootViewId = R.id.careerDetailMain,
                            )
                            val profileText = if (!careerWithProfile.profileName.isNullOrBlank()) {
                                "\n" + getString(R.string.career_profile_format, careerWithProfile.profileName)
                            } else ""
                            tvDetails.text = getString(R.string.career_current_format, career.name, career.level) + profileText
                        }
                    }
                }
            }
        }
    }

    private suspend fun findActiveOrNextTournamentId(careerId: Long): Long = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val seasonDao = db.seasonDao()
        val tournamentDao = db.tournamentDao()
        val matchDao = db.tournamentMatchDao()

        val season = seasonDao.getCurrentSeasonForCareer(careerId) ?: return@withContext -1L
        val tournaments = tournamentDao.getTournamentsListForSeason(season.id).sortedBy { it.order }

        for (t in tournaments) {
            if (!t.isCompleted) {
                val matches = matchDao.getMatchesListForTournament(t.id)
                if (matches.isNotEmpty()) {
                    return@withContext t.id
                }
            }
        }

        val firstPlanned = tournaments.firstOrNull { !it.isCompleted }
        if (firstPlanned != null) {
            val matches = matchDao.getMatchesListForTournament(firstPlanned.id)
            if (matches.isEmpty()) {
                val generateBracket = com.danny.dannyapp.domain.usecase.GenerateBracketUseCase(applicationContext)
                generateBracket(firstPlanned.id)
            }
            return@withContext firstPlanned.id
        }

        tournaments.firstOrNull()?.id ?: -1L
    }

    companion object {
        const val EXTRA_CAREER_ID = "extra_career_id"
    }
}
