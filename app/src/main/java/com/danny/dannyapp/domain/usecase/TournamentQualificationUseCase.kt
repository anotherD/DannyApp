package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_QUAL_PRELIM_BASE_ID
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
import kotlin.math.ceil

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
    val boardSizePerBoard: Int,
    val byePlayers: List<Long>,
    val seedCount: Int
) {
    val directR1Players: List<Long> get() = byePlayers
}

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

    private val resolveNonSimulatedQualifiersUseCase = ResolveNonSimulatedQualifiersUseCase()

    val grandSlamQualificationResolver = GrandSlamQualificationResolver(
        seasonRepository = seasonRepository,
        tournamentRepository = tournamentRepository,
        resolveNonSimulatedQualifiersUseCase = resolveNonSimulatedQualifiersUseCase
    )

    val preQualifyMainTournamentUseCase = PreQualifyMainTournamentUseCase(
        tournamentRepository = tournamentRepository,
        seasonRepository = seasonRepository,
        playerRepository = playerRepository,
        getOrderOfMeritUseCase = getOrderOfMeritUseCase,
        resolveNonSimulatedQualifiersUseCase = resolveNonSimulatedQualifiersUseCase,
        grandSlamQualificationResolver = grandSlamQualificationResolver
    )

    /**
     * Central resolution of the qualifier field split into prequalified players, non-prequalified players,
     * preliminary round players, R1 bye players, and seed count.
     */
    fun resolveQualifierFieldSplit(
        tourCardPlayerIds: List<Long>,
        preQualifiedIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        boardCount: Int
    ): QualifierFieldSplit {
        val remainingTc = tourCardPlayerIds.filterNot { it in preQualifiedIds }
        val nonPrequalifiedIds = QualificationHelper.sortPlayerIdsByOom(remainingTc, oomList)

        val totalNonPrequalified = nonPrequalifiedIds.size

        val minSlotsPerBoard = ceil(totalNonPrequalified.toDouble() / boardCount.toDouble()).toInt().coerceAtLeast(2)
        var boardSize = 2
        while (boardSize < minSlotsPerBoard) {
            boardSize *= 2
        }
        val totalR1Slots = boardSize * boardCount

        val byePlayers = if (totalNonPrequalified < totalR1Slots) {
            val padding = List(totalR1Slots - totalNonPrequalified) { PLACEHOLDER_OPEN_DRAW_ID }
            nonPrequalifiedIds + padding
        } else {
            nonPrequalifiedIds
        }

        val seedCount = (2 * boardCount).coerceAtMost(totalNonPrequalified)

        return QualifierFieldSplit(
            preQualifiedIds = preQualifiedIds,
            nonPrequalifiedIds = nonPrequalifiedIds,
            boardCount = boardCount,
            boardSizePerBoard = boardSize,
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
                ptOomEntries = ptOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM,
            TournamentEvent.QUALIFIER_WORLD_MASTERS -> resolveQualifierQualification(
                qualifierTournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                tourCardPlayerIds = tourCardPlayerIds,
                oomEntries = oomEntries,
                persistToDb = persistToDb
            )
            TournamentEvent.EUROPEAN_TOUR -> resolveEuropeanTourQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                persistToDb = persistToDb
            )
            TournamentEvent.UK_OPEN,
            TournamentEvent.EUROPEAN_OPEN -> resolveUkOrEuropeanOpenQualification(
                tournamentId = tournamentId,
                event = event,
                tourCardPlayerIds = tourCardPlayerIds,
                oomEntries = oomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX -> resolveOomAndProTourQualification(
                tournamentId = tournamentId,
                allPlayerIds = allPlayerIds,
                oomEntries = oomEntries,
                ptOomEntries = ptOomEntries
            )
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> resolveSingleRankingQualification(
                tournamentId = tournamentId,
                targetCount = 32,
                qualificationType = "ET_OOM",
                rankingEntries = etOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> resolveSingleRankingQualification(
                tournamentId = tournamentId,
                targetCount = 64,
                qualificationType = "PC_OOM",
                rankingEntries = pcOomEntries,
                allPlayers = allPlayers
            )
            TournamentEvent.GRAND_SLAM_OF_DARTS -> resolveGrandSlamQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                oomEntries = oomEntries,
                persistToDb = persistToDb
            )
            TournamentEvent.WORLD_CHAMPIONSHIP -> resolveWorldChampionshipQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
                persistToDb = persistToDb
            )
            TournamentEvent.WORLD_MASTERS -> resolveWorldMastersQualification(
                tournament = tournament,
                seasonId = seasonId,
                careerId = careerId,
                precedingQualifierWinners = precedingQualifierWinners,
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
        val sortedByPt = QualificationHelper.sortPlayerIdsByOom(tcIds, ptOomEntries)
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
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val event = TournamentEvent.valueOf(qualifierTournament.type)

        val frozenMainParticipants = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(
            qualifierTournament = qualifierTournament,
            seasonId = seasonId,
            careerId = careerId,
            persistToDb = persistToDb
        )
        val preQualifiedIds = frozenMainParticipants.map { it.playerId }.toSet()

        val mainEvent = requireNotNull(event.mainEventForQualifier)
        val boardCount = (mainEvent.targetParticipantCount - frozenMainParticipants.size).coerceAtLeast(1)

        val split = resolveQualifierFieldSplit(
            tourCardPlayerIds = tourCardPlayerIds,
            preQualifiedIds = preQualifiedIds.toList(),
            oomList = oomEntries,
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
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        val top16Oom = storedParticipants.filter { it.qualificationType == "OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val top16PtOom = storedParticipants.filter { it.qualificationType == "PT_OOM" }.sortedBy { it.seedNumber ?: Int.MAX_VALUE }.map { it.playerId }
        val hostQualifiers = storedParticipants.filter { it.qualificationType == "HOST_NATION" }.map { it.playerId }
        val regionalQualifiers = storedParticipants.filter { it.qualificationType == "QUALIFIER" }.map { it.playerId }

        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()
        val neededQualifierCount = (TournamentEvent.EUROPEAN_TOUR.targetParticipantCount - storedParticipants.size).coerceAtLeast(0)

        val tcQualifiers = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (persistToDb) {
            require(tcQualifiers.size == neededQualifierCount) {
                "European Tour erfordert exakt $neededQualifierCount Qualifier-Sieger, erhalten: ${tcQualifiers.size}"
            }
        } else if (tcQualifiers.isNotEmpty()) {
            require(tcQualifiers.size == neededQualifierCount) {
                "European Tour erfordert exakt $neededQualifierCount Qualifier-Sieger, erhalten: ${tcQualifiers.size}"
            }
        }

        val newParticipants = tcQualifiers.filterNot { qId -> storedParticipants.any { it.playerId == qId } }.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                qualificationType = "QUALIFIER"
            )
        }

        if (persistToDb && newParticipants.isNotEmpty()) {
            tournamentRepository.insertParticipants(newParticipants)
        }

        val allParticipants = storedParticipants + newParticipants
        val qualifiersPoolRaw = tcQualifiers + hostQualifiers + regionalQualifiers
        if (persistToDb) {
            require(qualifiersPoolRaw.size == 16) {
                "European Tour erfordert exakt 16 ungesetzte Qualifier-Teilnehmer, erhalten: ${qualifiersPoolRaw.size}"
            }
        }

        return TournamentQualificationResult(
            oomSortedIds = top16Oom,
            proTourSortedIds = top16PtOom,
            qualifierIds = qualifiersPoolRaw,
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
        oomEntries: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        require(tourCardPlayerIds.size == 128) {
            "UK/European Open erfordert exakt 128 Tour Card Inhaber, erhalten: ${tourCardPlayerIds.size}"
        }
        val sortedTcOom = QualificationHelper.sortPlayerIdsByOom(tourCardPlayerIds, oomEntries)

        val excludedForNonTc = tourCardPlayerIds.toSet() + USER_ID
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
        require(final32Qualifiers.size == 32) {
            "UK/European Open erfordert exakt 32 Qualifikanten, erhalten: ${final32Qualifiers.size}"
        }

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
                qualificationType = "OOM_97_128"
            )
        } + final32Qualifiers.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                qualificationType = "QUALIFIER"
            )
        }

        return TournamentQualificationResult(
            oomSortedIds = sortedTcOom,
            proTourSortedIds = emptyList(),
            qualifierIds = final32Qualifiers,
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
        ptOomEntries: List<OrderOfMeritEntry>
    ): TournamentQualificationResult {
        val sortedOom = QualificationHelper.sortPlayerIdsByOom(allPlayerIds, oomEntries)
        val top16Oom = sortedOom.take(16)
        val remainingForPt = allPlayerIds.filterNot { it in top16Oom }
        val top16Pt = QualificationHelper.sortPlayerIdsByOom(remainingForPt, ptOomEntries).take(16)

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
        allPlayers: List<Player>
    ): TournamentQualificationResult {
        val candidatePlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()
        val topPlayers = QualificationHelper.sortPlayerIdsByOom(candidatePlayerIds, rankingEntries).take(targetCount)
        require(topPlayers.size == targetCount) {
            "Ranglisten-Qualifikation ($qualificationType) erfordert exakt $targetCount Teilnehmer, erhalten: ${topPlayers.size}"
        }

        val participants = topPlayers.mapIndexed { index, playerId ->
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
            qualifierIds = topPlayers,
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
        oomEntries: List<OrderOfMeritEntry>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        return grandSlamQualificationResolver.finalizeMainTournamentField(
            tournamentId = tournamentId,
            storedParticipants = storedParticipants,
            precedingQualifierWinners = precedingQualifierWinners,
            oomEntries = oomEntries,
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
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()
        val neededQualifierCount = (TournamentEvent.WORLD_CHAMPIONSHIP.targetParticipantCount - storedParticipants.size).coerceAtLeast(0)

        val wmTchqWinners = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (persistToDb) {
            require(wmTchqWinners.size == neededQualifierCount) {
                "World Championship erfordert exakt $neededQualifierCount Qualifier-Sieger, erhalten: ${wmTchqWinners.size}"
            }
        } else if (wmTchqWinners.isNotEmpty()) {
            require(wmTchqWinners.size == neededQualifierCount) {
                "World Championship erfordert exakt $neededQualifierCount Qualifier-Sieger, erhalten: ${wmTchqWinners.size}"
            }
        }

        val newParticipants = wmTchqWinners.filterNot { wId -> storedParticipants.any { it.playerId == wId } }.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
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
        if (persistToDb) {
            require(remaining64PoolRaw.size == 64) {
                "World Championship erfordert exakt 64 ungesetzte Teilnehmer (International/Qualifier), erhalten: ${remaining64PoolRaw.size}"
            }
        }

        return TournamentQualificationResult(
            oomSortedIds = top32Oom + ranks33To64,
            proTourSortedIds = emptyList(),
            qualifierIds = remaining64PoolRaw,
            participants = allParticipants
        )
    }

    /**
     * Ermittelt Teilnehmer, gesetzte Spieler und Qualifier-Pools für das World Masters.
     */
    private suspend fun resolveWorldMastersQualification(
        tournament: Tournament,
        seasonId: Long,
        careerId: Long,
        precedingQualifierWinners: List<Long>,
        persistToDb: Boolean = true
    ): TournamentQualificationResult {
        val tournamentId = tournament.id
        var storedParticipants = tournamentRepository.getParticipantsForTournament(tournamentId)

        if (storedParticipants.isEmpty()) {
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
            if (qualTournament != null) {
                storedParticipants = preQualifyMainTournamentUseCase.freezePreQualifiedParticipantsForMainTournament(qualTournament, seasonId, careerId, persistToDb)
            }
        }

        val existingPlayerIds = storedParticipants.map { it.playerId }.toSet()
        val neededQualifierCount = (TournamentEvent.WORLD_MASTERS.targetParticipantCount - storedParticipants.size).coerceAtLeast(0)

        val wmWinners = precedingQualifierWinners.filterNot { it in existingPlayerIds }
        if (persistToDb || wmWinners.isNotEmpty()) {
            require(wmWinners.size == neededQualifierCount) {
                "World Masters erfordert exakt $neededQualifierCount Qualifier-Sieger, erhalten: ${wmWinners.size}"
            }
        }

        val newParticipants = wmWinners.filterNot { wId -> storedParticipants.any { it.playerId == wId } }.map { playerId ->
            TournamentParticipant(
                tournamentId = tournamentId,
                playerId = playerId,
                qualificationType = "QUALIFIER"
            )
        }

        if (persistToDb && newParticipants.isNotEmpty()) {
            tournamentRepository.insertParticipants(newParticipants)
        }

        val allParticipants = storedParticipants + newParticipants

        val top16Oom = allParticipants.filter { it.qualificationType == "OOM" && it.seedNumber != null }
            .sortedBy { it.seedNumber ?: Int.MAX_VALUE }
            .map { it.playerId }
        val unseededOom = allParticipants.filter { it.qualificationType == "OOM" && it.seedNumber == null }
            .map { it.playerId }
        val qualifiers = allParticipants.filter { it.qualificationType == "QUALIFIER" }
            .map { it.playerId }

        val unseededPool = unseededOom + qualifiers

        return TournamentQualificationResult(
            oomSortedIds = top16Oom,
            proTourSortedIds = emptyList(),
            qualifierIds = unseededPool,
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
        val r1MatchesPerBoard = if (boardCount > 0 && qualR1Matches.isNotEmpty()) {
            qualR1Matches.size / boardCount
        } else {
            4
        }
        val placeholderId = PLACEHOLDER_QUAL_PRELIM_BASE_ID - prelimMatchIndex
        val r1Match = qualR1Matches.firstOrNull { r1 ->
            r1.player1Id == placeholderId || r1.player2Id == placeholderId ||
                (winnerId != null && (r1.player1Id == winnerId || r1.player2Id == winnerId))
        }
        return r1Match?.let { it.matchIndex / r1MatchesPerBoard } ?: (prelimMatchIndex % boardCount)
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
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        currentCareerId: Long,
        preloadedParticipants: List<TournamentParticipant>? = null,
        preloadedPlayers: List<Player>? = null
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

        val sequence = QualificationHelper.getRoundSequence(event, allMatches)
        val roundEnum = TournamentRound.valueOf(roundKey)
        val roundIndex = sequence.indexOf(roundEnum)

        val groups = mutableListOf<EntrantGroupDomainData>()

        when (roundKey) {
            TournamentRound.R2_128.name -> {
                require(stored65To96.size == 32) {
                    "UK/European Open R2 erfordert genau 32 gespeicherte OOM_65_96-Teilnehmer (gefunden: ${stored65To96.size})"
                }
                val players = stored65To96.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 65 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_65_96, players = players))
            }
            TournamentRound.R3_96.name -> {
                require(stored33To64.size == 32) {
                    "UK/European Open R3 erfordert genau 32 gespeicherte OOM_33_64-Teilnehmer (gefunden: ${stored33To64.size})"
                }
                val players = stored33To64.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 33 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_33_64, players = players))
            }
            TournamentRound.L64.name -> {
                require(storedTop32.size == 32) {
                    "UK/European Open L64 erfordert genau 32 gespeicherte OOM_TOP_32-Teilnehmer (gefunden: ${storedTop32.size})"
                }
                val players = storedTop32.mapIndexed { index, id -> createEntrantPlayerDomainInfo(id, 1 + index) }
                groups.add(EntrantGroupDomainData(groupType = EntrantGroupType.OOM_TOP_32, players = players))
            }
        }

        if (roundIndex > 0) {
            val prevRoundEnum = sequence[roundIndex - 1]
            val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
            if (prevMatches.isNotEmpty() && prevMatches.any { it.isCompleted }) {
                val winnerIds = prevMatches.filter { it.isCompleted }.map { match ->
                    requireNotNull(match.winnerId) { "Abgeschlossenes Match ${match.id} in Runde ${prevRoundEnum.name} hat keinen winnerId" }
                }
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
            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            val qualTournament = preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
            val participants = if (qualTournament != null && qualTournament.isCompleted) {
                val qualMatches = tournamentRepository.getMatchesListForTournament(qualTournament.id)
                val existingIds = stored.map { it.playerId }.toSet()
                val qualWinners = qualMatches
                    .asSequence()
                    .filter { it.round == TournamentRound.QUAL_FINAL.name && it.winnerId != null }
                    .sortedBy { it.matchIndex }
                    .mapNotNull { it.winnerId }
                    .filterNot { it in existingIds }
                    .map { winnerId ->
                        TournamentParticipant(
                            tournamentId = tournament.id,
                            playerId = winnerId,
                            qualificationType = "QUALIFIER"
                        )
                    }
                    .toList()
                stored + qualWinners
            } else {
                stored
            }
            ParticipantPreviewResult(participants = participants, isFrozen = true)
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
