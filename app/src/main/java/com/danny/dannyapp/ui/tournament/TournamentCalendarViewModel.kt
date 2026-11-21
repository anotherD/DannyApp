package com.danny.dannyapp.ui.tournament

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.TournamentPhaseState
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.TournamentUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TournamentCalendarState {
    object Loading : TournamentCalendarState
    data class Success(val seasonId: Long, val tournaments: List<TournamentUiModel>, val simulatingTournamentId: Long? = null) : TournamentCalendarState
    data class Error(val message: String) : TournamentCalendarState
}

class TournamentCalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val careerRepository = application.appContainer.careerRepository
    private val tournamentRepository = application.appContainer.tournamentRepository
    private val seasonRepository = application.appContainer.seasonRepository
    private val playerRepository = application.appContainer.playerRepository

    private val generateSeasonCalendarUseCase = application.appContainer.generateSeasonCalendarUseCase
    private val generateBracketUseCase = application.appContainer.generateBracketUseCase
    private val advanceTournamentRoundUseCase = application.appContainer.advanceTournamentRoundUseCase
    private val tournamentProgressionUseCase = application.appContainer.tournamentProgressionUseCase
    private val getTournamentSummaryUseCase = application.appContainer.getTournamentSummaryUseCase

    private val _simulatingTournamentId = MutableStateFlow<Long?>(null)

    private val _uiState = MutableStateFlow<TournamentCalendarState>(TournamentCalendarState.Loading)
    val uiState: StateFlow<TournamentCalendarState> = _uiState.asStateFlow()

    /**
     * Lädt alle Turniere der aktuellen Saison einer Karriere und beobachtet deren Status.
     */
    fun loadTournaments(careerId: Long) {
        if (careerId <= 0L) {
            _uiState.value = TournamentCalendarState.Error(getApplication<Application>().getString(R.string.error_invalid_career_id))
            return
        }

        viewModelScope.launch {
            _uiState.value = TournamentCalendarState.Loading
            try {
                val season = seasonRepository.getOrCreateInitialSeasonForCareer(careerId)

                generateSeasonCalendarUseCase(season.id)

                val allPlayers = playerRepository.getAllPlayersList().associateBy { it.id }
                val careerWithProfile = careerRepository.getCareerById(careerId)

                combine(
                    tournamentRepository.getTournamentsForSeason(season.id),
                    tournamentRepository.getMatchesFlowForSeason(season.id),
                    tournamentRepository.getPlacementsFlowForSeasons(listOf(season.id)),
                    _simulatingTournamentId
                ) { tournaments, matches, placements, simulatingId ->
                    val uiModels = buildTournamentUiModels(tournaments, matches, placements, allPlayers, careerWithProfile)
                    TournamentCalendarState.Success(season.id, uiModels, simulatingId)
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Turnierkalender laden", e)
                _uiState.value = TournamentCalendarState.Error(e.message ?: getApplication<Application>().getString(R.string.error_unknown))
            }
        }
    }

    /**
     * Erstellt die UI-Modelle für alle Turniere einer Saison aus den Rohdaten.
     */
    private suspend fun buildTournamentUiModels(
        tournaments: List<Tournament>,
        matchesList: List<TournamentMatch>,
        placementsList: List<TournamentPlacement>,
        allPlayers: Map<Long, Player>,
        careerWithProfile: CareerWithProfile
    ): List<TournamentUiModel> {
        val matchesByTournament = matchesList.groupBy { it.tournamentId }
        val placementsByTournament = placementsList.groupBy { it.tournamentId }

        val activeRoundsByTournament: Map<Long, TournamentRound?> = tournaments
            .filter { !it.isCompleted && (matchesByTournament[it.id]?.isNotEmpty() == true) }
            .associate { tournament ->
                tournament.id to advanceTournamentRoundUseCase.evaluateTournamentState(tournament.id)?.activeRound
            }

        val summaries = getTournamentSummaryUseCase.buildSeasonSummaries(
            tournaments = tournaments,
            matchesByTournament = matchesByTournament,
            placementsByTournament = placementsByTournament,
            activeRoundsByTournament = activeRoundsByTournament
        )

        return summaries.map { summary ->
            val status = when (summary.phaseState) {
                TournamentPhaseState.COMPLETED -> TournamentStatus.COMPLETED
                TournamentPhaseState.ACTIVE -> TournamentStatus.ACTIVE
                TournamentPhaseState.PLANNED -> TournamentStatus.PLANNED
            }

            val winnerName: String? = summary.winnerId?.let { winnerId ->
                if (winnerId == USER_ID) getApplication<Application>().getString(R.string.player_name_you) else allPlayers[winnerId]?.name ?: getApplication<Application>().getString(R.string.player_name_unknown)
            }

            val qualifierWinners: List<PlayerUiInfo>? = if (summary.qualifierWinnerIds.isNotEmpty()) {
                summary.qualifierWinnerIds.map { winnerId ->
                    UIHelper.resolvePlayerUiInfo(
                        playerId = winnerId,
                        player = allPlayers[winnerId],
                        careerWithProfile = careerWithProfile
                    )
                }
            } else null

            val activeRoundName: String? = summary.activeRound?.let { activeRound ->
                TournamentUiHelper.getDisplayRoundName(activeRound.name, getApplication())
            }

            val userReachedRound: String? = summary.userReachedRound?.let { reachedRound ->
                TournamentUiHelper.getDisplayRoundName(reachedRound, getApplication())
            }

            TournamentUiModel(
                tournament = summary.tournament,
                status = status,
                isNextPlanned = summary.isNextPlanned,
                winnerName = winnerName,
                qualifierWinners = qualifierWinners,
                userReachedRound = userReachedRound,
                activeRoundName = activeRoundName
            )
        }
    }

    /**
     * Startet ein Turnier und generiert bei Bedarf den Turnierbaum.
     */
    suspend fun startTournament(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val matches = tournamentRepository.getMatchesListForTournament(tournamentId)
            if (matches.isEmpty()) {
                generateBracketUseCase(tournamentId)
            }
            true
        } catch (e: Exception) {
            getApplication<Application>().appContainer.reportError("Turnier starten", e)
            false
        }
    }

    /**
     * Simuliert ein komplettes Turnier bis zum Ende.
     */
    fun simulateCompleteTournament(tournamentId: Long) {
        if (_simulatingTournamentId.value != null) return
        viewModelScope.launch {
            _simulatingTournamentId.value = tournamentId
            try {
                withContext(Dispatchers.IO) {
                    val tournament = tournamentRepository.getTournamentById(tournamentId)
                    if (tournament.isCompleted) return@withContext

                    tournamentProgressionUseCase.simulateCompleteTournament(tournamentId)
                }
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Turnier simulieren", e)
            } finally {
                _simulatingTournamentId.value = null
            }
        }
    }
}
