package com.danny.dannyapp.ui

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
import kotlinx.coroutines.launch

class CareerOverviewFragment : Fragment(R.layout.fragment_career_overview) {

    private lateinit var careerViewModel: CareerDetailViewModel
    private lateinit var calendarViewModel: TournamentCalendarViewModel

    private lateinit var tvCareerOverviewDetails: TextView
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

        tvCareerOverviewDetails = view.findViewById(R.id.tvCareerOverviewDetails)
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
                            val profileText = if (!careerWithProfile.profileName.isNullOrBlank()) {
                                "\n" + getString(R.string.career_profile_format, careerWithProfile.profileName)
                            } else ""
                            tvCareerOverviewDetails.text = getString(R.string.career_current_format, career.name, career.level) + profileText
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
                                bindActiveTournament(containerActiveTournament, targetModel)
                            } else {
                                containerActiveTournament.removeAllViews()
                                containerActiveTournament.visibility = View.GONE
                                tvNoActiveTournament.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindActiveTournament(container: FrameLayout, model: TournamentUiModel) {
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
            }
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
