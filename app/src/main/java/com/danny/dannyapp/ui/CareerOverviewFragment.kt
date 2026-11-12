package com.danny.dannyapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import com.danny.dannyapp.domain.model.TournamentEvent
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
                                bindTournamentTile(containerActiveTournament, targetModel)
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

    private fun bindTournamentTile(container: FrameLayout, model: TournamentUiModel) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val itemView = inflater.inflate(R.layout.item_tournament, container, false)

        val tvTournamentName: TextView = itemView.findViewById(R.id.tvTournamentName)
        val tvTournamentTypeBadge: TextView = itemView.findViewById(R.id.tvTournamentTypeBadge)
        val tvTournamentStatus: TextView = itemView.findViewById(R.id.tvTournamentStatus)
        val tvTournamentDetails: TextView = itemView.findViewById(R.id.tvTournamentDetails)
        val btnStartTournament: Button = itemView.findViewById(R.id.btnStartTournament)

        val tournament = model.tournament
        tvTournamentName.text = "#${tournament.order} ${tournament.name}"

        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { null }
        val isMajor = event in listOf(
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN, TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX, TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.GRAND_SLAM_OF_DARTS, TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS,
            TournamentEvent.WORLD_CHAMPIONSHIP
        )
        val isMajorQualifier = event in listOf(TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)
        val isEtQualifier = event == TournamentEvent.QUALIFIER_ET
        val isEuropeanTour = event == TournamentEvent.EUROPEAN_TOUR

        val typeText = when {
            isMajor -> getString(R.string.tournament_type_major)
            isMajorQualifier -> getString(R.string.tournament_type_major_qualifier)
            isEtQualifier -> getString(R.string.tournament_type_et_qualifier)
            isEuropeanTour -> getString(R.string.tournament_type_european_tour)
            else -> getString(R.string.tournament_type_players_championship)
        }

        tvTournamentTypeBadge.visibility = View.VISIBLE
        tvTournamentTypeBadge.text = typeText
        val badgeColorRes = when {
            isMajor -> R.color.badge_major
            isMajorQualifier -> R.color.badge_major_qualifier
            isEtQualifier -> R.color.badge_et_qualifier
            isEuropeanTour -> R.color.badge_european_tour
            else -> R.color.badge_players_championship
        }
        tvTournamentTypeBadge.background?.setTint(ContextCompat.getColor(requireContext(), badgeColorRes))

        val statusText = when (model.status) {
            TournamentStatus.COMPLETED -> {
                val winner = if (model.winnerName != null) " • Sieger: ${model.winnerName}" else ""
                "Abgeschlossen$winner"
            }
            TournamentStatus.ACTIVE -> {
                if (!model.activeRoundName.isNullOrBlank()) {
                    "Aktiv • ${model.activeRoundName}"
                } else {
                    "Aktiv"
                }
            }
            TournamentStatus.PLANNED -> "Geplant"
        }
        tvTournamentStatus.text = statusText
        val statusBgColorRes = when (model.status) {
            TournamentStatus.COMPLETED -> R.color.status_completed
            TournamentStatus.ACTIVE -> R.color.status_active
            TournamentStatus.PLANNED -> R.color.status_planned
        }
        tvTournamentStatus.background?.setTint(ContextCompat.getColor(requireContext(), statusBgColorRes))

        val host = tournament.hostCountry ?: "ENG"
        val flag = Country.getFlagEmoji(host)
        val city = getTournamentCity(host, tournament.name)
        tvTournamentDetails.text = "$flag $city"

        if (model.status == TournamentStatus.PLANNED && model.isNextPlanned) {
            btnStartTournament.visibility = View.VISIBLE
            btnStartTournament.setOnClickListener {
                calendarViewModel.startTournament(tournament.id, careerId)
                val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                    putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, tournament.id)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                    putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
                }
                startActivity(intent)
            }
        } else {
            btnStartTournament.visibility = View.GONE
        }

        itemView.setOnClickListener {
            val intent = Intent(requireContext(), TournamentDetailActivity::class.java).apply {
                putExtra(TournamentDetailActivity.EXTRA_TOURNAMENT_ID, tournament.id)
                putExtra(TournamentDetailActivity.EXTRA_CAREER_ID, careerId)
                putExtra(TournamentDetailActivity.EXTRA_CAREER_NAME, careerName)
            }
            startActivity(intent)
        }

        container.addView(itemView)
    }

    private fun getTournamentCity(hostCountry: String, tournamentName: String): String {
        val nameLower = tournamentName.lowercase()
        when {
            nameLower.contains("uk open") -> return "Minehead"
            nameLower.contains("world matchplay") -> return "Blackpool"
            nameLower.contains("world grand prix") -> return "Leicester"
            nameLower.contains("european championship") -> return "Dortmund"
            nameLower.contains("grand slam") -> return "Wolverhampton"
            nameLower.contains("players championship finals") -> return "Minehead"
            nameLower.contains("world championship") -> return "London"
            nameLower.contains("european open") -> return "Graz"
            nameLower.contains("baltic sea") -> return "Kiel"
            nameLower.contains("poland") -> return "Krakau"
            nameLower.contains("international darts open") -> return "Riesa"
            nameLower.contains("france") -> return "Liévin"
            nameLower.contains("german darts grand prix") -> return "München"
            nameLower.contains("european darts grand prix") -> return "Sindelfingen"
            nameLower.contains("european darts trophy") -> return "Göttingen"
            nameLower.contains("austrian darts open") -> return "Graz"
            nameLower.contains("czech") -> return "Prag"
            nameLower.contains("german darts open") -> return "Leverkusen"
            nameLower.contains("slovak") -> return "Bratislava"
            nameLower.contains("belgian") -> return "Gent"
            nameLower.contains("swiss") -> return "Basel"
            nameLower.contains("hungarian") -> return "Budapest"
            nameLower.contains("dutch") -> return "Maastricht"
        }
        return when (hostCountry.uppercase()) {
            "ENG" -> "Wigan"
            "DEU" -> "Hildesheim"
            "NLD" -> "Den Bosch"
            "AUT" -> "Graz"
            "CZE" -> "Prag"
            "SVK" -> "Bratislava"
            "BEL" -> "Gent"
            "SUI" -> "Basel"
            "HUN" -> "Budapest"
            "POL" -> "Krakau"
            "FRA" -> "Liévin"
            else -> hostCountry
        }
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
