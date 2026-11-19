package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Domain representation of UK Open / European Open entrant groups. */
enum class EntrantGroupType {
    OOM_65_96,
    OOM_33_64,
    OOM_TOP_32,
    ROUND_WINNERS
}

/** Pure domain model for a player entry in an entrant group. */
data class EntrantPlayerDomainInfo(
    val id: Long,
    val name: String,
    val countryCode: String,
    val seed: Int? = null,
    val skill: Int = SkillLevel.DEFAULT.value
)

/** Pure domain model for an entrant group. */
data class EntrantGroupDomainData(
    val groupType: EntrantGroupType,
    val prevRoundName: String? = null,
    val players: List<EntrantPlayerDomainInfo>
)

/** Pure domain model representing the split of players for a qualifier event. */
data class QualifierFieldSplit(
    val preQualifiedIds: List<Long>,
    val nonPrequalifiedIds: List<Long>,
    val boardCount: Int,
    val prelimPlayers: List<Long>,
    val byePlayers: List<Long>,
    val seedCount: Int
)

/**
 * Strukturiertes Ergebnis der Qualifikations- und Teilnehmerpool-Ermittlung
 * als zentrale Single Source of Truth für Turnierstart und Auslosung.
 */
data class TournamentQualificationResult(
    val oomSortedIds: List<Long> = emptyList(),
    val proTourSortedIds: List<Long> = emptyList(),
    val qualifierIds: List<Long> = emptyList(),
    val participants: List<TournamentParticipant> = emptyList(),
    val preQualifiedOomIds: List<Long> = emptyList(),
    val preQualifiedPtOomIds: List<Long> = emptyList(),
    val qualifierFieldSplit: QualifierFieldSplit? = null
)

/**
 * Central domain use-case for tournament qualification, seeding, player sorting, fill-to-target logic,
 * and qualifier board assignment.
 */
