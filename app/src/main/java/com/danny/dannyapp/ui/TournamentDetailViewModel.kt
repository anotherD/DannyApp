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
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.MatchDistance
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.CalculateGroupStandingsUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GetTournamentSummaryUseCase
import com.danny.dannyapp.domain.model.GroupStandingEntry
import com.danny.dannyapp.domain.usecase.CompleteMatchUseCase
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import com.danny.dannyapp.domain.usecase.TournamentQualificationUseCase
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.Locale

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
        val openDrawInfoMap: Map<Int, Pair<String, List<EntrantGroupInfo>>> = emptyMap(),
        val isFinalRound: Boolean = false
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
    private val seasonDao = db.seasonDao()
    private val careerRepository = CareerRepository(application)

    private val generateBracketUseCase = GenerateBracketUseCase(application)
    private val completeMatchUseCase = CompleteMatchUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)
    private val tournamentProgressionUseCase = TournamentProgressionUseCase(application)
    private val tournamentQualificationUseCase = TournamentQualificationUseCase(application)

    /** Domain use-case: single source of truth for Grand-Slam group standings. */
    private val calculateGroupStandingsUseCase = CalculateGroupStandingsUseCase()

    /** Domain use-case: single source of truth for season-level tournament summaries. */
    private val getTournamentSummaryUseCase = GetTournamentSummaryUseCase()

    private val _uiState = MutableStateFlow<TournamentDetailState>(TournamentDetailState.Loading)
    val uiState: StateFlow<TournamentDetailState> = _uiState.asStateFlow()

    private val _isSimulatingBatch = MutableStateFlow(false)
    val isSimulatingBatch: StateFlow<Boolean> = _isSimulatingBatch.asStateFlow()

    private var currentTournamentId: Long = -1L
    private var currentCareerId: Long = -1L
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
        TournamentConfigLoader.loadFromAssets(getApplication())
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
                selectedGroupIndexFlow,
                _isSimulatingBatch
            ) { tournaments, matches, _, _, isSimulatingBatch ->
                if (isSimulatingBatch) null
                else {
                    val updatedTournament = tournaments.find { it.id == tournamentId } ?: targetTournament
                    val event = try {
                        TournamentEvent.valueOf(updatedTournament.type)
                    } catch (_: Exception) {
                        TournamentEvent.PLAYERS_CHAMPIONSHIP
                    }
                    Triple(updatedTournament, event, matches)
                }
            }
            .filterNotNull()
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
        val host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity)

        val totalPrizeText = if (event.effectiveTotalPrizeFund > 0) {
            getApplication<Application>().getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            getApplication<Application>().getString(R.string.tournament_qualifying_no_prize)
        }

        val prizeItems = getPrizeMoneyItems(event)

        // Collect matches for all non-completed tournaments to let the domain use-case decide
        // which is the "next planned" entry without duplicating that logic here.
        val matchesByTournamentForSummary = allTournaments
            .filter { !it.isCompleted }
            .associate { tourney ->
                tourney.id to tournamentMatchDao.getMatchesListForTournament(tourney.id)
            }
        val summaries = getTournamentSummaryUseCase.buildSeasonSummaries(
            tournaments = allTournaments,
            matchesByTournament = matchesByTournamentForSummary,
            placementsByTournament = emptyMap(),
            activeRoundsByTournament = emptyMap()
        )
        val isNextPlanned = summaries.find { it.tournament.id == tournament.id }?.isNextPlanned ?: false

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
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }

        val progressState = advanceTournamentRoundUseCase.evaluateTournamentState(currentTournamentId)

        if (!hasUserSelectedRound && progressState?.activeRound != null) {
            val activeIndex = sequence.indexOf(progressState.activeRound)
            if (activeIndex != -1) {
                selectedRoundIndexFlow.value = activeIndex
            }
        }

        if (selectedRoundIndexFlow.value !in availableRounds.indices) {
            selectedRoundIndexFlow.value = 0
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndexFlow.value) ?: ""
        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }
        val isCurrentRoundCompleted = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.all { it.isCompleted }

        val activeRoundDisplayName = progressState?.activeRound?.let {
            TournamentUiHelper.getDisplayRoundName(it.name, getApplication())
        } ?: availableRoundDisplayNames.firstOrNull() ?: ""

        val canAdvanceRound = progressState?.canAdvanceRound ?: false
        val isFinalRound = progressState?.isFinalRound ?: false
        val hasOpenCpuMatchesInActiveRound = progressState?.hasOpenCpuMatchesInActiveRound ?: false

        val roundStatuses = sequence.map { round ->
            when (progressState?.roundPhases?.get(round)) {
                TournamentRoundPhase.COMPLETED -> RoundStatus.COMPLETED
                TournamentRoundPhase.ACTIVE -> RoundStatus.ACTIVE
                TournamentRoundPhase.PENDING, null -> RoundStatus.PENDING
            }
        }

        // Trigger Auto-Simulate if needed
        if (autoSimulateCpu && hasOpenCpuMatchesInActiveRound) {
            completeMatchUseCase.simulateAllOpenCpuMatches(currentTournamentId)
            return
        }

        // Trigger Auto-Advance if all matches in current round are completed
        if (autoAdvanceRound && canAdvanceRound) {
            hasUserSelectedRound = false
            advanceTournamentRoundUseCase(currentTournamentId)
            return
        }

        val isUkOpenUngenerated = isOpenDrawRoundPending(event, currentRoundKey, dbMatchesForRound)

        val season = seasonDao.getSeasonById(tournament.seasonId)
        val careerId = season?.careerId ?: currentCareerId

        val isOpenDrawInfoVisible = isUkOpenUngenerated
        val openEntrantsGroups = if (isOpenDrawInfoVisible) {
            getUkOpenEntrantGroups(currentTournamentId, careerId, currentRoundKey, allMatches, event)
        } else {
            emptyList()
        }

        val currentRoundMatches = dbMatchesForRound

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) {
            ('A'..'P').map { it.toString() }
        } else {
            emptyList()
        }

        if (isGrandSlamGroup && selectedGroupIndexFlow.value !in availableGroups.indices) {
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            val selectedGroupLetter = availableGroups[selectedGroupIndexFlow.value]
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

        val qualifierBoardCount = if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4

        // QUAL_R1-Matches zur Board-Zuordnung der Vorrundensieger
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }

        val rawMatchUiModels = filteredMatches.map { match ->
            createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
        }

        val matchUiModels = if (currentRoundKey == TournamentRound.QUAL_PRELIM.name) {
            rawMatchUiModels.map { model ->
                val board = tournamentQualificationUseCase.resolvePrelimTargetBoard(qualR1Matches, model.match.matchIndex, model.match.winnerId, qualifierBoardCount)
                model.copy(targetBoard = board)
            }.sortedWith(
                compareBy<MatchUiModel> { it.targetBoard ?: 0 }
                    .thenBy { it.match.matchIndex }
            )
        } else {
            rawMatchUiModels
        }

        val isSelectedRoundPlayable = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.any { !it.isCompleted }
        val prizeMoneyItems = getPrizeMoneyItems(event)

        val prevRoundDisplayName = if (selectedRoundIndexFlow.value > 0) {
            availableRoundDisplayNames.getOrNull(selectedRoundIndexFlow.value - 1) ?: ""
        } else {
            ""
        }

        val hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE
        val host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity)

        val allRoundsMatches = availableRounds.map { roundKey ->
            val dbMatchesForRound = allMatches.filter { it.round == roundKey }
            val roundMatches = dbMatchesForRound

            val roundModels = roundMatches.map { match ->
                createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, roundKey, event, seededPlayerMap)
            }

            if (roundKey == TournamentRound.QUAL_PRELIM.name) {
                roundModels.map { model ->
                    val board = tournamentQualificationUseCase.resolvePrelimTargetBoard(qualR1Matches, model.match.matchIndex, model.match.winnerId, qualifierBoardCount)
                    model.copy(targetBoard = board)
                }
            } else {
                roundModels
            }
        }

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userName, userCountryCode, allPlayersMap, tournament.seasonId, tournament.id)
        } else emptyMap()

        val openDrawInfoMap = mutableMapOf<Int, Pair<String, List<EntrantGroupInfo>>>()
        for ((r, roundKey) in availableRounds.withIndex()) {
            val dbMatchesForRound = allMatches.filter { it.round == roundKey }
            val isUkOpenUngenerated = isOpenDrawRoundPending(event, roundKey, dbMatchesForRound)
            if (isUkOpenUngenerated) {
                val prevName = availableRoundDisplayNames.getOrNull(r - 1) ?: ""
                val groups = getUkOpenEntrantGroups(currentTournamentId, careerId, roundKey, allMatches, event)
                openDrawInfoMap[r] = prevName to groups
            }
        }

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = hostCountryCode,
            activeRoundDisplayName = activeRoundDisplayName,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndexFlow.value,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndexFlow.value,
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
            openDrawInfoMap = openDrawInfoMap,
            isFinalRound = isFinalRound
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

        if (!hasUserSelectedRound || selectedRoundIndexFlow.value !in availableRounds.indices) {
            selectedRoundIndexFlow.value = availableRounds.lastIndex.coerceAtLeast(0)
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndexFlow.value) ?: ""
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }

        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }
        val currentRoundMatches = dbMatchesForRound

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) ('A'..'P').map { it.toString() } else emptyList()

        if (isGrandSlamGroup && selectedGroupIndexFlow.value !in availableGroups.indices) {
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            currentRoundMatches.filter { it.groupName == availableGroups[selectedGroupIndexFlow.value] }
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

        val qualifierBoardCount = if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4

        // QUAL_R1-Matches zur Board-Zuordnung der Vorrundensieger
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }

        val rawMatchUiModels = filteredMatches.map { match ->
            createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
        }

        val matchUiModels = if (currentRoundKey == TournamentRound.QUAL_PRELIM.name) {
            rawMatchUiModels.map { model ->
                val board = tournamentQualificationUseCase.resolvePrelimTargetBoard(qualR1Matches, model.match.matchIndex, model.match.winnerId, qualifierBoardCount)
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
        val host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity)

        val allRoundsMatches = availableRounds.map { roundKey ->
            val roundMatches = allMatches.filter { it.round == roundKey }

            val roundModels = roundMatches.map { match ->
                createMatchUiModel(match, userName, userCountryCode, userLevel, allPlayersMap, roundKey, event, seededPlayerMap)
            }

            if (roundKey == TournamentRound.QUAL_PRELIM.name) {
                roundModels.map { model ->
                    val board = tournamentQualificationUseCase.resolvePrelimTargetBoard(qualR1Matches, model.match.matchIndex, model.match.winnerId, qualifierBoardCount)
                    model.copy(targetBoard = board)
                }
            } else {
                roundModels
            }
        }

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userName, userCountryCode, allPlayersMap, tournament.seasonId, tournament.id)
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
            selectedRoundIndex = selectedRoundIndexFlow.value,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndexFlow.value,
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

    /**
     * Calculates the Grand-Slam group standings by delegating to [CalculateGroupStandingsUseCase]
     * (single source of truth) and mapping the domain [GroupStandingEntry] objects to
     * [GrandSlamGroupItem] UI models, enriched with player names, flags and seeding info.
     */
    private suspend fun calculateGrandSlamGroupStandings(
        allMatches: List<TournamentMatch>,
        userName: String,
        userCountryCode: String,
        allPlayersMap: Map<Long, Player>,
        seasonId: Long,
        tournamentId: Long
    ): Map<String, List<GrandSlamGroupItem>> {
        if (allMatches.none { it.round == TournamentRound.GROUP.name }) return emptyMap()

        val seededPlayerMap = getSeededPlayerMap(TournamentEvent.GRAND_SLAM_OF_DARTS, seasonId, tournamentId)

        // Domain use-case performs all statistics and sorting – no duplicated logic here.
        val standingsByGroup = calculateGroupStandingsUseCase(allMatches)

        return ('A'..'P').mapNotNull { groupChar ->
            val groupLetter = groupChar.toString()
            val entries = standingsByGroup[groupLetter] ?: return@mapNotNull null
            val items = entries.map { entry ->
                val playerSeed = seededPlayerMap[entry.playerId]
                val playerUiInfo = if (entry.playerId == USER_ID) {
                    PlayerUiInfo(
                        id = USER_ID,
                        name = userName,
                        countryCode = userCountryCode,
                        seed = playerSeed
                    )
                } else {
                    val dbPlayer = allPlayersMap[entry.playerId]
                    PlayerUiInfo(
                        id = entry.playerId,
                        name = dbPlayer?.name ?: "CPU #${entry.playerId}",
                        countryCode = dbPlayer?.countryCode ?: Country.DEFAULT_CODE,
                        skill = dbPlayer?.skill ?: SkillLevel.DEFAULT.value,
                        seed = playerSeed
                    )
                }
                GrandSlamGroupItem(
                    rank = entry.rank,
                    player = playerUiInfo,
                    played = entry.played,
                    legDiff = entry.legDiff,
                    points = entry.points
                )
            }
            groupLetter to items
        }.toMap()
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
                if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && roundKey == TournamentRound.GROUP.name) {
                    app.getString(R.string.match_tbd_open_draw)
                } else if (event == TournamentEvent.EUROPEAN_TOUR && roundKey == TournamentRound.L32.name) {
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
            playerId <= -9000L && playerId > -9020L -> {
                val groupIdx = (-9000L - playerId).toInt()
                val groupLetter = ('A' + groupIdx).toString()
                app.getString(R.string.grand_slam_group_winner_format, groupLetter)
            }
            playerId < 0L && playerId != USER_ID -> {
                app.getString(R.string.match_tbd_open_draw)
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
        allPlayersMap: Map<Long, Player>,
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
        val p1Seed = if (match.player1Id > 0L || match.player1Id == USER_ID) {
            seededPlayerMap[match.player1Id]
        } else {
            null
        }
        val p1CountryCode = when {
            match.player1Id == USER_ID -> userCountryCode
            match.player1Id < 0L -> null
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
        val p2Seed = if (match.player2Id > 0L || match.player2Id == USER_ID) {
            seededPlayerMap[match.player2Id]
        } else {
            null
        }
        val p2CountryCode = when {
            match.player2Id == USER_ID -> userCountryCode
            match.player2Id < 0L -> null
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
        selectedRoundIndexFlow.value = index
    }

    /**
     * Selects a group by index.
     */
    fun selectGroup(index: Int) {
        selectedGroupIndexFlow.value = index
    }

    /**
     * Simulates all remaining CPU matches in the current active round.
     */
    fun simulateCpuMatches() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                completeMatchUseCase.simulateAllOpenCpuMatches(currentTournamentId)
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
                val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value
                hasUserSelectedRound = false
                tournamentProgressionUseCase.simulateCompleteTournament(currentTournamentId, userLevel)
            } finally {
                _isSimulatingBatch.value = false
            }
        }
    }

    /**
     * Manually triggers round advancement.
     */
    fun advanceRound() {
        viewModelScope.launch {
            hasUserSelectedRound = false
            advanceTournamentRoundUseCase(currentTournamentId)
        }
    }

    /**
     * Sets the winner for a user match in debug mode.
     */
    fun setUserMatchWinner(matchId: Long, userWins: Boolean) {
        viewModelScope.launch {
            completeMatchUseCase.completeWithPresetWinner(matchId, userWins)
        }
    }

    /**
     * Manually sets a user match score. Returns an error message if invalid, or `null` if saved successfully.
     */
    suspend fun setManualUserMatchScore(matchId: Long, p1Score: Int, p2Score: Int): String? {
        return completeMatchUseCase.completeManualMatch(matchId, p1Score, p2Score)
    }

    /**
     * Checks if an open draw round (UK Open or European Open) is pending generation.
     */
    private fun isOpenDrawRoundPending(
        event: TournamentEvent,
        roundKey: String,
        matchesForRound: List<TournamentMatch>
    ): Boolean {
        val isUkOrEuropeanOpen = event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN
        val openDrawRounds = listOf(
            TournamentRound.R2_128.name, TournamentRound.R3_96.name, TournamentRound.L64.name,
            TournamentRound.L32.name, TournamentRound.L16.name, TournamentRound.QF.name, TournamentRound.SF.name, TournamentRound.F.name
        )
        return isUkOrEuropeanOpen && matchesForRound.isEmpty() && roundKey in openDrawRounds
    }

    /**
     * Returns the round sequence for the given [event] and optional [allMatches].
     */
    private fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        return tournamentQualificationUseCase.getRoundSequence(event, allMatches)
    }

    /**
     * Returns the seeded player map for the given [event] and [seasonId].
     */
    private suspend fun getSeededPlayerMap(
        event: TournamentEvent,
        seasonId: Long,
        tournamentId: Long = 0L
    ): Map<Long, Int> {
        return tournamentQualificationUseCase.getSeededPlayerMap(event, seasonId, tournamentId)
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
        val domainGroups = tournamentQualificationUseCase.getUkOpenEntrantGroups(
            tournamentId = tournamentId,
            careerId = careerId,
            roundKey = roundKey,
            allMatches = allMatches,
            event = event,
            currentCareerId = currentCareerId
        )
        return TournamentDetailUiHelper.mapEntrantGroupsToUi(getApplication(), domainGroups)
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
