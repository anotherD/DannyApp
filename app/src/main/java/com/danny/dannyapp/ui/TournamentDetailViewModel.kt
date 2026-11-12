package com.danny.dannyapp.ui

import android.app.Application
import android.content.Context
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
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

sealed interface TournamentDetailState {
    object Loading : TournamentDetailState

    data class Preview(
        val tournament: Tournament,
        val event: TournamentEvent,
        val hostCountryText: String,
        val totalPrizeFundText: String,
        val prizeMoneyItems: List<PrizeMoneyItem>,
        val isNextPlanned: Boolean
    ) : TournamentDetailState

    data class Active(
        val tournament: Tournament,
        val event: TournamentEvent,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>,
        val autoSimulateCpu: Boolean,
        val autoAdvanceRound: Boolean,
        val isCurrentRoundCompleted: Boolean,
        val userLevel: Int
    ) : TournamentDetailState

    data class Review(
        val tournament: Tournament,
        val event: TournamentEvent,
        val winnerName: String,
        val winnerFlag: String,
        val finalScoreText: String,
        val opponentName: String,
        val opponentFlag: String,
        val userReachedRoundText: String,
        val userEarnedPrizeMoneyText: String,
        val availableRounds: List<String>,
        val availableRoundDisplayNames: List<String>,
        val selectedRoundIndex: Int,
        val availableGroups: List<String>,
        val selectedGroupIndex: Int,
        val matches: List<MatchUiModel>
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
    private val seasonDao = db.seasonDao()
    private val careerRepository = CareerRepository(application)

    private val generateBracketUseCase = GenerateBracketUseCase(application)
    private val simulateCpuMatchUseCase = SimulateCpuMatchUseCase(application)
    private val advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(application)

    private val _uiState = MutableStateFlow<TournamentDetailState>(TournamentDetailState.Loading)
    val uiState: StateFlow<TournamentDetailState> = _uiState.asStateFlow()

    private var currentTournamentId: Long = -1L
    private var currentCareerId: Long = -1L

    private var selectedRoundIndex: Int = 0
    private var selectedGroupIndex: Int = 0

    val autoSimulateCpu: Boolean
        get() = settingsManager.autoSimulateCpu

    val autoAdvanceRound: Boolean
        get() = settingsManager.autoAdvanceRound

    fun loadTournament(tournamentId: Long, careerId: Long) {
        this.currentTournamentId = tournamentId
        this.currentCareerId = careerId

        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            refreshState()
        }
    }

    private suspend fun refreshState() = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(currentTournamentId)
        if (tournament == null) {
            _uiState.value = TournamentDetailState.Error("Turnier nicht gefunden.")
            return@withContext
        }

