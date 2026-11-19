package com.danny.dannyapp.ui.tournament

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.CalculateGroupStandingsUseCase
import com.danny.dannyapp.domain.usecase.CompleteMatchUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import com.danny.dannyapp.domain.usecase.GetTournamentSummaryUseCase
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
        val seasonNumber: Int,
        val participantGroups: List<EntrantGroupInfo> = emptyList(),
        val isParticipantsFrozen: Boolean = false,
        val openQualifierSlotsText: String? = null
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
        val isFinalRound: Boolean = false,
        val participantGroups: List<EntrantGroupInfo> = emptyList(),
        val isParticipantsFrozen: Boolean = false,
        val openQualifierSlotsText: String? = null
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
        val grandSlamGroupStandings: Map<String, List<GrandSlamGroupItem>> = emptyMap(),
        val participantGroups: List<EntrantGroupInfo> = emptyList(),
        val isParticipantsFrozen: Boolean = false,
        val openQualifierSlotsText: String? = null
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
    private val tournamentParticipantDao = db.tournamentParticipantDao()
    private val getOrderOfMeritUseCase = GetOrderOfMeritUseCase(application)
    private val careerRepository = CareerRepository(application)

    private val generateBracketUseCase = GenerateBracketUseCase(application)
    private val completeMatchUseCase = CompleteMatchUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)
    private val tournamentProgressionUseCase = TournamentProgressionUseCase(application)
    private val tournamentQualificationUseCase = TournamentQualificationUseCase(application)
    private val calculateGroupStandingsUseCase = CalculateGroupStandingsUseCase()
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

    val autoSimulateCpu: Boolean get() = settingsManager.autoSimulateCpu
    val autoAdvanceRound: Boolean get() = settingsManager.autoAdvanceRound

    fun loadTournament(tournamentId: Long, careerId: Long) {
        TournamentConfigLoader.loadFromAssets(getApplication())
        this.currentTournamentId = tournamentId
        this.currentCareerId = careerId
        hasUserSelectedRound = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            val targetTournament = tournamentDao.getTournamentById(tournamentId)
                ?: return@launch run {
                    _uiState.value = TournamentDetailState.Error(getApplication<Application>().getString(R.string.tournament_not_found))
                }

            combine(
                tournamentDao.getTournamentsForSeason(targetTournament.seasonId),
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
                        _uiState.value = TournamentDetailState.Error(getApplication<Application>().getString(R.string.tournament_not_found))
                        return@combine null
                    }
                    Triple(updatedTournament, event, matches)
                }
            }.filterNotNull().collect { (tournament, event, matches) ->
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

    private suspend fun buildPreviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allTournaments: List<Tournament>,
        careerName: String,
        seasonNumber: Int
    ) {
        val host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity)
        val totalPrizeText = if (event.effectiveTotalPrizeFund > 0) {
            getApplication<Application>().getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            getApplication<Application>().getString(R.string.tournament_qualifying_no_prize)
        }

        val matchesByTournament = allTournaments.filter { !it.isCompleted }.associate { tourney ->
            tourney.id to tournamentMatchDao.getMatchesListForTournament(tourney.id)
        }
        val summaries = getTournamentSummaryUseCase.buildSeasonSummaries(
            tournaments = allTournaments,
            matchesByTournament = matchesByTournament,
            placementsByTournament = emptyMap(),
            activeRoundsByTournament = emptyMap()
        )
        val isNextPlanned = summaries.find { it.tournament.id == tournament.id }?.isNextPlanned ?: false
        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }
        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap)

        _uiState.value = TournamentDetailState.Preview(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE,
            totalPrizeFundText = totalPrizeText,
            prizeMoneyItems = getPrizeMoneyItems(event),
            isNextPlanned = isNextPlanned,
            careerName = careerName,
            seasonNumber = seasonNumber,
            participantGroups = participantData.participantGroups,
            isParticipantsFrozen = participantData.isParticipantsFrozen,
            openQualifierSlotsText = participantData.openQualifierSlotsText
        )
    }

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
            if (activeIndex != -1) selectedRoundIndexFlow.value = activeIndex
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

        if (autoSimulateCpu && hasOpenCpuMatchesInActiveRound) {
            completeMatchUseCase.simulateAllOpenCpuMatches(currentTournamentId)
            return
        }

        if (autoAdvanceRound && canAdvanceRound) {
            hasUserSelectedRound = false
            advanceTournamentRoundUseCase(currentTournamentId)
            return
        }

        val isUkOpenUngenerated = isOpenDrawRoundPending(event, currentRoundKey, dbMatchesForRound)
        val season = seasonDao.getSeasonById(tournament.seasonId)
        val careerId = season?.careerId ?: currentCareerId

        val isOpenDrawEvent = (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN)
        val preloadedParticipants = if (isOpenDrawEvent) tournamentParticipantDao.getParticipantsForTournament(currentTournamentId) else null
        val preloadedPlayers = if (isOpenDrawEvent) playerDao.getAllPlayersList() else null
        val preloadedOomEntries = if (isOpenDrawEvent) getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM) else null

        val openDrawInfoMap = mutableMapOf<Int, Pair<String, List<EntrantGroupInfo>>>()
        for ((roundIndex, roundKey) in availableRounds.withIndex()) {
            val matchesInRound = allMatches.filter { it.round == roundKey }
            if (isOpenDrawRoundPending(event, roundKey, matchesInRound)) {
                val prevName = availableRoundDisplayNames.getOrNull(roundIndex - 1) ?: ""
                val groups = getUkOpenEntrantGroups(
                    tournamentId = currentTournamentId,
                    careerId = careerId,
                    roundKey = roundKey,
                    allMatches = allMatches,
                    event = event,
                    preloadedParticipants = preloadedParticipants,
                    preloadedPlayers = preloadedPlayers,
                    preloadedOomEntries = preloadedOomEntries
                )
                openDrawInfoMap[roundIndex] = prevName to groups
            }
        }

        val isOpenDrawInfoVisible = isUkOpenUngenerated
        val openEntrantsGroups = if (isOpenDrawInfoVisible) {
            openDrawInfoMap[selectedRoundIndexFlow.value]?.second ?: getUkOpenEntrantGroups(
                tournamentId = currentTournamentId,
                careerId = careerId,
                roundKey = currentRoundKey,
                allMatches = allMatches,
                event = event,
                preloadedParticipants = preloadedParticipants,
                preloadedPlayers = preloadedPlayers,
                preloadedOomEntries = preloadedOomEntries
            )
        } else emptyList()

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) ('A'..'P').map { it.toString() } else emptyList()

        if (isGrandSlamGroup && selectedGroupIndexFlow.value !in availableGroups.indices) {
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            dbMatchesForRound.filter { it.groupName == availableGroups[selectedGroupIndexFlow.value] }
        } else dbMatchesForRound

        val allPlayersMap = (preloadedPlayers ?: playerDao.getAllPlayersList()).associateBy { it.id }
        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)
        val qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromMatches(event, allMatches)
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }
        val userContext = resolveUserDisplayContext()

        val matchUiModels = buildRoundMatchUiModels(
            matches = filteredMatches,
            roundKey = currentRoundKey,
            event = event,
            userContext = userContext,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount,
            sortPrelimByBoard = true
        )

        val isSelectedRoundPlayable = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.any { !it.isCompleted }
        val prevRoundDisplayName = if (selectedRoundIndexFlow.value > 0) {
            availableRoundDisplayNames.getOrNull(selectedRoundIndexFlow.value - 1) ?: ""
        } else ""

        val allRoundsMatches = buildAllRoundsMatches(
            availableRounds = availableRounds,
            allMatches = allMatches,
            event = event,
            userContext = userContext,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount
        )

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userContext.userName, userContext.userCountryCode, allPlayersMap, tournament.seasonId, tournament.id)
        } else emptyMap()

        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap)

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity),
            hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE,
            activeRoundDisplayName = activeRoundDisplayName,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndexFlow.value,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndexFlow.value,
            matches = matchUiModels,
            allRoundsMatches = allRoundsMatches,
            prizeMoneyItems = getPrizeMoneyItems(event),
            autoSimulateCpu = autoSimulateCpu,
            autoAdvanceRound = autoAdvanceRound,
            isCurrentRoundCompleted = isCurrentRoundCompleted,
            isSelectedRoundPlayable = isSelectedRoundPlayable,
            canAdvanceRound = canAdvanceRound,
            userLevel = userContext.userLevel,
            careerName = careerName,
            seasonNumber = seasonNumber,
            isOpenDrawInfoVisible = isOpenDrawInfoVisible,
            prevRoundDisplayName = prevRoundDisplayName,
            openEntrantsGroups = openEntrantsGroups,
            roundStatuses = roundStatuses,
            grandSlamGroupStandings = grandSlamGroupStandings,
            hasOpenCpuMatchesInActiveRound = hasOpenCpuMatchesInActiveRound,
            openDrawInfoMap = openDrawInfoMap,
            isFinalRound = isFinalRound,
            participantGroups = participantData.participantGroups,
            isParticipantsFrozen = participantData.isParticipantsFrozen,
            openQualifierSlotsText = participantData.openQualifierSlotsText
        )
    }

    private suspend fun buildReviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        careerName: String,
        seasonNumber: Int
    ) {
        val finalMatch = allMatches.find { it.round == TournamentRound.F.name } ?: allMatches.lastOrNull()
        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }
        val userContext = resolveUserDisplayContext()

        val winnerId = finalMatch?.winnerId
        val winner = if (winnerId != null) {
            if (winnerId == USER_ID) {
                PlayerUiInfo(id = USER_ID, name = userContext.userName, countryCode = userContext.userCountryCode)
            } else {
                val player = allPlayersMap[winnerId]
                PlayerUiInfo(
                    id = winnerId,
                    name = player?.name ?: getApplication<Application>().getString(R.string.player_name_unknown),
                    countryCode = player?.countryCode ?: Country.DEFAULT_CODE,
                    skill = player?.skill ?: SkillLevel.DEFAULT.value
                )
            }
        } else PlayerUiInfo(id = 0L, name = getApplication<Application>().getString(R.string.player_name_unknown), countryCode = Country.DEFAULT_CODE)

        val userPlacement = tournamentPlacementDao.getPlacementsListForTournament(tournament.id).find { it.playerId == USER_ID }
        val userReachedRoundText = if (userPlacement != null) {
            TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
        } else getApplication<Application>().getString(R.string.tournament_review_not_qualified)

        val userEarnedPrizeMoneyText = if (userPlacement != null && userPlacement.prizeMoney > 0) {
            getApplication<Application>().getString(R.string.prize_money_format, userPlacement.prizeMoney)
        } else "£0"

        val sequence = getRoundSequence(event, allMatches)
        val availableRounds = sequence.map { it.name }

        if (!hasUserSelectedRound || selectedRoundIndexFlow.value !in availableRounds.indices) {
            selectedRoundIndexFlow.value = availableRounds.lastIndex.coerceAtLeast(0)
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndexFlow.value) ?: ""
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }
        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) ('A'..'P').map { it.toString() } else emptyList()

        if (isGrandSlamGroup && selectedGroupIndexFlow.value !in availableGroups.indices) {
            selectedGroupIndexFlow.value = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            dbMatchesForRound.filter { it.groupName == availableGroups[selectedGroupIndexFlow.value] }
        } else dbMatchesForRound

        val isQualifier = event.isQualifier
        val qualifierWinners = if (isQualifier) {
            allMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }.mapNotNull { match ->
                val wId = match.winnerId ?: return@mapNotNull null
                if (wId == USER_ID) PlayerUiInfo(id = USER_ID, name = userContext.userName, countryCode = userContext.userCountryCode)
                else {
                    val player = allPlayersMap[wId]
                    PlayerUiInfo(id = wId, name = player?.name ?: "CPU #$wId", countryCode = player?.countryCode ?: Country.DEFAULT_CODE, skill = player?.skill ?: SkillLevel.DEFAULT.value)
                }
            }
        } else null

        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)
        val qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromMatches(event, allMatches)
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }

        val matchUiModels = buildRoundMatchUiModels(
            matches = filteredMatches,
            roundKey = currentRoundKey,
            event = event,
            userContext = userContext,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount,
            sortPrelimByBoard = true
        )

        val allRoundsMatches = buildAllRoundsMatches(
            availableRounds = availableRounds,
            allMatches = allMatches,
            event = event,
            userContext = userContext,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount
        )

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, userContext.userName, userContext.userCountryCode, allPlayersMap, tournament.seasonId, tournament.id)
        } else emptyMap()

        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap)

        _uiState.value = TournamentDetailState.Review(
            tournament = tournament,
            event = event,
            host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity),
            hostCountryCode = tournament.hostCountry ?: Country.DEFAULT_CODE,
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
            prizeMoneyItems = getPrizeMoneyItems(event),
            careerName = careerName,
            isQualifier = isQualifier,
            qualifierWinners = qualifierWinners,
            seasonNumber = seasonNumber,
            grandSlamGroupStandings = grandSlamGroupStandings,
            participantGroups = participantData.participantGroups,
            isParticipantsFrozen = participantData.isParticipantsFrozen,
            openQualifierSlotsText = participantData.openQualifierSlotsText
        )
    }

    private data class ParticipantPreviewUiData(
        val participantGroups: List<EntrantGroupInfo>,
        val isParticipantsFrozen: Boolean,
        val openQualifierSlotsText: String?
    )

    private suspend fun buildParticipantPreviewUiData(
        tournament: Tournament,
        event: TournamentEvent,
        allPlayersMap: Map<Long, Player>
    ): ParticipantPreviewUiData {
        val previewResult = tournamentQualificationUseCase.getPreviewParticipants(
            tournament = tournament,
            seasonId = tournament.seasonId,
            careerId = currentCareerId
        )
        val userContext = resolveUserDisplayContext()

        val groupedParticipants = previewResult.participants.groupBy { it.qualificationType ?: "QUALIFIER" }
        val participantGroups = groupedParticipants.map { (qualType, participants) ->
            val title = getQualificationGroupTitle(qualType)
            val playerUiInfos = participants.map { participant ->
                if (participant.playerId == USER_ID) {
                    PlayerUiInfo(
                        id = USER_ID,
                        name = userContext.userName,
                        countryCode = userContext.userCountryCode,
                        seed = participant.seedNumber,
                        skill = userContext.userLevel
                    )
                } else {
                    val player = allPlayersMap[participant.playerId]
                    PlayerUiInfo(
                        id = participant.playerId,
                        name = player?.name ?: getApplication<Application>().getString(R.string.player_name_unknown),
                        countryCode = player?.countryCode ?: Country.DEFAULT_CODE,
                        seed = participant.seedNumber,
                        skill = player?.skill ?: SkillLevel.DEFAULT.value
                    )
                }
            }.sortedWith(compareBy<PlayerUiInfo> { it.seed ?: Int.MAX_VALUE }.thenBy { it.name })

            EntrantGroupInfo(title = title, players = playerUiInfos)
        }

        val targetCount = event.targetParticipantCount
        val missingSlots = targetCount - previewResult.participants.size
        val openQualifierSlotsText = if (missingSlots > 0 && event.qualifierEventForMain != null) {
            val qualifierName = when (event.qualifierEventForMain) {
                TournamentEvent.QUALIFIER_ET -> getApplication<Application>().getString(R.string.qualifier_name_et)
                TournamentEvent.QUALIFIER_GRAND_SLAM -> getApplication<Application>().getString(R.string.qualifier_name_grand_slam)
                TournamentEvent.QUALIFIER_WM -> getApplication<Application>().getString(R.string.qualifier_name_wm)
                else -> null
            }
            if (qualifierName != null) {
                if (missingSlots == 1) {
                    getApplication<Application>().getString(R.string.tournament_info_open_qualifier_slot_single, qualifierName)
                } else {
                    getApplication<Application>().getString(R.string.tournament_info_open_qualifier_slots, missingSlots, qualifierName)
                }
            } else null
        } else null

        return ParticipantPreviewUiData(
            participantGroups = participantGroups,
            isParticipantsFrozen = previewResult.isFrozen,
            openQualifierSlotsText = openQualifierSlotsText
        )
    }

    private fun getQualificationGroupTitle(qualificationType: String): String {
        val resId = when (qualificationType) {
            "OOM" -> R.string.qualification_group_oom
            "PT_OOM" -> R.string.qualification_group_pt_oom
            "PC_OOM" -> R.string.qualification_group_pc_oom
            "ET_OOM" -> R.string.qualification_group_et_oom
            "HOST_NATION" -> R.string.qualification_group_host_nation
            "QUALIFIER" -> R.string.qualification_group_qualifier
            "TOUR_CARD" -> R.string.qualification_group_tour_card
            "OOM_TOP_32" -> R.string.qualification_group_oom_top_32
            "OOM_33_64" -> R.string.qualification_group_oom_33_64
            "OOM_65_96" -> R.string.qualification_group_oom_65_96
            "OOM_97_128" -> R.string.qualification_group_oom_97_128
            "MAJOR" -> R.string.qualification_group_major
            "ADDITIONAL" -> R.string.qualification_group_additional
            "PRO_TOUR_WINNER" -> R.string.qualification_group_pro_tour_winner
            "POT_2" -> R.string.qualification_group_pot_2
            "POT_3" -> R.string.qualification_group_pot_3
            else -> R.string.qualification_group_qualifier
        }
        return getApplication<Application>().getString(resId)
    }

    private data class UserDisplayContext(
        val userLevel: Int,
        val userName: String,
        val userCountryCode: String
    )

    /**
     * Ermittelt Anzeigename, Landescode und Spielstärke des Users für die aktuelle Karriere.
     */
    private suspend fun resolveUserDisplayContext(): UserDisplayContext {
        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: SkillLevel.DEFAULT.value
        val userName = careerWithProfile?.playerName?.ifBlank { null }
            ?: careerWithProfile?.profileName
            ?: careerWithProfile?.career?.name
            ?: getApplication<Application>().getString(R.string.player_name_you)
        val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE
        return UserDisplayContext(userLevel, userName, userCountryCode)
    }

    /**
     * Wandelt die Datenbank-Matches einer Runde in UI-Modelle um und ergänzt bei Vorrunden-Matches das Ziel-Board.
     */
    private fun buildRoundMatchUiModels(
        matches: List<TournamentMatch>,
        roundKey: String,
        event: TournamentEvent,
        userContext: UserDisplayContext,
        allPlayersMap: Map<Long, Player>,
        seededPlayerMap: Map<Long, Int>,
        qualR1Matches: List<TournamentMatch>,
        qualifierBoardCount: Int,
        sortPrelimByBoard: Boolean
    ): List<MatchUiModel> {
        val rawModels = matches.map { match ->
            TournamentDetailMapper.createMatchUiModel(
                context = getApplication(),
                match = match,
                userName = userContext.userName,
                userCountryCode = userContext.userCountryCode,
                userLevel = userContext.userLevel,
                allPlayersMap = allPlayersMap,
                roundKey = roundKey,
                event = event,
                seededPlayerMap = seededPlayerMap
            )
        }
        if (roundKey != TournamentRound.QUAL_PRELIM.name) return rawModels

        val withBoards = rawModels.map { model ->
            val board = tournamentQualificationUseCase.resolvePrelimTargetBoard(
                qualR1Matches = qualR1Matches,
                prelimMatchIndex = model.match.matchIndex,
                winnerId = model.match.winnerId,
                boardCount = qualifierBoardCount
            )
            model.copy(targetBoard = board)
        }
        return if (sortPrelimByBoard) {
            withBoards.sortedWith(compareBy<MatchUiModel> { it.targetBoard ?: 0 }.thenBy { it.match.matchIndex })
        } else {
            withBoards
        }
    }

    /**
     * Erzeugt die komplette Runden-Matrix für den Turnierbaum (Bracket).
     */
    private fun buildAllRoundsMatches(
        availableRounds: List<String>,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        userContext: UserDisplayContext,
        allPlayersMap: Map<Long, Player>,
        seededPlayerMap: Map<Long, Int>,
        qualR1Matches: List<TournamentMatch>,
        qualifierBoardCount: Int
    ): List<List<MatchUiModel>> {
        return availableRounds.map { roundKey ->
            buildRoundMatchUiModels(
                matches = allMatches.filter { it.round == roundKey },
                roundKey = roundKey,
                event = event,
                userContext = userContext,
                allPlayersMap = allPlayersMap,
                seededPlayerMap = seededPlayerMap,
                qualR1Matches = qualR1Matches,
                qualifierBoardCount = qualifierBoardCount,
                sortPrelimByBoard = false
            )
        }
    }

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
        val standingsByGroup = calculateGroupStandingsUseCase(allMatches)
        return TournamentDetailMapper.mapGrandSlamGroupStandings(standingsByGroup, userName, userCountryCode, allPlayersMap, seededPlayerMap)
    }

    private fun getPrizeMoneyItems(event: TournamentEvent): List<PrizeMoneyItem> {
        return TournamentDetailMapper.buildPrizeMoneyItems(getApplication(), event, getRoundSequence(event))
    }

    fun startTournament() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            generateBracketUseCase(currentTournamentId)
        }
    }

    fun selectRound(index: Int) {
        hasUserSelectedRound = true
        selectedRoundIndexFlow.value = index
    }

    fun selectGroup(index: Int) {
        selectedGroupIndexFlow.value = index
    }

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

    fun advanceRound() {
        viewModelScope.launch {
            hasUserSelectedRound = false
            advanceTournamentRoundUseCase(currentTournamentId)
        }
    }

    fun setUserMatchWinner(matchId: Long, userWins: Boolean) {
        viewModelScope.launch {
            completeMatchUseCase.completeWithPresetWinner(matchId, userWins)
        }
    }

    suspend fun setManualUserMatchScore(matchId: Long, p1Score: Int, p2Score: Int): String? {
        return completeMatchUseCase.completeManualMatch(matchId, p1Score, p2Score)
    }

    private fun isOpenDrawRoundPending(event: TournamentEvent, roundKey: String, matchesForRound: List<TournamentMatch>): Boolean {
        if (matchesForRound.isNotEmpty()) return false
        val round = TournamentRound.fromKey(roundKey) ?: return false
        return event.isOpenDrawRound(round)
    }

    private fun getRoundSequence(event: TournamentEvent, allMatches: List<TournamentMatch> = emptyList()): List<TournamentRound> {
        return tournamentQualificationUseCase.getRoundSequence(event, allMatches)
    }

    private suspend fun getSeededPlayerMap(event: TournamentEvent, seasonId: Long, tournamentId: Long = 0L): Map<Long, Int> {
        return tournamentQualificationUseCase.getSeededPlayerMap(event, seasonId, tournamentId)
    }

    private suspend fun getUkOpenEntrantGroups(
        tournamentId: Long,
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        preloadedParticipants: List<TournamentParticipant>? = null,
        preloadedPlayers: List<Player>? = null,
        preloadedOomEntries: List<OrderOfMeritEntry>? = null
    ): List<EntrantGroupInfo> {
        val domainGroups = tournamentQualificationUseCase.getUkOpenEntrantGroups(
            tournamentId = tournamentId,
            careerId = careerId,
            roundKey = roundKey,
            allMatches = allMatches,
            event = event,
            currentCareerId = currentCareerId,
            preloadedParticipants = preloadedParticipants,
            preloadedPlayers = preloadedPlayers,
            preloadedOomEntries = preloadedOomEntries
        )
        return TournamentDetailUiHelper.mapEntrantGroupsToUi(getApplication(), domainGroups)
    }
}
