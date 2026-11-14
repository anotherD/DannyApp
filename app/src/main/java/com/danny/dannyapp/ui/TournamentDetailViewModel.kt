package com.danny.dannyapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.SettingsManager
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.MatchDistance
import com.danny.dannyapp.domain.model.MatchFormatType
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

data class EntrantGroupInfo(
    val title: String,
    val players: List<EntrantPlayerInfo>
)

data class EntrantPlayerInfo(
    val id: Long,
    val name: String,
    val countryCode: String,
    val seed: Int?
)

enum class RoundStatus {
    COMPLETED,
    ACTIVE,
    PENDING
}

sealed interface TournamentDetailState {
    object Loading : TournamentDetailState

    data class Preview(
        val tournament: Tournament,
        val event: TournamentEvent,
        val hostCountryText: String,
        val totalPrizeFundText: String,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val isNextPlanned: Boolean,
        val careerName: String,
        val seasonNumber: Int
    ) : TournamentDetailState

    data class Active(
        val tournament: Tournament,
        val event: TournamentEvent,
        val activeRoundDisplayName: String,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>,
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
        val roundStatuses: List<RoundStatus> = emptyList()
    ) : TournamentDetailState

    data class Review(
        val tournament: Tournament,
        val event: TournamentEvent,
        val winnerName: String,
        val winnerCountryCode: String,
        val finalScoreText: String,
        val opponentName: String,
        val opponentCountryCode: String,
        val userReachedRoundText: String,
        val userEarnedPrizeMoneyText: String,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val careerName: String,
        val isQualifier: Boolean = false,
        val qualifierWinners: List<Pair<String, String>> = emptyList(),
        val seasonNumber: Int
    ) : TournamentDetailState

