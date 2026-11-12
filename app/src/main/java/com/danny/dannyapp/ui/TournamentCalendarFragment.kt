package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import kotlinx.coroutines.launch

class TournamentCalendarFragment : Fragment(R.layout.fragment_tournament_calendar) {

    private val viewModel: TournamentCalendarViewModel by activityViewModels()
    private lateinit var adapter: TournamentCalendarAdapter
    private lateinit var rvTournaments: RecyclerView
    private lateinit var tvEmptyTournaments: TextView

    private var careerId: Long = -1L
    private var careerName: String = ""
    private var hasScrolledToActive = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        careerId = arguments?.getLong(ARG_CAREER_ID, -1L) ?: -1L
        careerName = arguments?.getString(ARG_CAREER_NAME) ?: ""

        rvTournaments = view.findViewById(R.id.rvTournaments)
        tvEmptyTournaments = view.findViewById(R.id.tvEmptyTournaments)

        val layoutManager = LinearLayoutManager(requireContext())
        rvTournaments.layoutManager = layoutManager

        adapter = TournamentCalendarAdapter(
            onTournamentClick = { model ->
                val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, model.tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            },
            onStartTournamentClick = { model ->
                viewModel.startTournament(model.tournament.id, careerId)
                val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, model.tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            }
        )
        rvTournaments.adapter = adapter

        observeState()

        if (careerId > 0L) {
            viewModel.loadTournaments(careerId)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is TournamentCalendarState.Loading -> {
                            tvEmptyTournaments.text = getString(R.string.loading)
                            tvEmptyTournaments.visibility = TextView.VISIBLE
                            rvTournaments.visibility = RecyclerView.GONE
                        }
                        is TournamentCalendarState.Success -> {
                            adapter.submitList(state.tournaments) {
                                if (!hasScrolledToActive && state.tournaments.isNotEmpty()) {
                                    val targetIndex = state.tournaments.indexOfFirst { it.status == TournamentStatus.ACTIVE || it.isNextPlanned }
                                    if (targetIndex != -1) {
                                        (rvTournaments.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                                            targetIndex,
                                            rvTournaments.height / 2 - 100
                                        )
                                        hasScrolledToActive = true
                                    }
                                }
                            }
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
        private const val ARG_CAREER_ID = "arg_career_id"
        private const val ARG_CAREER_NAME = "arg_career_name"

        fun newInstance(careerId: Long, careerName: String): TournamentCalendarFragment {
            return TournamentCalendarFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CAREER_ID, careerId)
                    putString(ARG_CAREER_NAME, careerName)
                }
            }
        }
    }
}
