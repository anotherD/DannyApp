package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
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
                            val sb = SpannableStringBuilder()
                            sb.append("Aktuelle Karriere:\n").append(career.name)
                            sb.append("\n").append(getString(R.string.career_level_format, career.level))
                            sb.append("\n").append(getString(R.string.season_format, career.season))
                            if (!careerWithProfile.profileName.isNullOrBlank()) {
                                sb.append("\n").append(getString(R.string.career_profile_format, careerWithProfile.profileName))
                            }
                            if (!careerWithProfile.playerName.isNullOrBlank()) {
                                sb.append("\n")
                                val countryCode = careerWithProfile.countryCode ?: "DEU"
                                val flagResId = Country.getFlagResId(countryCode)
                                if (flagResId != null) {
                                    val start = sb.length
                                    sb.append("   ").append(careerWithProfile.playerName)
                                    val drawable = ContextCompat.getDrawable(requireContext(), flagResId)
                                    if (drawable != null) {
                                        val heightPx = (tvCareerOverviewDetails.textSize * 1.17f).toInt()
                                        val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                            (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
                                        } else {
                                            (heightPx * 1.5f).toInt()
                                        }
                                        drawable.setBounds(0, 0, widthPx, heightPx)
                                        val imageSpan = TournamentUiHelper.VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
                                        sb.setSpan(imageSpan, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    }
                                } else {
                                    val flagEmoji = Country.getFlagEmoji(countryCode)
                                    sb.append("$flagEmoji ${careerWithProfile.playerName}")
                                }
                            }
                            tvCareerOverviewDetails.text = sb
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
