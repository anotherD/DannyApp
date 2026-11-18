package com.danny.dannyapp.ui.tournament

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import com.danny.dannyapp.domain.usecase.GetTournamentSummaryUseCase
import com.danny.dannyapp.domain.usecase.TournamentPhaseState
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import com.danny.dannyapp.domain.usecase.TournamentSummary
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
    private val getTournamentSummaryUseCase = GetTournamentSummaryUseCase()

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
                    if (winnerId == USER_ID) {
                        PlayerUiInfo(id = USER_ID, name = getApplication<Application>().getString(R.string.player_name_you), countryCode = userCountryCode)
                    } else {
                        val dbPlayer = allPlayers[winnerId]
                        PlayerUiInfo(
                            id = winnerId,
                            name = dbPlayer?.name ?: "${getApplication<Application>().getString(R.string.player_name_unknown)} #$winnerId",
                            countryCode = dbPlayer?.countryCode ?: Country.DEFAULT_CODE,
                            skill = dbPlayer?.skill ?: SkillLevel.DEFAULT.value
                        )
                    }
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
