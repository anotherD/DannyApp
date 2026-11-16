package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.MatchDistance
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.MatchScoreValidator
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import com.danny.dannyapp.ui.enums.RoundStatus
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.GrandSlamGroupItem
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

sealed interface TournamentDetailState {
    object Loading : TournamentDetailState

    data class Preview(
        val tournament: Tournament,
        val event: TournamentEvent,
        val host: String,
        val hostCountryCode: String,
        val totalPrizeFundText: String,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val isNextPlanned: Boolean,
        val careerName: String,
        val seasonNumber: Int
    ) : TournamentDetailState

    data class Active(
        val tournament: Tournament,
        val event: TournamentEvent,
        val host: String,
        val hostCountryCode: String,
        val activeRoundDisplayName: String,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>,
        val allRoundsMatches: List<List<MatchUiModel>>,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val autoSimulateCpu: Boolean,
        val autoAdvanceRound: Boolean,
        val isCurrentRoundCompleted: Boolean,
        val isSelectedRoundPlayable: Boolean,
        val canAdvanceRound: Boolean,
        val userLevel: Int,
        val careerName: String,
        val seasonNumber: Int,
        val isOpenDrawInfoVisible: Boolean = false,
        val prevRoundDisplayName: String = "",
        val openEntrantsGroups: List<EntrantGroupInfo> = emptyList(),
        val roundStatuses: List<RoundStatus> = emptyList(),
        val grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>> = emptyMap(),
        val hasOpenCpuMatchesInActiveRound: Boolean = false,
        val openDrawInfoMap: Map<Int, Pair<String, List<EntrantGroupInfo>>> = emptyMap()
    ) : TournamentDetailState

    data class Review(
        val tournament: Tournament,
        val event: TournamentEvent,
        val host: String,
        val hostCountryCode: String,
        val winner: PlayerUiInfo,
        val userReachedRoundText: String,
        val userEarnedPrizeMoneyText: String,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>,
        val allRoundsMatches: List<List<MatchUiModel>>,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val careerName: String,
        val isQualifier: Boolean = false,
        val qualifierWinners: List<PlayerUiInfo>? = emptyList(),
        val seasonNumber: Int,
        val grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>> = emptyMap()
    ) : TournamentDetailState

    data class Error(val message: String) : TournamentDetailState
}

/**
 * ViewModel managing UI state and user interactions for tournament details.
 */
class TournamentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val db = AppDatabase.getDatabase(application)
    private val tournamentDao = db.tournamentDao()
    private val tournamentMatchDao = db.tournamentMatchDao()
    private val tournamentPlacementDao = db.tournamentPlacementDao()
    private val playerDao = db.playerDao()
    private val tourCardDao = db.tourCardDao()
    private val seasonDao = db.seasonDao()
    private val careerRepository = CareerRepository(application)

    private val generateBracketUseCase = GenerateBracketUseCase(application)
    private val simulateCpuMatchUseCase = SimulateCpuMatchUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)
    private val previewHelper = TournamentPreviewHelper(application)

    private val _uiState = MutableStateFlow<TournamentDetailState>(TournamentDetailState.Loading)
    val uiState: StateFlow<TournamentDetailState> = _uiState.asStateFlow()

    private val _isSimulatingBatch = MutableStateFlow(false)
    val isSimulatingBatch: StateFlow<Boolean> = _isSimulatingBatch.asStateFlow()

    private var currentTournamentId: Long = -1L
    private var currentCareerId: Long = -1L

    private var selectedRoundIndex: Int = 0
    private var selectedGroupIndex: Int = 0
    private var hasUserSelectedRound: Boolean = false

    private val selectedRoundIndexFlow = MutableStateFlow(0)
    private val selectedGroupIndexFlow = MutableStateFlow(0)

    private var loadJob: Job? = null

    val autoSimulateCpu: Boolean
        get() = settingsManager.autoSimulateCpu

    val autoAdvanceRound: Boolean
        get() = settingsManager.autoAdvanceRound

    /**
     * Loads the tournament details for the given [tournamentId] and [careerId].
     */
    fun loadTournament(tournamentId: Long, careerId: Long) {
        this.currentTournamentId = tournamentId
        this.currentCareerId = careerId
        hasUserSelectedRound = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading

            val targetTournament = tournamentDao.getTournamentById(tournamentId)
            if (targetTournament == null) {
                _uiState.value = TournamentDetailState.Error(
                    getApplication<Application>().getString(R.string.tournament_not_found)
                )
                return@launch
            }

            val seasonId = targetTournament.seasonId

            combine(
                tournamentDao.getTournamentsForSeason(seasonId),
                tournamentMatchDao.getMatchesForTournament(tournamentId),
                selectedRoundIndexFlow,
                selectedGroupIndexFlow
            ) { tournaments, matches, _, _ ->
                val updatedTournament = tournaments.find { it.id == tournamentId } ?: targetTournament
                val event = try {
                    TournamentEvent.valueOf(updatedTournament.type)
                } catch (_: Exception) {
                    TournamentEvent.PLAYERS_CHAMPIONSHIP
                }
                Triple(updatedTournament, event, matches)
            }
            .filter { !_isSimulatingBatch.value }
            .collect { (tournament, event, matches) ->
                val allTournaments = tournamentDao.getTournamentsListForSeason(tournament.seasonId)
                val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                val careerName = careerWithProfile?.career?.name ?: "Karriere"
                val seasonNumber = careerWithProfile?.career?.season ?: 1

                if (tournament.isCompleted) {
                    buildReviewState(tournament, event, matches, careerName, seasonNumber)
                } else if (matches.isEmpty()) {
                    buildPreviewState(tournament, event, allTournaments, careerName, seasonNumber)
                } else {
                    buildActiveState(tournament, event, matches, careerName, seasonNumber)
                }
            }
        }
    }

    /**
     * Builds prize money and match distance items for the tournament info dialog.
     */
    private fun getPrizeMoneyItems(event: TournamentEvent): List<PrizeMoneyItem> {
        val app = getApplication<Application>()
        val sequence = getRoundSequence(event).reversed()
        val winnerPrize = event.getRankingPrizeMoney(TournamentRound.WINNER)
        val winnerPrizeText = if (winnerPrize > 0) "£%,d".format(Locale.US, winnerPrize.toLong()) else "£0"
        val winnerDistance = event.getMatchDistance(TournamentRound.F)
        val winnerItem = PrizeMoneyItem(
            roundName = TournamentUiHelper.getDisplayRoundName(TournamentRound.WINNER.name, app),
            matchDistanceText = formatMatchDistance(winnerDistance),
            prizeMoneyText = winnerPrizeText
        )

        val roundItems = sequence.map { round ->
            val prize = event.getRankingPrizeMoney(round)
            val prizeText = if (prize > 0) "£%,d".format(Locale.US, prize.toLong()) else "£0"
            val distance = event.getMatchDistance(round)
            PrizeMoneyItem(
                roundName = TournamentUiHelper.getDisplayRoundName(round.name, app),
                matchDistanceText = formatMatchDistance(distance),
                prizeMoneyText = prizeText
            )
        }
        return listOf(winnerItem) + roundItems
    }

    /**
     * Builds the preview UI state for planned tournaments.
     */
    private suspend fun buildPreviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allTournaments: List<Tournament>,
        careerName: String,
        seasonNumber: Int
    ) {
        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        val host = TournamentUiHelper.getTournamentCity(hostCountryCode, tournament.name)

        val totalPrizeText = if (event.totalPrizeFund > 0) {
            getApplication<Application>().getString(R.string.tournament_total_prize_fund_format, event.totalPrizeFund.toLong())
        } else {
            getApplication<Application>().getString(R.string.tournament_qualifying_no_prize)
        }

        val prizeItems = getPrizeMoneyItems(event)

        val sortedTournaments = allTournaments.sortedBy { it.order }
        val hasActiveTournament = sortedTournaments.any { tourney ->
            !tourney.isCompleted && tournamentMatchDao.getMatchesListForTournament(tourney.id).isNotEmpty()
        }
        val earliestPlanned = sortedTournaments.firstOrNull { tourney ->
            !tourney.isCompleted && tournamentMatchDao.getMatchesListForTournament(tourney.id).isEmpty()
        }
        val isNextPlanned = !hasActiveTournament && (earliestPlanned?.id == tournament.id)

        _uiState.value = TournamentDetailState.Preview(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = hostCountryCode,
            totalPrizeFundText = totalPrizeText,
            prizeMoneyItems = prizeItems,
            isNextPlanned = isNextPlanned,
            careerName = careerName,
            seasonNumber = seasonNumber
        )
    }

    /**
     * Builds the active UI state for ongoing tournaments.
     */
    private suspend fun buildActiveState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        careerName: String,
        seasonNumber: Int
    ) {
        val sequence = getRoundSequence(event, allMatches)
        val availableRounds = sequence.map { it.name }

        val activeRoundKeyInDb = sequence.firstOrNull { round ->
            val matches = allMatches.filter { it.round == round.name }
            matches.isNotEmpty() && matches.any { !it.isCompleted }
        }?.name ?: sequence.lastOrNull { round ->
            allMatches.any { it.round == round.name }
        }?.name ?: availableRounds.firstOrNull() ?: ""

        val activeRoundDisplayName = TournamentUiHelper.getDisplayRoundName(activeRoundKeyInDb, getApplication())

        if (selectedRoundIndex !in availableRounds.indices) {
            val activeIndex = availableRounds.indexOf(activeRoundKeyInDb)
            selectedRoundIndex = if (activeIndex != -1) activeIndex else availableRounds.lastIndex.coerceAtLeast(0)
            selectedRoundIndexFlow.value = selectedRoundIndex
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndex) ?: ""
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }

        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }
        val isUkOrEuropeanOpen = event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN
        val isUkOpenUngenerated = isUkOrEuropeanOpen && dbMatchesForRound.isEmpty() && currentRoundKey in listOf(
            TournamentRound.R2_128.name, TournamentRound.R3_96.name, TournamentRound.L64.name,
            TournamentRound.L32.name, TournamentRound.L16.name, TournamentRound.QF.name, TournamentRound.SF.name, TournamentRound.F.name
        )

        val season = seasonDao.getSeasonById(tournament.seasonId)
        val careerId = season?.careerId ?: currentCareerId

        val isOpenDrawInfoVisible = isUkOpenUngenerated
        val openEntrantsGroups = if (isOpenDrawInfoVisible) {
            getUkOpenEntrantGroups(currentTournamentId, careerId, currentRoundKey, allMatches, event)
        } else {
            emptyList()
        }

        val currentRoundMatches = if (dbMatchesForRound.isNotEmpty()) {
            dbMatchesForRound
        } else if (isOpenDrawInfoVisible) {
            emptyList()
        } else {
            generatePreviewMatchesWithPropagation(currentTournamentId, currentRoundKey, event, allMatches, tournament.seasonId)
        }

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) {
            ('A'..'P').map { it.toString() }
        } else {
            emptyList()
        }

        if (isGrandSlamGroup && selectedGroupIndex !in availableGroups.indices) {
            selectedGroupIndex = 0
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            val selectedGroupLetter = availableGroups[selectedGroupIndex]
            currentRoundMatches.filter { it.groupName == selectedGroupLetter }
        } else {
            currentRoundMatches
        }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value
        val userName = careerWithProfile?.playerName?.ifBlank { null }
            ?: careerWithProfile?.profileName
            ?: careerWithProfile?.career?.name
            ?: getApplication<Application>().getString(R.string.player_name_you)
        val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE

        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }
        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)

        val k = when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
            TournamentEvent.QUALIFIER_WM -> 4
            else -> 4
        }

        val rawMatchUiModels = filteredMatches.map { match ->
            createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
        }

        val matchUiModels = if (currentRoundKey == TournamentRound.QUAL_PRELIM.name) {
            val prelimCount = rawMatchUiModels.size
            val byeCount = 8 * k - prelimCount
            rawMatchUiModels.map { model ->
                val board = (byeCount + model.match.matchIndex) % k
                model.copy(targetBoard = board)
            }.sortedWith(
                compareBy<MatchUiModel> { it.targetBoard ?: 0 }
                    .thenBy { it.match.matchIndex }
            )
        } else {
            rawMatchUiModels
        }

        val isCurrentRoundCompleted = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.all { it.isCompleted }
        val isSelectedRoundPlayable = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.any { !it.isCompleted }
        val prizeMoneyItems = getPrizeMoneyItems(event)

        val nextRoundKey = availableRounds.getOrNull(selectedRoundIndex + 1)
        val isNextRoundGenerated = nextRoundKey != null && allMatches.any { it.round == nextRoundKey }
        val canAdvanceRound = dbMatchesForRound.isNotEmpty() && isCurrentRoundCompleted && !isNextRoundGenerated

        // Trigger Auto-Simulate if needed
        if (autoSimulateCpu && isSelectedRoundPlayable && dbMatchesForRound.any { !it.isCompleted && it.player1Id != USER_ID && it.player2Id != USER_ID }) {
            simulateCpuMatchUseCase.simulateAllCpuMatches(currentTournamentId)
            return
        }

        // Trigger Auto-Advance if all matches in current round are completed
        if (autoAdvanceRound && isCurrentRoundCompleted) {
            val advanced = advanceTournamentRoundUseCase(currentTournamentId)
            if (advanced) {
                val reloadedTournament = tournamentDao.getTournamentById(currentTournamentId)
                val newMatches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                if (reloadedTournament?.isCompleted == true) {
                    buildReviewState(reloadedTournament, event, newMatches, careerName, seasonNumber)
                    return
                } else {
                    val latestGenerated = sequence.lastOrNull { round -> newMatches.any { it.round == round.name } }
                    selectedRoundIndex = if (latestGenerated != null) availableRounds.indexOf(latestGenerated.name).coerceAtLeast(0) else 0
                    selectedRoundIndexFlow.value = selectedRoundIndex
                    buildActiveState(reloadedTournament ?: tournament, event, newMatches, careerName, seasonNumber)
                    return
                }
            }
        }

        val prevRoundDisplayName = if (selectedRoundIndex > 0) {
            availableRoundDisplayNames.getOrNull(selectedRoundIndex - 1) ?: ""
        } else {
            ""
        }

        val roundStatuses = availableRounds.map { roundKey ->
            val matchesInRound = allMatches.filter { it.round == roundKey }
            when {
                matchesInRound.isNotEmpty() && matchesInRound.all { it.isCompleted } -> RoundStatus.COMPLETED
                matchesInRound.isNotEmpty() && matchesInRound.any { !it.isCompleted } -> RoundStatus.ACTIVE
                roundKey == activeRoundKeyInDb -> RoundStatus.ACTIVE
                else -> RoundStatus.PENDING
            }
        }

        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        val host = TournamentUiHelper.getTournamentCity(hostCountryCode, tournament.name)

        val allRoundsMatches = availableRounds.map { roundKey ->
            val dbMatchesForRound = allMatches.filter { it.round == roundKey }
            val isUkOrEuropeanOpen = event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN
            val isUkOpenUngenerated = isUkOrEuropeanOpen && dbMatchesForRound.isEmpty() && roundKey in listOf(
                TournamentRound.R2_128.name, TournamentRound.R3_96.name, TournamentRound.L64.name,
                TournamentRound.L32.name, TournamentRound.L16.name, TournamentRound.QF.name, TournamentRound.SF.name, TournamentRound.F.name
            )

            val roundMatches = if (dbMatchesForRound.isNotEmpty()) {
                dbMatchesForRound
            } else if (isUkOpenUngenerated) {
                emptyList()
            } else {
                generatePreviewMatchesWithPropagation(currentTournamentId, roundKey, event, allMatches, tournament.seasonId)
            }

            val roundModels = roundMatches.map { match ->
                createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, roundKey, event, seededPlayerMap)
            }

            if (roundKey == TournamentRound.QUAL_PRELIM.name) {
                val prelimCount = roundModels.size
                val byeCount = 8 * k - prelimCount
                roundModels.map { model ->
                    val board = (byeCount + model.match.matchIndex) % k
                    model.copy(targetBoard = board)
                }
            } else {
                roundModels
            }
        }

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userName, userCountryCode, allPlayersMap)
        } else emptyMap()

        val openDrawInfoMap = mutableMapOf<Int, Pair<String, List<EntrantGroupInfo>>>()
        for ((r, roundKey) in availableRounds.withIndex()) {
            val dbMatchesForRound = allMatches.filter { it.round == roundKey }
            val isUkOrEuropeanOpen = event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN
            val isUkOpenUngenerated = isUkOrEuropeanOpen && dbMatchesForRound.isEmpty() && roundKey in listOf(
                TournamentRound.R2_128.name, TournamentRound.R3_96.name, TournamentRound.L64.name,
                TournamentRound.L32.name, TournamentRound.L16.name, TournamentRound.QF.name, TournamentRound.SF.name, TournamentRound.F.name
            )
            if (isUkOpenUngenerated) {
                val prevName = availableRoundDisplayNames.getOrNull(r - 1) ?: ""
                val groups = getUkOpenEntrantGroups(currentTournamentId, careerId, roundKey, allMatches, event)
                openDrawInfoMap[r] = prevName to groups
            }
        }

        val activeRoundMatches = allMatches.filter { it.round == activeRoundKeyInDb }
        val hasOpenCpuMatchesInActiveRound = activeRoundMatches.any {
            !it.isCompleted && it.player1Id != USER_ID && it.player2Id != USER_ID
        }

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = hostCountryCode,
            activeRoundDisplayName = activeRoundDisplayName,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels,
            allRoundsMatches = allRoundsMatches,
            prizeMoneyItems = prizeMoneyItems,
            autoSimulateCpu = autoSimulateCpu,
            autoAdvanceRound = autoAdvanceRound,
            isCurrentRoundCompleted = isCurrentRoundCompleted,
            isSelectedRoundPlayable = isSelectedRoundPlayable,
            canAdvanceRound = canAdvanceRound,
            userLevel = userLevel,
            careerName = careerName,
            seasonNumber = seasonNumber,
            isOpenDrawInfoVisible = isOpenDrawInfoVisible,
            prevRoundDisplayName = prevRoundDisplayName,
            openEntrantsGroups = openEntrantsGroups,
            roundStatuses = roundStatuses,
            grandSlamGroupStandings = grandSlamGroupStandings,
            hasOpenCpuMatchesInActiveRound = hasOpenCpuMatchesInActiveRound,
            openDrawInfoMap = openDrawInfoMap
        )
    }

    /**
     * Builds the review UI state for completed tournaments.
     */
    private suspend fun buildReviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        careerName: String,
        seasonNumber: Int
    ) {
        val finalMatch = allMatches.find { it.round == TournamentRound.F.name } ?: allMatches.lastOrNull()
        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value
        val userName = careerWithProfile?.playerName?.ifBlank { null }
            ?: careerWithProfile?.profileName
            ?: careerWithProfile?.career?.name
            ?: getApplication<Application>().getString(R.string.player_name_you)
        val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE

        val winnerId = finalMatch?.winnerId
        val winner = if (winnerId != null) {
            if (winnerId == USER_ID) {
                PlayerUiInfo(id = USER_ID, name = userName, countryCode = userCountryCode)
            } else {
                val player = allPlayersMap[winnerId]
                PlayerUiInfo(
                    id = winnerId,
                    name = player?.name ?: getApplication<Application>().getString(R.string.player_name_unknown),
                    countryCode = player?.countryCode ?: Country.DEFAULT_CODE,
                    skill = player?.skill ?: SkillLevel.DEFAULT.value
                )
            }
        } else {
            PlayerUiInfo(id = 0L, name = getApplication<Application>().getString(R.string.player_name_unknown), countryCode = Country.DEFAULT_CODE)
        }

        val placements = tournamentPlacementDao.getPlacementsListForTournament(tournament.id)
        val userPlacement = placements.find { it.playerId == USER_ID }
        val userReachedRoundText = if (userPlacement != null) {
            TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
        } else {
            getApplication<Application>().getString(R.string.tournament_review_not_qualified)
        }

        val userEarnedPrizeMoneyText = if (userPlacement != null && userPlacement.prizeMoney > 0) {
            "£%,d".format(Locale.US, userPlacement.prizeMoney.toLong())
        } else {
            "£0"
        }

        val sequence = getRoundSequence(event, allMatches)
        val availableRounds = sequence.map { it.name }

        if (!hasUserSelectedRound || selectedRoundIndex !in availableRounds.indices) {
            selectedRoundIndex = availableRounds.lastIndex.coerceAtLeast(0)
            selectedRoundIndexFlow.value = selectedRoundIndex
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndex) ?: ""
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }

        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }
        val currentRoundMatches = if (dbMatchesForRound.isNotEmpty()) {
            dbMatchesForRound
        } else {
            generatePreviewMatchesForRound(tournament.id, currentRoundKey, event)
        }

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) ('A'..'P').map { it.toString() } else emptyList()

        if (isGrandSlamGroup && selectedGroupIndex !in availableGroups.indices) {
            selectedGroupIndex = 0
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            currentRoundMatches.filter { it.groupName == availableGroups[selectedGroupIndex] }
        } else {
            currentRoundMatches
        }

        val isQualifier = event in listOf(
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM
        )

        val qualifierWinners = if (isQualifier) {
            val qualFinalMatches = allMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }
            qualFinalMatches.mapNotNull { match ->
                val wId = match.winnerId ?: return@mapNotNull null
                if (wId == USER_ID) {
                    PlayerUiInfo(id = USER_ID, name = userName, countryCode = userCountryCode)
                } else {
                    val player = allPlayersMap[wId]
                    PlayerUiInfo(
                        id = wId,
                        name = player?.name ?: "CPU #$wId",
                        countryCode = player?.countryCode ?: Country.DEFAULT_CODE,
                        skill = player?.skill ?: SkillLevel.DEFAULT.value
                    )
                }
            }
        } else null

        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)

        val k = when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
            TournamentEvent.QUALIFIER_WM -> 4
            else -> 4
        }

        val rawMatchUiModels = filteredMatches.map { match ->
            createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
        }

        val matchUiModels = if (currentRoundKey == TournamentRound.QUAL_PRELIM.name) {
            val prelimCount = rawMatchUiModels.size
            val byeCount = 8 * k - prelimCount
            rawMatchUiModels.map { model ->
                val board = (byeCount + model.match.matchIndex) % k
                model.copy(targetBoard = board)
            }.sortedWith(
                compareBy<MatchUiModel> { it.targetBoard ?: 0 }
                    .thenBy { it.match.matchIndex }
            )
        } else {
            rawMatchUiModels
        }

        val prizeMoneyItems = getPrizeMoneyItems(event)
        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        val host = TournamentUiHelper.getTournamentCity(hostCountryCode, tournament.name)

        val allRoundsMatches = availableRounds.map { roundKey ->
            val dbMatchesForRound = allMatches.filter { it.round == roundKey }
            val roundMatches = if (dbMatchesForRound.isNotEmpty()) {
                dbMatchesForRound
            } else {
                generatePreviewMatchesForRound(tournament.id, roundKey, event)
            }

            val roundModels = roundMatches.map { match ->
                createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, roundKey, event, seededPlayerMap)
            }

            if (roundKey == TournamentRound.QUAL_PRELIM.name) {
                val prelimCount = roundModels.size
                val byeCount = 8 * k - prelimCount
                roundModels.map { model ->
                    val board = (byeCount + model.match.matchIndex) % k
                    model.copy(targetBoard = board)
                }
            } else {
                roundModels
            }
        }

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userName, userCountryCode, allPlayersMap)
        } else emptyMap()

        _uiState.value = TournamentDetailState.Review(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = hostCountryCode,
            winner = winner,
            userReachedRoundText = userReachedRoundText,
            userEarnedPrizeMoneyText = userEarnedPrizeMoneyText,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels,
            allRoundsMatches = allRoundsMatches,
            prizeMoneyItems = prizeMoneyItems,
            careerName = careerName,
            isQualifier = isQualifier,
            qualifierWinners = qualifierWinners,
            seasonNumber = seasonNumber,
            grandSlamGroupStandings = grandSlamGroupStandings
        )
    }

    private fun calculateGrandSlamGroupStandings(
        allMatches: List<TournamentMatch>,
        userName: String,
        userCountryCode: String,
        allPlayersMap: Map<Long, Player>
    ): Map<String, List<GrandSlamGroupItem>> {
        val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
        if (groupMatches.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, List<GrandSlamGroupItem>>()
        val groups = ('A'..'P').map { it.toString() }

        class GroupStats(val playerId: Long) {
            var played = 0
            var wins = 0
            var legsWon = 0
            var legsLost = 0
            val legDiff: Int get() = legsWon - legsLost
            val points: Int get() = wins * 2
        }

        for (groupLetter in groups) {
            val matchesForGroup = groupMatches.filter { it.groupName == groupLetter }
            if (matchesForGroup.isEmpty()) continue

            val playerIdsInGroup = matchesForGroup.flatMap { listOf(it.player1Id, it.player2Id) }.filter { it != 0L }.distinct()
            val statsMap = playerIdsInGroup.associateWith { GroupStats(it) }

            for (m in matchesForGroup) {
                if (m.isCompleted) {
                    val s1 = statsMap[m.player1Id]
                    val s2 = statsMap[m.player2Id]
                    if (s1 != null && s2 != null) {
                        s1.played++
                        s2.played++
                        s1.legsWon += m.player1Score
                        s1.legsLost += m.player2Score
                        s2.legsWon += m.player2Score
                        s2.legsLost += m.player1Score

                        if (m.winnerId == m.player1Id) s1.wins++
                        else if (m.winnerId == m.player2Id) s2.wins++
                    }
                }
            }

            val sortedStats = statsMap.values.sortedWith(
                compareByDescending<GroupStats> { it.wins }
                    .thenByDescending { it.legDiff }
                    .thenByDescending { it.legsWon }
                    .thenBy { it.playerId }
            )

            val items = sortedStats.mapIndexed { index, s ->
                val pInfo = if (s.playerId == USER_ID) {
                    PlayerUiInfo(id = USER_ID, name = userName, countryCode = userCountryCode)
                } else {
                    val p = allPlayersMap[s.playerId]
                    PlayerUiInfo(
                        id = s.playerId,
                        name = p?.name ?: "CPU #${s.playerId}",
                        countryCode = p?.countryCode ?: Country.DEFAULT_CODE,
                        skill = p?.skill ?: SkillLevel.DEFAULT.value
                    )
                }
                GrandSlamGroupItem(
                    rank = index + 1,
                    player = pInfo,
                    played = s.played,
                    legDiff = s.legDiff,
                    points = s.points
                )
            }
            result[groupLetter] = items
        }
        return result
    }

    /**
     * Resolves placeholder player names for TBD open draws or previous round winners.
     */
    private fun resolvePlaceholderPlayerName(
        playerId: Long,
        matchIndex: Int,
        isPlayer1: Boolean,
        event: TournamentEvent,
        roundKey: String,
        app: Application
    ): String? {
        return when {
            playerId == PLACEHOLDER_OPEN_DRAW_ID -> app.getString(R.string.match_tbd_open_draw)
            playerId == PLACEHOLDER_PREVIOUS_WINNER_ID -> {
                if (event == TournamentEvent.EUROPEAN_TOUR && roundKey == TournamentRound.L32.name) {
                    if (isPlayer1) {
                        val seedOrder = com.danny.dannyapp.domain.usecase.BracketGenerator.BRACKET_SEED_ORDER_16
                        val seedRank = (seedOrder.getOrNull(matchIndex) ?: matchIndex) + 1
                        app.getString(R.string.tournament_placeholder_top16_seed, seedRank)
                    } else {
                        val seedOrder = com.danny.dannyapp.domain.usecase.BracketGenerator.BRACKET_SEED_ORDER_16
                        val seedIndex = seedOrder.getOrNull(matchIndex) ?: matchIndex
                        app.getString(R.string.tournament_placeholder_winner, 16 - seedIndex)
                    }
                } else {
                    app.getString(R.string.tournament_placeholder_winner, 2 * matchIndex + if (isPlayer1) 1 else 2)
                }
            }
            playerId <= -1000L && playerId > -2000L -> {
                val prelimIdx = (-1000L - playerId).toInt() + 1
                app.getString(R.string.tournament_placeholder_winner, prelimIdx)
            }
            else -> null
        }
    }

    /**
     * Creates a [MatchUiModel] from a [TournamentMatch] entity.
     */
    private fun createMatchUiModel(
        match: TournamentMatch,
        userName: String,
        userCountryCode: String,
        userLevel: Int,
        allPlayersMap: Map<Long, com.danny.dannyapp.data.db.Player>,
        roundKey: String,
        event: TournamentEvent,
        seededPlayerMap: Map<Long, Int> = emptyMap()
    ): MatchUiModel {
        val app = getApplication<Application>()
        val isP1User = (match.player1Id == USER_ID)
        val isP2User = (match.player2Id == USER_ID)
        val isUserMatch = isP1User || isP2User

        val p1Placeholder = resolvePlaceholderPlayerName(match.player1Id, match.matchIndex, true, event, roundKey, app)
        val p1Name = when {
            match.player1Id == USER_ID -> userName
            p1Placeholder != null -> p1Placeholder
            else -> {
                val player = allPlayersMap[match.player1Id]
                player?.name ?: "CPU #${match.player1Id}"
            }
        }
        val p1Seed = if (match.player1Id != PLACEHOLDER_OPEN_DRAW_ID && match.player1Id != PLACEHOLDER_PREVIOUS_WINNER_ID) {
            seededPlayerMap[match.player1Id]
        } else {
            null
        }
        val p1CountryCode = when (match.player1Id) {
            USER_ID -> userCountryCode
            PLACEHOLDER_OPEN_DRAW_ID, PLACEHOLDER_PREVIOUS_WINNER_ID -> null
            else -> allPlayersMap[match.player1Id]?.countryCode ?: Country.DEFAULT_CODE
        }
        val p1Skill = if (isP1User) userLevel else allPlayersMap[match.player1Id]?.skill ?: SkillLevel.DEFAULT.value
        val player1 = PlayerUiInfo(
            id = match.player1Id,
            name = p1Name,
            countryCode = p1CountryCode,
            seed = p1Seed,
            skill = p1Skill
        )

        val p2Placeholder = resolvePlaceholderPlayerName(match.player2Id, match.matchIndex, false, event, roundKey, app)
        val p2Name = when {
            match.player2Id == USER_ID -> userName
            p2Placeholder != null -> p2Placeholder
            else -> {
                val player = allPlayersMap[match.player2Id]
                player?.name ?: "CPU #${match.player2Id}"
            }
        }
        val p2Seed = if (match.player2Id != PLACEHOLDER_OPEN_DRAW_ID && match.player2Id != PLACEHOLDER_PREVIOUS_WINNER_ID) {
            seededPlayerMap[match.player2Id]
        } else {
            null
        }
        val p2CountryCode = when (match.player2Id) {
            USER_ID -> userCountryCode
            PLACEHOLDER_OPEN_DRAW_ID, PLACEHOLDER_PREVIOUS_WINNER_ID -> null
            else -> allPlayersMap[match.player2Id]?.countryCode ?: Country.DEFAULT_CODE
        }
        val p2Skill = if (isP2User) userLevel else allPlayersMap[match.player2Id]?.skill ?: SkillLevel.DEFAULT.value
        val player2 = PlayerUiInfo(
            id = match.player2Id,
            name = p2Name,
            countryCode = p2CountryCode,
            seed = p2Seed,
            skill = p2Skill
        )

        val scoreText = if (match.isCompleted) "${match.player1Score} : ${match.player2Score}" else "- : -"
        val roundDisplayName = TournamentUiHelper.getDisplayRoundName(roundKey, app)

        return MatchUiModel(
            match = match,
            player1 = player1,
            player2 = player2,
            isUserMatch = isUserMatch,
            isCompleted = match.isCompleted,
            winnerId = match.winnerId,
            scoreText = scoreText,
            roundDisplayName = roundDisplayName
        )
    }

    /**
     * Starts the tournament by generating its initial bracket matches.
     */
    fun startTournament() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            generateBracketUseCase(currentTournamentId)
        }
    }

    /**
     * Selects a round by index.
     */
    fun selectRound(index: Int) {
        hasUserSelectedRound = true
        selectedRoundIndex = index
        selectedRoundIndexFlow.value = index
    }

    /**
     * Selects a group by index.
     */
    fun selectGroup(index: Int) {
        selectedGroupIndex = index
        selectedGroupIndexFlow.value = index
    }

    /**
     * Simulates all remaining CPU matches in the current tournament.
     */
    fun simulateCpuMatches() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                simulateCpuMatchUseCase.simulateAllCpuMatches(currentTournamentId)
            } finally {
                _isSimulatingBatch.value = false
            }
        }
    }

    /**
     * Simulates the entire tournament to completion.
     */
    fun simulateCompleteTournament() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                var tournament = tournamentDao.getTournamentById(currentTournamentId) ?: return@launch
                if (tournament.isCompleted) return@launch

                var matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                if (matches.isEmpty()) {
                    generateBracketUseCase(currentTournamentId)
                    matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                }

                while (true) {
                    tournament = tournamentDao.getTournamentById(currentTournamentId) ?: break
                    if (tournament.isCompleted) break

                    val currentMatches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                    if (currentMatches.isEmpty()) break

                    val uncompleted = currentMatches.filter { !it.isCompleted }
                    if (uncompleted.isNotEmpty()) {
                        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value
                        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }

                        for (match in uncompleted) {
                            val event = TournamentEvent.valueOf(tournament.type)
                            val round = TournamentRound.valueOf(match.round)
                            val distance = event.getMatchDistance(round)

                            if (match.player1Id == USER_ID || match.player2Id == USER_ID) {
                                val isP1User = (match.player1Id == USER_ID)
                                val opponentId = if (isP1User) match.player2Id else match.player1Id
                                val opponentPlayer = allPlayersMap[opponentId]
                                val opponentSkill = (opponentPlayer?.skill ?: SkillLevel.DEFAULT.value).coerceIn(1, 10)

                                val player1Skill = if (isP1User) userLevel else opponentSkill
                                val player2Skill = if (isP1User) opponentSkill else userLevel

                                val p1WinProb = player1Skill.toDouble() / (player1Skill + player2Skill)
                                val userWins = Random.nextDouble() < p1WinProb

                                val (player1Score, player2Score, winnerId) = generateScoresForWinner(
                                    isPlayer1Winner = userWins,
                                    player1Id = match.player1Id,
                                    player2Id = match.player2Id,
                                    dist = distance
                                )

                                val updated = match.copy(
                                    player1Score = player1Score,
                                    player2Score = player2Score,
                                    winnerId = winnerId,
                                    isCompleted = true
                                )
                                tournamentMatchDao.updateMatch(updated)
                            } else {
                                simulateCpuMatchUseCase(match)
                            }
                        }
                    }

                    val advanced = advanceTournamentRoundUseCase(currentTournamentId)
                    if (!advanced) break
                }
            } finally {
                _isSimulatingBatch.value = false
                val tournament = tournamentDao.getTournamentById(currentTournamentId)
                val event = tournament?.let { try { TournamentEvent.valueOf(it.type) } catch (_: Exception) { TournamentEvent.PLAYERS_CHAMPIONSHIP } }
                val matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                val careerName = careerWithProfile?.career?.name ?: "Karriere"
                val seasonNumber = careerWithProfile?.career?.season ?: 1
                if (tournament != null && event != null) {
                    if (tournament.isCompleted) {
                        buildReviewState(tournament, event, matches, careerName, seasonNumber)
                    } else {
                        buildActiveState(tournament, event, matches, careerName, seasonNumber)
                    }
                }
            }
        }
    }

    /**
     * Manually triggers round advancement.
     */
    fun advanceRound() {
        viewModelScope.launch {
            advanceTournamentRoundUseCase(currentTournamentId)
            val tournament = tournamentDao.getTournamentById(currentTournamentId)
            if (tournament != null) {
                val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { TournamentEvent.PLAYERS_CHAMPIONSHIP }
                val matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                val sequence = getRoundSequence(event, matches)
                val availableRounds = sequence.map { it.name }
                val roundKeysInMatches = matches.map { it.round }.distinct()
                val latestRound = sequence.lastOrNull { it.name in roundKeysInMatches }
                if (latestRound != null) {
                    val newIndex = availableRounds.indexOf(latestRound.name)
                    if (newIndex != -1) {
                        selectedRoundIndex = newIndex
                        selectedRoundIndexFlow.value = newIndex
                    }
                }
            }
        }
    }

    /**
     * Sets the winner for a user match in debug mode.
     */
    fun setUserMatchWinner(matchId: Long, userWins: Boolean) {
        viewModelScope.launch {
            val match = tournamentMatchDao.getMatchById(matchId) ?: return@launch
            if (match.isCompleted) return@launch

            val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@launch
            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)
            val distance = event.getMatchDistance(round)

            val isP1User = (match.player1Id == USER_ID)
            val isP1Winner = if (isP1User) userWins else !userWins

            val (player1Score, player2Score, winnerId) = generateScoresForWinner(
                isPlayer1Winner = isP1Winner,
                player1Id = match.player1Id,
                player2Id = match.player2Id,
                dist = distance
            )

            val updated = match.copy(
                player1Score = player1Score,
                player2Score = player2Score,
                winnerId = winnerId,
                isCompleted = true
            )

            tournamentMatchDao.updateMatch(updated)
        }
    }

    /**
     * Manually sets a user match score. Returns an error message if invalid, or `null` if saved successfully.
     */
    suspend fun setManualUserMatchScore(matchId: Long, p1Score: Int, p2Score: Int): String? {
        val match = tournamentMatchDao.getMatchById(matchId)
            ?: return getApplication<Application>().getString(R.string.match_not_found)
        if (match.isCompleted) {
            return getApplication<Application>().getString(R.string.match_already_completed)
        }

        val tournament = tournamentDao.getTournamentById(match.tournamentId)
            ?: return getApplication<Application>().getString(R.string.tournament_not_found)
        val event = TournamentEvent.valueOf(tournament.type)
        val round = TournamentRound.valueOf(match.round)
        val distance = event.getMatchDistance(round)

        val validationError = validateMatchScore(p1Score, p2Score, distance)
        if (validationError != null) return validationError

        val winnerId = if (p1Score > p2Score) match.player1Id else match.player2Id

        val updated = match.copy(
            player1Score = p1Score,
            player2Score = p2Score,
            winnerId = winnerId,
            isCompleted = true
        )

        tournamentMatchDao.updateMatch(updated)
        return null
    }

    /**
     * Validates a match score for a given [matchDistance].
     */
    fun validateMatchScore(player1Score: Int, player2Score: Int, matchDistance: MatchDistance): String? {
        return MatchScoreValidator.validateMatchScore(
            context = getApplication(),
            player1Score = player1Score,
            player2Score = player2Score,
            matchDistance = matchDistance
        )
    }

    /**
     * Generates valid scores for a match given the winner determination and [dist].
     */
    private fun generateScoresForWinner(
        isPlayer1Winner: Boolean,
        player1Id: Long,
        player2Id: Long,
        dist: MatchDistance
    ): Triple<Int, Int, Long> {
        return MatchScoreValidator.generateScoresForWinner(
            isPlayer1Winner = isPlayer1Winner,
            player1Id = player1Id,
            player2Id = player2Id,
            matchDistance = dist
        )
    }

    /**
     * Returns the round sequence for the given [event] and optional [allMatches].
     */
    private fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        return previewHelper.getRoundSequence(event, allMatches)
    }

    /**
     * Returns the seeded player map for the given [event] and [seasonId].
     */
    private suspend fun getSeededPlayerMap(
        event: TournamentEvent,
        seasonId: Long,
        tournamentId: Long = 0L
    ): Map<Long, Int> {
        return previewHelper.getSeededPlayerMap(event, seasonId, tournamentId)
    }

    /**
     * Generates preview matches for the specified [roundKey].
     */
    private fun generatePreviewMatchesForRound(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent
    ): List<TournamentMatch> {
        return previewHelper.generatePreviewMatchesForRound(tournamentId, roundKey, event)
    }

    /**
     * Generates preview matches with propagation from previous rounds.
     */
    private suspend fun generatePreviewMatchesWithPropagation(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long
    ): List<TournamentMatch> {
        return previewHelper.generatePreviewMatchesWithPropagation(
            tournamentId = tournamentId,
            roundKey = roundKey,
            event = event,
            allMatches = allMatches,
            seasonId = seasonId,
            currentCareerId = currentCareerId
        )
    }

    /**
     * Retrieves entrant group information for open draw events.
     */
    private suspend fun getUkOpenEntrantGroups(
        tournamentId: Long,
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent
    ): List<EntrantGroupInfo> {
        return previewHelper.getUkOpenEntrantGroups(
            tournamentId = tournamentId,
            careerId = careerId,
            roundKey = roundKey,
            allMatches = allMatches,
            event = event,
            currentCareerId = currentCareerId
        )
    }

    /**
     * Formats the match distance for display.
     */
    private fun formatMatchDistance(dist: MatchDistance): String {
        val app = getApplication<Application>()
        val formatText = when (dist.format) {
            MatchFormatType.LEGS -> app.getString(R.string.match_format_legs)
            MatchFormatType.SETS_BEST_OF_5_LEGS -> app.getString(R.string.match_format_sets_5legs)
            MatchFormatType.SETS_BEST_OF_3_LEGS -> app.getString(R.string.match_format_sets_3legs)
            MatchFormatType.SETS_DOUBLE_IN_OUT -> app.getString(R.string.match_format_sets_double_in_out)
        }
        val clearText = if (dist.twoClearLegsRule) app.getString(R.string.match_format_two_clear_legs) else ""
        return app.getString(R.string.match_format_best_of, dist.bestOf, formatText, clearText)
    }
}
