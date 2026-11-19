package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedizierter Resolver für die Qualifikations- und Setzlistenlogik des Grand Slam of Darts.
 */
class GrandSlamQualificationResolver(
    private val seasonDao: SeasonDao,
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val resolveNonSimulatedQualifiersUseCase: ResolveNonSimulatedQualifiersUseCase,
    private val sortPlayerIdsByOom: (List<Long>, List<OrderOfMeritEntry>, List<Player>) -> List<Long>,
    private val fillToTargetCount: (List<Long>, Int, List<Long>, List<OrderOfMeritEntry>) -> List<Long>
) {

    /**
     * Resolves major event winners/finalists (up to 24) and additional non-tour card qualifiers (12)
     * for Grand Slam of Darts according to section 2.4.6.
     * Returns Pair(majorIds, additionalIds).
     */
    suspend fun resolveMajorAndAdditionalIds(
        seasonId: Long,
        tourCardPlayerIds: List<Long>,
        allPlayers: List<Player>,
        oomEntries: List<OrderOfMeritEntry>
    ): Pair<List<Long>, List<Long>> = withContext(Dispatchers.IO) {
        val season = seasonDao.getSeasonById(seasonId)
        val currentSeasonTournaments = tournamentDao.getTournamentsListForSeason(seasonId)
        val prevSeasonTournaments = if (season != null) {
            val careerSeasons = seasonDao.getSeasonsListForCareer(season.careerId)
            val prevSeason = careerSeasons.find { it.seasonNumber == season.seasonNumber - 1 }
            if (prevSeason != null) {
                tournamentDao.getTournamentsListForSeason(prevSeason.id)
            } else emptyList()
        } else emptyList()

        val allCompletedTournaments = (currentSeasonTournaments + prevSeasonTournaments)
            .filter { it.isCompleted }

        val majorPriorityList = listOf(
            TournamentEvent.WORLD_CHAMPIONSHIP,
            TournamentEvent.GRAND_SLAM_OF_DARTS,
            TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX,
            TournamentEvent.UK_OPEN,
            TournamentEvent.EUROPEAN_OPEN,
            TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS
        )

        val winnersAndRunnerUps = mutableListOf<Pair<Long, Long?>>()
        for (majorEvent in majorPriorityList) {
            val mostRecent = allCompletedTournaments
                .filter { it.type == majorEvent.name }
                .sortedWith(
                    compareByDescending<Tournament> { it.seasonId == seasonId }
                        .thenByDescending { it.order }
                )
                .firstOrNull()

            if (mostRecent != null) {
                val matches = tournamentMatchDao.getMatchesListForTournament(mostRecent.id)
                val finalMatch = matches.firstOrNull { it.round == TournamentRound.F.name && it.isCompleted && it.winnerId != null }
                val wId = finalMatch?.winnerId
                if (wId != null && (wId > 0L || wId == USER_ID)) {
                    val winnerId = wId
                    val runnerUpId = if (finalMatch.winnerId == finalMatch.player1Id) {
                        finalMatch.player2Id.takeIf { it > 0L || it == USER_ID }
                    } else {
                        finalMatch.player1Id.takeIf { it > 0L || it == USER_ID }
                    }
                    winnersAndRunnerUps.add(Pair(winnerId, runnerUpId))
                }
            }
        }

        val majorIds = mutableListOf<Long>()
        for ((winnerId, _) in winnersAndRunnerUps) {
            if (majorIds.size >= 24) break
            if (winnerId !in majorIds) {
                majorIds.add(winnerId)
            }
        }
        for ((_, runnerUpId) in winnersAndRunnerUps) {
            if (majorIds.size >= 24) break
            if (runnerUpId != null && runnerUpId !in majorIds) {
                majorIds.add(runnerUpId)
            }
        }

        val regionalRegions = listOf(
            ResolveNonSimulatedQualifiersUseCase.ASIAN_TOUR,
            ResolveNonSimulatedQualifiersUseCase.CDC_NORTH_AMERICA,
            ResolveNonSimulatedQualifiersUseCase.CDLC_LATIN_AMERICA,
            ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC,
            ResolveNonSimulatedQualifiersUseCase.OCEANIA
        )
        val excludedForAdd = (tourCardPlayerIds.toSet() + majorIds.toSet() + USER_ID).toMutableSet()
        val regionalQualifiers = mutableListOf<Long>()
        for (region in regionalRegions) {
            if (regionalQualifiers.size >= 4) break
            val resolved = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                regionCountries = region,
                count = 1,
                excludedPlayerIds = excludedForAdd,
                allPlayers = allPlayers,
                oomList = oomEntries
            )
            regionalQualifiers.addAll(resolved)
            excludedForAdd.addAll(resolved)
        }

        val unranked8 = resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(
            count = 8,
            excludedPlayerIds = excludedForAdd,
            allPlayers = allPlayers
        )
        val additionalIds = regionalQualifiers + unranked8

        Pair(majorIds, additionalIds)
    }

    /**
     * Zählt die Saisonsiege je Spieler für einen bestimmten Event-Typ.
     */
    private suspend fun countSeasonEventWinners(
        seasonTournaments: List<Tournament>,
        eventType: TournamentEvent
    ): Map<Long, Int> {
        val completedTournaments = seasonTournaments.filter { it.type == eventType.name && it.isCompleted }
        val winsMap = mutableMapOf<Long, Int>()
        for (t in completedTournaments) {
            val matches = tournamentMatchDao.getMatchesListForTournament(t.id)
            val finalMatch = matches.firstOrNull { it.round == TournamentRound.F.name && it.isCompleted && it.winnerId != null }
            if (finalMatch != null && finalMatch.winnerId != null && (finalMatch.winnerId > 0L || finalMatch.winnerId == USER_ID)) {
                val wId = finalMatch.winnerId
                winsMap[wId] = (winsMap[wId] ?: 0) + 1
            }
        }
        return winsMap
    }

    /**
     * Ermittelt alle vorqualifizierten Hauptfeld-Teilnehmer vor Durchführung des Grand Slam Qualifiers (bis zu 40 Plätze).
     */
    suspend fun resolvePreQualifiedBeforeQualifier(
        mainTournamentId: Long,
        seasonId: Long,
        tourCardPlayerIds: List<Long>,
        allPlayers: List<Player>,
        oomEntries: List<OrderOfMeritEntry>
    ): List<TournamentParticipant> = withContext(Dispatchers.IO) {
        val seasonTournaments = tournamentDao.getTournamentsListForSeason(seasonId)
        val (majorIds, additionalIds) = resolveMajorAndAdditionalIds(
            seasonId = seasonId,
            tourCardPlayerIds = tourCardPlayerIds,
            allPlayers = allPlayers,
            oomEntries = oomEntries
        )

        val participants = mutableListOf<TournamentParticipant>()
        val qualifiedSoFar = mutableListOf<Long>()

        // 1. majorIds (qualificationType = "MAJOR")
        majorIds.forEach { playerId ->
            if (qualifiedSoFar.size < 40 && playerId !in qualifiedSoFar) {
                qualifiedSoFar.add(playerId)
                participants.add(
                    TournamentParticipant(
                        tournamentId = mainTournamentId,
                        playerId = playerId,
                        seedNumber = null,
                        qualificationType = "MAJOR"
                    )
                )
            }
        }

        // 2. additionalIds (qualificationType = "ADDITIONAL")
        additionalIds.forEach { playerId ->
            if (qualifiedSoFar.size < 40 && playerId !in qualifiedSoFar) {
                qualifiedSoFar.add(playerId)
                participants.add(
                    TournamentParticipant(
                        tournamentId = mainTournamentId,
                        playerId = playerId,
                        seedNumber = null,
                        qualificationType = "ADDITIONAL"
                    )
                )
            }
        }

        // 3. EUROPEAN_TOUR-Saisonsieger (sortiert nach Anzahl ET-Siege DESC, dann OOM)
        val etWinsMap = countSeasonEventWinners(seasonTournaments, TournamentEvent.EUROPEAN_TOUR)
        val sortedEtWinners = sortPlayerIdsByOom(etWinsMap.keys.toList(), oomEntries, allPlayers)
            .sortedByDescending { etWinsMap[it] ?: 0 }

        for (etWinner in sortedEtWinners) {
            if (qualifiedSoFar.size >= 40) break
            if (etWinner !in qualifiedSoFar) {
                qualifiedSoFar.add(etWinner)
                participants.add(
                    TournamentParticipant(
                        tournamentId = mainTournamentId,
                        playerId = etWinner,
                        seedNumber = null,
                        qualificationType = "PRO_TOUR_WINNER"
                    )
                )
            }
        }

        // 4. PLAYERS_CHAMPIONSHIP-Saisonsieger (sortiert nach Anzahl PC-Siege DESC, dann OOM)
        if (qualifiedSoFar.size < 40) {
            val pcWinsMap = countSeasonEventWinners(seasonTournaments, TournamentEvent.PLAYERS_CHAMPIONSHIP)
            val sortedPcWinners = sortPlayerIdsByOom(pcWinsMap.keys.toList(), oomEntries, allPlayers)
                .sortedByDescending { pcWinsMap[it] ?: 0 }

            for (pcWinner in sortedPcWinners) {
                if (qualifiedSoFar.size >= 40) break
                if (pcWinner !in qualifiedSoFar) {
                    qualifiedSoFar.add(pcWinner)
                    participants.add(
                        TournamentParticipant(
                            tournamentId = mainTournamentId,
                            playerId = pcWinner,
                            seedNumber = null,
                            qualificationType = "PRO_TOUR_WINNER"
                        )
                    )
                }
            }
        }

        participants
    }

    /**
     * Finalisiert das Hauptfeld des Grand Slam of Darts mit Qualifier-Siegern und Töpfen 1-3.
     */
    suspend fun finalizeMainTournamentField(
        tournamentId: Long,
        storedParticipants: List<TournamentParticipant>,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult = withContext(Dispatchers.IO) {
        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()
        val neededQualifierCount = (48 - storedParticipants.size).coerceAtLeast(0)

        var tchqWinners = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (tchqWinners.size > neededQualifierCount) {
            tchqWinners = tchqWinners.take(neededQualifierCount)
        }
        if (tchqWinners.size < neededQualifierCount) {
            val nonPreqTc = tourCardPlayerIds.filterNot { it in existingPlayerIds || it in tchqWinners }
            tchqWinners = fillToTargetCount(tchqWinners, neededQualifierCount, nonPreqTc, oomEntries)
        }

        val qualified48Ids = (storedParticipants.map { it.playerId } + tchqWinners).distinct()
        val final48Ids = fillToTargetCount(qualified48Ids, 48, allPlayerIds, oomEntries)

        val sorted48 = sortPlayerIdsByOom(final48Ids, oomEntries, allPlayers)
        val pot1 = sorted48.take(16)
        val pot2 = sorted48.drop(16).take(16)
        val pot3 = sorted48.drop(32).take(16)

        val finalParticipants = pot1.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = "OOM"
            )
        } + pot2.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "POT_2"
            )
        } + pot3.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "POT_3"
            )
        }

        tournamentParticipantDao.insertParticipants(finalParticipants)

        TournamentQualificationResult(
            oomSortedIds = pot1,
            proTourSortedIds = pot2,
            qualifierIds = pot3,
            participants = finalParticipants
        )
    }
}
