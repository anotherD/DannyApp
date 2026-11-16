package com.danny.dannyapp.ui

import android.content.Context
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.Country
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.PlayerUiInfo

/**
 * Helper class for tournament bracket previews, seeding calculation, and round sequences.
 */
class TournamentPreviewHelper(
    private val context: Context,
    private val seasonDao: SeasonDao = AppDatabase.getDatabase(context).seasonDao(),
    private val tourCardDao: TourCardDao = AppDatabase.getDatabase(context).tourCardDao(),
    private val playerDao: PlayerDao = AppDatabase.getDatabase(context).playerDao(),
    private val careerRepository: CareerRepository = CareerRepository(context),
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase = GetOrderOfMeritUseCase(context)
) {

    /**
     * Determines the sequence of rounds for a given [TournamentEvent].
     * If [allMatches] is provided and non-empty, truncates the sequence to start from the earliest round present in matches.
     */
    fun getRoundSequence(
        event: TournamentEvent,
        allMatches: List<TournamentMatch> = emptyList()
    ): List<TournamentRound> {
        val fullSequence = when (event) {
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_FINAL
            )
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
            else -> listOf(TournamentRound.L32, TournamentRound.L16)
        }

        if (allMatches.isEmpty()) return fullSequence

        val startIndex = allMatches.mapNotNull { match ->
            fullSequence.indexOfFirst { it.name == match.round }.takeIf { it >= 0 }
        }.minOrNull() ?: 0

        return fullSequence.subList(startIndex, fullSequence.size)
    }

    /**
     * Maps player IDs to their seed numbers for a given [TournamentEvent] and [seasonId].
     */
    suspend fun getSeededPlayerMap(event: TournamentEvent, seasonId: Long): Map<Long, Int> {
        val season = seasonDao.getSeasonById(seasonId) ?: return emptyMap()
        val careerId = season.careerId

        val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
        val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val pcOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PC_OOM)
        val etOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.ET_OOM)

        return when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val tourCardIds = fillToTargetCount(tourCardPlayerIds, 128, allPlayerIds, oomEntries)
                val sortedByPt = getPlayerListSortedByOom(tourCardIds, ptOomEntries)
                sortedByPt.take(32).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.EUROPEAN_TOUR -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top16Oom = sortedOomTc.take(16)
                val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
                val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomEntries).take(16)
                val seedMap = mutableMapOf<Long, Int>()
                top16Oom.forEachIndexed { index, id -> seedMap[id] = index + 1 }
                top16PtOom.forEachIndexed { index, id -> seedMap[id] = index + 1 }
                seedMap
            }
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> emptyMap()
            TournamentEvent.WORLD_MATCHPLAY -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.WORLD_GRAND_PRIX -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val sortedOom = getPlayerListSortedByOom(allPlayerIds, oomEntries)
                sortedOom.take(16).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                val top32Et = getPlayerListSortedByOom(allPlayerIds, etOomEntries).take(32)
                val final32Et = fillToTargetCount(top32Et, 32, allPlayerIds, oomEntries)
                final32Et.mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val top64Pc = getPlayerListSortedByOom(allPlayerIds, pcOomEntries).take(64)
                val final64Pc = fillToTargetCount(top64Pc, 64, allPlayerIds, oomEntries)
                final64Pc.mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val top40Oom = sortedOomTc.take(40)
                val remainingForPt = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40Pt = getPlayerListSortedByOom(remainingForPt, ptOomEntries).take(40)
                val tc80 = top40Oom + top40Pt
                val sortedTc80ByOom = getPlayerListSortedByOom(tc80, oomEntries)
                sortedTc80ByOom.take(32).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM -> {
                val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
                val prequalifiedSet = when (event) {
                    TournamentEvent.QUALIFIER_ET -> {
                        val top16Oom = sortedOomTc.take(16)
                        val remaining = tourCardPlayerIds.filterNot { it in top16Oom }
                        (top16Oom + getPlayerListSortedByOom(remaining, ptOomEntries).take(16)).toSet()
                    }
                    TournamentEvent.QUALIFIER_GRAND_SLAM -> {
                        val top16Oom = sortedOomTc.take(16)
                        val remaining = tourCardPlayerIds.filterNot { it in top16Oom }
                        (top16Oom + getPlayerListSortedByOom(remaining, ptOomEntries).take(16)).toSet()
                    }
                    TournamentEvent.QUALIFIER_WM -> {
                        val top40Oom = sortedOomTc.take(40)
                        val remaining = tourCardPlayerIds.filterNot { it in top40Oom }
                        (top40Oom + getPlayerListSortedByOom(remaining, ptOomEntries).take(40)).toSet()
                    }
                    else -> emptySet()
                }
                val nonPrequalified = tourCardPlayerIds.filterNot { it in prequalifiedSet }
                val sortedNonPrequalified = getPlayerListSortedByOom(nonPrequalified, oomEntries)
                val targetQualifierCount = when (event) {
                    TournamentEvent.QUALIFIER_ET -> 10
                    TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                    TournamentEvent.QUALIFIER_WM -> 4
                    else -> 4
                }
                val nonPrequalifiedCount = sortedNonPrequalified.size
                val targetCount = when {
                    nonPrequalifiedCount > 8 * targetQualifierCount -> 8 * targetQualifierCount
                    nonPrequalifiedCount > 4 * targetQualifierCount -> 4 * targetQualifierCount
                    nonPrequalifiedCount > 2 * targetQualifierCount -> 2 * targetQualifierCount
                    else -> targetQualifierCount
                }
                val seedCount = if (nonPrequalifiedCount > targetCount) {
                    (2 * targetCount - nonPrequalifiedCount).coerceAtLeast(targetQualifierCount)
                } else {
                    targetQualifierCount
                }
                sortedNonPrequalified.take(seedCount).mapIndexed { index, id -> id to (index + 1) }.toMap()
            }
        }
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
            TournamentRound.QUAL_FINAL -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 10
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
                TournamentEvent.QUALIFIER_WM -> 4
                else -> 4
            }
            TournamentRound.QUAL_R2 -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 20
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 16
                TournamentEvent.QUALIFIER_WM -> 8
                else -> 8
            }
            TournamentRound.QUAL_R1 -> when (event) {
                TournamentEvent.QUALIFIER_ET -> 40
                TournamentEvent.QUALIFIER_GRAND_SLAM -> 32
                TournamentEvent.QUALIFIER_WM -> 16
                else -> 16
            }
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

    /**
     * Generates preview matches for a round and propagates winner IDs or seeds from previous rounds.
     */
    suspend fun generatePreviewMatchesWithPropagation(
        tournamentId: Long,
        roundKey: String,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long,
        currentCareerId: Long
    ): List<TournamentMatch> {
        val baseMatches = generatePreviewMatchesForRound(tournamentId, roundKey, event)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return baseMatches }

        if (event in listOf(TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM)) {
            return generateQualifierPreviewMatches(
                tournamentId = tournamentId,
                roundKey = roundKey,
                roundEnum = roundEnum,
                event = event,
                allMatches = allMatches,
                seasonId = seasonId,
                currentCareerId = currentCareerId,
                fallbackMatches = baseMatches
            )
        }

        if (event == TournamentEvent.EUROPEAN_TOUR && roundEnum == TournamentRound.L32) {
            val season = seasonDao.getSeasonById(seasonId)
            val careerId = season?.careerId ?: currentCareerId
            val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
            val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
            val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
            val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomEntries)
            val top16Oom = sortedOomTc.take(16)
            val l48Matches = allMatches.filter { it.round == TournamentRound.L48.name }.sortedBy { it.matchIndex }

            val seedOrder = com.danny.dannyapp.domain.usecase.BracketGenerator.BRACKET_SEED_ORDER_16
            return baseMatches.map { match ->
                val seedIndex = seedOrder.getOrNull(match.matchIndex) ?: match.matchIndex
                val seededPlayer1 = top16Oom.getOrNull(seedIndex) ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                val l48Match = l48Matches.getOrNull(15 - match.matchIndex)
                val seededPlayer2 = if (l48Match?.isCompleted == true && l48Match.winnerId != null) {
                    l48Match.winnerId
                } else {
                    PLACEHOLDER_PREVIOUS_WINNER_ID
                }
                match.copy(player1Id = seededPlayer1, player2Id = seededPlayer2)
            }
        }

        val sequence = getRoundSequence(event)
        val roundIndex = sequence.indexOf(roundEnum)
        if (roundIndex <= 0) {
            return baseMatches
        }

        val prevRoundEnum = sequence[roundIndex - 1]
        val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
        if (prevMatches.isEmpty()) return baseMatches

        return baseMatches.map { match ->
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
     * Builds entrant group information for open draw events like UK Open or European Open.
     */
    suspend fun getUkOpenEntrantGroups(
        careerId: Long,
        roundKey: String,
        allMatches: List<TournamentMatch>,
        event: TournamentEvent,
        currentCareerId: Long
    ): List<EntrantGroupInfo> {
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val allPlayers = playerDao.getAllPlayersList()
        val allPlayerIds = (listOf(USER_ID) + allPlayers.map { it.id }).distinct()
        val sortedPlayerIds = getPlayerListSortedByOom(allPlayerIds, oomEntries)
        val allPlayersMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(currentCareerId)
        val userName = careerWithProfile?.playerName?.ifBlank { null }
            ?: careerWithProfile?.profileName
            ?: careerWithProfile?.career?.name
            ?: context.getString(R.string.player_name_you)
        val userCountryCode = careerWithProfile?.countryCode ?: Country.DEFAULT_USER_CODE

        fun createEntrantPlayerInfo(id: Long, rank: Int?): PlayerUiInfo {
            val name = if (id == USER_ID) userName else allPlayersMap[id]?.name ?: "CPU #$id"
            val country = if (id == USER_ID) userCountryCode else allPlayersMap[id]?.countryCode ?: Country.DEFAULT_CODE
            val skill = if (id == USER_ID) SkillLevel.DEFAULT.value else allPlayersMap[id]?.skill ?: SkillLevel.DEFAULT.value
            return PlayerUiInfo(id = id, name = name, countryCode = country, seed = rank, skill = skill)
        }

        val sequence = getRoundSequence(event, allMatches)
        val roundEnum = try { TournamentRound.valueOf(roundKey) } catch (_: Exception) { return emptyList() }
        val roundIndex = sequence.indexOf(roundEnum)

        val groups = mutableListOf<EntrantGroupInfo>()

        when (roundKey) {
            TournamentRound.R2_128.name -> {
                val players = sortedPlayerIds.drop(64).take(32).mapIndexed { index, id -> createEntrantPlayerInfo(id, 65 + index) }
                groups.add(EntrantGroupInfo(title = context.getString(R.string.entrant_reason_oom_65_96), players = players))
            }
            TournamentRound.R3_96.name -> {
                val players = sortedPlayerIds.drop(32).take(32).mapIndexed { index, id -> createEntrantPlayerInfo(id, 33 + index) }
                groups.add(EntrantGroupInfo(title = context.getString(R.string.entrant_reason_oom_33_64), players = players))
            }
            TournamentRound.L64.name -> {
                val players = sortedPlayerIds.take(32).mapIndexed { index, id -> createEntrantPlayerInfo(id, 1 + index) }
                groups.add(EntrantGroupInfo(title = context.getString(R.string.entrant_reason_oom_top_32), players = players))
            }
        }

        if (roundIndex > 0) {
            val prevRoundEnum = sequence[roundIndex - 1]
            val prevMatches = allMatches.filter { it.round == prevRoundEnum.name }.sortedBy { it.matchIndex }
            if (prevMatches.isNotEmpty() && prevMatches.any { it.isCompleted }) {
                val winnerIds = prevMatches.mapNotNull { if (it.isCompleted) it.winnerId else null }
                if (winnerIds.isNotEmpty()) {
                    val prevRoundDisplayName = TournamentUiHelper.getDisplayRoundName(prevRoundEnum.name, context)
                    val winnerPlayers = winnerIds.map { id -> createEntrantPlayerInfo(id, null) }
                    groups.add(EntrantGroupInfo(title = context.getString(R.string.entrant_reason_round_winners, prevRoundDisplayName), players = winnerPlayers))
                }
            }
        }

        return groups
    }

    /**
     * Returns the non-prequalified Tour Card holders for a qualifier tournament, sorted by Order of Merit.
     */
    suspend fun getNonPrequalifiedTcHoldersForQualifier(
        event: TournamentEvent,
        tourCardPlayerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>,
        ptOomList: List<OrderOfMeritEntry>,
        allPlayers: List<Player>
    ): List<Long> {
        val sortedOomTc = getPlayerListSortedByOom(tourCardPlayerIds, oomList)
        val top16Oom = sortedOomTc.take(16)
        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val top16PtOom = getPlayerListSortedByOom(remainingTcAfterOom, ptOomList).take(16)

        val preQualifiedSet: Set<Long> = when (event) {
            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM -> (top16Oom + top16PtOom).toSet()
            TournamentEvent.QUALIFIER_WM -> {
                val top40Oom = sortedOomTc.take(40)
                val remainingAfterTop40Oom = tourCardPlayerIds.filterNot { it in top40Oom }
                val top40PtOom = getPlayerListSortedByOom(remainingAfterTop40Oom, ptOomList).take(40)
                (top40Oom + top40PtOom).toSet()
            }
            else -> emptySet()
        }

        val nonPrequalified = tourCardPlayerIds.filterNot { it in preQualifiedSet }
        return getPlayerListSortedByOom(nonPrequalified, oomList)
    }

    /**
     * Sorts a list of player IDs based on their Order of Merit ranking, prize money, skill level, and name.
     */
    private suspend fun getPlayerListSortedByOom(
        playerIds: List<Long>,
        oomList: List<OrderOfMeritEntry>
    ): List<Long> {
        val allPlayers = playerDao.getAllPlayersList()
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
                    .thenByDescending { oomMap[it]?.skill ?: SkillLevel.DEFAULT.value }
            )

        for (candidate in remainingCandidates) {
            if (result.size >= targetCount) break
            result.add(candidate)
        }
        return result
    }

    /**
     * Helper method to generate qualifier preview matches.
     */
    private suspend fun generateQualifierPreviewMatches(
        tournamentId: Long,
        roundKey: String,
        roundEnum: TournamentRound,
        event: TournamentEvent,
        allMatches: List<TournamentMatch>,
        seasonId: Long,
        currentCareerId: Long,
        fallbackMatches: List<TournamentMatch>
    ): List<TournamentMatch> {
        val boardCount = when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
            TournamentEvent.QUALIFIER_WM -> 4
            else -> 4
        }
        val season = seasonDao.getSeasonById(seasonId)
        val careerId = season?.careerId ?: currentCareerId
        val tourCards = tourCardDao.getTourCardsListForSeason(seasonId)
        val tourCardPlayerIds = tourCards.map { it.tourCard.playerId }.distinct()
        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.PT_OOM)
        val allPlayers = playerDao.getAllPlayersList()
        val nonPreqTc = getNonPrequalifiedTcHoldersForQualifier(event, tourCardPlayerIds, oomEntries, ptOomEntries, allPlayers)
        val nonPreqCount = nonPreqTc.size

        return when (roundEnum) {
            TournamentRound.QUAL_R1 -> {
                val byeCount = (16 * boardCount - nonPreqCount).coerceAtLeast(0)
                val byePlayers = nonPreqTc.take(byeCount)
                val prelimMatches = allMatches.filter { it.round == TournamentRound.QUAL_PRELIM.name }.sortedBy { it.matchIndex }
                val prelimWinners = prelimMatches.mapIndexed { idx, match ->
                    match.winnerId ?: (-1000L - idx)
                }
                val mainFieldPlayers = byePlayers + prelimWinners

                val boardPlayers = mutableMapOf<Int, MutableList<Long>>()
                for (boardIndex in 0 until boardCount) {
                    boardPlayers[boardIndex] = mutableListOf()
                }
                mainFieldPlayers.forEachIndexed { index, playerId ->
                    val boardIndex = index % boardCount
                    boardPlayers[boardIndex]?.add(playerId)
                }

                val round1Matches = mutableListOf<TournamentMatch>()
                for (boardIndex in 0 until boardCount) {
                    val playerList = boardPlayers[boardIndex] ?: mutableListOf()
                    val seedId = nonPreqTc.getOrNull(boardIndex)
                    if (seedId != null && seedId in playerList && playerList.first() != seedId) {
                        playerList.remove(seedId)
                        playerList.add(0, seedId)
                    }
                    for (matchIdx in 0 until 4) {
                        val player1Id = playerList.getOrNull(2 * matchIdx) ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                        val player2Id = playerList.getOrNull(2 * matchIdx + 1) ?: PLACEHOLDER_PREVIOUS_WINNER_ID
                        round1Matches.add(
                            TournamentMatch(
                                id = -3000L - (boardIndex * 10) - matchIdx,
                                tournamentId = tournamentId,
                                round = roundKey,
                                matchIndex = 4 * boardIndex + matchIdx,
                                player1Id = player1Id,
                                player2Id = player2Id
                            )
                        )
                    }
                }
                round1Matches
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
}
