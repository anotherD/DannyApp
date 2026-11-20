package com.danny.dannyapp.domain.usecase

import android.content.Context
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GetOrderOfMeritUseCase(
    private val careerRepository: CareerRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository
) {
    constructor(context: Context) : this(
        CareerRepository(context),
        SeasonRepository(context),
        TournamentRepository(context),
        PlayerRepository(context)
    )

    suspend operator fun invoke(careerId: Long, type: OrderOfMeritType): List<OrderOfMeritEntry> = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        val seasons = seasonRepository.getSeasonsListForCareer(careerId)
        if (seasons.isEmpty()) return@withContext emptyList()

        val seasonIds = seasons.map { id -> id.id }
        val currentSeason = seasons.maxByOrNull { it.seasonNumber }

        val allTournaments = mutableListOf<Tournament>()
        for (season in seasons) {
            allTournaments.addAll(tournamentRepository.getTournamentsListForSeason(season.id))
        }

        val allPlacements = tournamentRepository.getPlacementsForSeasons(seasonIds)
        val allPlayers = playerRepository.getAllPlayersList()
        val playerMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerRepository.getCareerById(careerId)
        val userName = careerWithProfile.playerName
        val userCountryCode = careerWithProfile.countryCode
        val userSkill = SkillLevel.DEFAULT.value

        val seasonMap = seasons.associateBy { it.id }

        val selectedTournamentIds = when (type) {
            OrderOfMeritType.OOM -> {
                // OOM: Summe aus prize_money aller Turniere der jüngsten 2 Austragungen pro Turnier-Event (saisonübergreifend)
                allTournaments.groupBy { it.name }
                    .flatMap { (_, tournaments) ->
                        tournaments.sortedWith(
                            compareByDescending<Tournament> { seasonMap[it.seasonId]?.seasonNumber ?: 0 }
                                .thenByDescending { it.order }
                                .thenByDescending { it.id }
                        ).take(2)
                    }
                    .map { it.id }
            }
            OrderOfMeritType.PT_OOM -> {
                // PT_OOM: Summe aus prize_money von PLAYERS_CHAMPIONSHIP und EUROPEAN_TOUR, jeweils nur die jüngste Austragung pro Event
                allTournaments.filter {
                    val t = it.type.uppercase()
                    t.contains("PLAYERS_CHAMPIONSHIP") || t.contains("EUROPEAN_TOUR")
                }.groupBy { it.name }
                    .flatMap { (_, tournaments) ->
                        tournaments.sortedWith(
                            compareByDescending<Tournament> { seasonMap[it.seasonId]?.seasonNumber ?: 0 }
                                .thenByDescending { it.order }
                                .thenByDescending { it.id }
                        ).take(1)
                    }
                    .map { it.id }
            }
            OrderOfMeritType.PC_OOM -> {
                // PC_OOM: Ausschließlich PLAYERS_CHAMPIONSHIP der aktuellen Saison
                if (currentSeason == null) emptyList()
                else allTournaments.filter {
                    it.seasonId == currentSeason.id && it.type.uppercase().contains("PLAYERS_CHAMPIONSHIP")
                }.map { it.id }
            }
            OrderOfMeritType.ET_OOM -> {
                // ET_OOM: Ausschließlich EUROPEAN_TOUR der aktuellen Saison
                if (currentSeason == null) emptyList()
                else allTournaments.filter {
                    it.seasonId == currentSeason.id && it.type.uppercase().contains("EUROPEAN_TOUR")
                }.map { it.id }
            }
        }

        val filteredPlacements = allPlacements.filter { it.tournamentId in selectedTournamentIds }
        val prizeMoneyMap = mutableMapOf<Long, Double>()

        // Ensure user is in map
        prizeMoneyMap[USER_ID] = 0.0

        for (placement in filteredPlacements) {
            val current = prizeMoneyMap[placement.playerId] ?: 0.0
            prizeMoneyMap[placement.playerId] = current + placement.prizeMoney
        }

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

        // Sort descending by prize money, then skill, then name
        val sorted = entries.sortedWith(
            compareByDescending<OrderOfMeritEntry> { it.prizeMoney }
                .thenByDescending { it.skill }
                .thenBy { it.name }
        )

        sorted.mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
    }

    /**
     * Beobachtet reaktiv Saisons und Platzierungen für eine Karriere
     * und emittiert bei jeder Änderung die berechnete Order of Merit Einträge.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeOrderOfMerit(careerId: Long, type: OrderOfMeritType): Flow<List<OrderOfMeritEntry>> {
        return seasonRepository.getSeasonsForCareer(careerId)
            .flatMapLatest { seasons ->
                val seasonIds = seasons.map { it.id }
                if (seasonIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    tournamentRepository.getPlacementsFlowForSeasons(seasonIds)
                }
            }
            .map {
                invoke(careerId, type)
            }
    }
}
