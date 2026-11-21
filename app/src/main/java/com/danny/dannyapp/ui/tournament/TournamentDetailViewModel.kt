package com.danny.dannyapp.ui.tournament

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.appContainer
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import com.danny.dannyapp.domain.usecase.ManualScoreResult
import com.danny.dannyapp.ui.UIHelper
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
import kotlinx.coroutines.sync.Mutex

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
class TournamentDetailViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val settingsManager = application.appContainer.settingsManager
    private val tournamentRepository = application.appContainer.tournamentRepository
    private val seasonRepository = application.appContainer.seasonRepository
    private val playerRepository = application.appContainer.playerRepository
    private val getOrderOfMeritUseCase = application.appContainer.getOrderOfMeritUseCase
    private val careerRepository = application.appContainer.careerRepository

    private val generateBracketUseCase = application.appContainer.generateBracketUseCase
    private val completeMatchUseCase = application.appContainer.completeMatchUseCase
    private val advanceTournamentRoundUseCase = application.appContainer.advanceTournamentRoundUseCase
    private val tournamentProgressionUseCase = application.appContainer.tournamentProgressionUseCase
    private val tournamentQualificationUseCase = application.appContainer.tournamentQualificationUseCase
    private val calculateGroupStandingsUseCase = application.appContainer.calculateGroupStandingsUseCase
    private val getTournamentSummaryUseCase = application.appContainer.getTournamentSummaryUseCase

    private val _uiState = MutableStateFlow<TournamentDetailState>(TournamentDetailState.Loading)
    val uiState: StateFlow<TournamentDetailState> = _uiState.asStateFlow()

    private val _isSimulatingBatch = MutableStateFlow(false)
    val isSimulatingBatch: StateFlow<Boolean> = _isSimulatingBatch.asStateFlow()

    companion object {
        private const val KEY_TOURNAMENT_ID = "state_tournament_id"
        private const val KEY_CAREER_ID = "state_career_id"
        private const val KEY_SELECTED_ROUND_INDEX = "state_selected_round_index"
        private const val KEY_SELECTED_GROUP_INDEX = "state_selected_group_index"
        private const val KEY_HAS_USER_SELECTED_ROUND = "state_has_user_selected_round"
    }

    private var currentTournamentId: Long = savedStateHandle.get<Long>(KEY_TOURNAMENT_ID) ?: -1L
    private var currentCareerId: Long = -1L

    private var hasUserSelectedRound: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_HAS_USER_SELECTED_ROUND) ?: false
        set(value) {
            savedStateHandle[KEY_HAS_USER_SELECTED_ROUND] = value
        }

    private val selectedRoundIndexFlow = savedStateHandle.getStateFlow(KEY_SELECTED_ROUND_INDEX, 0)
    private val selectedGroupIndexFlow = savedStateHandle.getStateFlow(KEY_SELECTED_GROUP_INDEX, 0)
    private var loadJob: Job? = null
    private val autoStepMutex = Mutex()

    init {
        if (currentTournamentId > 0L) {
            val careerId = savedStateHandle.get<Long>(KEY_CAREER_ID) ?: -1L
            if (careerId > 0L) {
                loadTournament(currentTournamentId, careerId)
            } else {
                viewModelScope.launch {
                    try {
                        val tournament = tournamentRepository.getTournamentById(currentTournamentId)
                        val season = seasonRepository.getSeasonById(tournament.seasonId)
                        loadTournament(currentTournamentId, season.careerId)
                    } catch (e: Exception) {
                        getApplication<Application>().appContainer.reportError("Turnier laden", e)
                    }
                }
            }
        }
    }

    val autoSimulateCpu: Boolean get() = settingsManager.autoSimulateCpu
    val autoAdvanceRound: Boolean get() = settingsManager.autoAdvanceRound

    /**
     * Lädt ein Turnier und initialisiert den reaktiven UI-State.
     */
    fun loadTournament(tournamentId: Long, careerId: Long) {
        TournamentConfigLoader.loadFromAssets(getApplication())
        this.currentTournamentId = tournamentId
        this.currentCareerId = careerId
        savedStateHandle[KEY_TOURNAMENT_ID] = tournamentId
        savedStateHandle[KEY_CAREER_ID] = careerId
        hasUserSelectedRound = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            try {
                val targetTournament = tournamentRepository.getTournamentById(tournamentId)

                combine(
                    tournamentRepository.getTournamentsForSeason(targetTournament.seasonId),
                    tournamentRepository.getMatchesForTournament(tournamentId),
                    selectedRoundIndexFlow,
                    selectedGroupIndexFlow,
                    _isSimulatingBatch
                ) { tournaments, matches, _, _, isSimulatingBatch ->
                    if (isSimulatingBatch) null
                    else {
                        val updatedTournament = tournaments.find { it.id == tournamentId } ?: targetTournament
                        val event = TournamentEvent.valueOf(updatedTournament.type)
                        Triple(updatedTournament, event, matches)
                    }
                }.filterNotNull().collect { (tournament, event, matches) ->
                    val allTournaments = tournamentRepository.getTournamentsListForSeason(tournament.seasonId)
                    val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                    val careerName = careerWithProfile.career.name
                    val seasonNumber = careerWithProfile.career.season

                    if (tournament.isCompleted) {
                        buildReviewState(tournament, event, matches, careerName, seasonNumber, careerWithProfile)
                    } else if (matches.isEmpty()) {
                        buildPreviewState(tournament, event, allTournaments, careerName, seasonNumber, careerWithProfile)
                    } else {
                        buildActiveState(tournament, event, matches, careerName, seasonNumber, careerWithProfile)
                    }
                }
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Turnier laden", e)
            }
        }
    }

    /**
     * Baut den Preview-State für ein noch nicht gestartetes Turnier auf.
     */
    private suspend fun buildPreviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allTournaments: List<Tournament>,
        careerName: String,
        seasonNumber: Int,
        careerWithProfile: CareerWithProfile
    ) {
        val host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity)
        val totalPrizeText = if (event.effectiveTotalPrizeFund > 0) {
            getApplication<Application>().getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            getApplication<Application>().getString(R.string.tournament_qualifying_no_prize)
        }

        val matchesByTournament = tournamentRepository.getMatchesListForSeason(tournament.seasonId).groupBy { it.tournamentId }
        val summaries = getTournamentSummaryUseCase.buildSeasonSummaries(
            tournaments = allTournaments,
            matchesByTournament = matchesByTournament,
            placementsByTournament = emptyMap(),
            activeRoundsByTournament = emptyMap()
        )
        val isNextPlanned = summaries.find { it.tournament.id == tournament.id }?.isNextPlanned ?: false
        val allPlayersMap = playerRepository.getAllPlayersList().associateBy { it.id }
        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap, careerWithProfile)

        _uiState.value = TournamentDetailState.Preview(
            tournament = tournament,
            event = event,
            host = host,
            hostCountryCode = tournament.hostCountry,
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

    /**
     * Baut den Active-State für ein laufendes Turnier auf.
     */
    private suspend fun buildActiveState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        careerName: String,
        seasonNumber: Int,
        careerWithProfile: CareerWithProfile
    ) {
        val sequence = getRoundSequence(event, allMatches)
        val availableRounds = sequence.map { it.name }
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }

        val progressState = advanceTournamentRoundUseCase.evaluateTournamentState(currentTournamentId)

        if (!hasUserSelectedRound && progressState?.activeRound != null) {
            val activeIndex = sequence.indexOf(progressState.activeRound)
            if (activeIndex != -1) savedStateHandle[KEY_SELECTED_ROUND_INDEX] = activeIndex
        }

        if (selectedRoundIndexFlow.value !in availableRounds.indices) {
            savedStateHandle[KEY_SELECTED_ROUND_INDEX] = 0
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

        if ((autoSimulateCpu && hasOpenCpuMatchesInActiveRound) || (autoAdvanceRound && canAdvanceRound)) {
            if (autoStepMutex.tryLock()) {
                viewModelScope.launch {
                    try {
                        if (autoSimulateCpu && hasOpenCpuMatchesInActiveRound) {
                            completeMatchUseCase.simulateAllOpenCpuMatches(currentTournamentId)
                        } else if (autoAdvanceRound && canAdvanceRound) {
                            hasUserSelectedRound = false
                            advanceTournamentRoundUseCase(currentTournamentId)
                        }
                    } catch (e: Exception) {
                        getApplication<Application>().appContainer.reportError("Automatische Simulation", e)
                    } finally {
                        autoStepMutex.unlock()
                    }
                }
            }
            return
        }

        val isUkOpenUngenerated = isOpenDrawRoundPending(event, currentRoundKey, dbMatchesForRound)
        val season = seasonRepository.getSeasonById(tournament.seasonId)
        val careerId = season.careerId

        val isOpenDrawEvent = (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN)
        val preloadedParticipants = if (isOpenDrawEvent) tournamentRepository.getParticipantsForTournament(currentTournamentId) else null
        val preloadedPlayers = if (isOpenDrawEvent) playerRepository.getAllPlayersList() else null
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

        val openEntrantsGroups = if (isUkOpenUngenerated) {
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
            savedStateHandle[KEY_SELECTED_GROUP_INDEX] = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            dbMatchesForRound.filter { it.groupName == availableGroups[selectedGroupIndexFlow.value] }
        } else dbMatchesForRound

        val allPlayersMap = (preloadedPlayers ?: playerRepository.getAllPlayersList()).associateBy { it.id }
        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)
        val qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromMatches(event, allMatches)
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }

        val matchUiModels = buildRoundMatchUiModels(
            matches = filteredMatches,
            roundKey = currentRoundKey,
            event = event,
            careerWithProfile = careerWithProfile,
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
            careerWithProfile = careerWithProfile,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount
        )

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, careerWithProfile, allPlayersMap, tournament.seasonId, tournament.id)
        } else emptyMap()

        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap, careerWithProfile)

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity),
            hostCountryCode = tournament.hostCountry,
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
            careerName = careerName,
            seasonNumber = seasonNumber,
            isOpenDrawInfoVisible = isUkOpenUngenerated,
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

    /**
     * Baut den Review-State für ein beendetes Turnier auf.
     */
    private suspend fun buildReviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        careerName: String,
        seasonNumber: Int,
        careerWithProfile: CareerWithProfile
    ) {
        val finalMatch = allMatches.find { it.round == TournamentRound.F.name } ?: allMatches.lastOrNull()
        val allPlayersMap = playerRepository.getAllPlayersList().associateBy { it.id }

        val winnerId = finalMatch?.winnerId
        val winner = if (winnerId != null) {
            UIHelper.resolvePlayerUiInfo(
                context = getApplication(),
                playerId = winnerId,
                player = allPlayersMap[winnerId],
                careerWithProfile = careerWithProfile
            )
        } else PlayerUiInfo(id = 0L, name = getApplication<Application>().getString(R.string.player_name_unknown), countryCode = Country.DEFAULT_CODE)

        val userPlacement = tournamentRepository.getPlacementsListForTournament(tournament.id).find { it.playerId == USER_ID }
        val userReachedRoundText = if (userPlacement != null) {
            TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
        } else getApplication<Application>().getString(R.string.tournament_review_not_qualified)

        val userEarnedPrizeMoneyText = if (userPlacement != null && userPlacement.prizeMoney > 0) {
            getApplication<Application>().getString(R.string.prize_money_format, userPlacement.prizeMoney)
        } else "£0"

        val sequence = getRoundSequence(event, allMatches)
        val availableRounds = sequence.map { it.name }

        if (!hasUserSelectedRound || selectedRoundIndexFlow.value !in availableRounds.indices) {
            savedStateHandle[KEY_SELECTED_ROUND_INDEX] = availableRounds.lastIndex.coerceAtLeast(0)
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndexFlow.value) ?: ""
        val availableRoundDisplayNames = availableRounds.map { TournamentUiHelper.getDisplayRoundName(it, getApplication()) }
        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) ('A'..'P').map { it.toString() } else emptyList()

        if (isGrandSlamGroup && selectedGroupIndexFlow.value !in availableGroups.indices) {
            savedStateHandle[KEY_SELECTED_GROUP_INDEX] = 0
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            dbMatchesForRound.filter { it.groupName == availableGroups[selectedGroupIndexFlow.value] }
        } else dbMatchesForRound

        val isQualifier = event.isQualifier
        val qualifierWinners = if (isQualifier) {
            allMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }.mapNotNull { match ->
                val wId = match.winnerId ?: return@mapNotNull null
                UIHelper.resolvePlayerUiInfo(
                    context = getApplication(),
                    playerId = wId,
                    player = allPlayersMap[wId],
                    careerWithProfile = careerWithProfile
                )
            }
        } else null

        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId, tournament.id)
        val qualifierBoardCount = TournamentDetailUiHelper.getQualifierBoardCountFromMatches(event, allMatches)
        val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }

        val matchUiModels = buildRoundMatchUiModels(
            matches = filteredMatches,
            roundKey = currentRoundKey,
            event = event,
            careerWithProfile = careerWithProfile,
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
            careerWithProfile = careerWithProfile,
            allPlayersMap = allPlayersMap,
            seededPlayerMap = seededPlayerMap,
            qualR1Matches = qualR1Matches,
            qualifierBoardCount = qualifierBoardCount
        )

        val grandSlamGroupStandings = if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            calculateGrandSlamGroupStandings(allMatches, careerWithProfile, allPlayersMap, tournament.seasonId, tournament.id)
        } else emptyMap()

        val participantData = buildParticipantPreviewUiData(tournament, event, allPlayersMap, careerWithProfile)

        _uiState.value = TournamentDetailState.Review(
            tournament = tournament,
            event = event,
            host = TournamentUiHelper.getTournamentCity(tournament.hostCountry, tournament.hostCity),
            hostCountryCode = tournament.hostCountry,
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

    /**
     * Erstellt die Teilnehmer-Preview-Daten für ein Turnier.
     */
    private data class ParticipantPreviewUiData(
        val participantGroups: List<EntrantGroupInfo>,
        val isParticipantsFrozen: Boolean,
        val openQualifierSlotsText: String?
    )

    /**
     * Baut die Teilnehmer-Preview-Daten für ein Turnier auf.
     */
    private suspend fun buildParticipantPreviewUiData(
        tournament: Tournament,
        event: TournamentEvent,
        allPlayersMap: Map<Long, Player>,
        careerWithProfile: CareerWithProfile
    ): ParticipantPreviewUiData {
        val previewResult = tournamentQualificationUseCase.getPreviewParticipants(
            tournament = tournament,
            seasonId = tournament.seasonId,
            careerId = currentCareerId
        )

        val groupedParticipants = previewResult.participants.groupBy { it.qualificationType }
        val participantGroups = groupedParticipants.map { (qualType, participants) ->
            val title = getQualificationGroupTitle(qualType)
            val playerUiInfos = participants.map { participant ->
                UIHelper.resolvePlayerUiInfo(
                    context = getApplication(),
                    playerId = participant.playerId,
                    player = allPlayersMap[participant.playerId],
                    careerWithProfile = careerWithProfile,
                    seed = participant.seedNumber
                )
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

    /**
     * Liefert den angezeigten Titel für den Qualifikationstyp.
     */
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

    /**
     * Wandelt die Datenbank-Matches einer Runde in UI-Modelle um und ergänzt bei Vorrunden-Matches das Ziel-Board.
     */
    private fun buildRoundMatchUiModels(
        matches: List<TournamentMatch>,
        roundKey: String,
        event: TournamentEvent,
        careerWithProfile: CareerWithProfile,
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
                careerWithProfile = careerWithProfile,
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
        careerWithProfile: CareerWithProfile,
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
                careerWithProfile = careerWithProfile,
                allPlayersMap = allPlayersMap,
                seededPlayerMap = seededPlayerMap,
                qualR1Matches = qualR1Matches,
                qualifierBoardCount = qualifierBoardCount,
                sortPrelimByBoard = false
            )
        }
    }

    /**
     * Berechnet die Gruppentabellen für den Grand Slam of Darts.
     */
    private suspend fun calculateGrandSlamGroupStandings(
        allMatches: List<TournamentMatch>,
        careerWithProfile: CareerWithProfile,
        allPlayersMap: Map<Long, Player>,
        seasonId: Long,
        tournamentId: Long
    ): Map<String, List<GrandSlamGroupItem>> {
        if (allMatches.none { it.round == TournamentRound.GROUP.name }) return emptyMap()
        val seededPlayerMap = getSeededPlayerMap(TournamentEvent.GRAND_SLAM_OF_DARTS, seasonId, tournamentId)
        val standingsByGroup = calculateGroupStandingsUseCase(allMatches)
        return TournamentDetailMapper.mapGrandSlamGroupStandings(getApplication(), standingsByGroup, careerWithProfile, allPlayersMap, seededPlayerMap)
    }

    /**
     * Erstellt die Liste der Preisgeldverteilung für das Event.
     */
    private fun getPrizeMoneyItems(event: TournamentEvent): List<PrizeMoneyItem> {
        return TournamentDetailMapper.buildPrizeMoneyItems(getApplication(), event, getRoundSequence(event))
    }

    /**
     * Startet das aktuelle Turnier und generiert den Turnierbaum.
     */
    fun startTournament() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            try {
                generateBracketUseCase(currentTournamentId)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Turnier starten", e)
            }
        }
    }

    /**
     * Wählt eine bestimmte Runde im Turnier aus.
     */
    fun selectRound(index: Int) {
        hasUserSelectedRound = true
        savedStateHandle[KEY_SELECTED_ROUND_INDEX] = index
    }

    /**
     * Wählt eine bestimmte Gruppe aus.
     */
    fun selectGroup(index: Int) {
        savedStateHandle[KEY_SELECTED_GROUP_INDEX] = index
    }

    /**
     * Simuliert alle verbleibenden CPU-Matches der aktiven Runde.
     */
    fun simulateCpuMatches() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                completeMatchUseCase.simulateAllOpenCpuMatches(currentTournamentId)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Match simulieren", e)
            } finally {
                _isSimulatingBatch.value = false
            }
        }
    }

    /**
     * Simuliert das gesamte Turnier bis zum Finale.
     */
    fun simulateCompleteTournament() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                hasUserSelectedRound = false
                tournamentProgressionUseCase.simulateCompleteTournament(currentTournamentId)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Runde simulieren", e)
            } finally {
                _isSimulatingBatch.value = false
            }
        }
    }

    /**
     * Rückt das Turnier in die nächste Runde vor.
     */
    fun advanceRound() {
        viewModelScope.launch {
            try {
                hasUserSelectedRound = false
                advanceTournamentRoundUseCase(currentTournamentId)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Rundenwechsel", e)
            }
        }
    }

    /**
     * Setzt den Gewinner eines User-Matches mit vordefiniertem Sieger.
     */
    fun setUserMatchWinner(matchId: Long, userWins: Boolean) {
        viewModelScope.launch {
            try {
                completeMatchUseCase.completeWithPresetWinner(matchId, userWins)
            } catch (e: Exception) {
                getApplication<Application>().appContainer.reportError("Match speichern", e)
            }
        }
    }

    /**
     * Erfasst manuell das Ergebnis eines User-Matches.
     */
    suspend fun setManualUserMatchScore(matchId: Long, p1Score: Int, p2Score: Int): ManualScoreResult {
        return try {
            completeMatchUseCase.completeManualMatch(matchId, p1Score, p2Score)
        } catch (e: Exception) {
            getApplication<Application>().appContainer.reportError("Match speichern", e)
            ManualScoreResult.ValidationError(com.danny.dannyapp.domain.usecase.ScoreValidationError.UnexpectedError)
        }
    }

    /**
     * Prüft, ob für eine Auslosungsrunde (Open Draw) noch Matches ausstehen.
     */
    private fun isOpenDrawRoundPending(event: TournamentEvent, roundKey: String, matchesForRound: List<TournamentMatch>): Boolean {
        if (matchesForRound.isNotEmpty()) return false
        val round = TournamentRound.fromKey(roundKey) ?: return false
        return event.isOpenDrawRound(round)
    }

    /**
     * Ermittelt die Rundenabfolge für das angemeldete Event.
     */
    private fun getRoundSequence(event: TournamentEvent, allMatches: List<TournamentMatch> = emptyList()): List<TournamentRound> {
        return tournamentQualificationUseCase.getRoundSequence(event, allMatches)
    }

    /**
     * Ruft die Setzliste der Spieler für das Event ab.
     */
    private suspend fun getSeededPlayerMap(event: TournamentEvent, seasonId: Long, tournamentId: Long = 0L): Map<Long, Int> {
        return tournamentQualificationUseCase.getSeededPlayerMap(event, seasonId, tournamentId)
    }

    /**
     * Ermittelt die Teilnehmergruppen für Auslosungsrunden (z. B. UK Open).
     */
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
