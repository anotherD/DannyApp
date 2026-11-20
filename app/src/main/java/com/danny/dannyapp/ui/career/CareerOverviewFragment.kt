package com.danny.dannyapp.ui.career

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.danny.dannyapp.ui.oom.OrderOfMeritViewModel
import com.danny.dannyapp.ui.tournament.TournamentCalendarState
import com.danny.dannyapp.ui.tournament.TournamentCalendarViewModel
import com.danny.dannyapp.ui.tournament.TournamentDetailActivity
import com.danny.dannyapp.ui.tournament.TournamentUiHelper
import kotlinx.coroutines.launch

class CareerOverviewFragment : Fragment(R.layout.fragment_career_overview) {

    private lateinit var careerViewModel: CareerDetailViewModel
    private lateinit var calendarViewModel: TournamentCalendarViewModel
    private lateinit var oomViewModel: OrderOfMeritViewModel

    private lateinit var tvCareerOverviewPlayer: TextView
    private lateinit var tvCareerOverviewMeta: TextView
    private lateinit var tvCareerOverviewProfile: TextView
    private lateinit var tvOverviewRankOom: TextView
    private lateinit var tvOverviewRankPt: TextView
    private lateinit var tvOverviewRankPc: TextView
    private lateinit var tvOverviewRankEt: TextView
    private lateinit var containerActiveTournament: FrameLayout
    private lateinit var tvNoActiveTournament: TextView

    private var careerId: Long = -1L
    private var careerName: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        careerId = arguments?.getLong(ARG_CAREER_ID, -1L) ?: -1L
        careerName = arguments?.getString(ARG_CAREER_NAME) ?: ""

        careerViewModel = ViewModelProvider(requireActivity())[CareerDetailViewModel::class.java]
        calendarViewModel = ViewModelProvider(requireActivity())[TournamentCalendarViewModel::class.java]
        oomViewModel = ViewModelProvider(requireActivity())[OrderOfMeritViewModel::class.java]

        tvCareerOverviewPlayer = view.findViewById(R.id.tvCareerOverviewPlayer)
        tvCareerOverviewMeta = view.findViewById(R.id.tvCareerOverviewMeta)
        tvCareerOverviewProfile = view.findViewById(R.id.tvCareerOverviewProfile)
        tvOverviewRankOom = view.findViewById(R.id.tvOverviewRankOom)
        tvOverviewRankPt = view.findViewById(R.id.tvOverviewRankPt)
        tvOverviewRankPc = view.findViewById(R.id.tvOverviewRankPc)
        tvOverviewRankEt = view.findViewById(R.id.tvOverviewRankEt)
        containerActiveTournament = view.findViewById(R.id.containerActiveTournament)
        tvNoActiveTournament = view.findViewById(R.id.tvNoActiveTournament)

        observeData()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    careerViewModel.careerState.collect { state ->
                        if (state is CareerDetailState.Success) {
                            val careerWithProfile = state.careerWithProfile
                            val career = careerWithProfile.career
                            careerName = career.name

                            tvCareerOverviewMeta.text = getString(R.string.career_season_level_format, getString(R.string.season_format, career.season), getString(R.string.career_level_format, career.level))

                            UIHelper.formatFlagAndText(tvCareerOverviewPlayer, careerWithProfile.countryCode, careerWithProfile.playerName)
                            tvCareerOverviewProfile.text = getString(R.string.career_profile_format, careerWithProfile.profileName)
                        }
                    }
                }

                launch {
                    calendarViewModel.uiState.collect { state ->
                        if (state is TournamentCalendarState.Success) {
                            val targetModel = state.tournaments.find { it.status == TournamentStatus.ACTIVE }
                                ?: state.tournaments.find { it.isNextPlanned }
                                ?: state.tournaments.firstOrNull { !it.tournament.isCompleted }

                            if (targetModel != null) {
                                tvNoActiveTournament.visibility = View.GONE
                                containerActiveTournament.visibility = View.VISIBLE
                                bindActiveTournament(containerActiveTournament, targetModel, state.simulatingTournamentId)
                            } else {
                                containerActiveTournament.removeAllViews()
                                containerActiveTournament.visibility = View.GONE
                                tvNoActiveTournament.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                launch {
                    oomViewModel.oomEntries.collect { entries ->
                        bindRankingEntry(tvOverviewRankOom, entries)
                    }
                }

                launch {
                    oomViewModel.ptOomEntries.collect { entries ->
                        bindRankingEntry(tvOverviewRankPt, entries)
                    }
                }

                launch {
                    oomViewModel.pcOomEntries.collect { entries ->
                        bindRankingEntry(tvOverviewRankPc, entries)
                    }
                }

                launch {
                    oomViewModel.etOomEntries.collect { entries ->
                        bindRankingEntry(tvOverviewRankEt, entries)
                    }
                }
            }
        }
    }

    /**
     * Bindet den Ranglisten-Eintrag des aktuellen Nutzers an das angegebene TextView.
     */
    private fun bindRankingEntry(targetTextView: TextView, entries: List<OrderOfMeritEntry>) {
        val userEntry = entries.find { entry -> entry.isUser }
        if (userEntry != null) {
            val rankText = getString(R.string.rank_format, userEntry.rank)
            val prizeText = getString(R.string.prize_money_format, userEntry.prizeMoney)
            targetTextView.text = getString(R.string.career_overview_ranking_item_format, rankText, prizeText)
        } else {
            targetTextView.text = getString(R.string.career_overview_rank_unranked)
        }
    }

    private fun bindActiveTournament(container: FrameLayout, model: TournamentUiModel, simulatingTournamentId: Long?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val itemView = inflater.inflate(R.layout.item_tournament, container, false)

        TournamentUiHelper.bindTournamentTile(
            itemView = itemView,
            model = model,
            onTournamentClick = { uiModel ->
                val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, uiModel.tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            },
            onStartTournamentClick = { uiModel ->
                viewLifecycleOwner.lifecycleScope.launch {
                    calendarViewModel.startTournament(uiModel.tournament.id)
                    val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                        putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, uiModel.tournament.id)
                        putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                        putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                    }
                    startActivity(intent)
                }
            },
            onSimulateCompleteClick = { uiModel ->
                viewLifecycleOwner.lifecycleScope.launch {
                    calendarViewModel.simulateCompleteTournament(uiModel.tournament.id)
                }
            },
            simulatingTournamentId = simulatingTournamentId
        )

        container.addView(itemView)
    }

    companion object {
        private const val ARG_CAREER_ID = "arg_career_id"
        private const val ARG_CAREER_NAME = "arg_career_name"

        fun newInstance(careerId: Long, careerName: String): CareerOverviewFragment {
            return CareerOverviewFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CAREER_ID, careerId)
                    putString(ARG_CAREER_NAME, careerName)
                }
            }
        }
    }
}