class TournamentQualificationUseCase(
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val careerDao: CareerDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase,
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
        AppDatabase.getDatabase(context.applicationContext).careerDao(),
        GetOrderOfMeritUseCase(context),
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao()
    )

    private val resolveNonSimulatedQualifiersUseCase = ResolveNonSimulatedQualifiersUseCase()

    /**
     * Determines the sequence of rounds for a given [TournamentEvent].
     * If [allMatches] is provided and non-empty, truncates the sequence to start from the earliest round present in matches.
     */
    fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        val fullSequence = event.getRoundSequence()

        if (allMatches.isEmpty()) return fullSequence

        val startIndex = allMatches.mapNotNull { match ->
            fullSequence.indexOfFirst { it.name == match.round }.takeIf { it >= 0 }
        }.minOrNull() ?: 0

        return fullSequence.subList(startIndex, fullSequence.size)
    }

    /**
     * Sorts a list of player IDs based on Order of Merit ranking, prize money, skill level, and name.
     */
    fun sortPlayerIdsByOom(
        playerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        val oomMap = oomList.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }

        return playerIds.sortedWith(
            compareBy<Long> { oomMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { oomMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending {
                    if (it == USER_ID) SkillLevel.DEFAULT.value else dbPlayerMap[it]?.skill ?: SkillLevel.DEFAULT.value
                }
                .thenBy {
                    if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: ""
                }
        )
    }

    /**
     * Fills an initial list of player IDs to a target count using fallback candidates sorted by Order of Merit.
     */
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
                    .thenByDescending { oomMap[it]?.skill ?: SkillLevel.DEFAULT.value }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }

    /**
     * Resolves prequalified Tour Card holders (top OOM and top Pro Tour OOM) for events or qualifiers.
     * Returns a pair of (topOomIds, topPtOomIds).
     */
    fun resolvePrequalifiedTourCardIds(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): Pair<List<Long>, List<Long>> {
        val isWorldChampionship = event == TournamentEvent.QUALIFIER_WM || event == TournamentEvent.WORLD_CHAMPIONSHIP
        val targetOomCount = if (isWorldChampionship) 40 else 16
        val targetPtOomCount = if (isWorldChampionship) 40 else 16

        val sortedOomTc = sortPlayerIdsByOom(tourCardPlayerIds, oomList, allPlayers)
        val topOomIds = sortedOomTc.take(targetOomCount)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in topOomIds }
        val topPtOomIds = sortPlayerIdsByOom(remainingTcAfterOom, ptOomList, allPlayers).take(targetPtOomCount)

        return Pair(topOomIds, topPtOomIds)
    }

    /**
     * Resolves major event winners/finalists (up to 24) and additional non-tour card qualifiers (12)
     * for Grand Slam of Darts according to section 2.4.6.
     * Returns Pair(majorIds, additionalIds).
     */
    suspend fun resolveGrandSlamMajorAndAdditionalIds(
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
                if (wId != null && wId > 0L) {
                    val winnerId = wId
                    val runnerUpId = if (finalMatch.winnerId == finalMatch.player1Id) {
                        finalMatch.player2Id.takeIf { it > 0L }
                    } else {
                        finalMatch.player1Id.takeIf { it > 0L }
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
     * Calculates the dynamic qualifier board count (K) for qualifier events.
     * For QUALIFIER_GRAND_SLAM: (40 - preQualifiedCount).coerceAtLeast(1).
     * For other events: event.qualifierBoardCount.
     */
    suspend fun calculateDynamicQualifierBoardCount(
        event: TournamentEvent,
        seasonId: Long,
        tourCardPlayerIds: List<Long>,
        allPlayers: List<Player>,
        oomEntries: List<OrderOfMeritEntry>
    ): Int {
        if (event == TournamentEvent.QUALIFIER_GRAND_SLAM) {
            val (majorIds, additionalIds) = resolveGrandSlamMajorAndAdditionalIds(
                seasonId = seasonId,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayers = allPlayers,
                oomEntries = oomEntries
            )
            val preQualifiedCount = (majorIds + additionalIds).distinct().size
            return (40 - preQualifiedCount).coerceAtLeast(1)
        }
        return if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4
    }

    /**
     * Central resolution of the qualifier field split into prequalified players, non-prequalified players,
     * preliminary round players, R1 bye players, and seed count.
     */
    fun resolveQualifierFieldSplit(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        dynamicBoardCount: Int? = null
    ): QualifierFieldSplit {
        val (topOomIds, topPtOomIds) = resolvePrequalifiedTourCardIds(
            event = event,
            tourCardPlayerIds = tourCardPlayerIds,
            oomList = oomList,
            ptOomList = ptOomList,
            allPlayers = allPlayers
        )
        val preQualifiedIds = (topOomIds + topPtOomIds).distinct()
        val remainingTc = tourCardPlayerIds.filterNot { it in preQualifiedIds }
        val nonPrequalifiedIds = sortPlayerIdsByOom(remainingTc, oomList, allPlayers)

        val boardCount = if (dynamicBoardCount != null && dynamicBoardCount > 0) {
            dynamicBoardCount
        } else if (event.qualifierBoardCount > 0) {
            event.qualifierBoardCount
        } else {
            4
        }

        val totalNonPrequalified = nonPrequalifiedIds.size

        val prelimPlayers = if (totalNonPrequalified > 8 * boardCount) {
            nonPrequalifiedIds.takeLast(2 * (totalNonPrequalified - 8 * boardCount))
        } else {
            emptyList()
        }

        val byePlayers = if (totalNonPrequalified > 8 * boardCount) {
            nonPrequalifiedIds.take((16 * boardCount - totalNonPrequalified).coerceAtLeast(0))
        } else {
            nonPrequalifiedIds
        }

        val seedCount = (2 * boardCount).coerceAtMost(totalNonPrequalified)

        return QualifierFieldSplit(
            preQualifiedIds = preQualifiedIds,
            nonPrequalifiedIds = nonPrequalifiedIds,
            boardCount = boardCount,
            prelimPlayers = prelimPlayers,
            byePlayers = byePlayers,
            seedCount = seedCount
        )
    }

    /**
     * Zentrale Single Source of Truth für Qualifikation, Töpfe, Setzlisten und Teilnehmer
     * aller PDC-Turnierarten und Qualifiers.
     */
    suspend fun resolveTournamentQualification(
        tournament: Tournament,
        seasonId: Long,
        careerId: Long,
        precedingQualifierWinners: List<Long> = emptyList()
    ): TournamentQualificationResult = withContext(Dispatchers.IO) {
        val event = TournamentEvent.valueOf(tournament.type)
        val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
        val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        val tournamentId = tournament.id

        when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> resolvePlayersChampionshipQualification(
                tournamentId = tournamentId,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM -> resolveQualifierQualification(
                tournamentId = tournamentId,
                event = event,
                seasonId = seasonId,
                tourCardPlayerIds = tourCardPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.EUROPEAN_TOUR -> resolveEuropeanTourQualification(
                tournamentId = tournamentId,
                event = event,
                hostCountry = tournament.hostCountry,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.UK_OPEN,
            TournamentEvent.EUROPEAN_OPEN -> resolveUkOrEuropeanOpenQualification(
                tournamentId = tournamentId,
                event = event,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX -> resolveOomAndProTourQualification(
                tournamentId = tournamentId,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> resolveSingleRankingQualification(
                tournamentId = tournamentId,
                targetCount = 32,
                qualificationType = "ET_OOM",
                rankingEntries = etOomEntries,
                allPlayerIds = allPlayerIds,
                allPlayers = allPlayers,
                oomEntries = oomEntries
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> resolveSingleRankingQualification(
                tournamentId = tournamentId,
                targetCount = 64,
                qualificationType = "PC_OOM",
                rankingEntries = pcOomEntries,
                allPlayerIds = allPlayerIds,
                allPlayers = allPlayers,
                oomEntries = oomEntries
            )
            TournamentEvent.GRAND_SLAM_OF_DARTS -> resolveGrandSlamQualification(
                tournamentId = tournamentId,
                event = event,
                seasonId = seasonId,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.WORLD_CHAMPIONSHIP -> resolveWorldChampionshipQualification(
                tournamentId = tournamentId,
                event = event,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
        }
    }

    /**
     * Ermittelt Teilnehmer und Setzliste für ein Players Championship Turnier.
     */
    private fun resolvePlayersChampionshipQualification(
        tournamentId: Long,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val tcIds = if (tourCardPlayerIds.size >= 128) {
            tourCardPlayerIds.take(128)
        } else {
            val needed = 128 - tourCardPlayerIds.size
            val excluded = tourCardPlayerIds.toSet() + USER_ID
            val unrankedFill = resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(needed, excluded, allPlayers)
            tourCardPlayerIds + unrankedFill
        }
        val sortedByPt = sortPlayerIdsByOom(tcIds, ptOomEntries, allPlayers)
        val top32 = sortedByPt.take(32)
        val remaining96 = sortedByPt.drop(32)

        val participants = top32.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = "PT_OOM"
            )
        } + remaining96.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "TOUR_CARD"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = sortedByPt,
            proTourSortedIds = top32,
            qualifierIds = remaining96,
            participants = participants
        )
    }

    /**
     * Ermittelt Teilnehmer und Board-Aufteilung für Tour Card Holder Qualifier Turniere.
     */
    private suspend fun resolveQualifierQualification(
        tournamentId: Long,
        event: TournamentEvent,
        seasonId: Long,
        tourCardPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val dynamicBoardCount = calculateDynamicQualifierBoardCount(
            event = event,
            seasonId = seasonId,
            tourCardPlayerIds = tourCardPlayerIds,
            allPlayers = allPlayers,
            oomEntries = oomEntries
        )

        val split = resolveQualifierFieldSplit(
            event = event,
            tourCardPlayerIds = tourCardPlayerIds,
            oomList = oomEntries,
            ptOomList = ptOomEntries,
            allPlayers = allPlayers,
            dynamicBoardCount = dynamicBoardCount
        )

        val participants = split.nonPrequalifiedIds.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = if (index < split.seedCount) index + 1 else null,
                qualificationType = "TOUR_CARD"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = split.nonPrequalifiedIds,
            proTourSortedIds = emptyList(),
            qualifierIds = split.nonPrequalifiedIds,
            participants = participants,
            qualifierFieldSplit = split
        )
    }

    /**
     * Ermittelt Teilnehmer, Setzliste und Qualifier-Pools für European Tour Events.
     */
    private fun resolveEuropeanTourQualification(
        tournamentId: Long,
        event: TournamentEvent,
        hostCountry: String?,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val (top16Oom, top16PtOom) = resolvePrequalifiedTourCardIds(
            event = event,
            tourCardPlayerIds = tourCardPlayerIds,
            oomList = oomEntries,
            ptOomList = ptOomEntries,
            allPlayers = allPlayers
        )
        val etPreQualified = (top16Oom + top16PtOom).toSet()
        val nonPreqEtTc = tourCardPlayerIds.filterNot { it in etPreQualified }

        var tcQualifiers = if (precedingQualifierWinners.size >= 10) {
            precedingQualifierWinners.take(10)
        } else {
            precedingQualifierWinners
        }
        if (tcQualifiers.size < 10) {
            tcQualifiers = fillToTargetCount(tcQualifiers, 10, nonPreqEtTc, oomEntries)
        }

        val effectiveHostCountry = hostCountry ?: Country.DEFAULT_CODE
        val excludedBase = tourCardPlayerIds.toSet() + etPreQualified + tcQualifiers.toSet() + USER_ID

        val hostQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
            setOf(effectiveHostCountry), 4, excludedBase, allPlayers, oomEntries
        )
        val excludedAfterHost = excludedBase + hostQualifiers.toSet()

        val nordicBalticQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
            ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC, 1, excludedAfterHost, allPlayers, oomEntries
        )
        val excludedAfterNb = excludedAfterHost + nordicBalticQualifiers.toSet()

        val eastEuropeQualifiers = resolveNonSimulatedQualifiersUseCase.resolveRegional(
            ResolveNonSimulatedQualifiersUseCase.EAST_SOUTH_EAST_EUROPE, 1, excludedAfterNb, allPlayers, oomEntries
        )
        val otherQualifiers = nordicBalticQualifiers + eastEuropeQualifiers

        var qualifiersPool = tcQualifiers + hostQualifiers + otherQualifiers
        if (qualifiersPool.size < 16) {
            val candidatePool = allPlayerIds.filterNot { it in etPreQualified || it in qualifiersPool }
            qualifiersPool = fillToTargetCount(qualifiersPool, 16, candidatePool, oomEntries)
        }

        val participantList = mutableListOf<TournamentParticipant>()
        top16Oom.forEachIndexed { index, playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = index + 1,
                    qualificationType = "OOM"
                )
            )
        }
        top16PtOom.forEachIndexed { index, playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = index + 1,
                    qualificationType = "PT_OOM"
                )
            )
        }
        tcQualifiers.forEach { playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = null,
                    qualificationType = "QUALIFIER"
                )
            )
        }
        hostQualifiers.forEach { playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = null,
                    qualificationType = "HOST_NATION"
                )
            )
        }
        otherQualifiers.forEach { playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = null,
                    qualificationType = "QUALIFIER"
                )
            )
        }
        qualifiersPool.filterNot { qualifierPlayerId -> participantList.any { it.playerId == qualifierPlayerId } }.forEach { playerId ->
            participantList.add(
                TournamentParticipant(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    seedNumber = null,
                    qualificationType = "QUALIFIER"
                )
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = top16Oom,
            proTourSortedIds = top16PtOom,
            qualifierIds = qualifiersPool,
            participants = participantList,
            preQualifiedOomIds = top16Oom,
            preQualifiedPtOomIds = top16PtOom
        )
    }

    /**
     * Ermittelt Teilnehmer und Einstiegsrunden für UK Open und European Open.
     */
    private fun resolveUkOrEuropeanOpenQualification(
        tournamentId: Long,
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
        val sortedTcOom = sortPlayerIdsByOom(tcIds, oomEntries, allPlayers)

        val excludedForNonTc = tcIds.toSet() + USER_ID
        val final32Qualifiers = if (event == TournamentEvent.UK_OPEN) {
            resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(32, excludedForNonTc, allPlayers)
        } else {
            val regionalSet = ResolveNonSimulatedQualifiersUseCase.DACH +
                ResolveNonSimulatedQualifiersUseCase.NLD_BEL +
                ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC +
                ResolveNonSimulatedQualifiersUseCase.EAST_SOUTH_EAST_EUROPE +
                ResolveNonSimulatedQualifiersUseCase.MEDITERRANEAN
            val regional16 = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                regionalSet, 16, excludedForNonTc, allPlayers, oomEntries
            )
            val excludedForUnranked = excludedForNonTc + regional16.toSet()
            val unranked16 = resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(16, excludedForUnranked, allPlayers)
            regional16 + unranked16
        }
        val final32QualifiersFull = fillToTargetCount(final32Qualifiers, 32, allPlayerIds.filterNot { it in tcIds }, oomEntries)

        val participants = sortedTcOom.take(32).mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = "OOM_TOP_32"
            )
        } + sortedTcOom.drop(32).take(32).mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = 33 + index,
                qualificationType = "OOM_33_64"
            )
        } + sortedTcOom.drop(64).take(32).mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = 65 + index,
                qualificationType = "OOM_65_96"
            )
        } + sortedTcOom.drop(96).take(32).map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "OOM_97_128"
            )
        } + final32QualifiersFull.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "QUALIFIER"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = sortedTcOom,
            proTourSortedIds = emptyList(),
            qualifierIds = final32QualifiersFull,
            participants = participants
        )
    }

    /**
     * Ermittelt Teilnehmer und Setzliste basierend auf OoM (Top 16) und ProTour OoM (nächste 16)
     * für World Matchplay und World Grand Prix.
     */
    private fun resolveOomAndProTourQualification(
        tournamentId: Long,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val sortedOom = sortPlayerIdsByOom(allPlayerIds, oomEntries, allPlayers)
        val top16Oom = sortedOom.take(16)
        val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
        val top16Pt = sortPlayerIdsByOom(remainingForPt, ptOomEntries, allPlayers).take(16)

        val participants = top16Oom.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = "OOM"
            )
        } + top16Pt.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "PT_OOM"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = top16Oom,
            proTourSortedIds = top16Pt,
            qualifierIds = emptyList(),
            participants = participants
        )
    }

    /**
     * Ermittelt Teilnehmer und Setzliste basierend auf einer einzelnen Rangliste
     * (z. B. ET OoM für European Championship, PC OoM für Players Championship Finals).
     */
    private fun resolveSingleRankingQualification(
        tournamentId: Long,
        targetCount: Int,
        qualificationType: String,
        rankingEntries: List<OrderOfMeritEntry>,
        allPlayerIds: List<Long>,
        allPlayers: List<Player>,
        oomEntries: List<OrderOfMeritEntry>
    ): TournamentQualificationResult {
        val topPlayers = sortPlayerIdsByOom(allPlayerIds, rankingEntries, allPlayers).take(targetCount)
        val finalPlayers = fillToTargetCount(topPlayers, targetCount, allPlayerIds, oomEntries)

        val participants = finalPlayers.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = qualificationType
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = emptyList(),
            proTourSortedIds = emptyList(),
            qualifierIds = finalPlayers,
            participants = participants
        )
    }

    /**
     * Ermittelt Teilnehmer, Setzliste und Lostöpfe für den Grand Slam of Darts gemäß Abschnitt 2.4.6.
     */
    private suspend fun resolveGrandSlamQualification(
        tournamentId: Long,
        event: TournamentEvent,
        seasonId: Long,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        // 1. Major IDs + Additional IDs
        val (majorIds, additionalIds) = resolveGrandSlamMajorAndAdditionalIds(
            seasonId = seasonId,
            tourCardPlayerIds = tourCardPlayerIds,
            allPlayers = allPlayers,
            oomEntries = oomEntries
        )

        val qualifiedSoFar = (majorIds + additionalIds).distinct().toMutableList()

        // 2. Punkt 3 (QUALIFIER_GRAND_SLAM-Sieger)
        val k = calculateDynamicQualifierBoardCount(
            event = TournamentEvent.QUALIFIER_GRAND_SLAM,
            seasonId = seasonId,
            tourCardPlayerIds = tourCardPlayerIds,
            allPlayers = allPlayers,
            oomEntries = oomEntries
        )

        val tchqWinners = if (precedingQualifierWinners.isNotEmpty()) {
            precedingQualifierWinners.distinct().filterNot { it in qualifiedSoFar }
        } else {
            emptyList()
        }

        val neededTchqCount = k - tchqWinners.size
        val dynamicTchqWinners = if (neededTchqCount > 0) {
            val remainingTc = tourCardPlayerIds.filterNot { it in qualifiedSoFar || it in tchqWinners }
            val sortedRemainingTc = sortPlayerIdsByOom(remainingTc, oomEntries, allPlayers)
            tchqWinners + sortedRemainingTc.take(neededTchqCount)
        } else {
            tchqWinners
        }

        for (pId in dynamicTchqWinners) {
            if (pId !in qualifiedSoFar) {
                qualifiedSoFar.add(pId)
            }
        }

        // 3. Punkt 4 (Auffüllen auf 40 Spieler – ET- & PC-Saisonsieger)
        val seasonTournaments = tournamentDao.getTournamentsListForSeason(seasonId)
        val completedEtTournaments = seasonTournaments.filter { it.type == TournamentEvent.EUROPEAN_TOUR.name && it.isCompleted }
        val etWinsMap = mutableMapOf<Long, Int>()
        for (t in completedEtTournaments) {
            val matches = tournamentMatchDao.getMatchesListForTournament(t.id)
            val finalMatch = matches.firstOrNull { it.round == TournamentRound.F.name && it.isCompleted && it.winnerId != null }
            if (finalMatch != null && finalMatch.winnerId != null && finalMatch.winnerId > 0L) {
                val wId = finalMatch.winnerId
                etWinsMap[wId] = (etWinsMap[wId] ?: 0) + 1
            }
        }
        val sortedEtWinners = sortPlayerIdsByOom(etWinsMap.keys.toList(), oomEntries, allPlayers)
            .sortedByDescending { etWinsMap[it] ?: 0 }

        for (etWinner in sortedEtWinners) {
            if (qualifiedSoFar.size >= 40) break
            if (etWinner !in qualifiedSoFar) {
                qualifiedSoFar.add(etWinner)
            }
        }

        if (qualifiedSoFar.size < 40) {
            val completedPcTournaments = seasonTournaments.filter { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP.name && it.isCompleted }
            val pcWinsMap = mutableMapOf<Long, Int>()
            for (t in completedPcTournaments) {
                val matches = tournamentMatchDao.getMatchesListForTournament(t.id)
                val finalMatch = matches.firstOrNull { it.round == TournamentRound.F.name && it.isCompleted && it.winnerId != null }
                if (finalMatch != null && finalMatch.winnerId != null && finalMatch.winnerId > 0L) {
                    val wId = finalMatch.winnerId
                    pcWinsMap[wId] = (pcWinsMap[wId] ?: 0) + 1
                }
            }
            val sortedPcWinners = sortPlayerIdsByOom(pcWinsMap.keys.toList(), oomEntries, allPlayers)
                .sortedByDescending { pcWinsMap[it] ?: 0 }

            for (pcWinner in sortedPcWinners) {
                if (qualifiedSoFar.size >= 40) break
                if (pcWinner !in qualifiedSoFar) {
                    qualifiedSoFar.add(pcWinner)
                }
            }
        }

        // 4. Punkt 5 (Auffüllen auf 48 Spieler)
        val final48 = fillToTargetCount(qualifiedSoFar, 48, allPlayerIds, oomEntries)

        // 5. 3 Lostöpfe (je 16 Spieler) nach aktueller OOM sortieren
        val sorted48 = sortPlayerIdsByOom(final48, oomEntries, allPlayers)
        val pot1 = sorted48.take(16)
        val pot2 = sorted48.drop(16).take(16)
        val pot3 = sorted48.drop(32).take(16)

        val participants = pot1.mapIndexed { index, playerId ->
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

        return TournamentQualificationResult(
            oomSortedIds = pot1,
            proTourSortedIds = pot2,
            qualifierIds = pot3,
            participants = participants
        )
    }

    /**
     * Ermittelt Teilnehmer, gesetzte Spieler und Qualifier-Pools für die Weltmeisterschaft.
     */
    private fun resolveWorldChampionshipQualification(
        tournamentId: Long,
        event: TournamentEvent,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val (top40Oom, top40PtOom) = resolvePrequalifiedTourCardIds(
            event = event,
            tourCardPlayerIds = tourCardPlayerIds,
            oomList = oomEntries,
            ptOomList = ptOomEntries,
            allPlayers = allPlayers
        )

        val tc80 = top40Oom + top40PtOom
        val sortedTc80ByOom = sortPlayerIdsByOom(tc80, oomEntries, allPlayers)
        val top32Oom = sortedTc80ByOom.take(32)
        val ranks33To64 = sortedTc80ByOom.drop(32).take(32)
        val remaining16 = sortedTc80ByOom.drop(64)

        val nonPreqWmTc = tourCardPlayerIds.filterNot { it in tc80 }
        var wmTchqWinners = if (precedingQualifierWinners.size >= 2) {
            precedingQualifierWinners.take(2)
        } else {
            precedingQualifierWinners
        }
        if (wmTchqWinners.size < 2) {
            wmTchqWinners = fillToTargetCount(wmTchqWinners, 2, nonPreqWmTc, oomEntries)
        }

        val excludedBase = tourCardPlayerIds.toSet() + tc80.toSet() + wmTchqWinners.toSet() + USER_ID
        val currentExcluded = excludedBase.toMutableSet()

        val wmQualifiers = mutableListOf<Long>()
        fun addRegional(countries: Set<String>, count: Int) {
            val resolved = resolveNonSimulatedQualifiersUseCase.resolveRegional(
                countries, count, currentExcluded, allPlayers, oomEntries
            )
            wmQualifiers.addAll(resolved)
            currentExcluded.addAll(resolved)
        }

        addRegional(ResolveNonSimulatedQualifiersUseCase.DACH, 2)
        addRegional(ResolveNonSimulatedQualifiersUseCase.NORDIC_BALTIC, 2)
        addRegional(ResolveNonSimulatedQualifiersUseCase.EAST_SOUTH_EAST_EUROPE, 2)
        addRegional(setOf("CZE"), 1)
        addRegional(setOf("POL"), 1)
        addRegional(setOf("HUN"), 1)
        addRegional(ResolveNonSimulatedQualifiersUseCase.MEDITERRANEAN, 1)
        addRegional(ResolveNonSimulatedQualifiersUseCase.NLD_BEL, 2)
        addRegional(ResolveNonSimulatedQualifiersUseCase.ASIAN_TOUR, 4)
        addRegional(setOf("CHN"), 1)
        addRegional(setOf("JPN"), 1)
        addRegional(setOf("IND"), 1)
        addRegional(ResolveNonSimulatedQualifiersUseCase.CDC_NORTH_AMERICA, 2)
        addRegional(ResolveNonSimulatedQualifiersUseCase.CDLC_LATIN_AMERICA, 1)
        addRegional(ResolveNonSimulatedQualifiersUseCase.OCEANIA, 2)
        addRegional(ResolveNonSimulatedQualifiersUseCase.AFRICA, 2)

        val unranked20 = resolveNonSimulatedQualifiersUseCase.resolveUnrankedPool(
            20, currentExcluded, allPlayers
        )
        wmQualifiers.addAll(unranked20)
        currentExcluded.addAll(unranked20)

        val remaining64Pool = fillToTargetCount(
            remaining16 + wmTchqWinners + wmQualifiers,
            64,
            allPlayerIds.filterNot { it in top32Oom || it in ranks33To64 },
            oomEntries
        )

        val participants = top32Oom.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = index + 1,
                qualificationType = "OOM"
            )
        } + ranks33To64.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "PT_OOM"
            )
        } + remaining64Pool.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "QUALIFIER"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = top32Oom + ranks33To64,
            proTourSortedIds = emptyList(),
            qualifierIds = remaining64Pool,
            participants = participants
        )
    }

    /**
     * Resolves the target board index for a preliminary round qualifier match.
     */
    fun resolvePrelimTargetBoard(
        qualR1Matches: List<TournamentMatch>,
        prelimMatchIndex: Int,
        winnerId: Long?,
        boardCount: Int
    ): Int {
        val placeholderId = -1000L - prelimMatchIndex
        val r1Match = qualR1Matches.firstOrNull { r1 ->
            r1.player1Id == placeholderId || r1.player2Id == placeholderId ||
                (winnerId != null && (r1.player1Id == winnerId || r1.player2Id == winnerId))
        }
        return r1Match?.let { it.matchIndex / 4 } ?: (prelimMatchIndex % boardCount)
    }

    /**
     * Maps player IDs to their seed numbers for a given [TournamentEvent] and [seasonId].
     * If [tournamentId] is provided and participants exist in DB, returns frozen seeds from DB.
     */
    suspend fun getSeededPlayerMap(
        event: TournamentEvent,
        seasonId: Long,
        tournamentId: Long = 0L
    ): Map<Long, Int> = withContext(Dispatchers.IO) {
        if (event == TournamentEvent.UK_OPEN || event == TournamentEvent.EUROPEAN_OPEN) return@withContext emptyMap()

        if (tournamentId > 0L) {
            val seededParticipants = tournamentParticipantDao.getSeededParticipantsForTournament(tournamentId)
            if (seededParticipants.isNotEmpty()) {
                return@withContext seededParticipants.associate { it.playerId to it.seedNumber!! }
            }
        }

        val season = seasonDao.getSeasonById(seasonId) ?: return@withContext emptyMap()
        val careerId = season.careerId

        val dummyTournament = Tournament(
            id = tournamentId,
            seasonId = seasonId,
            name = event.name,
            type = event.name,
            order = 1
        )
        val qualificationResult = resolveTournamentQualification(
            tournament = dummyTournament,
            seasonId = seasonId,
            careerId = careerId
        )

        qualificationResult.participants
            .filter { it.seedNumber != null }
            .associate { it.playerId to it.seedNumber!! }
    }

    /**
     * Builds entrant group domain data for open draw events like UK Open or European Open.
     */
    suspend fun getUkOpenEntrantGroups(
        tournamentId: Long,
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        currentCareerId: Long,
        preloadedParticipants: List<TournamentParticipant>? = null,
        preloadedPlayers: List<Player>? = null,
        preloadedOomEntries: List<OrderOfMeritEntry>? = null
    ): List<EntrantGroupDomainData> = withContext(Dispatchers.IO) {
        val storedParticipants = preloadedParticipants ?: tournamentParticipantDao.getParticipantsForTournament(tournamentId)
        val stored65To96 = storedParticipants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val stored33To64 = storedParticipants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val storedTop32 = storedParticipants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

        val allPlayers = preloadedPlayers ?: playerDao.getAllPlayersList()
        val allPlayersMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerDao.getCareerById(currentCareerId)
        val userName = careerWithProfile?.playerName?.ifBlank { null }
            ?: careerWithProfile?.profileName
            ?: careerWithProfile?.career?.name
            ?: "Du"
        val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE

        fun createEntrantPlayerDomainInfo(id: Long, rank: Int?): EntrantPlayerDomainInfo {
            val name = if (id == USER_ID) userName else allPlayersMap[id]?.name ?: "CPU #$id"
            val country = if (id == USER_ID) userCountryCode else allPlayersMap[id]?.countryCode ?: Country.DEFAULT_CODE
            val skill = if (id == USER_ID) SkillLevel.DEFAULT.value else allPlayersMap[id]?.skill ?: SkillLevel.DEFAULT.value
            return EntrantPlayerDomainInfo(id = id, name = name, countryCode = country, seed = rank, skill = skill)
        }

        val sequence = getRoundSequence(event, allMatches)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return@withContext emptyList() }
        val roundIndex = sequence.indexOf(roundEnum)

        val groups = mutableListOf<EntrantGroupDomainData>()

        val oomEntries = preloadedOomEntries ?: getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()
        val sortedPlayerIds = sortPlayerIdsByOom(allPlayerIds, oomEntries, allPlayers)

        when (roundKey) {
            TournamentRound.R2_128.name -> {
                val pIds = if (stored65To96.isNotEmpty()) stored65To96 else sortedPlayerIds.drop(64).take(32)
                val players = pIds.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 65 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_65_96, players = players))
            }
            TournamentRound.R3_96.name -> {
                val pIds = if (stored33To64.isNotEmpty()) stored33To64 else sortedPlayerIds.drop(32).take(32)
                val players = pIds.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 33 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_33_64, players = players))
            }
            TournamentRound.L64.name -> {
                val pIds = if (storedTop32.isNotEmpty()) storedTop32 else sortedPlayerIds.take(32)
                val players = pIds.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 1 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_TOP_32, players = players))
            }
        }

        if (roundIndex > 0) {
            val prevRoundEnum = sequence[roundIndex - 1]
            val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
            if (prevMatches.isNotEmpty() && prevMatches.any { it.isCompleted }) {
                val winnerIds = prevMatches.mapNotNull { if (it.isCompleted) it.winnerId else null }
                if (winnerIds.isNotEmpty()) {
                    val winnerPlayers = winnerIds.map { id -> createEntrantPlayerDomainInfo(id, null) }
                    groups.add(
                        EntrantGroupDomainData(
                            groupType = EntrantGroupType.ROUND_WINNERS,
                            prevRoundName = prevRoundEnum.name,
                            players = winnerPlayers
                        )
                    )
                }
            }
        }

        return@withContext groups
    }
}
