package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TournamentStatus {
    COMPLETED, ACTIVE, PLANNED
}

data class TournamentUiModel(
    val tournament: Tournament,
    val status: TournamentStatus,
    val isNextPlanned: Boolean,
    val winnerName: String?,
    val userReachedRound: String?,
    val activeRoundName: String?
)

sealed interface TournamentCalendarState {
    object Loading : TournamentCalendarState
    data class Success(val seasonId: Long, val tournaments: List<TournamentUiModel>) : TournamentCalendarState
    data class Error(val message: String) : TournamentCalendarState
}

class TournamentCalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val tournamentDao = db.tournamentDao()
    private val tournamentMatchDao = db.tournamentMatchDao()
    private val tournamentPlacementDao = db.tournamentPlacementDao()
    private val seasonDao = db.seasonDao()
    private val playerDao = db.playerDao()
    private val careerRepository = CareerRepository(application)

    private val generateSeasonCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentDao)
    private val generateBracketUseCase = GenerateBracketUseCase(application)

    private val _uiState = MutableStateFlow<TournamentCalendarState>(TournamentCalendarState.Loading)
    val uiState: StateFlow<TournamentCalendarState> = _uiState.asStateFlow()

    fun loadTournaments(careerId: Long) {
        if (careerId <= 0L) {
            _uiState.value = TournamentCalendarState.Error("Ungültige Karriere-ID")
            return
        }

        viewModelScope.launch {
            _uiState.value = TournamentCalendarState.Loading
            try {
                val season = seasonDao.getCurrentSeasonForCareer(careerId) ?: run {
                    seasonDao.insertSeason(Season(careerId = careerId, seasonNumber = 1))
                    seasonDao.getCurrentSeasonForCareer(careerId) ?: throw IllegalStateException("Could not create season")
                }

                // Ensure calendar is generated
                generateSeasonCalendarUseCase(season.id)

                val allPlayers = playerDao.getAllPlayersList().associateBy { it.id }

                combine(
                    tournamentDao.getTournamentsForSeason(season.id),
                    tournamentMatchDao.getMatchesFlowForSeason(season.id),
                    tournamentPlacementDao.getPlacementsFlowForSeasons(listOf(season.id))
                ) { tournaments, matches, placements ->
                    buildTournamentUiModels(tournaments, matches, placements, allPlayers)
                }.collect { uiModels ->
                    _uiState.value = TournamentCalendarState.Success(season.id, uiModels)
                }
            } catch (e: Exception) {
                _uiState.value = TournamentCalendarState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    private fun buildTournamentUiModels(
        tournaments: List<Tournament>,
        matchesList: List<TournamentMatch>,
        placementsList: List<TournamentPlacement>,
        allPlayers: Map<Long, com.danny.dannyapp.data.db.Player>
    ): List<TournamentUiModel> {
        val sorted = tournaments.sortedBy { val order = it.order; order }
        val matchesByTournament = matchesList.groupBy { it.tournamentId }
        val placementsByTournament = placementsList.groupBy { it.tournamentId }

        val hasActiveTournament = sorted.any { t ->
            !t.isCompleted && (matchesByTournament[t.id]?.isNotEmpty() == true)
        }

        var nextAssigned = false

        return sorted.map { tournament ->
            val matches = matchesByTournament[tournament.id] ?: emptyList()
            val status: TournamentStatus
            val isNextPlanned: Boolean

            if (tournament.isCompleted) {
                status = TournamentStatus.COMPLETED
                isNextPlanned = false
            } else if (matches.isNotEmpty()) {
                status = TournamentStatus.ACTIVE
                isNextPlanned = false
            } else {
                status = TournamentStatus.PLANNED
                if (!hasActiveTournament && !nextAssigned) {
                    isNextPlanned = true
                    nextAssigned = true
                } else {
                    isNextPlanned = false
                }
            }

            var winnerName: String? = null
            var userReachedRound: String? = null
            var activeRoundName: String? = null

            if (status == TournamentStatus.COMPLETED) {
                val finalMatch = matches.find { it.round == "F" || it.round == "FINAL" } ?: matches.lastOrNull()
                if (finalMatch?.winnerId != null) {
                    val winnerId = finalMatch.winnerId!!
                    winnerName = if (winnerId == USER_ID) "Du" else allPlayers[winnerId]?.name ?: "CPU"
                }

                val placements = placementsByTournament[tournament.id] ?: emptyList()
                val userPlacement = placements.find { it.playerId == USER_ID }
                if (userPlacement != null) {
                    userReachedRound = TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
                }
            } else if (status == TournamentStatus.ACTIVE) {
                val uncompletedMatches = matches.filter { !it.isCompleted }
                val currentRoundKey = uncompletedMatches.firstOrNull()?.round ?: matches.lastOrNull()?.round
                if (currentRoundKey != null) {
                    activeRoundName = TournamentUiHelper.getDisplayRoundName(currentRoundKey, getApplication())
                }
            }

            TournamentUiModel(
                tournament = tournament,
                status = status,
                isNextPlanned = isNextPlanned,
                winnerName = winnerName,
                userReachedRound = userReachedRound,
                activeRoundName = activeRoundName
            )
        }
    }

    fun startTournament(tournamentId: Long, careerId: Long) {
        viewModelScope.launch {
            try {
                val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
                if (matches.isEmpty()) {
                    generateBracketUseCase(tournamentId)
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
