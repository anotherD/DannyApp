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
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend operator fun invoke(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId) ?: return@withContext false
        if (tournament.isCompleted) return@withContext false

        val event = TournamentEvent.valueOf(tournament.type)
        val allMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (allMatches.isEmpty()) return@withContext false

        val roundSequence = event.getRoundSequence()


        val currentRoundEnum = roundSequence.firstOrNull { round ->
            val matches = allMatches.filter { it.round == round.name }
            if (matches.isEmpty()) return@firstOrNull false
            val hasIncompleteMatches = matches.any { !it.isCompleted }
            if (hasIncompleteMatches) {
                true
            } else {
                val nextRoundIdx = roundSequence.indexOf(round) + 1
                if (nextRoundIdx in roundSequence.indices) {
                    val nextRound = roundSequence[nextRoundIdx]
                    val nextMatches = allMatches.filter { it.round == nextRound.name }
                    nextMatches.isNotEmpty() && nextMatches.any {
                        (it.player1Id < 0L && it.player1Id != USER_ID) ||
                            (it.player2Id < 0L && it.player2Id != USER_ID)
                    }
                } else false
            }
        } ?: roundSequence.lastOrNull { round ->
            allMatches.any { it.round == round.name }
        } ?: return@withContext false

        // Special handling for Grand Slam of Darts group stage
        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS) {
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }.sortedBy { it.matchIndex }
            val day1Matches = groupMatches.filter { it.matchIndex in 0..15 }
            val day2Matches = groupMatches.filter { it.matchIndex in 16..31 }
            val day3Matches = groupMatches.filter { it.matchIndex in 32..47 }

            if (day1Matches.isNotEmpty() && day1Matches.all { it.isCompleted } && day2Matches.any { it.player2Id == PLACEHOLDER_PREVIOUS_WINNER_ID }) {
                val updatedGroupMatches = mutableListOf<TournamentMatch>()
                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val d1Match = day1Matches.find { it.groupName == groupLetter && it.matchIndex == groupIdx } ?: continue
                    val w = d1Match.winnerId ?: continue
                    val l = if (d1Match.player1Id == w) d1Match.player2Id else d1Match.player1Id

                    val d2Match = day2Matches.find { it.groupName == groupLetter && it.matchIndex == 16 + groupIdx }
                    if (d2Match != null) {
                        updatedGroupMatches.add(d2Match.copy(player2Id = l))
                    }
                    val d3Match = day3Matches.find { it.groupName == groupLetter && it.matchIndex == 32 + groupIdx }
                    if (d3Match != null) {
                        updatedGroupMatches.add(d3Match.copy(player2Id = w))
                    }
                }
                if (updatedGroupMatches.isNotEmpty()) {
                    tournamentMatchDao.insertMatches(updatedGroupMatches)
                }
                return@withContext true
            }

            if (groupMatches.size == 48 && groupMatches.all { it.isCompleted }) {
                val l16Matches = allMatches.filter { it.round == TournamentRound.L16.name }.sortedBy { it.matchIndex }
                if (l16Matches.isNotEmpty() && l16Matches.any { it.player1Id < 0L && it.player1Id != USER_ID }) {
                    val groupWinners = mutableListOf<Long>()
                    val placementsToInsert = mutableListOf<TournamentPlacement>()
                    val existingPlacements = tournamentPlacementDao.getPlacementsListForTournament(tournamentId)
                    val existingPlacementPlayerIds = existingPlacements.map { it.playerId }.toSet()

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
                        for (m in matchesForGroup) {
                            val p1Stats = statsMap[m.player1Id]
                            val p2Stats = statsMap[m.player2Id]
                            if (p1Stats != null && p2Stats != null) {
                                p1Stats.legsWon += m.player1Score
                                p1Stats.legsLost += m.player2Score
                                p2Stats.legsWon += m.player2Score
                                p2Stats.legsLost += m.player1Score
                                if (m.winnerId == m.player1Id) p1Stats.wins++
                                else if (m.winnerId == m.player2Id) p2Stats.wins++
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
                        if (sortedGroup.size >= 2 && sortedGroup[1].playerId !in existingPlacementPlayerIds) {
                            placementsToInsert.add(
                                TournamentPlacement(
                                    tournamentId = tournamentId,
                                    playerId = sortedGroup[1].playerId,
                                    reachedRound = TournamentRound.GROUP_2ND.name,
                                    prizeMoney = event.getRankingPrizeMoney(TournamentRound.GROUP_2ND)
                                )
                            )
                        }
                        if (sortedGroup.size >= 3 && sortedGroup[2].playerId !in existingPlacementPlayerIds) {
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

                    val updatedL16 = l16Matches.mapIndexed { i, match ->
                        val p1 = groupWinners.getOrElse(2 * i) { 0L }
                        val p2 = groupWinners.getOrElse(2 * i + 1) { 0L }
                        match.copy(player1Id = p1, player2Id = p2)
                    }
                    tournamentMatchDao.insertMatches(updatedL16)
                    return@withContext true
                }
            }
        }

        val currentRoundMatches = allMatches.filter { it.round == currentRoundEnum.name }
        if (currentRoundMatches.isEmpty() || currentRoundMatches.any { !it.isCompleted }) {
            return@withContext false // Not all matches in current round are finished yet
        }

        // Special handling for qualifier tournaments (QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM)
        if (event == TournamentEvent.QUALIFIER_ET || event == TournamentEvent.QUALIFIER_GRAND_SLAM || event == TournamentEvent.QUALIFIER_WM) {
            val K = when (event) {
                TournamentEvent.QUALIFIER_ET -> 10
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                TournamentEvent.QUALIFIER_WM -> 4
                else -> 4
            }

            if (currentRoundEnum == TournamentRound.QUAL_FINAL) {
                tournamentDao.updateTournament(tournament.copy(isCompleted = true))
                return@withContext true
            }

            val nextRoundEnum = roundSequence[roundSequence.indexOf(currentRoundEnum) + 1]
            val existingNextMatches = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }

            when (currentRoundEnum) {
                TournamentRound.QUAL_PRELIM -> {
                    val prelimWinners = currentRoundMatches.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
                    val updatedR1 = existingNextMatches.map { m ->
                        var p1 = m.player1Id
                        var p2 = m.player2Id
                        for (i in prelimWinners.indices) {
                            val ph = -1000L - i
                            if (p1 == ph) p1 = prelimWinners[i]
                            if (p2 == ph) p2 = prelimWinners[i]
                        }
                        m.copy(player1Id = p1, player2Id = p2)
                    }
                    if (updatedR1.isNotEmpty()) {
                        tournamentMatchDao.insertMatches(updatedR1)
                    }
                }
                TournamentRound.QUAL_R1 -> {
                    val r1MatchesByBoard = currentRoundMatches.sortedBy { it.matchIndex }
                    val updatedR2 = existingNextMatches.map { m ->
                        val b = m.matchIndex / 2
                        val isEven = (m.matchIndex % 2 == 0)
                        val boardMatches = r1MatchesByBoard.filter { it.matchIndex in (4 * b)..(4 * b + 3) }.sortedBy { it.matchIndex }
                        val w0 = boardMatches.getOrNull(0)?.winnerId ?: 0L
                        val w1 = boardMatches.getOrNull(1)?.winnerId ?: 0L
                        val w2 = boardMatches.getOrNull(2)?.winnerId ?: 0L
                        val w3 = boardMatches.getOrNull(3)?.winnerId ?: 0L
                        if (isEven) m.copy(player1Id = w0, player2Id = w1) else m.copy(player1Id = w2, player2Id = w3)
                    }
                    if (updatedR2.isNotEmpty()) {
                        tournamentMatchDao.insertMatches(updatedR2)
                    }
                }
                TournamentRound.QUAL_R2 -> {
                    val r2MatchesByBoard = currentRoundMatches.sortedBy { it.matchIndex }
                    val updatedFinal = existingNextMatches.map { m ->
                        val b = m.matchIndex
                        val boardMatches = r2MatchesByBoard.filter { it.matchIndex in (2 * b)..(2 * b + 1) }.sortedBy { it.matchIndex }
                        val w0 = boardMatches.getOrNull(0)?.winnerId ?: 0L
                        val w1 = boardMatches.getOrNull(1)?.winnerId ?: 0L
                        m.copy(player1Id = w0, player2Id = w1)
                    }
                    if (updatedFinal.isNotEmpty()) {
                        tournamentMatchDao.insertMatches(updatedFinal)
                    }
                }
                else -> {}
            }
            return@withContext true
        }

        val participants = tournamentParticipantDao.getParticipantsForTournament(tournamentId)

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
            val stored = participants.filter { it.qualificationType in listOf("OOM", "PT_OOM") }.map { it.playerId }
            if (stored.isNotEmpty()) {
                stored.toSet()
            } else {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
                val top16Oom = sortedOomTc.take(16)
                val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
                val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries, allPlayers).take(16)
                (top16Oom + top16PtOom).toSet()
            }
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

        // 3. Update matches for next round
        val currentIdx = roundSequence.indexOf(currentRoundEnum)
        if (currentIdx == -1 || currentIdx >= roundSequence.size - 1) {
            return@withContext true
        }

        val nextRoundEnum = roundSequence[currentIdx + 1]
        val sortedCurrentMatches = currentRoundMatches.sortedBy { it.matchIndex }
        val winners = sortedCurrentMatches.mapNotNull { it.winnerId }

        when (event) {
            TournamentEvent.EUROPEAN_TOUR -> {
                if (currentRoundEnum == TournamentRound.L48) {
                    val existingL32 = allMatches.filter { it.round == TournamentRound.L32.name }.sortedBy { it.matchIndex }
                    val l48Winners = sortedCurrentMatches.mapNotNull { it.winnerId }
                    val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
                    val updatedL32 = existingL32.mapIndexed { i, m ->
                        val seedIndex = seedOrder[i]
                        val w = l48Winners.getOrNull(15 - seedIndex) ?: 0L
                        m.copy(player2Id = w)
                    }
                    if (updatedL32.isNotEmpty()) {
                        tournamentMatchDao.insertMatches(updatedL32)
                    }
                } else {
                    val existingNext = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }
                    val updatedNext = existingNext.mapIndexed { i, m ->
                        val w1 = winners.getOrNull(2 * i) ?: 0L
                        val w2 = winners.getOrNull(2 * i + 1) ?: 0L
                        m.copy(player1Id = w1, player2Id = w2)
                    }
                    if (updatedNext.isNotEmpty()) {
                        tournamentMatchDao.insertMatches(updatedNext)
                    }
                }
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val stored65To96 = participants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
                val stored33To64 = participants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
                val storedTop32 = participants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

                val sortedTcOom = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries, allPlayers)
                val fullOom = fillToTargetCount(sortedTcOom, 128, allPlayerIds, oomEntries, allPlayers)
                val oom65To96 = if (stored65To96.isNotEmpty()) stored65To96 else fullOom.drop(64).take(32)
                val oom33To64 = if (stored33To64.isNotEmpty()) stored33To64 else fullOom.drop(32).take(32)
                val top32Oom = if (storedTop32.isNotEmpty()) storedTop32 else fullOom.take(32)
                val shuffledWinners = winners.shuffled()

                val nextMatches = when (currentRoundEnum) {
                    TournamentRound.R1_160 -> {
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
                if (nextMatches.isNotEmpty()) {
                    tournamentMatchDao.insertMatches(nextMatches)
                }
            }

            else -> {
                val existingNext = allMatches.filter { it.round == nextRoundEnum.name }.sortedBy { it.matchIndex }
                val updatedNext = existingNext.mapIndexed { i, m ->
                    val w1 = winners.getOrNull(2 * i) ?: 0L
                    val w2 = winners.getOrNull(2 * i + 1) ?: 0L
                    m.copy(player1Id = w1, player2Id = w2)
                }
                if (updatedNext.isNotEmpty()) {
                    tournamentMatchDao.insertMatches(updatedNext)
                }
            }
        }

        return@withContext true
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
                    if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value
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
                    .thenByDescending { if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }
}
