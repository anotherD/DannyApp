package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class GetOrderOfMeritUseCase(
    private val careerRepository: CareerRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository
) {

    suspend operator fun invoke(careerId: Long, type: OrderOfMeritType): List<OrderOfMeritEntry> = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        val seasons = seasonRepository.getSeasonsListForCareer(careerId)
        require(seasons.isNotEmpty()) { "Keine Saisons für Karriere-ID $careerId gefunden" }

        val seasonIds = seasons.map { it.id }
        val currentSeason = seasonRepository.getCurrentSeasonForCareer(careerId)

        val allTournaments = tournamentRepository.getTournamentsListForSeasons(seasonIds)

        val allPlacements = tournamentRepository.getPlacementsForSeasons(seasonIds)
        val allPlayers = playerRepository.getAllPlayersList()
        val playerMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(careerId)
        val userName = careerWithProfile.playerName
        val userCountryCode = careerWithProfile.countryCode
        val userSkill = SkillLevel.DEFAULT.value

        val seasonMap = seasons.associateBy { it.id }

        val tournamentChronologicalDescendingComparator = compareByDescending<Tournament> {
            val season = requireNotNull(seasonMap[it.seasonId]) { "Season mit ID ${it.seasonId} nicht gefunden" }
            season.seasonNumber
        }.thenByDescending { it.order }
            .thenByDescending { it.id }

        val startedOrCompletedTournaments = allTournaments.filter { it.isCompleted || it.currentRound != null || seasonMap[it.seasonId]?.isCompleted == true }

        val oomTournaments = startedOrCompletedTournaments.groupBy { it.name }
            .flatMap { (_, tournaments) ->
                tournaments.sortedWith(tournamentChronologicalDescendingComparator).take(2)
            }
            .sortedWith(tournamentChronologicalDescendingComparator)

        val ptOomTournaments = startedOrCompletedTournaments.filter {
            val tournamentType = it.type.uppercase()
            tournamentType.contains("PLAYERS_CHAMPIONSHIP") || tournamentType.contains("EUROPEAN_TOUR")
        }.groupBy { it.name }
            .flatMap { (_, tournaments) ->
                tournaments.sortedWith(tournamentChronologicalDescendingComparator).take(1)
            }
            .sortedWith(tournamentChronologicalDescendingComparator)

        val pcOomTournaments = allTournaments.filter {
            it.seasonId == currentSeason.id && it.type.uppercase().contains("PLAYERS_CHAMPIONSHIP")
        }.sortedWith(tournamentChronologicalDescendingComparator)

        val etOomTournaments = allTournaments.filter {
            it.seasonId == currentSeason.id && it.type.uppercase().contains("EUROPEAN_TOUR")
        }.sortedWith(tournamentChronologicalDescendingComparator)

        val relevantTournaments = when (type) {
            OrderOfMeritType.OOM -> oomTournaments
            OrderOfMeritType.PT_OOM -> ptOomTournaments
            OrderOfMeritType.PC_OOM -> pcOomTournaments
            OrderOfMeritType.ET_OOM -> etOomTournaments
        }

        val selectedTournamentIds = relevantTournaments.map { it.id }.toSet()
        val filteredPlacements = allPlacements.filter { it.tournamentId in selectedTournamentIds }
        val prizeMoneyMap = mutableMapOf<Long, Double>()

        // Ensure user is in map
        prizeMoneyMap[USER_ID] = 0.0

        val tourCardPlayerIds = seasonRepository.getTourCardPlayerIdsForSeason(currentSeason.id)
        for (tcPlayerId in tourCardPlayerIds) {
            if (tcPlayerId == USER_ID || playerMap.containsKey(tcPlayerId)) {
                prizeMoneyMap.putIfAbsent(tcPlayerId, 0.0)
            }
        }

        for (placement in filteredPlacements) {
            val current = prizeMoneyMap[placement.playerId] ?: 0.0
            prizeMoneyMap[placement.playerId] = current + placement.prizeMoney
        }

        val placementPrizeMap = allPlacements.associate { (it.playerId to it.tournamentId) to it.prizeMoney }

        val entries = prizeMoneyMap.map { (playerId, prizeMoney) ->
            val isUser = playerId == USER_ID
            val name: String
            val countryCode: String
            val skill: Int

            if (isUser) {
                name = userName
                countryCode = userCountryCode
                skill = userSkill
            } else {
                val player = requireNotNull(playerMap[playerId]) { "CPU-Spieler mit ID $playerId nicht gefunden" }
                name = player.name
                countryCode = player.countryCode
                skill = player.skill
            }

            OrderOfMeritEntry(
                playerId = playerId,
                name = name,
                countryCode = countryCode,
                skill = skill,
                prizeMoney = prizeMoney,
                rank = 0,
                isUser = isUser
            )
        }

        val shuffledPlayerIds = entries.map { it.playerId }.shuffled()
        val randomTieBreakerMap = shuffledPlayerIds.mapIndexed { index, playerId -> playerId to index }.toMap()

        // PDC Countback Rule (Section 3.2): Gesamtpreisgeld DESC -> Countback Sub-Rangliste -> Countback OOM -> Los (Zufall)
        val sorted = entries.sortedWith { entryA, entryB ->
            val prizeDiff = entryB.prizeMoney.compareTo(entryA.prizeMoney)
            if (prizeDiff != 0) return@sortedWith prizeDiff

            val countbackDiff = compareCountback(entryA.playerId, entryB.playerId, relevantTournaments, placementPrizeMap)
            if (countbackDiff != 0) return@sortedWith countbackDiff

            if (type != OrderOfMeritType.OOM) {
                val oomTotalA = oomTournaments.sumOf { placementPrizeMap[entryA.playerId to it.id] ?: 0.0 }
                val oomTotalB = oomTournaments.sumOf { placementPrizeMap[entryB.playerId to it.id] ?: 0.0 }
                val oomTotalDiff = oomTotalB.compareTo(oomTotalA)
                if (oomTotalDiff != 0) return@sortedWith oomTotalDiff

                val oomCountbackDiff = compareCountback(entryA.playerId, entryB.playerId, oomTournaments, placementPrizeMap)
                if (oomCountbackDiff != 0) return@sortedWith oomCountbackDiff
            }

            val tieBreakerA = requireNotNull(randomTieBreakerMap[entryA.playerId]) { "Tie-Breaker-Los für Spieler ${entryA.playerId} nicht gefunden" }
            val tieBreakerB = requireNotNull(randomTieBreakerMap[entryB.playerId]) { "Tie-Breaker-Los für Spieler ${entryB.playerId} nicht gefunden" }
            tieBreakerA.compareTo(tieBreakerB)
        }

        sorted.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
    }

    /**
     * Vergleicht zwei Spieler anhand der PDC-Countback-Regel auf einer Liste chronologisch absteigend sortierter Turniere.
     * 1. Kumulierte Preisgeldsumme der letzten 4 Turniere.
     * 2. Schrittweiser Vergleich des 5., 6., ... Turniers bis zum Anfang des Fensters.
     * Gibt einen negativen Wert zurück, wenn playerA vor playerB rangiert (höheres Preisgeld).
     */
    private fun compareCountback(
        playerA: Long,
        playerB: Long,
        tournaments: List<Tournament>,
        placementPrizeMap: Map<Pair<Long, Long>, Double>
    ): Int {
        if (tournaments.isEmpty()) return 0

        val last4Tournaments = tournaments.take(4)
        val sumA4 = last4Tournaments.sumOf { placementPrizeMap[playerA to it.id] ?: 0.0 }
        val sumB4 = last4Tournaments.sumOf { placementPrizeMap[playerB to it.id] ?: 0.0 }
        val diff4 = sumB4.compareTo(sumA4)
        if (diff4 != 0) return diff4

        for (i in 4 until tournaments.size) {
            val tournament = tournaments[i]
            val prizeA = placementPrizeMap[playerA to tournament.id] ?: 0.0
            val prizeB = placementPrizeMap[playerB to tournament.id] ?: 0.0
            val diff = prizeB.compareTo(prizeA)
            if (diff != 0) return diff
        }

        return 0
    }

    /**
     * Beobachtet reaktiv Saisons und Platzierungen für eine Karriere
     * und emittiert bei jeder Änderung die berechnete Order of Merit Einträge.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeOrderOfMerit(careerId: Long, type: OrderOfMeritType): Flow<List<OrderOfMeritEntry>> {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        return seasonRepository.getSeasonsForCareer(careerId)
            .flatMapLatest { seasons ->
                if (seasons.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val seasonIds = seasons.map { it.id }
                    val currentSeason = requireNotNull(seasons.maxByOrNull { it.seasonNumber })
                    combine(
                        tournamentRepository.getPlacementsFlowForSeasons(seasonIds),
                        seasonRepository.getTourCardsForSeason(currentSeason.id)
                    ) { _, _ ->
                        invoke(careerId, type)
                    }
                }
            }
    }
}
