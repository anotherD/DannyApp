package com.danny.dannyapp.ui.tournament

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.TournamentUiModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class TournamentCalendarFragment : Fragment(R.layout.fragment_tournament_calendar) {

    private lateinit var viewModel: TournamentCalendarViewModel
    private lateinit var adapter: TournamentCalendarAdapter
    private lateinit var rvTournaments: RecyclerView
    private lateinit var tvEmptyTournaments: TextView
    private lateinit var fabFocusCurrentTournament: FloatingActionButton

    private var careerId: Long = -1L
    private var careerName: String = ""
    private var hasScrolledToActive = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        careerId = arguments?.getLong(ARG_CAREER_ID, -1L) ?: -1L
        careerName = arguments?.getString(ARG_CAREER_NAME) ?: ""

        viewModel = ViewModelProvider(requireActivity())[TournamentCalendarViewModel::class.java]

        rvTournaments = view.findViewById(R.id.rvTournaments)
        tvEmptyTournaments = view.findViewById(R.id.tvEmptyTournaments)
        fabFocusCurrentTournament = view.findViewById(R.id.fabFocusCurrentTournament)

        fabFocusCurrentTournament.setOnClickListener {
            val targetIndex = findTargetTournamentIndex(adapter.currentList)
            if (targetIndex != -1) {
                TournamentDetailUiHelper.scrollToMatchCentered(rvTournaments, targetIndex)
            }
        }

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
                viewLifecycleOwner.lifecycleScope.launch {
                    val started = viewModel.startTournament(model.tournament.id)
                    if (started) {
                        val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                            putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, model.tournament.id)
                            putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                            putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                        }
                        startActivity(intent)
                    }
                }
            },
            onSimulateCompleteClick = { model ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.simulateUpToTournament(model.tournament.id)
                }
            }
        )
        rvTournaments.adapter = adapter

        observeState()

        if (careerId > 0L) {
            viewModel.loadTournaments(careerId)
        }
    }

    private fun findTargetTournamentIndex(tournaments: List<TournamentUiModel>): Int {
        return tournaments.indexOfFirst { it.status == TournamentStatus.ACTIVE || it.isNextPlanned }
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
                            fabFocusCurrentTournament.visibility = View.GONE
                        }
                        is TournamentCalendarState.Success -> {
                            adapter.simulatingTournamentId = if (state.simulationProgress != null) -1L else null
                            adapter.submitList(state.tournaments) {
                                if (!hasScrolledToActive && state.tournaments.isNotEmpty()) {
                                    val targetIndex = findTargetTournamentIndex(state.tournaments)
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
                            val targetIndex = findTargetTournamentIndex(state.tournaments)
                            if (state.tournaments.isEmpty()) {
                                tvEmptyTournaments.text = getString(R.string.no_tournaments_available)
                                tvEmptyTournaments.visibility = TextView.VISIBLE
                                rvTournaments.visibility = RecyclerView.GONE
                                fabFocusCurrentTournament.visibility = View.GONE
                            } else {
                                tvEmptyTournaments.visibility = TextView.GONE
                                rvTournaments.visibility = RecyclerView.VISIBLE
                                fabFocusCurrentTournament.visibility = if (targetIndex != -1) View.VISIBLE else View.GONE
                            }
                        }
                        is TournamentCalendarState.Error -> {
                            tvEmptyTournaments.text = state.message
                            tvEmptyTournaments.visibility = TextView.VISIBLE
                            rvTournaments.visibility = RecyclerView.GONE
                            fabFocusCurrentTournament.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && adapter.itemCount > 0) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
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
