package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class TournamentStatus {
    COMPLETED, ACTIVE, PLANNED
}

data class TournamentUiModel(
    val tournament: Tournament,
    val status: TournamentStatus,
    val isNextPlanned: Boolean,
    val winnerName: String?,
    val userReachedRound: String?
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
    private val simulateCpuMatchUseCase = SimulateCpuMatchUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)

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

                val tournaments = generateSeasonCalendarUseCase(season.id)
                val uiModels = buildTournamentUiModels(season.id, tournaments)
                _uiState.value = TournamentCalendarState.Success(season.id, uiModels)
            } catch (e: Exception) {
                _uiState.value = TournamentCalendarState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    private suspend fun buildTournamentUiModels(seasonId: Long, tournaments: List<Tournament>): List<TournamentUiModel> = withContext(Dispatchers.IO) {
        val sorted = tournaments.sortedBy { it.order }
        val allPlayers = playerDao.getAllPlayersList().associateBy { it.id }

        // Strict Tournament Locking: Check if any tournament is active (not completed and has matches)
        val hasActiveTournament = sorted.any { t ->
            !t.isCompleted && tournamentMatchDao.getMatchesListForTournament(t.id).isNotEmpty()
        }

        var nextAssigned = false

        sorted.map { tournament ->
            val matches = tournamentMatchDao.getMatchesListForTournament(tournament.id)
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

            if (status == TournamentStatus.COMPLETED) {
                val finalMatch = matches.find { it.round == "F" || it.round == "FINAL" } ?: matches.lastOrNull()
                if (finalMatch?.winnerId != null) {
                    val winnerId = finalMatch.winnerId!!
                    winnerName = if (winnerId == USER_ID) "Du" else allPlayers[winnerId]?.name ?: "CPU"
                }

                val placements = tournamentPlacementDao.getPlacementsListForTournament(tournament.id)
                val userPlacement = placements.find { it.playerId == USER_ID }
                if (userPlacement != null) {
                    userReachedRound = getDisplayRoundName(userPlacement.reachedRound)
                }
            }

            TournamentUiModel(
                tournament = tournament,
                status = status,
                isNextPlanned = isNextPlanned,
                winnerName = winnerName,
                userReachedRound = userReachedRound
            )
        }
    }

    private fun getDisplayRoundName(roundKey: String): String {
        return when (roundKey) {
            "WINNER" -> "Turniersieger"
            "F" -> "Runner-Up (Finale)"
            "SF" -> "Halbfinale"
            "QF" -> "Viertelfinale"
            "L16" -> "Achtelfinale"
            "L32" -> "Last 32"
            "L48" -> "Last 48"
            "L64" -> "Last 64"
            "L128" -> "Last 128"
            "R3_96" -> "Runde 3"
            "R2_128" -> "Runde 2"
            "R1_160" -> "Runde 1"
            "GROUP_2ND" -> "Gruppenphase (Platz 2)"
            "GROUP_3RD" -> "Gruppenphase (Platz 3)"
            "GROUP" -> "Gruppenphase"
            else -> roundKey
        }
    }

    fun startTournament(tournamentId: Long, careerId: Long) {
        viewModelScope.launch {
            try {
                val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
                if (matches.isEmpty()) {
                    generateBracketUseCase(tournamentId)
                }
                loadTournaments(careerId)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun simulateTournament(tournamentId: Long, careerId: Long) {
        viewModelScope.launch {
            _uiState.value = TournamentCalendarState.Loading
            try {
                val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@launch
                if (tournament.isCompleted) {
                    loadTournaments(careerId)
                    return@launch
                }

                var matches = tournamentMatchDao.getMatchesListForTournament(tournament.id)
                if (matches.isEmpty()) {
                    generateBracketUseCase(tournament.id)
                }

                val career = careerRepository.getCareerById(careerId)?.career
                val userSkill = career?.level?.coerceIn(1, 10) ?: 5

                while (tournamentDao.getTournamentById(tournament.id)?.isCompleted != true) {
                    simulateCpuMatchUseCase.simulateAllCpuMatches(tournament.id)

                    val currentMatches = tournamentMatchDao.getMatchesListForTournament(tournament.id)
                    val uncompletedUserMatches = currentMatches.filter {
                        !it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID)
                    }

                    for (umatch in uncompletedUserMatches) {
                        val oppId = if (umatch.player1Id == USER_ID) umatch.player2Id else umatch.player1Id
                        val opp = playerDao.getPlayerById(oppId)
                        val oppSkill = (opp?.skill ?: 5).coerceIn(1, 10)
                        val userWinProb = userSkill.toDouble() / (userSkill + oppSkill)

                        val p1Wins = Random.nextDouble() < userWinProb
                        val p1Score = if (p1Wins) 6 else 4
                        val p2Score = if (p1Wins) 4 else 6
                        val winnerId = if (p1Wins) USER_ID else oppId

                        val updated = umatch.copy(
                            player1Score = p1Score,
                            player2Score = p2Score,
                            winnerId = winnerId,
                            isCompleted = true
                        )
                        tournamentMatchDao.updateMatch(updated)
                    }

                    val advanced = advanceTournamentRoundUseCase(tournament.id)
                    if (!advanced) break
                }

                loadTournaments(careerId)
            } catch (e: Exception) {
                loadTournaments(careerId)
            }
        }
    }

    fun simulateCompleteSeason(careerId: Long) {
        viewModelScope.launch {
            _uiState.value = TournamentCalendarState.Loading
            try {
                val season = seasonDao.getCurrentSeasonForCareer(careerId) ?: return@launch
                val tournaments = tournamentDao.getTournamentsListForSeason(season.id).sortedBy { it.order }
                val career = careerRepository.getCareerById(careerId)?.career
                val userSkill = career?.level?.coerceIn(1, 10) ?: 5

                for (tournament in tournaments) {
                    if (tournament.isCompleted) continue

                    var matches = tournamentMatchDao.getMatchesListForTournament(tournament.id)
                    if (matches.isEmpty()) {
                        generateBracketUseCase(tournament.id)
                    }

                    while (tournamentDao.getTournamentById(tournament.id)?.isCompleted != true) {
                        simulateCpuMatchUseCase.simulateAllCpuMatches(tournament.id)

                        val currentMatches = tournamentMatchDao.getMatchesListForTournament(tournament.id)
                        val uncompletedUserMatches = currentMatches.filter {
                            !it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID)
                        }

                        for (umatch in uncompletedUserMatches) {
                            val oppId = if (umatch.player1Id == USER_ID) umatch.player2Id else umatch.player1Id
                            val opp = playerDao.getPlayerById(oppId)
                            val oppSkill = (opp?.skill ?: 5).coerceIn(1, 10)
                            val userWinProb = userSkill.toDouble() / (userSkill + oppSkill)

                            val p1Wins = Random.nextDouble() < userWinProb
                            val p1Score = if (p1Wins) 6 else 4
                            val p2Score = if (p1Wins) 4 else 6
                            val winnerId = if (p1Wins) USER_ID else oppId

                            val updated = umatch.copy(
                                player1Score = p1Score,
                                player2Score = p2Score,
                                winnerId = winnerId,
                                isCompleted = true
                            )
                            tournamentMatchDao.updateMatch(updated)
                        }

                        val advanced = advanceTournamentRoundUseCase(tournament.id)
                        if (!advanced) break
                    }
                }

                loadTournaments(careerId)
            } catch (e: Exception) {
                loadTournaments(careerId)
            }
        }
    }
}
