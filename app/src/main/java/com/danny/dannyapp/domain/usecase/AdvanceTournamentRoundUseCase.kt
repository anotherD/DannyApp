package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.TournamentProgressState
import com.danny.dannyapp.domain.model.TournamentRoundPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase to evaluate tournament progress, advance tournament rounds, and record prize money placements.
 * Acts as the single source of truth for round phases and active round determination.
 */
class AdvanceTournamentRoundUseCase(
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
        GetOrderOfMeritUseCase(context)
    )

    /**
     * Evaluates the current state of a tournament directly from the database (Single Source of Truth).
     * Determines active round, phase of each round, and whether the round can be advanced.
     *
     * @param tournamentId The ID of the tournament to evaluate.
     * @return [TournamentProgressState] containing the evaluation result, or null if tournament not found or no matches exist.
     */
    suspend fun evaluateTournamentState(tournamentId: Long): TournamentProgressState? = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext null
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (allMatches.isEmpty()) return@withContext null
        val existingPlacements = tournamentPlacementDao.getPlacementsListForTournament(tournamentId)

        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext null }
        val fullSequence = event.getRoundSequence()
        val firstRoundInMatches = fullSequence.firstOrNull { round -> allMatches.any { it.round == round.name } } ?: fullSequence.first()
        val roundSequence = fullSequence.dropWhile { it != firstRoundInMatches }

        if (tournament.isCompleted) {
            return@withContext TournamentProgressState(
                activeRound = null,
                roundPhases = roundSequence.associateWith { TournamentRoundPhase.COMPLETED },
                canAdvanceRound = false,
                isFinalRound = false,
                hasOpenCpuMatchesInActiveRound = false
            )
        }

        val isQualifier = event in listOf(
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM
        )

        if (isQualifier) {
            val openRound = roundSequence.firstOrNull { round ->
                val matches = allMatches.filter { it.round == round.name }
                matches.isNotEmpty() && matches.any { !it.isCompleted }
            }

            val activeRound: TournamentRound
            val canAdvanceRound: Boolean
            if (openRound != null) {
                activeRound = openRound
                canAdvanceRound = false
            } else {
                activeRound = TournamentRound.QUAL_FINAL
                canAdvanceRound = true
            }

            val roundPhases = mutableMapOf<TournamentRound, TournamentRoundPhase>()
            var foundActive = false
            for (round in roundSequence) {
                when {
                    round == activeRound -> {
                        roundPhases[round] = TournamentRoundPhase.ACTIVE
                        foundActive = true
                    }
                    !foundActive -> {
                        roundPhases[round] = TournamentRoundPhase.COMPLETED
                    }
                    else -> {
                        roundPhases[round] = TournamentRoundPhase.PENDING
                    }
                }
            }

            val activeRoundMatches = allMatches.filter { it.round == activeRound.name }
            val hasOpenCpuMatches = activeRoundMatches.any { match ->
                !match.isCompleted && match.player1Id > 0L && match.player2Id > 0L
            }

            return@withContext TournamentProgressState(
                activeRound = activeRound,
                roundPhases = roundPhases,
                canAdvanceRound = canAdvanceRound,
                isFinalRound = (activeRound == TournamentRound.QUAL_FINAL),
                hasOpenCpuMatchesInActiveRound = hasOpenCpuMatches
            )
        }

        var determinedActiveRound: TournamentRound? = null
        var canAdvanceRound = false
        val roundPhases = mutableMapOf<TournamentRound, TournamentRoundPhase>()

        for (round in roundSequence) {
            if (determinedActiveRound != null) {
                roundPhases[round] = TournamentRoundPhase.PENDING
                continue
            }

            val roundMatches = allMatches.filter { it.round == round.name }
            if (roundMatches.isEmpty()) {
                roundPhases[round] = TournamentRoundPhase.PENDING
                continue
            }

            if (roundMatches.any { !it.isCompleted }) {
                determinedActiveRound = round
                canAdvanceRound = false
                roundPhases[round] = TournamentRoundPhase.ACTIVE
            } else {
                val isAdvanced = isRoundAlreadyAdvancedOrAccounted(
                    event = event,
                    round = round,
                    roundSequence = roundSequence,
                    roundMatches = roundMatches,
                    allMatches = allMatches,
                    existingPlacements = existingPlacements
                )
                if (!isAdvanced) {
                    determinedActiveRound = round
                    canAdvanceRound = true
                    roundPhases[round] = TournamentRoundPhase.ACTIVE
                } else {
                    roundPhases[round] = TournamentRoundPhase.COMPLETED
                }
            }
        }

        val activeRound = determinedActiveRound
        val activeRoundMatches = if (activeRound != null) allMatches.filter { it.round == activeRound.name } else emptyList()
        val hasOpenCpuMatches = activeRoundMatches.any { match ->
            !match.isCompleted && match.player1Id > 0L && match.player2Id > 0L
        }
        val isFinalRound = (activeRound == TournamentRound.F || activeRound == TournamentRound.QUAL_FINAL)

        return@withContext TournamentProgressState(
            activeRound = activeRound,
            roundPhases = roundPhases,
            canAdvanceRound = canAdvanceRound,
            isFinalRound = isFinalRound,
            hasOpenCpuMatchesInActiveRound = hasOpenCpuMatches
        )
    }

    /**
     * Checks whether a completed round was already advanced or accounted for in the database.
     */
    private fun isRoundAlreadyAdvancedOrAccounted(
        event: TournamentEvent,
        round: TournamentRound,
        roundSequence: List<TournamentRound>,
        roundMatches: List<TournamentMatch>,
        allMatches: List<TournamentMatch>,
        existingPlacements: List<TournamentPlacement>
    ): Boolean {
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && round == TournamentRound.GROUP) {
            val l16Matches = allMatches.filter { it.round == TournamentRound.L16.name }
            return l16Matches.isNotEmpty() && l16Matches.any { it.player1Id > 0L || it.player1Id == USER_ID }
        }

        if ((event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) && round != TournamentRound.F) {
            val currentIndex = roundSequence.indexOf(round)
            val nextRound = if (currentIndex != -1 && currentIndex + 1 < roundSequence.size) roundSequence[currentIndex + 1] else null
            return nextRound != null && allMatches.any { it.round == nextRound.name }
        }

        if (round == TournamentRound.F) {
            return existingPlacements.any { it.reachedRound == TournamentRound.WINNER.name }
        } else {
            val losers = roundMatches.mapNotNull { match ->
                val winnerId = match.winnerId ?: return@mapNotNull null
                if (match.player1Id == winnerId) match.player2Id else match.player1Id
            }
            if (losers.isEmpty()) {
                return true
            }
            return existingPlacements.any { it.reachedRound == round.name }
        }
    }

    /**
     * Advances the tournament round if all conditions are met according to [evaluateTournamentState].
     * Records prize money placements immediately and updates matches for the next round or completes tournament.
     *
     * @param tournamentId The ID of the tournament to advance.
     * @return `true` if round advancement or tournament completion was executed, `false` otherwise.
     */
    suspend operator fun invoke(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        val state = evaluateTournamentState(tournamentId) ?: return@withContext false
        if (!state.canAdvanceRound) return@withContext false
        val activeRound = state.activeRound ?: return@withContext false

        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext false
        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext false }
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        val existingPlacements = tournamentPlacementDao.getPlacementsListForTournament(tournamentId)

        recordPlacementsForCompletedRound(
            tournament = tournament,
            round = activeRound,
            allMatches = allMatches,
            existingPlacements = existingPlacements
        )

        if (state.isFinalRound) {
            tournamentDao.updateTournament(tournament.copy(isCompleted = true))
            return@withContext true
        }

        advanceToNextRound(
            tournament = tournament,
            event = event,
            currentRound = activeRound,
            allMatches = allMatches
        )
        return@withContext true
    }

    /**
     * Writes [TournamentPlacement] records for a completed round immediately to the database.
     * Handles European Tour £0 opening loss exceptions, Grand Slam group placements, and winner placements.
     */
    private suspend fun recordPlacementsForCompletedRound(
        tournament: Tournament,
        round: TournamentRound,
        allMatches: List<TournamentMatch>,
        existingPlacements: List<TournamentPlacement>
    ) {
        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return }
        if (event in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
            return
        }

        val tournamentId = tournament.id
        val existingPlacementPlayerIds = existingPlacements.map { it.playerId }.toSet()
        val newPlacements = mutableListOf<TournamentPlacement>()
        val roundMatches = allMatches.filter { it.round == round.name }
        if (roundMatches.isEmpty() || !roundMatches.all { it.isCompleted }) return

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && round == TournamentRound.GROUP) {
            if (roundMatches.size == 48) {
                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val matchesForGroup = roundMatches.filter { it.groupName == groupLetter }
                    val playerIdsInGroup = matchesForGroup.flatMap { listOf(it.player1Id, it.player2Id) }.distinct()

                    class GroupPlayerStats(
                        val playerId: Long,
                        var wins: Int = 0,
                        var legsWon: Int = 0,
                        var legsLost: Int = 0
                    ) {
                        val legDiff: Int get() = legsWon - legsLost
                    }

                    val statsMap = playerIdsInGroup.associateWith { GroupPlayerStats(it) }
                    for (match in matchesForGroup) {
                        val player1Stats = statsMap[match.player1Id]
                        val player2Stats = statsMap[match.player2Id]
                        if (player1Stats != null && player2Stats != null) {
                            player1Stats.legsWon += match.player1Score
                            player1Stats.legsLost += match.player2Score
                            player2Stats.legsWon += match.player2Score
                            player2Stats.legsLost += match.player1Score
                            if (match.winnerId == match.player1Id) player1Stats.wins++
                            else if (match.winnerId == match.player2Id) player2Stats.wins++
                        }
                    }

                    val sortedGroup = statsMap.values.sortedWith(
                        compareByDescending<GroupPlayerStats> { it.wins }
                            .thenByDescending { it.legDiff }
                            .thenByDescending { it.legsWon }
                    )

                    if (sortedGroup.size >= 2 && sortedGroup[1].playerId !in existingPlacementPlayerIds && newPlacements.none { it.playerId == sortedGroup[1].playerId }) {
                        newPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = sortedGroup[1].playerId,
                                reachedRound = TournamentRound.GROUP_2ND.name,
                                prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_2ND)
                            )
                        )
                    }
                    if (sortedGroup.size >= 3 && sortedGroup[2].playerId !in existingPlacementPlayerIds && newPlacements.none { it.playerId == sortedGroup[2].playerId }) {
                        newPlacements.add(
                            TournamentPlacement(
                                tournamentId = tournamentId,
                                playerId = sortedGroup[2].playerId,
                                reachedRound = TournamentRound.GROUP_3RD.name,
                                prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_3RD)
                            )
                        )
                    }
                }
            }
        } else if (round == TournamentRound.F) {
            val finalMatch = roundMatches.firstOrNull()
            val winnerId = finalMatch?.winnerId
            val loserId = if (finalMatch != null && winnerId != null) {
                if (finalMatch.player1Id == winnerId) finalMatch.player2Id else finalMatch.player1Id
            } else null

            if (loserId != null && loserId !in existingPlacementPlayerIds && newPlacements.none { it.playerId == loserId }) {
                newPlacements.add(
                    TournamentPlacement(
                        tournamentId = tournamentId,
                        playerId = loserId,
                        reachedRound = TournamentRound.F.name,
                        prizeMoney = event.getRankingPrizeMoney(TournamentRound.F)
                    )
                )
            }

            if (winnerId != null && existingPlacements.none { it.playerId == winnerId && it.reachedRound == TournamentRound.WINNER.name } && newPlacements.none { it.playerId == winnerId && it.reachedRound == TournamentRound.WINNER.name }) {
                newPlacements.add(
                    TournamentPlacement(
                        tournamentId = tournamentId,
                        playerId = winnerId,
                        reachedRound = TournamentRound.WINNER.name,
                        prizeMoney = event.getRankingPrizeMoney(TournamentRound.WINNER)
                    )
                )
            }
        } else {
            val preQualifiedEtSeeds = if (event == TournamentEvent.EUROPEAN_TOUR) {
                getPreQualifiedEtSeeds(tournamentId, tournament.seasonId)
            } else emptySet()

            for (match in roundMatches) {
                val winnerId = match.winnerId ?: continue
                val loserId = if (match.player1Id == winnerId) match.player2Id else match.player1Id

                if (loserId !in existingPlacementPlayerIds && newPlacements.none { it.playerId == loserId }) {
                    val matchesPlayedByLoser = allMatches.count {
                        (it.player1Id == loserId || it.player2Id == loserId) && it.isCompleted
                    }
                    val lostFirstMatch = (matchesPlayedByLoser == 1)
                    val isPreQualifiedSeed = loserId in preQualifiedEtSeeds

                    val prizeMoney = event.getRankingPrizeMoney(
                        round = round,
                        isPreQualifiedSeedRelevant = isPreQualifiedSeed,
                        lostFirstMatch = lostFirstMatch
                    )

                    newPlacements.add(
                        TournamentPlacement(
                            tournamentId = tournamentId,
                            playerId = loserId,
                            reachedRound = round.name,
                            prizeMoney = prizeMoney
                        )
                    )
                }
            }
        }

        if (newPlacements.isNotEmpty()) {
            tournamentPlacementDao.insertPlacements(newPlacements)
        }
    }

    /**
     * Executes round progression by setting participants into the next round matches
     * or drawing subsequent rounds for unseeded open draws (UK Open / European Open).
     */
    private suspend fun advanceToNextRound(
        tournament: Tournament,
        event: TournamentEvent,
        currentRound: TournamentRound,
        allMatches: List<TournamentMatch>
    ) {
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && currentRound == TournamentRound.GROUP) {
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val l16Matches = allMatches.filter { it.round == TournamentRound.L16.name }.sortedBy { it.matchIndex }
            if (groupMatches.size == 48 && l16Matches.isNotEmpty()) {
                val groupWinners = mutableListOf<Long>()
                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val matchesForGroup = groupMatches.filter { it.groupName == groupLetter }
                    val playerIdsInGroup = matchesForGroup.flatMap { listOf(it.player1Id, it.player2Id) }.distinct()

                    class GroupPlayerStats(
                        val playerId: Long,
                        var wins: Int = 0,
                        var legsWon: Int = 0,
                        var legsLost: Int = 0
                    ) {
                        val legDiff: Int get() = legsWon - legsLost
                    }

                    val statsMap = playerIdsInGroup.associateWith { GroupPlayerStats(it) }
                    for (match in matchesForGroup) {
                        val player1Stats = statsMap[match.player1Id]
                        val player2Stats = statsMap[match.player2Id]
                        if (player1Stats != null && player2Stats != null) {
                            player1Stats.legsWon += match.player1Score
                            player1Stats.legsLost += match.player2Score
                            player2Stats.legsWon += match.player2Score
                            player2Stats.legsLost += match.player1Score
                            if (match.winnerId == match.player1Id) player1Stats.wins++
                            else if (match.winnerId == match.player2Id) player2Stats.wins++
                        }
                    }

                    val sortedGroup = statsMap.values.sortedWith(
                        compareByDescending<GroupPlayerStats> { it.wins }
                            .thenByDescending { it.legDiff }
                            .thenByDescending { it.legsWon }
                    )

                    if (sortedGroup.isNotEmpty()) {
                        groupWinners.add(sortedGroup[0].playerId)
                    }
                }

                val updatedL16 = l16Matches.mapIndexed { index, match ->
                    val player1Id = groupWinners.getOrElse(2 * index) { 0L }
                    val player2Id = groupWinners.getOrElse(2 * index + 1) { 0L }
                    match.copy(player1Id = player1Id, player2Id = player2Id)
                }
                tournamentMatchDao.insertMatches(updatedL16)
            }
            return
        }

        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) {
            val roundSequence = event.getRoundSequence()
            val currentIndex = roundSequence.indexOf(currentRound)
            if (currentIndex == -1 || currentIndex >= roundSequence.size - 1) return
            val nextRoundEnum = roundSequence[currentIndex + 1]

            val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
            val winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            val shuffledWinners = winners.shuffled()

            val season = seasonDao.getSeasonById(tournament.seasonId)
            val careerId = season?.careerId ?: 0L
            val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season?.id ?: 0L)
            val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()
            val allPlayers = playerDao.getAllPlayersList()
            val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()
            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)

            val participants = tournamentParticipantDao.getParticipantsForTournament(tournament.id)
            val stored65To96 = participants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val stored33To64 = participants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val storedTop32 = participants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

            val sortedTcOom = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
            val fullOom = fillToTargetCount(sortedTcOom, 128, allPlayerIds, oomEntries, allPlayers)
            val oom65To96 = if (stored65To96.isNotEmpty()) stored65To96 else fullOom.drop(64).take(32)
            val oom33To64 = if (stored33To64.isNotEmpty()) stored33To64 else fullOom.drop(32).take(32)
            val top32Oom = if (storedTop32.isNotEmpty()) storedTop32 else fullOom.take(32)

            val nextMatches = when (currentRound) {
                TournamentRound.R1_160 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R2_128.name,
                            matchIndex = index,
                            player1Id = oom65To96.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                TournamentRound.R2_128 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.R3_96.name,
                            matchIndex = index,
                            player1Id = oom33To64.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                TournamentRound.R3_96 -> {
                    (0 until 32).map { index ->
                        TournamentMatch(
                            tournamentId = tournament.id,
                            round = TournamentRound.L64.name,
                            matchIndex = index,
                            player1Id = top32Oom.getOrElse(index) { 0L },
                            player2Id = shuffledWinners.getOrElse(index) { 0L }
                        )
                    }
                }
                else -> generateStandardKoMatches(tournament.id, nextRoundEnum, shuffledWinners)
            }
            if (nextMatches.isNotEmpty()) {
                tournamentMatchDao.insertMatches(nextMatches)
            }
            return
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && currentRound == TournamentRound.L48) {
            val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
            val existingL32 = allMatches.filter { it.round == TournamentRound.L32.name }.sortedBy { it.matchIndex }
            val l48Winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
            val updatedL32 = existingL32.mapIndexed { index, match ->
                val seedIndex = seedOrder[index]
                val winnerId = l48Winners.getOrNull(15 - seedIndex) ?: 0L
                match.copy(player2Id = winnerId)
            }
            if (updatedL32.isNotEmpty()) {
                tournamentMatchDao.insertMatches(updatedL32)
            }
            return
        }

        val roundSequence = event.getRoundSequence()
        val currentIndex = roundSequence.indexOf(currentRound)
        if (currentIndex == -1 || currentIndex >= roundSequence.size - 1) return
        val nextRoundEnum = roundSequence[currentIndex + 1]

        val currentRoundMatches = allMatches.filter { it.round == currentRound.name }
        val winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
        val existingNext = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }

        if (existingNext.isNotEmpty()) {
            val updatedNext = existingNext.mapIndexed { index, match ->
                val player1Id = winners.getOrNull(2 * index) ?: match.player1Id
                val player2Id = winners.getOrNull(2 * index + 1) ?: match.player2Id
                match.copy(player1Id = player1Id, player2Id = player2Id)
            }
            tournamentMatchDao.insertMatches(updatedNext)
        }
    }

    /**
     * Propagates the winner of a completed match immediately into the corresponding next round slot.
     */
    suspend fun propagateWinnerOnMatchCompleted(match: TournamentMatch) = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@withContext
        val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { return@withContext }
        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) {
            return@withContext
        }
        val winnerId = match.winnerId ?: return@withContext
        val allMatches = tournamentMatchDao.getMatchesListForTournament(match.tournamentId)
        val roundSequence = event.getRoundSequence()

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && match.round == TournamentRound.GROUP.name && match.matchIndex in 0..15) {
            val groupIdx = match.matchIndex
            val groupLetter = ('A' + groupIdx).toString()
            val loserId = if (match.player1Id == winnerId) match.player2Id else match.player1Id
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val day2Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 16 + groupIdx }
            val day3Match = groupMatches.find { it.groupName == groupLetter && it.matchIndex == 32 + groupIdx }
            if (day2Match != null) {
                tournamentMatchDao.updateMatch(day2Match.copy(player2Id = loserId))
            }
            if (day3Match != null) {
                tournamentMatchDao.updateMatch(day3Match.copy(player2Id = winnerId))
            }
            return@withContext
        }

        if (match.round == TournamentRound.QUAL_PRELIM.name) {
            val qualR1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }
            val placeholderId = -1000L - match.matchIndex
            for (qualMatch in qualR1Matches) {
                var updated = qualMatch
                var changed = false
                if (qualMatch.player1Id == placeholderId) {
                    updated = updated.copy(player1Id = winnerId)
                    changed = true
                }
                if (qualMatch.player2Id == placeholderId) {
                    updated = updated.copy(player2Id = winnerId)
                    changed = true
                }
                if (changed) {
                    tournamentMatchDao.updateMatch(updated)
                }
            }
            return@withContext
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && match.round == TournamentRound.L48.name) {
            val l32Matches = allMatches.filter { it.round == TournamentRound.L32.name }
            val targetIdx = BracketGenerator.BRACKET_SEED_ORDER_16.indexOf(15 - match.matchIndex)
            val l32Match = l32Matches.find { it.matchIndex == targetIdx }
            if (l32Match != null) {
                tournamentMatchDao.updateMatch(l32Match.copy(player2Id = winnerId))
            }
            return@withContext
        }

        val currentRoundEnum = try { TournamentRound.valueOf(match.round) } catch (_: Exception) { return@withContext }
        val currentIdx = roundSequence.indexOf(currentRoundEnum)
        if (currentIdx < 0) return@withContext
        val nextRoundEnum = roundSequence.getOrNull(currentIdx + 1) ?: return@withContext
        val nextMatches = allMatches.filter { it.round == nextRoundEnum.name }
        val targetMatchIndex = match.matchIndex / 2
        val nextMatch = nextMatches.find { it.matchIndex == targetMatchIndex }
        if (nextMatch != null) {
            val updatedNext = if (match.matchIndex % 2 == 0) {
                nextMatch.copy(player1Id = winnerId)
            } else {
                nextMatch.copy(player2Id = winnerId)
            }
            tournamentMatchDao.updateMatch(updatedNext)
        }
    }

    /**
     * Determines pre-qualified seeds for the European Tour (Rule 4.5 check).
     */
    private suspend fun getPreQualifiedEtSeeds(tournamentId: Long, seasonId: Long): Set<Long> {
        val participants = tournamentParticipantDao.getParticipantsForTournament(tournamentId)
        val stored = participants.filter { it.qualificationType in listOf("OOM", "PT_OOM") }.map { it.playerId }
        if (stored.isNotEmpty()) return stored.toSet()

        val season = seasonDao.getSeasonById(seasonId) ?: return emptySet()
        val careerId = season.careerId
        val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season.id)
        val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerDao.getAllPlayersList()
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)

        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries, allPlayers).take(16)
        return (top16Oom + top16PtOom).toSet()
    }

    /**
     * Generates match pairings for an unseeded knockout round from a list of winners.
     */
    private fun generateStandardKoMatches(
        tournamentId: Long,
        nextRound: TournamentRound,
        winners: List<Long>
    ): List<TournamentMatch> {
        val nextMatchCount = winners.size / 2
        return (0 until nextMatchCount).map { index ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = nextRound.name,
                matchIndex = index,
                player1Id = winners[2 * index],
                player2Id = winners[2 * index + 1]
            )
        }
    }

    /**
     * Sorts player IDs by Order of Merit, then skill, then name.
     */
    private fun getPlayerListSortedByOom(
        playerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        val playerMap = oomList.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }
        return playerIds.sortedWith(
            compareBy<Long> { playerMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { playerMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending {
                    if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value
                }
                .thenBy {
                    if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                }
        )
    }

    /**
     * Fills an initial list of players up to [targetCount] with fallback candidates sorted by OOM.
     */
    private fun fillToTargetCount(
        initialList: List<Long>,
        targetCount: Int,
        fallbackCandidates: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        if (initialList.size >= targetCount) {
            return initialList.take(targetCount)
        }
        val result = initialList.toMutableList()
        val oomMap = oomList.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }
        val remainingCandidates = fallbackCandidates
            .filterNot { it in result }
            .sortedWith(
                compareBy<Long> { oomMap[it]?.rank ?: Int.MAX_VALUE }
                    .thenByDescending { oomMap[it]?.prizeMoney ?: 0.0 }
                    .thenByDescending { if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }
}
