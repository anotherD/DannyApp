package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.model.OrderOfMeritType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetOrderOfMeritUseCase(
    private val careerDao: CareerDao,
    private val seasonDao: SeasonDao,
    private val tournamentDao: TournamentDao,
    private val placementDao: TournamentPlacementDao,
    private val playerDao: PlayerDao
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).careerDao(),
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao()
    )

    suspend operator fun invoke(careerId: Long, type: OrderOfMeritType): List<OrderOfMeritEntry> = withContext(Dispatchers.IO) {
        val seasons = seasonDao.getSeasonsListForCareer(careerId)
        if (seasons.isEmpty()) return@withContext emptyList()

        val seasonIds = seasons.map { it.id }
        val currentSeason = seasons.maxByOrNull { it.seasonNumber }

        val allTournaments = mutableListOf<Tournament>()
        for (season in seasons) {
            allTournaments.addAll(tournamentDao.getTournamentsListForSeason(season.id))
        }

        val allPlacements = placementDao.getPlacementsForSeasons(seasonIds)
        val allPlayers = playerDao.getAllPlayersList()
        val playerMap = allPlayers.associateBy { it.id }

        val careerWithProfile = careerDao.getCareerById(careerId)
        val userName = careerWithProfile?.profileName ?: careerWithProfile?.career?.name ?: "User"
        val userSkill = careerWithProfile?.career?.level ?: 5

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
                countryCode = "GER"
                skill = userSkill
            } else {
                val player = playerMap[playerId]
                name = player?.name ?: "Unknown"
                countryCode = player?.countryCode ?: "ENG"
                skill = player?.skill ?: 5
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
}