        val event = try {
            TournamentEvent.valueOf(tournament.type)
        } catch (e: Exception) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP
        }

        val matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)

        if (tournament.isCompleted) {
            buildReviewState(tournament, event, matches)
        } else if (matches.isEmpty()) {
            buildPreviewState(tournament, event)
        } else {
            buildActiveState(tournament, event, matches)
        }
    }

    private suspend fun buildPreviewState(tournament: Tournament, event: TournamentEvent) {
        val host = tournament.hostCountry ?: "ENG"
        val flag = Country.getFlagEmoji(host)
        val hostText = "Austragungsland: $host $flag • Typ: ${tournament.type}"

        val totalPrize = if (event.totalPrizeFund > 0) {
            "Gesamtpreisgeld: £%,d".format(Locale.US, event.totalPrizeFund.toLong())
        } else {
            "Qualifikations-Turnier (kein Ranglisten-Preisgeld)"
        }

        val sequence = getRoundSequence(event)
        val items = sequence.map { round ->
            val prize = event.getRankingPrizeMoney(round)
            val prizeText = if (prize > 0) "£%,d".format(Locale.US, prize.toLong()) else "£0"
            val dist = event.getMatchDistance(round)
            PrizeMoneyItem(
                roundName = round.displayName,
                matchDistanceText = formatMatchDistance(dist),
                prizeMoneyText = prizeText
            )
        }

        val allTournaments = tournamentDao.getTournamentsListForSeason(tournament.seasonId).sortedBy { it.order }
        val earliestPlanned = allTournaments.firstOrNull { !it.isCompleted && tournamentMatchDao.getMatchesListForTournament(it.id).isEmpty() }
        val isNextPlanned = earliestPlanned?.id == tournament.id

        _uiState.value = TournamentDetailState.Preview(
            tournament = tournament,
            event = event,
            hostCountryText = hostText,
            totalPrizeFundText = totalPrize,
            prizeMoneyItems = items,
            isNextPlanned = isNextPlanned
        )
    }

    private suspend fun buildActiveState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>
    ) {
        val sequence = getRoundSequence(event)
        val availableRounds = sequence.map { it.name }

        val activeRoundKeyInDb = sequence.firstOrNull { r ->
            val matches = allMatches.filter { it.round == r.name }
            matches.isNotEmpty() && matches.any { !it.isCompleted }
        }?.name ?: sequence.lastOrNull { r ->
            allMatches.any { it.round == r.name }
        }?.name ?: availableRounds.firstOrNull() ?: ""

        if (selectedRoundIndex !in availableRounds.indices) {
            val activeIdx = availableRounds.indexOf(activeRoundKeyInDb)
            selectedRoundIndex = if (activeIdx != -1) activeIdx else availableRounds.lastIndex.coerceAtLeast(0)
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndex) ?: ""
        val availableRoundDisplayNames = availableRounds.map { getDisplayRoundName(it) }

        val dbMatchesForRound = allMatches.filter { it.round == currentRoundKey }
        val currentRoundMatches = if (dbMatchesForRound.isNotEmpty()) {
            dbMatchesForRound
        } else {
            generatePreviewMatchesForRound(currentTournamentId, currentRoundKey, event)
        }

        val isGrandSlamGroup = (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRoundKey == TournamentRound.GROUP.name)
        val availableGroups = if (isGrandSlamGroup) {
            ('A'..'P').map { it.toString() }
        } else {
            emptyList()
        }

        if (isGrandSlamGroup && selectedGroupIndex !in availableGroups.indices) {
            selectedGroupIndex = 0
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

        val matchUiModels = filteredMatches.map { m ->
            createMatchUiModel(m, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event)
        }

        val isCurrentRoundCompleted = dbMatchesForRound.isNotEmpty() && dbMatchesForRound.all { it.isCompleted }

        // Trigger Auto-Simulate if needed
        var stateNeedsRefresh = false
        if (autoSimulateCpu && dbMatchesForRound.any { !it.isCompleted && it.player1Id != USER_ID && it.player2Id != USER_ID }) {
            simulateCpuMatchUseCase.simulateAllCpuMatches(currentTournamentId)
            stateNeedsRefresh = true
        }

        // Trigger Auto-Advance if all matches in current round are completed
        val recheckedMatches = if (stateNeedsRefresh) tournamentMatchDao.getMatchesListForTournament(currentTournamentId) else allMatches
        val recheckedRoundMatches = recheckedMatches.filter { it.round == currentRoundKey }
        if (autoAdvanceRound && recheckedRoundMatches.isNotEmpty() && recheckedRoundMatches.all { it.isCompleted }) {
            val advanced = advanceTournamentRoundUseCase(currentTournamentId)
            if (advanced) {
                val reloadedTournament = tournamentDao.getTournamentById(currentTournamentId)
                val newMatches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                if (reloadedTournament?.isCompleted == true) {
                    buildReviewState(reloadedTournament, event, newMatches)
                    return
                } else {
                    val latestGen = sequence.lastOrNull { r -> newMatches.any { it.round == r.name } }
                    selectedRoundIndex = if (latestGen != null) availableRounds.indexOf(latestGen.name).coerceAtLeast(0) else 0
                    buildActiveState(reloadedTournament ?: tournament, event, newMatches)
                    return
                }
            }
        }

        if (stateNeedsRefresh) {
            val updatedMatches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
            val updatedRoundMatches = updatedMatches.filter { it.round == currentRoundKey }
            val updatedFiltered = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
                updatedRoundMatches.filter { it.groupName == availableGroups[selectedGroupIndex] }
            } else updatedRoundMatches

            val updatedModels = updatedFiltered.map { m ->
                createMatchUiModel(m, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event)
            }

            _uiState.value = TournamentDetailState.Active(
                tournament = tournament,
                event = event,
                availableRounds = availableRounds,
                availableRoundDisplayNames = availableRoundDisplayNames,
                selectedRoundIndex = selectedRoundIndex,
                availableGroups = availableGroups,
                selectedGroupIndex = selectedGroupIndex,
                matches = updatedModels,
                autoSimulateCpu = autoSimulateCpu,
                autoAdvanceRound = autoAdvanceRound,
                isCurrentRoundCompleted = updatedRoundMatches.isNotEmpty() && updatedRoundMatches.all { it.isCompleted },
                userLevel = userLevel
            )
            return
        }

        _uiState.value = TournamentDetailState.Active(
            tournament = tournament,
            event = event,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels,
            autoSimulateCpu = autoSimulateCpu,
            autoAdvanceRound = autoAdvanceRound,
            isCurrentRoundCompleted = isCurrentRoundCompleted,
            userLevel = userLevel
        )
    }

    private suspend fun buildReviewState(
        tournament: Tournament,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>
    ) {
        val finalMatch = allMatches.find { it.round == TournamentRound.F.name } ?: allMatches.lastOrNull()
        val allPlayersMap = playerDao.getAllPlayersList().associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userLevel = careerWithProfile?.career?.level?.coerceIn(1, 10) ?: 5
        val userName = careerWithProfile?.playerName?.ifBlank { null } ?: careerWithProfile?.profileName ?: careerWithProfile?.career?.name ?: "Du"
        val userCountryCode = careerWithProfile?.countryCode ?: "DEU"

        val winnerId = finalMatch?.winnerId
        val (winnerName, winnerFlag) = if (winnerId != null) {
            if (winnerId == USER_ID) {
                Pair(userName, Country.getFlagEmoji(userCountryCode))
            } else {
                val p = allPlayersMap[winnerId]
                Pair(p?.name ?: "Unbekannt", Country.getFlagEmoji(p?.countryCode ?: "ENG"))
            }
        } else Pair("Unbekannt", "🏳️")

        val opponentId = if (winnerId != null && finalMatch != null) {
            if (finalMatch.player1Id == winnerId) finalMatch.player2Id else finalMatch.player1Id
        } else null

        val (opponentName, opponentFlag) = if (opponentId != null) {
            if (opponentId == USER_ID) {
                Pair(userName, Country.getFlagEmoji(userCountryCode))
            } else {
                val p = allPlayersMap[opponentId]
                Pair(p?.name ?: "Unbekannt", Country.getFlagEmoji(p?.countryCode ?: "ENG"))
            }
        } else Pair("Unbekannt", "🏳️")

        val finalScoreText = if (finalMatch != null) "${finalMatch.player1Score} : ${finalMatch.player2Score}" else "-"

        val placements = tournamentPlacementDao.getPlacementsListForTournament(tournament.id)
        val userPlacement = placements.find { it.playerId == USER_ID }
        val userReachedRoundText = if (userPlacement != null) {
            getDisplayRoundName(userPlacement.reachedRound)
        } else "Nicht qualifiziert"

        val userEarnedPrizeMoneyText = if (userPlacement != null && userPlacement.prizeMoney > 0) {
            "£%,d".format(Locale.US, userPlacement.prizeMoney.toLong())
        } else "£0"

        val sequence = getRoundSequence(event)
        val availableRounds = sequence.map { it.name }

        if (selectedRoundIndex !in availableRounds.indices) {
            selectedRoundIndex = 0
        }

        val currentRoundKey = availableRounds.getOrNull(selectedRoundIndex) ?: ""
        val availableRoundDisplayNames = availableRounds.map { getDisplayRoundName(it) }

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
        }

        val filteredMatches = if (isGrandSlamGroup && availableGroups.isNotEmpty()) {
            currentRoundMatches.filter { it.groupName == availableGroups[selectedGroupIndex] }
        } else currentRoundMatches

        val matchUiModels = filteredMatches.map { m ->
            createMatchUiModel(m, userName, userCountryCode, userLevel, allPlayersMap, currentRoundKey, event)
        }

        _uiState.value = TournamentDetailState.Review(
            tournament = tournament,
            event = event,
            winnerName = winnerName,
            winnerFlag = winnerFlag,
            finalScoreText = finalScoreText,
            opponentName = opponentName,
            opponentFlag = opponentFlag,
            userReachedRoundText = userReachedRoundText,
            userEarnedPrizeMoneyText = userEarnedPrizeMoneyText,
            availableRounds = availableRounds,
            availableRoundDisplayNames = availableRoundDisplayNames,
            selectedRoundIndex = selectedRoundIndex,
            availableGroups = availableGroups,
            selectedGroupIndex = selectedGroupIndex,
            matches = matchUiModels
        )
    }

    private fun createMatchUiModel(
        m: TournamentMatch,
        userName: String,
        userCountryCode: String,
        userLevel: Int,
        allPlayersMap: Map<Long, com.danny.dannyapp.data.db.Player>,
        roundKey: String,
        event: TournamentEvent
    ): MatchUiModel {
        val app = getApplication<Application>()
        val isP1User = (m.player1Id == USER_ID)
        val isP2User = (m.player2Id == USER_ID)
        val isUserMatch = isP1User || isP2User

        val (p1Name, p1Flag) = when (m.player1Id) {
            USER_ID -> Pair(userName, Country.getFlagEmoji(userCountryCode))
            -998L -> Pair(app.getString(R.string.tournament_placeholder_unknown), "")
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
        val p1Skill = if (isP1User) userLevel else allPlayersMap[m.player1Id]?.skill ?: 5

        val (p2Name, p2Flag) = when (m.player2Id) {
            USER_ID -> Pair(userName, Country.getFlagEmoji(userCountryCode))
            -998L -> Pair(app.getString(R.string.tournament_placeholder_unknown), "")
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
        val p2Skill = if (isP2User) userLevel else allPlayersMap[m.player2Id]?.skill ?: 5

        val scoreText = if (m.isCompleted) "${m.player1Score} : ${m.player2Score}" else "- : -"

        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { TournamentRound.L128 }

        return MatchUiModel(
            match = m,
            player1Name = p1Name,
            player1Flag = p1Flag,
            player1Skill = p1Skill,
            player2Name = p2Name,
            player2Flag = p2Flag,
            player2Skill = p2Skill,
            isUserMatch = isUserMatch,
            isCompleted = m.isCompleted,
            winnerId = m.winnerId,
            scoreText = scoreText,
            roundDisplayName = roundEnum.displayName
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
            else -> 1
        }

        val isUkOrEuropeanOpenLiveDraw = (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) &&
                (roundEnum == TournamentRound.L64 || roundEnum == TournamentRound.L32 || roundEnum == TournamentRound.L16 ||
                 roundEnum == TournamentRound.QF || roundEnum == TournamentRound.SF || roundEnum == TournamentRound.F)

        val placeholderPlayerId = if (isUkOrEuropeanOpenLiveDraw) -998L else -999L

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
            refreshState()
        }
    }

    fun selectRound(index: Int) {
        selectedRoundIndex = index
        viewModelScope.launch {
            refreshState()
        }
    }

    fun selectGroup(index: Int) {
        selectedGroupIndex = index
        viewModelScope.launch {
            refreshState()
        }
    }

    fun simulateCpuMatches() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            simulateCpuMatchUseCase.simulateAllCpuMatches(currentTournamentId)
            refreshState()
        }
    }

    fun advanceRound() {
        viewModelScope.launch {
            _uiState.value = TournamentDetailState.Loading
            advanceTournamentRoundUseCase(currentTournamentId)

            val tournament = tournamentDao.getTournamentById(currentTournamentId)
            if (tournament != null) {
                val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { TournamentEvent.PLAYERS_CHAMPIONSHIP }
                val sequence = getRoundSequence(event)
                val availableRounds = sequence.map { it.name }
                val matches = tournamentMatchDao.getMatchesListForTournament(currentTournamentId)
                val roundKeysInMatches = matches.map { it.round }.distinct()
                val latestRound = sequence.lastOrNull { it.name in roundKeysInMatches }
                if (latestRound != null) {
                    val newIdx = availableRounds.indexOf(latestRound.name)
                    if (newIdx != -1) {
                        selectedRoundIndex = newIdx
                    }
                }
            }
            refreshState()
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
            refreshState()
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
            refreshState()
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
        refreshState()
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

    private fun getRoundSequence(event: TournamentEvent): List<TournamentRound> {
        return when (event) {
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
            TournamentEvent.QUALIFIER_ET -> listOf(
                TournamentRound.L128, TournamentRound.L64, TournamentRound.L32, TournamentRound.L16
            )
            TournamentEvent.QUALIFIER_GRAND_SLAM -> listOf(
                TournamentRound.L64, TournamentRound.L32, TournamentRound.L16, TournamentRound.QF
            )
            TournamentEvent.QUALIFIER_WM -> listOf(
                TournamentRound.L32, TournamentRound.L16, TournamentRound.QF, TournamentRound.SF
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
}
