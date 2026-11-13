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
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvanceTournamentRoundUseCase(
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        GetOrderOfMeritUseCase(context)
    )

    suspend operator fun invoke(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext false
        if (tournament.isCompleted) return@withContext false

        val event = TournamentEvent.valueOf(tournament.type)
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (allMatches.isEmpty()) return@withContext false

        val roundSequence = getRoundSequence(event)

        // Special handling for Grand Slam of Darts group stage
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && allMatches.all { it.round == TournamentRound.GROUP.name }) {
            return@withContext handleGrandSlamGroupStage(tournament, event, allMatches)
        }

        // Find the latest round in roundSequence present in allMatches
        val currentRoundEnum = roundSequence.lastOrNull { round ->
            allMatches.any { it.round == round.name }
        } ?: return@withContext false

        val currentRoundMatches = allMatches.filter { it.round == currentRoundEnum.name }
        if (currentRoundMatches.isEmpty() || currentRoundMatches.any { !it.isCompleted }) {
            return@withContext false // Not all matches in current round are finished yet
        }

        // Special handling for qualifier tournaments (QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM)
        if (event == TournamentEvent.QUALIFIER_ET || event == TournamentEvent.QUALIFIER_GRAND_SLAM || event == TournamentEvent.QUALIFIER_WM) {
            val targetWinners = when (event) {
                TournamentEvent.QUALIFIER_ET -> 10
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                TournamentEvent.QUALIFIER_WM -> 4
                else -> 4
            }

            if (currentRoundEnum == TournamentRound.QUAL_FINAL) {
                tournamentDao.updateTournament(tournament.copy(isCompleted = true))
                return@withContext true
            }

            val season = seasonDao.getSeasonById(tournament.seasonId)
                ?: throw IllegalArgumentException("Season not found for tournament: ${tournament.seasonId}")
            val careerId = season.careerId

            val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season.id)
            val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()
            val allPlayers = playerDao.getAllPlayersList()

            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
            val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)

            val nonPreqTc = getNonPrequalifiedTcHoldersForQualifier(
                event = event,
                tourCardPlayerIds = tourCardPlayerIds,
                oomList = oomEntries,
                ptOomList = ptOomEntries,
                allPlayers = allPlayers
            )

            val N = nonPreqTc.size
            val K = targetWinners

            val (nextRoundEnum, nextRoundPlayers) = when (currentRoundEnum) {
                TournamentRound.QUAL_PRELIM -> {
                    val byeCount = (16 * K - N).coerceAtLeast(0)
                    val byePlayers = nonPreqTc.take(byeCount)
                    val prelimWinners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
                    Pair(TournamentRound.QUAL_R1, byePlayers + prelimWinners)
                }
                TournamentRound.QUAL_R1 -> {
                    val isFirstRound = allMatches.none { it.round == TournamentRound.QUAL_PRELIM.name }
                    val byeCount = if (isFirstRound && N < 8 * K) (8 * K - N).coerceAtLeast(0) else 0
                    val byePlayers = nonPreqTc.take(byeCount)
                    val r1Winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
                    Pair(TournamentRound.QUAL_R2, byePlayers + r1Winners)
                }
                TournamentRound.QUAL_R2 -> {
                    val isFirstRound = allMatches.none { it.round == TournamentRound.QUAL_R1.name }
                    val byeCount = if (isFirstRound && N < 4 * K) (4 * K - N).coerceAtLeast(0) else 0
                    val byePlayers = nonPreqTc.take(byeCount)
                    val r2Winners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
                    Pair(TournamentRound.QUAL_FINAL, byePlayers + r2Winners)
                }
                else -> Pair(TournamentRound.QUAL_FINAL, currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId })
            }

            val nextMatchCount = nextRoundPlayers.size / 2
            val totalNextPlayers = nextRoundPlayers.size
            val nextMatches = (0 until nextMatchCount).map { i ->
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = nextRoundEnum.name,
                    matchIndex = i,
                    player1Id = nextRoundPlayers[i],
                    player2Id = nextRoundPlayers[totalNextPlayers - 1 - i]
                )
            }

            if (nextMatches.isNotEmpty()) {
                tournamentMatchDao.insertMatches(nextMatches)
            }
            return@withContext true
        }

        val season = seasonDao.getSeasonById(tournament.seasonId)
            ?: throw IllegalArgumentException("Season not found for tournament: ${tournament.seasonId}")
        val careerId = season.careerId

        // Fetch OOM data and pre-qualified seeds for European Tour Rule 4.5 check
        val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season.id)
        val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)

        val preQualifiedEtSeeds = if (event == TournamentEvent.EUROPEAN_TOUR) {
            val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
            val top16Oom = sortedOomTc.take(16)
            val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
            val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries, allPlayers).take(16)
            (top16Oom + top16PtOom).toSet()
        } else emptySet()

        // 1. Record placements for all losers in currentRoundMatches
        val existingPlacements = tournamentPlacementDao.getPlacementsListForTournament(tournamentId)
        val existingPlacementPlayerIds = existingPlacements.map { it.playerId }.toSet()
        val newPlacements = mutableListOf<TournamentPlacement>()

        for (match in currentRoundMatches) {
            val winnerId = match.winnerId ?: continue
            val loserId = if (match.player1Id == winnerId) match.player2Id else match.player1Id

            if (loserId !in existingPlacementPlayerIds && newPlacements.none { it.playerId == loserId }) {
                val matchesPlayedByLoser = allMatches.count { it.player1Id == loserId || it.player2Id == loserId }
                val lostFirstMatch = (matchesPlayedByLoser == 1)
                val isPreQualifiedSeed = loserId in preQualifiedEtSeeds

                val prizeMoney = event.getRankingPrizeMoney(
                    round = currentRoundEnum,
                    isPreQualifiedSeedRelevant = isPreQualifiedSeed,
                    lostFirstMatch = lostFirstMatch
                )

                newPlacements.add(
                    TournamentPlacement(
                        tournamentId = tournamentId,
                        playerId = loserId,
                        reachedRound = currentRoundEnum.name,
                        prizeMoney = prizeMoney
                    )
                )
            }
        }
        if (newPlacements.isNotEmpty()) {
            tournamentPlacementDao.insertPlacements(newPlacements)
        }

        // 2. If current round is Finale (F), record Winner placement and complete tournament
        if (currentRoundEnum == TournamentRound.F) {
            val finalMatch = currentRoundMatches.firstOrNull()
            val winnerId = finalMatch?.winnerId
            if (winnerId != null) {
                val updatedPlacements = tournamentPlacementDao.getPlacementsListForTournament(tournamentId)
                if (updatedPlacements.none { it.playerId == winnerId && it.reachedRound == TournamentRound.WINNER.name }) {
                    tournamentPlacementDao.insertPlacement(
                        TournamentPlacement(
                            tournamentId = tournamentId,
                            playerId = winnerId,
                            reachedRound = TournamentRound.WINNER.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.WINNER)
                        )
                    )
                }
            }
            tournamentDao.updateTournament(tournament.copy(isCompleted = true))
            return@withContext true
        }

        // 3. Generate matches for next round
        val currentIdx = roundSequence.indexOf(currentRoundEnum)
        if (currentIdx == -1 || currentIdx >= roundSequence.size - 1) {
            return@withContext true
        }

        val nextRoundEnum = roundSequence[currentIdx + 1]
        val sortedCurrentMatches = currentRoundMatches.sortedBy { it.matchIndex }
        val winners = sortedCurrentMatches.mapNotNull { it.winnerId }

        val nextMatches = when (event) {
            TournamentEvent.EUROPEAN_TOUR -> {
                if (currentRoundEnum == TournamentRound.L48) {
                    // ET R2 (L32): Top 16 OOM seeds play against winners of L48 crosswise
                    val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
                    val top16Oom = sortedOomTc.take(16)
                    val targetTop16 = fillToTargetCount(top16Oom, 16, allPlayerIds, oomEntries, allPlayers)

                    (0 until 16).map { i ->
                        TournamentMatch(
                            tournamentId = tournamentId,
                            round = TournamentRound.L32.name,
                            matchIndex = i,
                            player1Id = targetTop16[i],
                            player2Id = winners[15 - i]
                        )
                    }
                } else {
                    generateStandardKoMatches(tournamentId, nextRoundEnum, winners)
                }
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val sortedTcOom = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
                val fullOom = fillToTargetCount(sortedTcOom, 128, allPlayerIds, oomEntries, allPlayers)
                val shuffledWinners = winners.shuffled()

                when (currentRoundEnum) {
                    TournamentRound.R1_160 -> {
                        // R2_128: OOM ranks 65-96 (32 players) vs 32 winners of R1_160
                        val oom65To96 = fullOom.drop(64).take(32)
                        (0 until 32).map { i ->
                            TournamentMatch(
                                tournamentId = tournamentId,
                                round = TournamentRound.R2_128.name,
                                matchIndex = i,
                                player1Id = oom65To96[i],
                                player2Id = shuffledWinners[i]
                            )
                        }
                    }
                    TournamentRound.R2_128 -> {
                        // R3_96: OOM ranks 33-64 (32 players) vs 32 winners of R2_128
                        val oom33To64 = fullOom.drop(32).take(32)
                        (0 until 32).map { i ->
                            TournamentMatch(
                                tournamentId = tournamentId,
                                round = TournamentRound.R3_96.name,
                                matchIndex = i,
                                player1Id = oom33To64[i],
                                player2Id = shuffledWinners[i]
                            )
                        }
                    }
                    TournamentRound.R3_96 -> {
                        // L64: Top 32 OOM (32 players) vs 32 winners of R3_96
                        val top32Oom = fullOom.take(32)
                        (0 until 32).map { i ->
                            TournamentMatch(
                                tournamentId = tournamentId,
                                round = TournamentRound.L64.name,
                                matchIndex = i,
                                player1Id = top32Oom[i],
                                player2Id = shuffledWinners[i]
                            )
                        }
                    }
                    else -> generateStandardKoMatches(tournamentId, nextRoundEnum, winners)
                }
            }

            else -> generateStandardKoMatches(tournamentId, nextRoundEnum, winners)
        }

        if (nextMatches.isNotEmpty()) {
            tournamentMatchDao.insertMatches(nextMatches)
        }

        return@withContext true
    }

    private suspend fun handleGrandSlamGroupStage(
        tournament: Tournament,
        event: TournamentEvent,
        allGroupMatches: List<TournamentMatch>
    ): Boolean {
        if (allGroupMatches.any { !it.isCompleted }) {
            return false
        }

        val tournamentId = tournament.id
        val season = seasonDao.getSeasonById(tournament.seasonId)
            ?: throw IllegalArgumentException("Season not found for tournament: ${tournament.seasonId}")
        val careerId = season.careerId

        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)

        val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries, allPlayers)
        val top16Oom = sortedOom.take(16)
        val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
        val pot2 = getPlayerListSortedByOom(remainingForPt, ptOomEntries, allPlayers).take(16)
        val pot2Full = fillToTargetCount(pot2, 16, remainingForPt, ptOomEntries, allPlayers)

        if (allGroupMatches.size == 16) {
            // Day 1 completed (16 matches: pot1 vs pot3). Now generate Day 2 and Day 3 (32 matches total).
            val day2And3Matches = mutableListOf<TournamentMatch>()
            for (groupIdx in 0 until 16) {
                val groupLetter = ('A' + groupIdx).toString()
                val day1Match = allGroupMatches.find { it.groupName == groupLetter && it.matchIndex == groupIdx }
                    ?: continue
                val day1Winner = day1Match.winnerId!!
                val day1Loser = if (day1Match.player1Id == day1Winner) day1Match.player2Id else day1Match.player1Id
                val pot2Player = pot2Full[groupIdx]

                // Day 2: Pot 2 vs Day 1 Loser
                day2And3Matches.add(
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.GROUP.name,
                        matchIndex = 16 + groupIdx,
                        player1Id = pot2Player,
                        player2Id = day1Loser,
                        groupName = groupLetter
                    )
                )

                // Day 3: Pot 2 vs Day 1 Winner
                day2And3Matches.add(
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.GROUP.name,
                        matchIndex = 32 + groupIdx,
                        player1Id = pot2Player,
                        player2Id = day1Winner,
                        groupName = groupLetter
                    )
                )
            }
            tournamentMatchDao.insertMatches(day2And3Matches)
            return true
        } else if (allGroupMatches.size == 48) {
            // All 48 group matches completed. Calculate group standings & generate L16.
            val groupWinners = mutableListOf<Long>()
            val placementsToInsert = mutableListOf<TournamentPlacement>()

            for (groupIdx in 0 until 16) {
                val groupLetter = ('A' + groupIdx).toString()
                val matchesForGroup = allGroupMatches.filter { it.groupName == groupLetter }
                val playerIdsInGroup = matchesForGroup.flatMap { listOf(it.player1Id, it.player2Id) }.distinct()

                data class GroupPlayerStats(
                    val playerId: Long,
                    var wins: Int = 0,
                    var legsWon: Int = 0,
                    var legsLost: Int = 0
                ) {
                    val legDiff: Int get() = legsWon - legsLost
                }

                val statsMap = playerIdsInGroup.associateWith { GroupPlayerStats(it) }

                for (m in matchesForGroup) {
                    val p1Stats = statsMap[m.player1Id]
                    val p2Stats = statsMap[m.player2Id]
                    if (p1Stats != null && p2Stats != null) {
                        p1Stats.legsWon += m.player1Score
                        p1Stats.legsLost += m.player2Score
                        p2Stats.legsWon += m.player2Score
                        p2Stats.legsLost += m.player1Score

                        if (m.winnerId == m.player1Id) {
                            p1Stats.wins += 1
                        } else if (m.winnerId == m.player2Id) {
                            p2Stats.wins += 1
                        }
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
                if (sortedGroup.size >= 2) {
                    placementsToInsert.add(
                        TournamentPlacement(
                            tournamentId = tournamentId,
                            playerId = sortedGroup[1].playerId,
                            reachedRound = TournamentRound.GROUP_2ND.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_2ND)
                        )
                    )
                }
                if (sortedGroup.size >= 3) {
                    placementsToInsert.add(
                        TournamentPlacement(
                            tournamentId = tournamentId,
                            playerId = sortedGroup[2].playerId,
                            reachedRound = TournamentRound.GROUP_3RD.name,
                            prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_3RD)
                        )
                    )
                }
            }

            if (placementsToInsert.isNotEmpty()) {
                tournamentPlacementDao.insertPlacements(placementsToInsert)
            }

            // Generate L16 matches: Sieger A vs Sieger B, Sieger C vs Sieger D...
            val l16Matches = (0 until 8).map { i ->
                val p1 = groupWinners.getOrElse(2 * i) { 0L }
                val p2 = groupWinners.getOrElse(2 * i + 1) { 0L }
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = TournamentRound.L16.name,
                    matchIndex = i,
                    player1Id = p1,
                    player2Id = p2
                )
            }
            tournamentMatchDao.insertMatches(l16Matches)
            return true
        }

        return false
    }

    private fun generateStandardKoMatches(
        tournamentId: Long,
        nextRound: TournamentRound,
        winners: List<Long>
    ): List<TournamentMatch> {
        val nextMatchCount = winners.size / 2
        return (0 until nextMatchCount).map { i ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = nextRound.name,
                matchIndex = i,
                player1Id = winners[2 * i],
                player2Id = winners[2 * i + 1]
            )
        }
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
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_FINAL
            )
        }
    }

    private fun getNonPrequalifiedTcHoldersForQualifier(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomList, allPlayers)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomList, allPlayers).take(16)

        val preQualified: Set<Long> = when (event) {
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM -> (top16Oom + top16PtOom).toSet()
            TournamentEvent.QUALIFIER_WM -> {
                val top40Oom = sortedOomTc.take(40)
                val remainingAfterTop40Oom = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40PtOom = getPlayerListSortedByOom(remainingAfterTop40Oom, ptOomList, allPlayers).take(40)
                (top40Oom + top40PtOom).toSet()
            }
            else -> emptySet()
        }

        val nonPreq = tourCardPlayerIds.filterNot { it in preQualified }
        return getPlayerListSortedByOom(nonPreq, oomList, allPlayers)
    }

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
                    if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5
                }
                .thenBy {
                    if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                }
        )
    }

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
                    .thenByDescending { if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5 }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }
}
