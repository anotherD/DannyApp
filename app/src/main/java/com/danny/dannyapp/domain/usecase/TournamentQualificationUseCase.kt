package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.TournamentRepository
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
 * Ergebnis einer Teilnehmer-Vorschau mit Angabe, ob die Teilnehmer bereits eingefroren (gespeichert) sind.
 */
data class ParticipantPreviewResult(
    val participants: List<TournamentParticipant>,
    val isFrozen: Boolean
)

/**
 * Central domain use-case for tournament qualification, seeding, player sorting, fill-to-target logic,
 * and qualifier board assignment.
 */
class TournamentQualificationUseCase(
    private val seasonRepository: SeasonRepository,
    private val playerRepository: PlayerRepository,
    private val tournamentRepository: TournamentRepository,
    private val careerRepository: CareerRepository,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {

    constructor(context: Context) : this(
        SeasonRepository(context),
        PlayerRepository(context),
        TournamentRepository(context),
        CareerRepository(context),
        GetOrderOfMeritUseCase(context)
    )

    private val resolveNonSimulatedQualifiersUseCase = ResolveNonSimulatedQualifiersUseCase()

    val grandSlamQualificationResolver = GrandSlamQualificationResolver(
        seasonRepository = seasonRepository,
        tournamentRepository = tournamentRepository,
        resolveNonSimulatedQualifiersUseCase = resolveNonSimulatedQualifiersUseCase,
        sortPlayerIdsByOom = ::sortPlayerIdsByOom,
        fillToTargetCount = ::fillToTargetCount
    )

    val preQualifyMainTournamentUseCase = PreQualifyMainTournamentUseCase(
        tournamentRepository = tournamentRepository,
        seasonRepository = seasonRepository,
        playerRepository = playerRepository,
        getOrderOfMeritUseCase = getOrderOfMeritUseCase,
        resolveNonSimulatedQualifiersUseCase = resolveNonSimulatedQualifiersUseCase,
        grandSlamQualificationResolver = grandSlamQualificationResolver,
        resolvePrequalifiedTourCardIds = ::resolvePrequalifiedTourCardIds,
        sortPlayerIdsByOom = ::sortPlayerIdsByOom
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
     * Resolves major event winners/finalists (up to 24) and additional non-tour card qualifiers (12)
     * for Grand Slam of Darts according to section 2.4.6.
     * Returns Pair(majorIds, additionalIds).
     */
    fun findMainTournamentForQualifier(
        qualifierTournament: Tournament,
        seasonTournaments: List<Tournament>
    ): Tournament = preQualifyMainTournamentUseCase.findMainTournamentForQualifier(qualifierTournament, seasonTournaments)

    fun findQualifierTournamentForMain(
        mainTournament: Tournament,
        seasonTournaments: List<Tournament>
    ): Tournament? = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(mainTournament, seasonTournaments)

    suspend fun freezePreQualifiedParticipantsForMainTournament(
        qualifierTournament: Tournament,
        seasonId: Long,
        careerId: Long,
        persistToDb: Boolean = true
    ): List<TournamentParticipant> = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(
        qualifierTournament, seasonId, careerId, persistToDb
    )

    /**
     * Central resolution of the qualifier field split into prequalified players, non-prequalified players,
     * preliminary round players, R1 bye players, and seed count.
     */
    fun resolveQualifierFieldSplit(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        preQualifiedIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        boardCount: Int
    ): QualifierFieldSplit {
        val remainingTc = tourCardPlayerIds.filterNot { it in preQualifiedIds }
        val nonPrequalifiedIds = sortPlayerIdsByOom(remainingTc, oomList, allPlayers)

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
        precedingQualifierWinners: List<Long> = emptyList(),
        persistToDb: Boolean = true
    ): TournamentQualificationResult = withContext(Dispatchers.IO) {
        val event = TournamentEvent.valueOf(tournament.type)
        val tourCards = seasonRepository.getTourCardsListForSeason(seasonId)
        val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerRepository.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        val tournamentId = tournament.id

        val result = when (event) {
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
                qualifierTournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                tourCardPlayerIds = tourCardPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers,
                persistToDb = persistToDb
            )
            TournamentEvent.EUROPEAN_TOUR -> resolveEuropeanTourQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers,
                persistToDb = persistToDb
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
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers,
                persistToDb = persistToDb
            )
            TournamentEvent.WORLD_CHAMPIONSHIP -> resolveWorldChampionshipQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                tourCardPlayerIds = tourCardPlayerIds,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers,
                persistToDb = persistToDb
            )
        }

        if (persistToDb && result.participants.isNotEmpty()) {
            val existing = tournamentRepository.getParticipantsForTournament(tournamentId)
            val existingIds = existing.map { it.playerId }.toSet()
            val toInsert = result.participants.distinctBy { it.playerId }.filterNot { it.playerId in existingIds }
            if (toInsert.isNotEmpty()) {
                tournamentRepository.insertParticipants(toInsert)
            }
        }

        result
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
        qualifierTournament: Tournament,
        seasonId: Long,
        careerId: Long,
        tourCardPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val event = TournamentEvent.valueOf(qualifierTournament.type)

        val frozenMainParticipants = freezePreQualifiedParticipantsForMainTournament(
            qualifierTournament = qualifierTournament,
            seasonId = seasonId,
            careerId = careerId,
            persistToDb = persistToDb
        )
        val preQualifiedIds = frozenMainParticipants.map { it.playerId }.toSet()

        val boardCount = if (event == TournamentEvent.QUALIFIER_GRAND_SLAM) {
            (40 - frozenMainParticipants.size).coerceAtLeast(1)
        } else {
            event.qualifierBoardCount
        }

        val split = resolveQualifierFieldSplit(
            event = event,
            tourCardPlayerIds = tourCardPlayerIds,
            preQualifiedIds = preQualifiedIds.toList(),
            oomList = oomEntries,
            allPlayers = allPlayers,
            boardCount = boardCount
        )

        val participants = split.nonPrequalifiedIds.mapIndexed { index, playerId ->
            TournamentParticipant(
                tournamentId = qualifierTournament.id,
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
    private suspend fun resolveEuropeanTourQualification(
        tournament: Tournament,
        seasonId: Long,
        careerId: Long,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        val top16Oom = storedParticipants.filter { it.qualificationType == "OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val top16PtOom = storedParticipants.filter { it.qualificationType == "PT_OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val hostQualifiers = storedParticipants.filter { it.qualificationType == "HOST_NATION" }.map { it.playerId }
        val regionalQualifiers = storedParticipants.filter { it.qualificationType == "QUALIFIER" }.map { it.playerId }

        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()

        var tcQualifiers = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (tcQualifiers.size > 10) {
            tcQualifiers = tcQualifiers.take(10)
        }
        if (persistToDb && tcQualifiers.size < 10) {
            val nonPreqTc = tourCardPlayerIds.filterNot { it in existingPlayerIds || it in tcQualifiers }
            tcQualifiers = fillToTargetCount(tcQualifiers, 10, nonPreqTc, oomEntries)
        }

        val newParticipants = tcQualifiers.filterNot { qId -> storedParticipants.any { it.playerId == qId } }.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "QUALIFIER"
            )
        }

        if (persistToDb && newParticipants.isNotEmpty()) {
            tournamentRepository.insertParticipants(newParticipants)
        }

        val allParticipants = storedParticipants + newParticipants
        val qualifiersPoolRaw = tcQualifiers + hostQualifiers + regionalQualifiers
        val excludedFromFallback = (top16Oom + top16PtOom + qualifiersPoolRaw).toSet()
        val qualifiersPool = fillToTargetCount(
            qualifiersPoolRaw,
            16,
            allPlayerIds.filterNot { it in excludedFromFallback },
            oomEntries
        )

        return TournamentQualificationResult(
            oomSortedIds = top16Oom,
            proTourSortedIds = top16PtOom,
            qualifierIds = qualifiersPool,
            participants = allParticipants,
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
        tournament: Tournament,
        seasonId: Long,
        careerId: Long,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        return grandSlamQualificationResolver.finalizeMainTournamentField(
            tournamentId = tournamentId,
            storedParticipants = storedParticipants,
            precedingQualifierWinners = precedingQualifierWinners,
            tourCardPlayerIds = tourCardPlayerIds,
            allPlayerIds = allPlayerIds,
            oomEntries = oomEntries,
            allPlayers = allPlayers,
            persistToDb = persistToDb
        )
    }

    /**
     * Ermittelt Teilnehmer, gesetzte Spieler und Qualifier-Pools für die Weltmeisterschaft.
     */
    private suspend fun resolveWorldChampionshipQualification(
        tournament: Tournament,
        seasonId: Long,
        careerId: Long,
        precedingQualifierWinners: List<Long>,
        tourCardPlayerIds: List<Long>,
        allPlayerIds: List<Long>,
        oomEntries: List<OrderOfMeritEntry>,
        ptOomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()

        var wmTchqWinners = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (wmTchqWinners.size > 2) {
            wmTchqWinners = wmTchqWinners.take(2)
        }
        if (persistToDb && wmTchqWinners.size < 2) {
            val nonPreqTc = tourCardPlayerIds.filterNot { it in existingPlayerIds || it in wmTchqWinners }
            wmTchqWinners = fillToTargetCount(wmTchqWinners, 2, nonPreqTc, oomEntries)
        }

        val newParticipants = wmTchqWinners.filterNot { wId -> storedParticipants.any { it.playerId == wId } }.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                seedNumber = null,
                qualificationType = "QUALIFIER"
            )
        }

        if (persistToDb && newParticipants.isNotEmpty()) {
            tournamentRepository.insertParticipants(newParticipants)
        }

        val allParticipants = storedParticipants + newParticipants

        val top32Oom = allParticipants.filter { it.qualificationType == "OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val ranks33To64 = allParticipants.filter { it.qualificationType == "PT_OOM" }.map { it.playerId }
        val remaining64PoolRaw = allParticipants.filterNot { it.playerId in top32Oom || it.playerId in ranks33To64 }.map { it.playerId }
        val excludedFromFallback = (top32Oom + ranks33To64 + remaining64PoolRaw).toSet()
        val remaining64Pool = fillToTargetCount(
            remaining64PoolRaw,
            64,
            allPlayerIds.filterNot { it in excludedFromFallback },
            oomEntries
        )

        return TournamentQualificationResult(
            oomSortedIds = top32Oom + ranks33To64,
            proTourSortedIds = emptyList(),
            qualifierIds = remaining64Pool,
            participants = allParticipants
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
            val seededParticipants = tournamentRepository.getSeededParticipantsForTournament(tournamentId)
            if (seededParticipants.isNotEmpty()) {
                return@withContext seededParticipants.associate { it.playerId to it.seedNumber!! }
            }
        }

        val season = seasonRepository.getSeasonById(seasonId)
        val careerId = season.careerId

        val dummyTournament = Tournament(
            id = tournamentId,
            seasonId = seasonId,
            name = event.name,
            type = event.name,
            order = 1,
            hostCountry = Country.DEFAULT_USER_CODE,
            hostCity = "London"
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
        val storedParticipants = preloadedParticipants ?: tournamentRepository.getParticipantsForTournament(tournamentId)
        val stored65To96 = storedParticipants.filter { it.qualificationType == "OOM_65_96" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val stored33To64 = storedParticipants.filter { it.qualificationType == "OOM_33_64" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val storedTop32 = storedParticipants.filter { it.qualificationType == "OOM_TOP_32" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }

        val allPlayers = preloadedPlayers ?: playerRepository.getAllPlayersList()
        val allPlayersMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userName = careerWithProfile.playerName
        val userCountryCode = careerWithProfile.countryCode

        fun createEntrantPlayerDomainInfo(id: Long, rank: Int?): EntrantPlayerDomainInfo {
            val name: String
            val country: String
            val skill: Int
            if (id == USER_ID) {
                name = userName
                country = userCountryCode
                skill = SkillLevel.DEFAULT.value
            } else {
                val player = requireNotNull(allPlayersMap[id]) { "CPU-Spieler mit ID $id nicht gefunden" }
                name = player.name
                country = player.countryCode
                skill = player.skill
            }
            return EntrantPlayerDomainInfo(id = id, name = name, countryCode = country, seed = rank, skill = skill)
        }

        val sequence = getRoundSequence(event, allMatches)
        val roundEnum = TournamentRound.valueOf(roundKey)
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

    /**
     * Liefert eine reine Live-Vorschau der Teilnehmer für ein Turnier inklusive Freeze-Status (`isFrozen`),
     * ohne Datenbank-Schreibzugriffe (Single Source of Truth).
     */
    suspend fun getPreviewParticipants(
        tournament: Tournament,
        seasonId: Long,
        careerId: Long
    ): ParticipantPreviewResult = withContext(Dispatchers.IO) {
        val stored = tournamentRepository.getParticipantsForTournament(tournament.id)
        if (stored.isNotEmpty()) {
            ParticipantPreviewResult(participants = stored, isFrozen = true)
        } else {
            val liveResult = resolveTournamentQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                persistToDb = false
            )
            ParticipantPreviewResult(participants = liveResult.participants, isFrozen = false)
        }
    }
}
