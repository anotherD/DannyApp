package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.MatchPairing
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateBracketUseCase(
    private val tournamentDao: TournamentDao,
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        GetOrderOfMeritUseCase(context)
    )

    suspend operator fun invoke(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val tournament = tournamentDao.getTournamentById(tournamentId)
            ?: throw IllegalArgumentException("Tournament not found: $tournamentId")
        val season = seasonDao.getSeasonById(tournament.seasonId)
            ?: throw IllegalArgumentException("Season not found for tournament: ${tournament.seasonId}")

        val existingMatches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (existingMatches.isNotEmpty()) {
            return@withContext existingMatches
        }

        val event = TournamentEvent.valueOf(tournament.type)
        val careerId = season.careerId

        // 1. Fetch Tour Cards for this season
        val tourCardsWithPlayer = tourCardDao.getTourCardsListForSeason(season.id)
        val tourCardPlayerIds = tourCardsWithPlayer.map { it.tourCard.playerId }.distinct()

        // 2. Fetch All Players
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        // 3. Fetch Orders of Merit
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        // Helper sorting function for player IDs using Order of Merit rank / prize money / skill / name
        fun getPlayerListSortedByOom(playerIds: List<Long>, oomList: List<OrderOfMeritEntry>): List<Long> {
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

        // Helper to get winners from preceding qualifier tournament
        suspend fun getQualifierWinnersFromPreceding(targetCount: Int): List<Long> {
            val prevTournament = tournamentDao.getTournamentsListForSeason(season.id)
                .find { it.order == tournament.order - 1 } ?: return emptyList()
            val prevMatches = tournamentMatchDao.getMatchesListForTournament(prevTournament.id)
            if (prevMatches.isEmpty()) return emptyList()

            val rounds = prevMatches.map { it.round }.distinct()
            val finalRoundName = rounds.lastOrNull { r ->
                prevMatches.filter { it.round == r }.all { it.isCompleted && it.winnerId != null }
            } ?: rounds.lastOrNull() ?: return emptyList()

            val finalRoundMatches = prevMatches.filter { it.round == finalRoundName }
            val winners = finalRoundMatches.mapNotNull { it.winnerId }
            if (winners.size >= targetCount) {
                return winners.take(targetCount)
            }
            return winners
        }

        // Pre-qualified sets for ET and Grand Slam
        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries).take(16)
        val etPreQualified = (top16Oom + top16PtOom).toSet()

        val nonPreqEtTc = tourCardPlayerIds.filterNot { it in etPreQualified }

        val matchPairings: List<MatchPairing> = when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedByPt = getPlayerListSortedByOom(tcIds, ptOomEntries)
                val top32 = sortedByPt.take(32)
                val remaining96 = sortedByPt.drop(32)
                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedByPt,
                    proTourSortedIds = top32,
                    qualifierIds = remaining96
                )
            }

            TournamentEvent.QUALIFIER_ET -> {
                val sortedNonPreq = getPlayerListSortedByOom(nonPreqEtTc, oomEntries)
                val participants = fillToTargetCount(sortedNonPreq, 40, tourCardPlayerIds, oomEntries)
                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = participants
                )
            }

            TournamentEvent.EUROPEAN_TOUR -> {
                var tcQualifiers = getQualifierWinnersFromPreceding(10)
                if (tcQualifiers.size < 10) {
                    tcQualifiers = fillToTargetCount(tcQualifiers, 10, nonPreqEtTc, oomEntries)
                }

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val hostCountry = tournament.hostCountry ?: "GER"
                val hostCountryNonTc = nonTcPlayerIds.filter { id ->
                    val player = allPlayers.find { it.id == id }
                    player?.countryCode == hostCountry && player.tourCardYear == 0
                }
                val hostQualifiers = fillToTargetCount(hostCountryNonTc, 4, nonTcPlayerIds, oomEntries).take(4)
                val remainingNonTc = nonTcPlayerIds.filterNot { it in hostQualifiers }
                val otherQualifiers = getPlayerListSortedByOom(remainingNonTc, oomEntries).take(2)

                var qualifiersPool = tcQualifiers + hostQualifiers + otherQualifiers
                if (qualifiersPool.size < 16) {
                    val candidatePool = allPlayerIds.filterNot { it in etPreQualified || it in qualifiersPool }
                    qualifiersPool = fillToTargetCount(qualifiersPool, 16, candidatePool, oomEntries)
                }

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16PtOom,
                    qualifierIds = qualifiersPool
                )
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedTcOom = getPlayerListSortedByOom(tcIds, oomEntries)

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tcIds }
                val nonTcQualifiers = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(32)
                val final32Qualifiers = fillToTargetCount(nonTcQualifiers, 32, allPlayerIds.filterNot { it in tcIds }, oomEntries)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = sortedTcOom,
                    qualifierIds = final32Qualifiers
                )
            }

            TournamentEvent.WORLD_MATCHPLAY -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                val top16Oom = sortedOom.take(16)
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val top16Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt
                )
            }

            TournamentEvent.WORLD_GRAND_PRIX -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                val top16Oom = sortedOom.take(16)
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val top16Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt
                )
            }

            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val top32Et = getPlayerListSortedByOom(allPlayerIds, etOomEntries).take(32)
                val final32Et = fillToTargetCount(top32Et, 32, allPlayerIds, oomEntries)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = final32Et
                )
            }

            TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                val remainingForPt = tourCardPlayerIds.filterNot { it in top16Oom }
                val gsPreq = top16Oom + getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)
                val nonPreqGsTc = tourCardPlayerIds.filterNot { it in gsPreq }
                val participants = fillToTargetCount(getPlayerListSortedByOom(nonPreqGsTc, oomEntries), 32, tourCardPlayerIds, oomEntries)
                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = participants
                )
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val pot2 = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(16)

                var gsTchqWinners = getQualifierWinnersFromPreceding(8)
                if (gsTchqWinners.size < 8) {
                    gsTchqWinners = fillToTargetCount(gsTchqWinners, 8, tourCardPlayerIds, oomEntries)
                }
                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val otherQuals = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(8)
                val pot3 = fillToTargetCount(gsTchqWinners + otherQuals, 16, allPlayerIds, oomEntries)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top16Oom,
                    proTourSortedIds = pot2,
                    qualifierIds = pot3
                )
            }

            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val top64Pc = getPlayerListSortedByOom(allPlayerIds, pcOomEntries).take(64)
                val final64Pc = fillToTargetCount(top64Pc, 64, allPlayerIds, oomEntries)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = final64Pc
                )
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                val top40Oom = sortedOom.take(40)

                val remainingForPt = allPlayerIds.filterNot { it in top40Oom }
                val top40Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(40)

                val tc80 = top40Oom + top40Pt
                val sortedTc80ByOom = getPlayerListSortedByOom(tc80, oomEntries)
                val top32Oom = sortedTc80ByOom.take(32)
                val ranks33To64 = sortedTc80ByOom.drop(32).take(32)
                val remaining16 = sortedTc80ByOom.drop(64)

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds || it in tc80 }
                val nonTc48 = getPlayerListSortedByOom(nonTcPlayerIds, oomEntries).take(48)
                val remaining64Pool = fillToTargetCount(remaining16 + nonTc48, 64, allPlayerIds.filterNot { it in top32Oom || it in ranks33To64 }, oomEntries)

                BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = top32Oom + ranks33To64,
                    qualifierIds = remaining64Pool
                )
            }
        }

        val tournamentMatches = matchPairings.map { pairing ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = pairing.round.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id,
                groupName = pairing.groupName
            )
        }

        tournamentMatchDao.insertMatches(tournamentMatches)
        tournamentMatchDao.getMatchesListForTournament(tournamentId)
    }

    private fun fillToTargetCount(
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
}
