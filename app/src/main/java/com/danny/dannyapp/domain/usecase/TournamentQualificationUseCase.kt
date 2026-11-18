package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
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
 * qualifier board assignment, and preview match propagation.
 */
class TournamentQualificationUseCase(
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val careerDao: CareerDao,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {

    private val calculateGroupStandingsUseCase: CalculateGroupStandingsUseCase = CalculateGroupStandingsUseCase()

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
        AppDatabase.getDatabase(context.applicationContext).careerDao(),
        GetOrderOfMeritUseCase(context)
    )

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
     * Central resolution of the qualifier field split into prequalified players, non-prequalified players,
     * preliminary round players, R1 bye players, and seed count.
     */
    fun resolveQualifierFieldSplit(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
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

        val boardCount = if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4
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
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
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

                TournamentQualificationResult(
                    oomSortedIds = sortedByPt,
                    proTourSortedIds = top32,
                    qualifierIds = remaining96,
                    participants = participants
                )
            }

            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM -> {
                val split = resolveQualifierFieldSplit(
                    event = event,
                    tourCardPlayerIds = tourCardPlayerIds,
                    oomList = oomEntries,
                    ptOomList = ptOomEntries,
                    allPlayers = allPlayers
                )

                val participants = split.nonPrequalifiedIds.mapIndexed { index, playerId ->
                    TournamentParticipant(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        seedNumber = if (index < split.seedCount) index + 1 else null,
                        qualificationType = "TOUR_CARD"
                    )
                }

                TournamentQualificationResult(
                    oomSortedIds = split.nonPrequalifiedIds,
                    proTourSortedIds = emptyList(),
                    qualifierIds = split.nonPrequalifiedIds,
                    participants = participants,
                    qualifierFieldSplit = split
                )
            }

            TournamentEvent.EUROPEAN_TOUR -> {
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

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val hostCountry = tournament.hostCountry ?: Country.DEFAULT_CODE
                val hostCountryNonTc = nonTcPlayerIds.filter { id ->
                    val player = allPlayers.find { it.id == id }
                    player?.countryCode == hostCountry && player.tourCardYear == 0
                }
                val hostQualifiers = fillToTargetCount(hostCountryNonTc, 4, nonTcPlayerIds, oomEntries).take(4)
                val remainingNonTc = nonTcPlayerIds.filterNot { it in hostQualifiers }
                val otherQualifiers = sortPlayerIdsByOom(remainingNonTc, oomEntries, allPlayers).take(2)

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

                TournamentQualificationResult(
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16PtOom,
                    qualifierIds = qualifiersPool,
                    participants = participantList,
                    preQualifiedOomIds = top16Oom,
                    preQualifiedPtOomIds = top16PtOom
                )
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val tcIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedTcOom = sortPlayerIdsByOom(tcIds, oomEntries, allPlayers)

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tcIds }
                val nonTcQualifiers = sortPlayerIdsByOom(nonTcPlayerIds, oomEntries, allPlayers).take(32)
                val final32Qualifiers = fillToTargetCount(nonTcQualifiers, 32, allPlayerIds.filterNot { it in tcIds }, oomEntries)

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
                } + final32Qualifiers.map { playerId ->
                    TournamentParticipant(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        seedNumber = null,
                        qualificationType = "QUALIFIER"
                    )
                }

                TournamentQualificationResult(
                    oomSortedIds = sortedTcOom,
                    proTourSortedIds = emptyList(),
                    qualifierIds = final32Qualifiers,
                    participants = participants
                )
            }

            TournamentEvent.WORLD_MATCHPLAY -> {
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

                TournamentQualificationResult(
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt,
                    qualifierIds = emptyList(),
                    participants = participants
                )
            }

            TournamentEvent.WORLD_GRAND_PRIX -> {
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

                TournamentQualificationResult(
                    oomSortedIds = top16Oom,
                    proTourSortedIds = top16Pt,
                    qualifierIds = emptyList(),
                    participants = participants
                )
            }

            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val top32Et = sortPlayerIdsByOom(allPlayerIds, etOomEntries, allPlayers).take(32)
                val final32Et = fillToTargetCount(top32Et, 32, allPlayerIds, oomEntries)

                val participants = final32Et.mapIndexed { index, playerId ->
                    TournamentParticipant(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        seedNumber = index + 1,
                        qualificationType = "ET_OOM"
                    )
                }

                TournamentQualificationResult(
                    oomSortedIds = emptyList(),
                    proTourSortedIds = emptyList(),
                    qualifierIds = final32Et,
                    participants = participants
                )
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val top16Oom = resolvePrequalifiedTourCardIds(
                    event = event,
                    tourCardPlayerIds = tourCardPlayerIds,
                    oomList = oomEntries,
                    ptOomList = ptOomEntries,
                    allPlayers = allPlayers
                ).first
                val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
                val pot2 = sortPlayerIdsByOom(remainingForPt, ptOomEntries, allPlayers).take(16)

                var gsTchqWinners = if (precedingQualifierWinners.size >= 8) {
                    precedingQualifierWinners.take(8)
                } else {
                    precedingQualifierWinners
                }
                if (gsTchqWinners.size < 8) {
                    gsTchqWinners = fillToTargetCount(gsTchqWinners, 8, tourCardPlayerIds, oomEntries)
                }
                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds }
                val otherQuals = sortPlayerIdsByOom(nonTcPlayerIds, oomEntries, allPlayers).take(8)
                val pot3 = fillToTargetCount(gsTchqWinners + otherQuals, 16, allPlayerIds, oomEntries)

                val participants = top16Oom.mapIndexed { index, playerId ->
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
                        qualificationType = "PT_OOM"
                    )
                } + pot3.map { playerId ->
                    TournamentParticipant(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        seedNumber = null,
                        qualificationType = "QUALIFIER"
                    )
                }

                TournamentQualificationResult(
                    oomSortedIds = top16Oom,
                    proTourSortedIds = pot2,
                    qualifierIds = pot3,
                    participants = participants
                )
            }

            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val top64Pc = sortPlayerIdsByOom(allPlayerIds, pcOomEntries, allPlayers).take(64)
                val final64Pc = fillToTargetCount(top64Pc, 64, allPlayerIds, oomEntries)

                val participants = final64Pc.mapIndexed { index, playerId ->
                    TournamentParticipant(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        seedNumber = index + 1,
                        qualificationType = "PC_OOM"
                    )
                }

                TournamentQualificationResult(
                    oomSortedIds = emptyList(),
                    proTourSortedIds = emptyList(),
                    qualifierIds = final64Pc,
                    participants = participants
                )
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
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
                var wmTchqWinners = if (precedingQualifierWinners.size >= 4) {
                    precedingQualifierWinners.take(4)
                } else {
                    precedingQualifierWinners
                }
                if (wmTchqWinners.size < 4) {
                    wmTchqWinners = fillToTargetCount(wmTchqWinners, 4, nonPreqWmTc, oomEntries)
                }

                val nonTcPlayerIds = allPlayerIds.filterNot { it in tourCardPlayerIds || it in tc80 }
                val nonTc44 = sortPlayerIdsByOom(nonTcPlayerIds, oomEntries, allPlayers).take(44)
                val nonTc44Full = fillToTargetCount(nonTc44, 44, nonTcPlayerIds, oomEntries)

                val remaining64Pool = fillToTargetCount(
                    remaining16 + wmTchqWinners + nonTc44Full,
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

                TournamentQualificationResult(
                    oomSortedIds = top32Oom + ranks33To64,
                    proTourSortedIds = emptyList(),
                    qualifierIds = remaining64Pool,
                    participants = participants
                )
            }
        }
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
     * Generates preview matches for a round and propagates winner IDs or seeds from previous rounds.
     */
    suspend fun generatePreviewMatchesWithPropagation(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long,
        fallbackCareerId: Long
    ): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val baseMatches = generatePreviewMatchesForRound(tournamentId, roundKey, event)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return@withContext baseMatches }

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && roundEnum == TournamentRound.L16) {
            val groupMatches = allMatches.filter { it.round == TournamentRound.GROUP.name }
            val standingsByGroup = calculateGroupStandingsUseCase(groupMatches)
            val groupWinners = mutableMapOf<String, Long>()
            for (groupIdx in 0 until 16) {
                val groupLetter = ('A' + groupIdx).toString()
                val matchesForGroup = groupMatches.filter { it.groupName == groupLetter }
                if (matchesForGroup.isNotEmpty() && matchesForGroup.all { it.isCompleted }) {
                    val winnerId = standingsByGroup[groupLetter]?.firstOrNull()?.playerId
                    if (winnerId != null) {
                        groupWinners[groupLetter] = winnerId
                    }
                }
            }

            return@withContext (0 until 8).map { matchIndex ->
                val groupLetter1 = ('A' + 2 * matchIndex).toString()
                val groupLetter2 = ('A' + (2 * matchIndex + 1)).toString()
                val player1Id = groupWinners[groupLetter1] ?: (-9000L - (2 * matchIndex))
                val player2Id = groupWinners[groupLetter2] ?: (-9000L - (2 * matchIndex + 1))
                TournamentMatch(
                    id = -2000L - (roundEnum.ordinal * 100) - matchIndex,
                    tournamentId = tournamentId,
                    round = roundKey,
                    matchIndex = matchIndex,
                    player1Id = player1Id,
                    player2Id = player2Id,
                    isCompleted = false
                )
            }
        }

        if (event in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
            return@withContext generateQualifierPreviewMatches(
                tournamentId = tournamentId,
                roundKey = roundKey,
                roundEnum = roundEnum,
                event = event,
                allMatches = allMatches,
                seasonId = seasonId,
                fallbackCareerId = fallbackCareerId,
                fallbackMatches = baseMatches
            )
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && roundEnum == TournamentRound.L32) {
            val storedParticipants = tournamentParticipantDao.getParticipantsForTournament(tournamentId)
            val storedOom = storedParticipants.filter { it.qualificationType == "OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
            val top16Oom = if (storedOom.isNotEmpty()) {
                storedOom
            } else {
                val season = seasonDao.getSeasonById(seasonId)
                val careerId = season?.careerId ?: fallbackCareerId
                val dummyTournament = Tournament(
                    id = tournamentId,
                    seasonId = seasonId,
                    name = event.name,
                    type = event.name,
                    order = 1
                )
                val result = resolveTournamentQualification(
                    tournament = dummyTournament,
                    seasonId = seasonId,
                    careerId = careerId
                )
                result.preQualifiedOomIds
            }
            val l48Matches = allMatches.filter { it.round == TournamentRound.L48.name }.sortedBy { it.matchIndex }

            val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
            return@withContext baseMatches.map { match ->
                val seedIndex = seedOrder.getOrNull(match.matchIndex) ?: match.matchIndex
                val seededPlayer1 = top16Oom.getOrNull(seedIndex) ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                val l48Match = l48Matches.getOrNull(15 - seedIndex)
                val seededPlayer2 = if (l48Match?.isCompleted == true && l48Match.winnerId != null) {
                    l48Match.winnerId
                } else {
                    PLACEHOLDER_PREVIOUS_WINNER_ID
                }
                match.copy(player1Id = seededPlayer1, player2Id = seededPlayer2)
            }
        }

        val sequence = getRoundSequence(event, allMatches)
        val roundIndex = sequence.indexOf(roundEnum)
        if (roundIndex <= 0) {
            return@withContext baseMatches
        }

        val prevRoundEnum = sequence[roundIndex - 1]
        val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
        if (prevMatches.isEmpty()) return@withContext baseMatches

        return@withContext baseMatches.map { match ->
            val player1Match = prevMatches.getOrNull(2 * match.matchIndex)
            val player2Match = prevMatches.getOrNull(2 * match.matchIndex + 1)
            val player1Id = if (player1Match?.isCompleted == true && player1Match.winnerId != null) {
                player1Match.winnerId
            } else {
                PLACEHOLDER_PREVIOUS_WINNER_ID
            }
            val player2Id = if (player2Match?.isCompleted == true && player2Match.winnerId != null) {
                player2Match.winnerId
            } else {
                PLACEHOLDER_PREVIOUS_WINNER_ID
            }
            match.copy(player1Id = player1Id, player2Id = player2Id)
        }
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
        currentCareerId: Long
    ): List<EntrantGroupDomainData> = withContext(Dispatchers.IO) {
        val storedParticipants = tournamentParticipantDao.getParticipantsForTournament(tournamentId)
        val stored65To96 = storedParticipants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val stored33To64 = storedParticipants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val storedTop32 = storedParticipants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

        val allPlayers = playerDao.getAllPlayersList()
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

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
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

    /**
     * Generates placeholder preview matches for a specific round of a tournament.
     */
    fun generatePreviewMatchesForRound(
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
            TournamentRound.QUAL_FINAL -> if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4
            TournamentRound.QUAL_R2 -> 2 * (if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4)
            TournamentRound.QUAL_R1 -> 4 * (if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4)
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

        val placeholderPlayerId = if (isNoFixedTree) PLACEHOLDER_OPEN_DRAW_ID else PLACEHOLDER_PREVIOUS_WINNER_ID

        if (event == TournamentEvent.GRAND_SLAM_OF_DARTS && roundEnum == TournamentRound.L16) {
            return (0 until matchCount).map { matchIndex ->
                TournamentMatch(
                    id = -2000L - (roundEnum.ordinal * 100) - matchIndex,
                    tournamentId = tournamentId,
                    round = roundKey,
                    matchIndex = matchIndex,
                    player1Id = -9000L - (2 * matchIndex),
                    player2Id = -9000L - (2 * matchIndex + 1),
                    isCompleted = false
                )
            }
        }

        return (0 until matchCount).map { matchIndex ->
            TournamentMatch(
                id = -2000L - (roundEnum.ordinal * 100) - matchIndex,
                tournamentId = tournamentId,
                round = roundKey,
                matchIndex = matchIndex,
                player1Id = placeholderPlayerId,
                player2Id = placeholderPlayerId,
                groupName = if (roundEnum == TournamentRound.GROUP) ('A' + (matchIndex % 16)).toString() else null,
                isCompleted = false
            )
        }
    }

    private suspend fun generateQualifierPreviewMatches(
        tournamentId: Long,
        roundKey: String,
        roundEnum: TournamentRound,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long,
        fallbackCareerId: Long,
        fallbackMatches: List<TournamentMatch>
    ): List<TournamentMatch> {
        val season = seasonDao.getSeasonById(seasonId)
        val careerId = season?.careerId ?: fallbackCareerId
        val dummyTournament = Tournament(
            id = tournamentId,
            seasonId = seasonId,
            name = event.name,
            type = event.name,
            order = 1
        )
        val qualResult = resolveTournamentQualification(
            tournament = dummyTournament,
            seasonId = seasonId,
            careerId = careerId
        )
        val split = qualResult.qualifierFieldSplit ?: return fallbackMatches
        val boardCount = split.boardCount

        return when (roundEnum) {
            TournamentRound.QUAL_R1 -> {
                val existingR1Matches = allMatches
                    .filter { it.round == TournamentRound.QUAL_R1.name }
                    .sortedBy { it.matchIndex }

                if (existingR1Matches.isNotEmpty()) {
                    val completedPrelimMatches = allMatches
                        .filter { it.round == TournamentRound.QUAL_PRELIM.name && it.isCompleted }
                        .sortedBy { it.matchIndex }

                    existingR1Matches.map { storedR1Match ->
                        val resolvedPlayer1Id = resolvePrelimPlaceholder(
                            playerId = storedR1Match.player1Id,
                            completedPrelimMatches = completedPrelimMatches
                        )
                        val resolvedPlayer2Id = resolvePrelimPlaceholder(
                            playerId = storedR1Match.player2Id,
                            completedPrelimMatches = completedPrelimMatches
                        )
                        if (resolvedPlayer1Id != storedR1Match.player1Id || resolvedPlayer2Id != storedR1Match.player2Id) {
                            storedR1Match.copy(player1Id = resolvedPlayer1Id, player2Id = resolvedPlayer2Id)
                        } else {
                            storedR1Match
                        }
                    }
                } else {
                    val byePlayers = split.byePlayers
                    val prelimMatches = allMatches
                        .filter { it.round == TournamentRound.QUAL_PRELIM.name }
                        .sortedBy { it.matchIndex }
                    val prelimWinnersOrPlaceholders = prelimMatches.mapIndexed { prelimMatchIndex, match ->
                        match.winnerId ?: (-1000L - prelimMatchIndex)
                    }

                    val pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, byePlayers, prelimWinnersOrPlaceholders)
                    pairings.mapIndexed { matchIdx, pairing ->
                        TournamentMatch(
                            id = -3000L - matchIdx,
                            tournamentId = tournamentId,
                            round = roundKey,
                            matchIndex = pairing.matchIndex,
                            player1Id = if (pairing.player1Id == 0L) PLACEHOLDER_PREVIOUS_WINNER_ID else pairing.player1Id,
                            player2Id = if (pairing.player2Id == 0L) PLACEHOLDER_PREVIOUS_WINNER_ID else pairing.player2Id
                        )
                    }
                }
            }
            TournamentRound.QUAL_R2 -> {
                val r1Matches = allMatches.filter { it.round == TournamentRound.QUAL_R1.name }.sortedBy { it.matchIndex }
                val r2Matches = mutableListOf<TournamentMatch>()
                for (boardIndex in 0 until boardCount) {
                    val boardMatches = r1Matches.filter { it.matchIndex in (4 * boardIndex)..(4 * boardIndex + 3) }.sortedBy { it.matchIndex }
                    val winner0 = boardMatches.getOrNull(0)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                    val winner1 = boardMatches.getOrNull(1)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                    val winner2 = boardMatches.getOrNull(2)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                    val winner3 = boardMatches.getOrNull(3)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID

                    r2Matches.add(TournamentMatch(id = -4000L - (boardIndex * 10), tournamentId = tournamentId, round = roundKey, matchIndex = 2 * boardIndex, player1Id = winner0, player2Id = winner1))
                    r2Matches.add(TournamentMatch(id = -4000L - (boardIndex * 10) + 1, tournamentId = tournamentId, round = roundKey, matchIndex = 2 * boardIndex + 1, player1Id = winner2, player2Id = winner3))
                }
                r2Matches
            }
            TournamentRound.QUAL_FINAL -> {
                val r2Matches = allMatches.filter { it.round == TournamentRound.QUAL_R2.name }.sortedBy { it.matchIndex }
                val finalMatches = mutableListOf<TournamentMatch>()
                for (boardIndex in 0 until boardCount) {
                    val boardMatches = r2Matches.filter { it.matchIndex in (2 * boardIndex)..(2 * boardIndex + 1) }.sortedBy { it.matchIndex }
                    val winner0 = boardMatches.getOrNull(0)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                    val winner1 = boardMatches.getOrNull(1)?.winnerId ?: PLACEHOLDER_PREVIOUS_WINNER_ID

                    finalMatches.add(TournamentMatch(id = -5000L - boardIndex, tournamentId = tournamentId, round = roundKey, matchIndex = boardIndex, player1Id = winner0, player2Id = winner1))
                }
                finalMatches
            }
            else -> fallbackMatches
        }
    }

    /**
     * Löst einen Vorrunden-Platzhalter der Form `-1000L - prelimMatchIndex` auf:
     * Ist das entsprechende QUAL_PRELIM-Match bereits abgeschlossen, wird dessen [winnerId] zurückgegeben;
     * andernfalls bleibt der ursprüngliche Platzhalter erhalten.
     *
     * @param playerId Die zu prüfende Spieler-ID (kann echter Spieler oder Platzhalter sein).
     * @param completedPrelimMatches Bereits abgeschlossene QUAL_PRELIM-Matches, sortiert nach [matchIndex].
     * @return Tatsächliche [winnerId] des Vorrunden-Matches oder unvereränderter [playerId].
     */
    private fun resolvePrelimPlaceholder(
        playerId: Long,
        completedPrelimMatches: List<TournamentMatch>
    ): Long {
        val isPrelimPlaceholder = playerId <= -1000L && playerId > -2000L
        if (!isPrelimPlaceholder) return playerId

        val prelimMatchIndex = (-1000L - playerId).toInt()
        val correspondingPrelimMatch = completedPrelimMatches.find { it.matchIndex == prelimMatchIndex }
        return correspondingPrelimMatch?.winnerId ?: playerId
    }
}