    data class Error(val message: String) : TournamentDetailState
}

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
    private val getOrderOfMeritUseCase = GetOrderOfMeritUseCase(application)

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

    fun loadTournament(tournamentId: Long, careerId: Long) {
        this.currentTournamentId = tournamentId
        this.currentCareerId = careerId
        hasUserSelectedRound = false

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading

            val targetTournament = tournamentDao.getTournamentById(tournamentId)
            if (targetTournament == null) {
                _uiState.value = TournamentDetailState.Error("Turnier nicht gefunden.")
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

    private fun getPrizeMoneyItems(event: TournamentEvent): List<PrizeMoneyItem> {
        val app = getApplication<Application>()
        val sequence = getRoundSequence(event).reversed()
        val winnerPrize = event.getRankingPrizeMoney(TournamentRound.WINNER)
        val winnerPrizeText = if (winnerPrize > 0) "£%,d".format(Locale.US, winnerPrize.toLong()) else "£0"
        val winnerDist = event.getMatchDistance(TournamentRound.F)
        val winnerItem = PrizeMoneyItem(
            roundName = TournamentUiHelper.getDisplayRoundName(TournamentRound.WINNER.name, app),
            matchDistanceText = formatMatchDistance(winnerDist),
            prizeMoneyText = winnerPrizeText
        )

        val roundItems = sequence.map { round ->
            val prize = event.getRankingPrizeMoney(round)
            val prizeText = if (prize > 0) "£%,d".format(Locale.US, prize.toLong()) else "£0"
            val dist = event.getMatchDistance(round)
            PrizeMoneyItem(
                roundName = TournamentUiHelper.getDisplayRoundName(round.name, app),
                matchDistanceText = formatMatchDistance(dist),
                prizeMoneyText = prizeText
            )
        }
        return listOf(winnerItem) + roundItems
    }

    private suspend fun buildPreviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allTournaments: List<Tournament>,
        careerName: String,
        seasonNumber: Int
    ) {
        val host = tournament.hostCountry ?: "ENG"
        val city = TournamentUiHelper.getTournamentCity(host, tournament.name)
        val flag = Country.getFlagEmoji(host)
        val typeBadgeInfo = TournamentUiHelper.getTournamentTypeBadgeInfo(getApplication(), tournament.type)
        val venueFormat = getApplication<Application>().getString(R.string.tournament_venue_format, city, flag)
        val hostText = "$venueFormat\n${typeBadgeInfo.text}"

        val totalPrize = if (event.totalPrizeFund > 0) {
            getApplication<Application>().getString(R.string.tournament_total_prize_fund_format, event.totalPrizeFund.toLong())
        } else {
            getApplication<Application>().getString(R.string.tournament_qualifying_no_prize)
        }

        val items = getPrizeMoneyItems(event)

        val sortedTournaments = allTournaments.sortedBy { it.order }
        val hasActiveTournament = sortedTournaments.any { t -> !t.isCompleted && tournamentMatchDao.getMatchesListForTournament(t.id).isNotEmpty() }
        val earliestPlanned = sortedTournaments.firstOrNull { t -> !t.isCompleted && tournamentMatchDao.getMatchesListForTournament(t.id).isEmpty() }
        val isNextPlanned = !hasActiveTournament && (earliestPlanned?.id == tournament.id)

        _uiState.value = TournamentDetailState.Preview(
            tournament = tournament,
            event = event,
            hostCountryText = hostText,
            totalPrizeFundText = totalPrize,
            prizeMoneyItems = items,
            isNextPlanned = isNextPlanned,
            careerName = careerName,
            seasonNumber = seasonNumber
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

        val activeRoundKeyInDb = sequence.firstOrNull { r ->
            val matches = allMatches.filter { it.round == r.name }
            matches.isNotEmpty() && matches.any { !it.isCompleted }
        }?.name ?: sequence.lastOrNull { r ->
            allMatches.any { it.round == r.name }
        }?.name ?: availableRounds.firstOrNull() ?: ""

        val activeRoundDisplayName = TournamentUiHelper.getDisplayRoundName(activeRoundKeyInDb, getApplication())

        if (selectedRoundIndex !in availableRounds.indices) {
            val activeIdx = availableRounds.indexOf(activeRoundKeyInDb)
            selectedRoundIndex = if (activeIdx != -1) activeIdx else availableRounds.lastIndex.coerceAtLeast(0)
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
            getUkOpenEntrantGroups(careerId, currentRoundKey, allMatches, event)
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
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: 5
        val userName = careerWithProfile?.playerName?.ifBlank { null } ?: careerWithProfile?.profileName ?: careerWithProfile?.career?.name ?: "Du"
        val userCountryCode = careerWithProfile?.countryCode ?: "DEU"

        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }
        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId)

        val matchUiModels = filteredMatches.map { m ->
            createMatchUiModel(m, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
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
                    val latestGen = sequence.lastOrNull { r -> newMatches.any { it.round == r.name } }
                    selectedRoundIndex = if (latestGen != null) availableRounds.indexOf(latestGen.name).coerceAtLeast(0) else 0
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

        val roundStatuses = availableRounds.map { rk ->
            val matchesInRk = allMatches.filter { it.round == rk }
            when {
                matchesInRk.isNotEmpty() && matchesInRk.all { it.isCompleted } -> RoundStatus.COMPLETED
                matchesInRk.isNotEmpty() && matchesInRk.any { !it.isCompleted } -> RoundStatus.ACTIVE
                rk == activeRoundKeyInDb -> RoundStatus.ACTIVE
                else -> RoundStatus.PENDING
            }
        }

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            activeRoundDisplayName = activeRoundDisplayName,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels,
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
            roundStatuses = roundStatuses
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

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: 5
        val userName = careerWithProfile?.playerName?.ifBlank { null } ?: careerWithProfile?.profileName ?: careerWithProfile?.career?.name ?: "Du"
        val userCountryCode = careerWithProfile?.countryCode ?: "DEU"

        val winnerId = finalMatch?.winnerId
        val (winnerName, winnerCountryCode) = if (winnerId != null) {
            if (winnerId == USER_ID) {
                Pair(userName, userCountryCode)
            } else {
                val p = allPlayersMap[winnerId]
                Pair(p?.name ?: "Unbekannt", p?.countryCode ?: "ENG")
            }
        } else Pair("Unbekannt", "ENG")

        val opponentId = if (winnerId != null && finalMatch != null) {
            if (finalMatch.player1Id == winnerId) finalMatch.player2Id else finalMatch.player1Id
        } else null

        val (opponentName, opponentCountryCode) = if (opponentId != null) {
            if (opponentId == USER_ID) {
                Pair(userName, userCountryCode)
            } else {
                val p = allPlayersMap[opponentId]
                Pair(p?.name ?: "Unbekannt", p?.countryCode ?: "ENG")
            }
        } else Pair("Unbekannt", "ENG")

        val finalScoreText = if (finalMatch != null) "${finalMatch.player1Score} : ${finalMatch.player2Score}" else "-"

        val placements = tournamentPlacementDao.getPlacementsListForTournament(tournament.id)
        val userPlacement = placements.find { it.playerId == USER_ID }
        val userReachedRoundText = if (userPlacement != null) {
            TournamentUiHelper.getDisplayRoundName(userPlacement.reachedRound, getApplication())
        } else "Nicht qualifiziert"

        val userEarnedPrizeMoneyText = if (userPlacement != null && userPlacement.prizeMoney > 0) {
            "£%,d".format(Locale.US, userPlacement.prizeMoney.toLong())
        } else "£0"

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
        } else currentRoundMatches

        val isQualifier = event in listOf(
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM
        )

        val qualifierWinners = if (isQualifier) {
            val qualFinalMatches = allMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }
            qualFinalMatches.mapNotNull { m ->
                val wId = m.winnerId ?: return@mapNotNull null
                if (wId == USER_ID) {
                    Pair(userName, userCountryCode)
                } else {
                    val p = allPlayersMap[wId]
                    Pair(p?.name ?: "CPU #${wId}", p?.countryCode ?: "ENG")
                }
            }
        } else emptyList()

        val seededPlayerMap = getSeededPlayerMap(event, tournament.seasonId)
        val matchUiModels = filteredMatches.map { m ->
            createMatchUiModel(m, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event, seededPlayerMap)
        }
        val prizeMoneyItems = getPrizeMoneyItems(event)

        _uiState.value = TournamentDetailState.Review(
            tournament = tournament,
            event = event,
            winnerName = winnerName,
            winnerCountryCode = winnerCountryCode,
            finalScoreText = finalScoreText,
            opponentName = opponentName,
            opponentCountryCode = opponentCountryCode,
            userReachedRoundText = userReachedRoundText,
            userEarnedPrizeMoneyText = userEarnedPrizeMoneyText,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels,
            prizeMoneyItems = prizeMoneyItems,
            careerName = careerName,
            isQualifier = isQualifier,
            qualifierWinners = qualifierWinners,
            seasonNumber = seasonNumber
        )
    }

    private fun createMatchUiModel(
        m: TournamentMatch,
        userName: String,
        userCountryCode: String,
        userLevel: Int,
        allPlayersMap: Map<Long, com.danny.dannyapp.data.db.Player>,
        roundKey: String,
        event: TournamentEvent,
        seededPlayerMap: Map<Long, Int> = emptyMap()
    ): MatchUiModel {
        val app = getApplication<Application>()
        val isP1User = (m.player1Id == USER_ID)
        val isP2User = (m.player2Id == USER_ID)
        val isUserMatch = isP1User || isP2User

        val (p1Name, p1Flag) = when (m.player1Id) {
            USER_ID -> Pair(userName, Country.getFlagEmoji(userCountryCode))
            -998L -> Pair(app.getString(R.string.match_tbd_open_draw), "")
            -999L -> {
                if (event == TournamentEvent.EUROPEAN_TOUR && roundKey == TournamentRound.L32.name) {
                    Pair("Top 16 Seed #${m.matchIndex + 1}", "")
                } else {
                    Pair(app.getString(R.string.tournament_placeholder_winner, 2 * m.matchIndex + 1), "")
                }
            }
            else -> {
                val p = allPlayersMap[m.player1Id]
                Pair(p?.name ?: "CPU #${m.player1Id}", Country.getFlagEmoji(p?.countryCode ?: "ENG"))
            }
        }
        val p1Seed = if (m.player1Id != -998L && m.player1Id != -999L) seededPlayerMap[m.player1Id] else null

        val p1CountryCode = when (m.player1Id) {
            USER_ID -> userCountryCode
            -998L, -999L -> "ENG"
            else -> allPlayersMap[m.player1Id]?.countryCode ?: "ENG"
        }
        val (p2Name, p2Flag) = when (m.player2Id) {
            USER_ID -> Pair(userName, Country.getFlagEmoji(userCountryCode))
            -998L -> Pair(app.getString(R.string.match_tbd_open_draw), "")
            -999L -> {
                if (event == TournamentEvent.EUROPEAN_TOUR && roundKey == TournamentRound.L32.name) {
                    Pair("Sieger Match #${16 - m.matchIndex}", "")
                } else {
                    Pair(app.getString(R.string.tournament_placeholder_winner, 2 * m.matchIndex + 2), "")
                }
            }
            else -> {
                val p = allPlayersMap[m.player2Id]
                Pair(p?.name ?: "CPU #${m.player2Id}", Country.getFlagEmoji(p?.countryCode ?: "ENG"))
            }
        }
        val p2Seed = if (m.player2Id != -998L && m.player2Id != -999L) seededPlayerMap[m.player2Id] else null

        val p2CountryCode = when (m.player2Id) {
            USER_ID -> userCountryCode
            -998L, -999L -> "ENG"
            else -> allPlayersMap[m.player2Id]?.countryCode ?: "ENG"
        }

        val p1Skill = if (isP1User) userLevel else allPlayersMap[m.player1Id]?.skill ?: 5
        val p2Skill = if (isP2User) userLevel else allPlayersMap[m.player2Id]?.skill ?: 5

        val scoreText = if (m.isCompleted) "${m.player1Score} : ${m.player2Score}" else "- : -"

        val roundDisplayName = TournamentUiHelper.getDisplayRoundName(roundKey, app)

        return MatchUiModel(
            match = m,
            player1Name = p1Name,
            player1Seed = p1Seed,
            player1Flag = p1Flag,
            player1CountryCode = p1CountryCode,
            player1Skill = p1Skill,
            player2Name = p2Name,
            player2Seed = p2Seed,
            player2Flag = p2Flag,
            player2CountryCode = p2CountryCode,
            player2Skill = p2Skill,
            isUserMatch = isUserMatch,
            isCompleted = m.isCompleted,
            winnerId = m.winnerId,
            scoreText = scoreText,
            roundDisplayName = roundDisplayName
        )
    }

    private fun generatePreviewMatchesForRound(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent
    ): List<TournamentMatch> {
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { TournamentRound.F }
        val matchCount = when (roundEnum) {
            TournamentRound.F -> 1
            TournamentRound.SF -> 2
            TournamentRound.QF -> 4
            TournamentRound.L16 -> 8
            TournamentRound.L32 -> 16
            TournamentRound.L64 -> 32
            TournamentRound.L128 -> 64
            TournamentRound.L48 -> 24
            TournamentRound.R1_160, TournamentRound.R2_128, TournamentRound.R3_96 -> 32
            TournamentRound.GROUP -> 16
            TournamentRound.QUAL_FINAL -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 10
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                TournamentEvent.QUALIFIER_WM -> 4
                else -> 4
            }
            TournamentRound.QUAL_R2 -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 20
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 16
                TournamentEvent.QUALIFIER_WM -> 8
                else -> 8
            }
            TournamentRound.QUAL_R1 -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 40
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 32
                TournamentEvent.QUALIFIER_WM -> 16
                else -> 16
            }
            TournamentRound.QUAL_PRELIM -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 16
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 32
                TournamentEvent.QUALIFIER_WM -> 16
                else -> 16
            }
            else -> 1
        }

        val isNoFixedTree = event in listOf(
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM
        ) || ((event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) &&
                roundEnum in listOf(
                    TournamentRound.R2_128,
                    TournamentRound.R3_96,
                    TournamentRound.L64,
                    TournamentRound.L32,
                    TournamentRound.L16,
                    TournamentRound.QF,
                    TournamentRound.SF,
                    TournamentRound.F
                ))

        val placeholderPlayerId = if (isNoFixedTree) -998L else -999L

        return (0 until matchCount).map { i ->
            TournamentMatch(
                id = -2000L - (roundEnum.ordinal * 100) - i,
                tournamentId = tournamentId,
                round = roundKey,
                matchIndex = i,
                player1Id = placeholderPlayerId,
                player2Id = placeholderPlayerId,
                groupName = if (roundEnum == TournamentRound.GROUP) ('A' + (i % 16)).toString() else null,
                isCompleted = false
            )
        }
    }

    fun startTournament() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            generateBracketUseCase(currentTournamentId)
        }
    }

    fun selectRound(index: Int) {
        hasUserSelectedRound = true
        selectedRoundIndex = index
        selectedRoundIndexFlow.value = index
    }

    fun selectGroup(index: Int) {
        selectedGroupIndex = index
        selectedGroupIndexFlow.value = index
    }

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

    fun simulateCompleteTournament() {
        viewModelScope.launch {
            _isSimulatingBatch.value = true
            try {
                while (true) {
                    val tournament = tournamentDao.getTournamentById(currentTournamentId) ?: break
                    if (tournament.isCompleted) break

                    val matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                    if (matches.isEmpty()) break

                    val uncompleted = matches.filter { !it.isCompleted }
                    if (uncompleted.isNotEmpty()) {
                        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
                        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: 5
                        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }

                        for (match in uncompleted) {
                            val event = TournamentEvent.valueOf(tournament.type)
                            val round = TournamentRound.valueOf(match.round)
                            val dist = event.getMatchDistance(round)

                            if (match.player1Id == USER_ID || match.player2Id == USER_ID) {
                                val isP1User = (match.player1Id == USER_ID)
                                val oppId = if (isP1User) match.player2Id else match.player1Id
                                val oppPlayer = allPlayersMap[oppId]
                                val oppSkill = (oppPlayer?.skill ?: 5).coerceIn(1, 10)

                                val p1Skill = if (isP1User) userLevel else oppSkill
                                val p2Skill = if (isP1User) oppSkill else userLevel

                                val p1WinProb = p1Skill.toDouble() / (p1Skill + p2Skill)
                                val userWins = Random.nextDouble() < p1WinProb

                                val (p1Score, p2Score, winnerId) = generateScoresForWinner(
                                    isP1Winner = userWins,
                                    player1Id = match.player1Id,
                                    player2Id = match.player2Id,
                                    dist = dist
                                )

                                val updated = match.copy(
                                    player1Score = p1Score,
                                    player2Score = p2Score,
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
                    val newIdx = availableRounds.indexOf(latestRound.name)
                    if (newIdx != -1) {
                        selectedRoundIndex = newIdx
                        selectedRoundIndexFlow.value = newIdx
                    }
                }
            }
        }
    }

    fun simulateUserMatch(matchId: Long) {
        viewModelScope.launch {
            val match = tournamentMatchDao.getMatchById(matchId) ?: return@launch
            if (match.isCompleted) return@launch

            val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@launch
            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)
            val dist = event.getMatchDistance(round)

            val careerWithProfile = careerRepository.getCareerById(currentCareerId)
            val userSkill = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: 5

            val isP1User = (match.player1Id == USER_ID)
            val oppId = if (isP1User) match.player2Id else match.player1Id
            val oppPlayer = playerDao.getPlayerById(oppId)
            val oppSkill = (oppPlayer?.skill ?: 5).coerceIn(1, 10)

            val p1Skill = if (isP1User) userSkill else oppSkill
            val p2Skill = if (isP1User) oppSkill else userSkill

            val p1WinProb = p1Skill.toDouble() / (p1Skill + p2Skill)
            val userWins = Random.nextDouble() < p1WinProb

            val (p1Score, p2Score, winnerId) = generateScoresForWinner(
                isP1Winner = userWins,
                player1Id = match.player1Id,
                player2Id = match.player2Id,
                dist = dist
            )

            val updated = match.copy(
                player1Score = p1Score,
                player2Score = p2Score,
                winnerId = winnerId,
                isCompleted = true
            )

            tournamentMatchDao.updateMatch(updated)
        }
    }

    fun setUserMatchWinner(matchId: Long, userWins: Boolean) {
        viewModelScope.launch {
            val match = tournamentMatchDao.getMatchById(matchId) ?: return@launch
            if (match.isCompleted) return@launch

            val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@launch
            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)
            val dist = event.getMatchDistance(round)

            val isP1User = (match.player1Id == USER_ID)
            val isP1Winner = if (isP1User) userWins else !userWins

            val (p1Score, p2Score, winnerId) = generateScoresForWinner(
                isP1Winner = isP1Winner,
                player1Id = match.player1Id,
                player2Id = match.player2Id,
                dist = dist
            )

            val updated = match.copy(
                player1Score = p1Score,
                player2Score = p2Score,
                winnerId = winnerId,
                isCompleted = true
            )

            tournamentMatchDao.updateMatch(updated)
        }
    }

    suspend fun setManualUserMatchScore(matchId: Long, p1Score: Int, p2Score: Int): String? {
        val match = tournamentMatchDao.getMatchById(matchId) ?: return "Match nicht gefunden."
        if (match.isCompleted) return "Match ist bereits beendet."

        val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return "Turnier nicht gefunden."
        val event = TournamentEvent.valueOf(tournament.type)
        val round = TournamentRound.valueOf(match.round)
        val dist = event.getMatchDistance(round)

        val validationError = validateMatchScore(p1Score, p2Score, dist)
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

    private fun generateScoresForWinner(
        isP1Winner: Boolean,
        player1Id: Long,
        player2Id: Long,
        dist: MatchDistance
    ): Triple<Int, Int, Long> {
        val target = (dist.bestOf / 2) + 1
        val loserScore = Random.nextInt(0, target)
        var winnerScore = target

        if (dist.format == MatchFormatType.LEGS && dist.twoClearLegsRule && loserScore == target - 1) {
            winnerScore = target + 1
        }

        val p1Score = if (isP1Winner) winnerScore else loserScore
        val p2Score = if (isP1Winner) loserScore else winnerScore
        val winnerId = if (isP1Winner) player1Id else player2Id

        return Triple(p1Score, p2Score, winnerId)
    }

    fun validateMatchScore(p1Score: Int, p2Score: Int, matchDistance: MatchDistance): String? {
        if (p1Score < 0 || p2Score < 0) return "Scores dürfen nicht negativ sein."
        if (p1Score == p2Score) return "Es darf kein Unentschieden geben."

        val target = (matchDistance.bestOf / 2) + 1
        val winScore = maxOf(p1Score, p2Score)
        val loseScore = minOf(p1Score, p2Score)

        if (matchDistance.format == MatchFormatType.LEGS) {
            val maxLegs = target + 3
            if (matchDistance.twoClearLegsRule) {
                val isValid = (winScore >= target && winScore - loseScore >= 2) || (winScore == maxLegs && winScore - loseScore >= 1)
                if (!isValid) {
                    return "Ungültiges Leg-Ergebnis: Sieger benötigt $target Legs (mit 2 Legs Vorsprung) oder Tie-Break ($maxLegs:${maxLegs-1})."
                }
            } else {
                if (winScore != target || loseScore >= target) {
                    return "Ungültiges Leg-Ergebnis für Best-of-${matchDistance.bestOf}: Sieger muss genau $target Legs haben."
                }
            }
        } else {
            if (winScore != target || loseScore >= target) {
                return "Ungültiges Set-Ergebnis für Best-of-${matchDistance.bestOf} Sets: Sieger muss genau $target Sets erreichen."
            }
        }
        return null
    }

    private fun formatMatchDistance(dist: MatchDistance): String {
        val formatText = when (dist.format) {
            MatchFormatType.LEGS -> "Legs"
            MatchFormatType.SETS_BEST_OF_5_LEGS -> "Sets (5 Legs/Set)"
            MatchFormatType.SETS_BEST_OF_3_LEGS -> "Sets (3 Legs/Set)"
            MatchFormatType.SETS_DOUBLE_IN_OUT -> "Sets (Double In/Out)"
        }
        val clearText = if (dist.twoClearLegsRule) " (2 Legs Vorsprung)" else ""
        return "Best of ${dist.bestOf} $formatText$clearText"
    }

    private fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        val fullSequence = when (event) {
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_FINAL
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> listOf(
                TournamentRound.L128, TournamentRound.L64, TournamentRound.L32,
                TournamentRound.L16, TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.EUROPEAN_TOUR -> listOf(
                TournamentRound.L48, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> listOf(
                TournamentRound.R1_160, TournamentRound.R2_128, TournamentRound.R3_96,
                TournamentRound.L64, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.WORLD_MATCHPLAY, TournamentEvent.WORLD_GRAND_PRIX, TournamentEvent.EUROPEAN_CHAMPIONSHIP -> listOf(
                TournamentRound.L32, TournamentRound.L16, TournamentRound.QF,
                TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.GRAND_SLAM_OF_DARTS -> listOf(
                TournamentRound.GROUP, TournamentRound.L16, TournamentRound.QF,
                TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> listOf(
                TournamentRound.L64, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            TournamentEvent.WORLD_CHAMPIONSHIP -> listOf(
                TournamentRound.L128, TournamentRound.L64, TournamentRound.L32,
                TournamentRound.L16, TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            else -> listOf(TournamentRound.L32, TournamentRound.L16)
        }

        if (allMatches.isEmpty()) return fullSequence

        val startIndex = allMatches.mapNotNull { m ->
            fullSequence.indexOfFirst { it.name == m.round }.takeIf { it >= 0 }
        }.minOrNull() ?: 0

        return fullSequence.subList(startIndex, fullSequence.size)
    }

    private suspend fun getSeededPlayerMap(event: TournamentEvent, seasonId: Long): Map<Long, Int> {
        val season = seasonDao.getSeasonById(seasonId) ?: return emptyMap()
        val careerId = season.careerId

        val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
        val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        fun getPlayerListSortedByOom(playerIds: List<Long>, oomList: List<OrderOfMeritEntry>): List<Long> {
            val playerMap = oomList.associateBy { it.playerId }
            val dbPlayerMap = allPlayers.associateBy { it.id }
            return playerIds.sortedWith(
                compareBy<Long> { playerMap[it]?.rank ?: Int.MAX_VALUE }
                    .thenByDescending { playerMap[it]?.prizeMoney ?: 0.0 }
                    .thenByDescending { if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5 }
                    .thenBy { if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: "" }
            )
        }

        fun fillToTargetCount(
            initialList: List<Long>,
            targetCount: Int,
            fallbackCandidates: List<Long>,
            oomList: List<OrderOfMeritEntry>
        ): List<Long> {
            if (initialList.size >= targetCount) {
                return initialList.take(targetCount)
            }
            val result = initialList.toMutableList()
            val oomMap = oomList.associateBy { it.playerId }
            val remainingCandidates = fallbackCandidates
                .filterNot { it in result }
                .sortedWith(
                    compareBy<Long> { oomMap[it]?.rank ?: Int.MAX_VALUE }
                        .thenByDescending { oomMap[it]?.prizeMoney ?: 0.0 }
                        .thenByDescending { oomMap[it]?.skill ?: 5 }
                )

            for (candidate in remainingCandidates) {
                if (result.size >= targetCount) break
                result.add(candidate)
            }
            return result
        }

        return when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedByPt = getPlayerListSortedByOom(tcIds, ptOomEntries)
                sortedByPt.take(32).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.EUROPEAN_TOUR -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top16Oom = sortedOomTc.take(16)
                val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
                val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries).take(16)
                val map = mutableMapOf<Long, Int>()
                top16Oom.forEachIndexed { idx, id -> map[id] = idx + 1 }
                top16PtOom.forEachIndexed { idx, id -> map[id] = idx + 1 }
                map
            }
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> emptyMap()
            TournamentEvent.WORLD_MATCHPLAY -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.WORLD_GRAND_PRIX -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val top32Et = getPlayerListSortedByOom(allPlayerIds, etOomEntries).take(32)
                val final32Et = fillToTargetCount(top32Et, 32, allPlayerIds, oomEntries)
                final32Et.mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val top64Pc = getPlayerListSortedByOom(allPlayerIds, pcOomEntries).take(64)
                val final64Pc = fillToTargetCount(top64Pc, 64, allPlayerIds, oomEntries)
                final64Pc.mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top40Oom = sortedOomTc.take(40)
                val remainingForPt = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(40)
                val tc80 = top40Oom + top40Pt
                val sortedTc80ByOom = getPlayerListSortedByOom(tc80, oomEntries)
                sortedTc80ByOom.take(32).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val preqSet = when (event) {
                    TournamentEvent.QUALIFIER_ET -> {
                        val top16Oom = sortedOomTc.take(16)
                        val rem = tourCardPlayerIds.filterNot { it in top16Oom }
                        (top16Oom + getPlayerListSortedByOom(rem, ptOomEntries).take(16)).toSet()
                    }
                    TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                        val top16Oom = sortedOomTc.take(16)
                        val rem = tourCardPlayerIds.filterNot { it in top16Oom }
                        (top16Oom + getPlayerListSortedByOom(rem, ptOomEntries).take(16)).toSet()
                    }
                    TournamentEvent.QUALIFIER_WM -> {
                        val top40Oom = sortedOomTc.take(40)
                        val rem = tourCardPlayerIds.filterNot { it in top40Oom }
                        (top40Oom + getPlayerListSortedByOom(rem, ptOomEntries).take(40)).toSet()
                    }
                    else -> emptySet()
                }
                val nonPreq = tourCardPlayerIds.filterNot { it in preqSet }
                val sortedNonPreq = getPlayerListSortedByOom(nonPreq, oomEntries)
                val K = when (event) {
                    TournamentEvent.QUALIFIER_ET -> 10
                    TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                    TournamentEvent.QUALIFIER_WM -> 4
                    else -> 4
                }
                val N = sortedNonPreq.size
                val target = when {
                    N > 8 * K -> 8 * K
                    N > 4 * K -> 4 * K
                    N > 2 * K -> 2 * K
                    else -> K
                }
                val seedCount = if (N > target) (2 * target - N).coerceAtLeast(K) else K
                sortedNonPreq.take(seedCount).mapIndexed { idx, id -> id to (idx + 1) }.toMap()
            }
        }
    }

    private suspend fun generatePreviewMatchesWithPropagation(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long
    ): List<TournamentMatch> {
        val baseMatches = generatePreviewMatchesForRound(tournamentId, roundKey, event)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return baseMatches }

        if (event in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
            val K = when (event) {
                TournamentEvent.QUALIFIER_ET -> 10
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                TournamentEvent.QUALIFIER_WM -> 4
                else -> 4
            }
            val season = seasonDao.getSeasonById(seasonId)
            val careerId = season?.careerId ?: currentCareerId
            val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
            val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
            val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
            val allPlayers = playerDao.getAllPlayersList()
            val nonPreqTc = getNonPrequalifiedTcHoldersForQualifier(event, tourCardPlayerIds, oomEntries, ptOomEntries, allPlayers)
            val N = nonPreqTc.size

            when (roundEnum) {
                TournamentRound.QUAL_R1 -> {
                    val byeCount = (16 * K - N).coerceAtLeast(0)
                    val byePlayers = nonPreqTc.take(byeCount)
                    val prelimMatches = allMatches.filter { it.round == TournamentRound.QUAL_PRELIM.name }.sortedBy { it.matchIndex }
                    val prelimWinners = prelimMatches.map { it.winnerId ?: -999L }
                    val mainFieldPlayers = byePlayers + prelimWinners

                    val boardPlayers = mutableMapOf<Int, MutableList<Long>>()
                    for (b in 0 until K) { boardPlayers[b] = mutableListOf() }
                    mainFieldPlayers.forEachIndexed { index, id ->
                        val boardIdx = index % K
                        boardPlayers[boardIdx]?.add(id)
                    }

                    val r1Matches = mutableListOf<TournamentMatch>()
                    for (b in 0 until K) {
                        val list = boardPlayers[b] ?: mutableListOf()
                        val seedId = nonPreqTc.getOrNull(b)
                        if (seedId != null && seedId in list && list.first() != seedId) {
                            list.remove(seedId)
                            list.add(0, seedId)
                        }
                        for (mIdx in 0 until 4) {
                            val p1 = list.getOrNull(2 * mIdx) ?: -999L
                            val p2 = list.getOrNull(2 * mIdx + 1) ?: -999L
                            r1Matches.add(
                                TournamentMatch(
                                    id = -3000L - (b * 10) - mIdx,
                                    tournamentId = tournamentId,
                                    round = roundKey,
                                    matchIndex = 4 * b + mIdx,
                                    player1Id = p1,
                                    player2Id = p2
                                )
                            )
                        }
                    }
                    return r1Matches
                }
                TournamentRound.QUAL_R2 -> {
                    val r1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }.sortedBy { it.matchIndex }
                    val r2Matches = mutableListOf<TournamentMatch>()
                    for (b in 0 until K) {
                        val boardMatches = r1Matches.filter { it.matchIndex in (4 * b)..(4 * b + 3) }.sortedBy { it.matchIndex }
                        val w0 = boardMatches.getOrNull(0)?.winnerId ?: -999L
                        val w1 = boardMatches.getOrNull(1)?.winnerId ?: -999L
                        val w2 = boardMatches.getOrNull(2)?.winnerId ?: -999L
                        val w3 = boardMatches.getOrNull(3)?.winnerId ?: -999L

                        r2Matches.add(TournamentMatch(id = -4000L - (b * 10), tournamentId = tournamentId, round = roundKey, matchIndex = 2 * b, player1Id = w0, player2Id = w1))
                        r2Matches.add(TournamentMatch(id = -4000L - (b * 10) + 1, tournamentId = tournamentId, round = roundKey, matchIndex = 2 * b + 1, player1Id = w2, player2Id = w3))
                    }
                    return r2Matches
                }
                TournamentRound.QUAL_FINAL -> {
                    val r2Matches = allMatches.filter { it.round == TournamentRound.QUAL_R2.name }.sortedBy { it.matchIndex }
                    val finalMatches = mutableListOf<TournamentMatch>()
                    for (b in 0 until K) {
                        val boardMatches = r2Matches.filter { it.matchIndex in (2 * b)..(2 * b + 1) }.sortedBy { it.matchIndex }
                        val w0 = boardMatches.getOrNull(0)?.winnerId ?: -999L
                        val w1 = boardMatches.getOrNull(1)?.winnerId ?: -999L

                        finalMatches.add(TournamentMatch(id = -5000L - b, tournamentId = tournamentId, round = roundKey, matchIndex = b, player1Id = w0, player2Id = w1))
                    }
                    return finalMatches
                }
                else -> {}
            }
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && roundEnum == TournamentRound.L32) {
            val season = seasonDao.getSeasonById(seasonId)
            val careerId = season?.careerId ?: currentCareerId
            val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
            val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
            val allPlayers = playerDao.getAllPlayersList()
            val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
            val top16Oom = sortedOomTc.take(16)
            val l48Matches = allMatches.filter { it.round == TournamentRound.L48.name }.sortedBy { it.matchIndex }

            return baseMatches.map { m ->
                val s1 = top16Oom.getOrNull(m.matchIndex) ?: -999L
                val l48Match = l48Matches.getOrNull(15 - m.matchIndex)
                val s2 = if (l48Match?.isCompleted == true && l48Match.winnerId != null) l48Match.winnerId!! else -999L
                m.copy(player1Id = s1, player2Id = s2)
            }
        }

        val sequence = getRoundSequence(event)
        val roundIdx = sequence.indexOf(roundEnum)
        if (roundIdx <= 0) {
            return baseMatches
        }

        val prevRoundEnum = sequence[roundIdx - 1]
        val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
        if (prevMatches.isEmpty()) return baseMatches

        return baseMatches.map { m ->
            val p1Match = prevMatches.getOrNull(2 * m.matchIndex)
            val p2Match = prevMatches.getOrNull(2 * m.matchIndex + 1)
            val p1 = if (p1Match?.isCompleted == true && p1Match.winnerId != null) p1Match.winnerId!! else -999L
            val p2 = if (p2Match?.isCompleted == true && p2Match.winnerId != null) p2Match.winnerId!! else -999L
            m.copy(player1Id = p1, player2Id = p2)
        }
    }

    private suspend fun getUkOpenEntrantGroups(
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent
    ): List<EntrantGroupInfo> {
        val app = getApplication<Application>()
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()
        val sortedPlayerIds = getPlayerListSortedByOom(allPlayerIds, oomEntries)
        val allPlayersMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userName = careerWithProfile?.playerName?.ifBlank { null } ?: careerWithProfile?.profileName ?: careerWithProfile?.career?.name ?: "Du"
        val userCountryCode = careerWithProfile?.countryCode ?: "DEU"

        fun createEntrantPlayerInfo(id: Long, rank: Int?): EntrantPlayerInfo {
            val name = if (id == USER_ID) userName else allPlayersMap[id]?.name ?: "CPU #$id"
            val country = if (id == USER_ID) userCountryCode else allPlayersMap[id]?.countryCode ?: "ENG"
            return EntrantPlayerInfo(id = id, name = name, countryCode = country, seed = rank)
        }

        val sequence = getRoundSequence(event, allMatches)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return emptyList() }
        val roundIdx = sequence.indexOf(roundEnum)

        val groups = mutableListOf<EntrantGroupInfo>()

        when (roundKey) {
            TournamentRound.R2_128.name -> {
                val players = sortedPlayerIds.drop(64).take(32).mapIndexed { idx: Int, id: Long -> createEntrantPlayerInfo(id, 65 + idx) }
                groups.add(EntrantGroupInfo(title = app.getString(R.string.entrant_reason_oom_65_96), players = players))
            }
            TournamentRound.R3_96.name -> {
                val players = sortedPlayerIds.drop(32).take(32).mapIndexed { idx: Int, id: Long -> createEntrantPlayerInfo(id, 33 + idx) }
                groups.add(EntrantGroupInfo(title = app.getString(R.string.entrant_reason_oom_33_64), players = players))
            }
            TournamentRound.L64.name -> {
                val players = sortedPlayerIds.take(32).mapIndexed { idx: Int, id: Long -> createEntrantPlayerInfo(id, 1 + idx) }
                groups.add(EntrantGroupInfo(title = app.getString(R.string.entrant_reason_oom_top_32), players = players))
            }
        }

        if (roundIdx > 0) {
            val prevRoundEnum = sequence[roundIdx - 1]
            val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
            if (prevMatches.isNotEmpty() && prevMatches.any { it.isCompleted }) {
                val winnerIds = prevMatches.mapNotNull { if (it.isCompleted) it.winnerId else null }
                if (winnerIds.isNotEmpty()) {
                    val prevRoundDisplayName = TournamentUiHelper.getDisplayRoundName(prevRoundEnum.name, app)
                    val winnerPlayers = winnerIds.map { id -> createEntrantPlayerInfo(id, null) }
                    groups.add(EntrantGroupInfo(title = app.getString(R.string.entrant_reason_round_winners, prevRoundDisplayName), players = winnerPlayers))
                }
            }
        }

        return groups
    }

    private suspend fun getNonPrequalifiedTcHoldersForQualifier(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<com.danny.dannyapp.data.db.Player>
    ): List<Long> {
        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomList)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomList).take(16)

        val preQualified: Set<Long> = when (event) {
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM -> (top16Oom + top16PtOom).toSet()
            TournamentEvent.QUALIFIER_WM -> {
                val top40Oom = sortedOomTc.take(40)
                val remainingAfterTop40Oom = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40PtOom = getPlayerListSortedByOom(remainingAfterTop40Oom, ptOomList).take(40)
                (top40Oom + top40PtOom).toSet()
            }
            else -> emptySet()
        }

        val nonPreq = tourCardPlayerIds.filterNot { it in preQualified }
        return getPlayerListSortedByOom(nonPreq, oomList)
    }

    private suspend fun getPlayerListSortedByOom(
        playerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>
    ): List<Long> {
        val allPlayers = playerDao.getAllPlayersList()
        val playerMap = oomList.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }
        return playerIds.sortedWith(
            compareBy<Long> { playerMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { playerMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending {
                    if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5
                }
                .thenBy {
                    if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                }
        )
    }
}
