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
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.ui.enums.TournamentStatus
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.TournamentUiModel
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

    private val db = AppDatabase.getDatabase(application)
    private val tournamentDao = db.tournamentDao()
    private val tournamentMatchDao = db.tournamentMatchDao()
    private val tournamentPlacementDao = db.tournamentPlacementDao()
    private val seasonDao = db.seasonDao()
    private val playerDao = db.playerDao()
    private val careerRepository = CareerRepository(application)

    private val generateSeasonCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentDao)
    private val generateBracketUseCase = GenerateBracketUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)
    private val tournamentProgressionUseCase = TournamentProgressionUseCase(application)

    private val _simulatingTournamentId = MutableStateFlow<Long?>(null)

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
                val careerWithProfile = careerRepository.getCareerById(careerId)
                val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE

                combine(
                    tournamentDao.getTournamentsForSeason(season.id),
                    tournamentMatchDao.getMatchesFlowForSeason(season.id),
                    tournamentPlacementDao.getPlacementsFlowForSeasons(listOf(season.id)),
                    _simulatingTournamentId
                ) { tournaments, matches, placements, simulatingId ->
                    val uiModels = buildTournamentUiModels(tournaments, matches, placements, allPlayers, userCountryCode)
                    TournamentCalendarState.Success(season.id, uiModels, simulatingId)
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = TournamentCalendarState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    private suspend fun buildTournamentUiModels(
        tournaments: List<Tournament>,
        matchesList: List<TournamentMatch>,
        placementsList: List<TournamentPlacement>,
        allPlayers: Map<Long, com.danny.dannyapp.data.db.Player>,
        userCountryCode: String
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
            var qualifierWinners: List<PlayerUiInfo>? = null
            var userReachedRound: String? = null
            var activeRoundName: String? = null

            val isMultiWinnerQualifier = tournament.type in listOf("QUALIFIER_ET", "QUALIFIER_GRAND_SLAM", "QUALIFIER_WM")

            if (status == TournamentStatus.COMPLETED) {
                if (isMultiWinnerQualifier) {
                    val qualFinalMatches = matches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }
                    qualifierWinners = qualFinalMatches.mapNotNull { m ->
                        val winnerId = m.winnerId ?: return@mapNotNull null
                        if (winnerId == USER_ID) {
                            PlayerUiInfo(id = USER_ID, name = "Du", countryCode = userCountryCode)
                        } else {
                            val p = allPlayers[winnerId]
                            PlayerUiInfo(id = winnerId, name = p?.name ?: "CPU #${winnerId}", countryCode = p?.countryCode ?: Country.DEFAULT_CODE, skill = p?.skill ?: SkillLevel.DEFAULT.value)
                        }
                    }
                } else {
                    val finalMatch = matches.find { it.round == "F" || it.round == "FINAL" } ?: matches.lastOrNull()
                    if (finalMatch?.winnerId != null) {
                        val winnerId = finalMatch.winnerId
                        winnerName = if (winnerId == USER_ID) "Du" else allPlayers[winnerId]?.name ?: "CPU"
                    }
                }

                val placements = placementsByTournament[tournament.id] ?: emptyList()
                val userPlacement = placements.find { it.playerId == USER_ID }
                if (userPlacement != null) {
                    userReachedRound = TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
                }
            } else if (status == TournamentStatus.ACTIVE) {
                val activeRound = advanceTournamentRoundUseCase.evaluateTournamentState(tournament.id)?.activeRound
                if (activeRound != null) {
                    activeRoundName = TournamentUiHelper.getDisplayRoundName(activeRound.name, getApplication())
                }
            }

            TournamentUiModel(
                tournament = tournament,
                status = status,
                isNextPlanned = isNextPlanned,
                winnerName = winnerName,
                qualifierWinners = qualifierWinners,
                userReachedRound = userReachedRound,
                activeRoundName = activeRoundName
            )
        }
    }

    suspend fun startTournament(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
            if (matches.isEmpty()) {
                generateBracketUseCase(tournamentId)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun simulateCompleteTournament(tournamentId: Long) {
        if (_simulatingTournamentId.value != null) return
        viewModelScope.launch {
            _simulatingTournamentId.value = tournamentId
            try {
                withContext(Dispatchers.IO) {
                    val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext
                    if (tournament.isCompleted) return@withContext

                    val careerId = seasonDao.getSeasonById(tournament.seasonId)?.careerId ?: 0L
                    val careerWithProfile = careerRepository.getCareerById(careerId)
                    val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value

                    tournamentProgressionUseCase.simulateCompleteTournament(tournamentId, userLevel)
                }
            } catch (_: Exception) {
            } finally {
                _simulatingTournamentId.value = null
            }
        }
    }
}
