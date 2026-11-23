package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * UseCase zum Importieren historischer Order-of-Merit-Daten beim Anlegen einer Karriere.
 *
 * Legt zwei historische Vor-Saisons (Saison -1 und Saison 0) an und befüllt sie mit Platzierungen
 * aus initial_placements.csv sowie einem Plausibilitäts-Fallback für nicht in der CSV enthaltene Spieler.
 */
class InitializeCareerOomUseCase(
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val readInitialPlacementsLines: () -> Sequence<String>,
) {

    private data class RawPlacement(
        val playerName: String,
        val playerId: Long,
        val dateStr: String,
        val mappedTournamentName: String,
        val prizeMoney: Double,
    )

    private data class AssignedPlacement(
        val playerId: Long,
        val seasonNumber: Int,
        val mappedTournamentName: String,
        val prizeMoney: Double,
    )

    suspend operator fun invoke(careerId: Long) = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }

        val seasonIdMinus1 = seasonRepository.createHistoricalSeason(careerId, -1)
        val seasonId0 = seasonRepository.createHistoricalSeason(careerId, 0)

        val tournamentsMinus1 = tournamentRepository.getTournamentsListForSeason(seasonIdMinus1)
        val tournaments0 = tournamentRepository.getTournamentsListForSeason(seasonId0)

        val allPlayers = playerRepository.getAllPlayersList()
        val nameToPlayerId = allPlayers.associateBy({ it.name }, { it.id })

        val rawLines = readInitialPlacementsLines().toList()
        val parsedEntries = mutableListOf<RawPlacement>()
        var isFirstLine = true

        for (line in rawLines) {
            val cleanLine = line.replace("\uFEFF", "").trim()
            if (cleanLine.isBlank()) continue
            if (isFirstLine && cleanLine.startsWith("player_name", ignoreCase = true)) {
                isFirstLine = false
                continue
            }
            isFirstLine = false

            val parts = cleanLine.split(";")
            if (parts.size < 4) continue

            val playerName = parts[0].trim()
            val dateStr = parts[1].trim()
            val rawTournamentName = parts[2].trim()
            val prizeMoney = parts[3].trim().toDoubleOrNull() ?: continue

            val playerId = nameToPlayerId[playerName] ?: continue

            val mappedName = mapRawTournamentName(rawTournamentName)
            parsedEntries.add(
                RawPlacement(
                    playerName = playerName,
                    playerId = playerId,
                    dateStr = dateStr,
                    mappedTournamentName = mappedName,
                    prizeMoney = prizeMoney,
                )
            )
        }

        val playersWithCsvPlacements = parsedEntries.map { it.playerId }.toSet()

        val maxDate = parsedEntries.maxOfOrNull { it.dateStr }
        val cutoffDate = if (maxDate != null) {
            val parts = maxDate.split(".")
            if (parts.size == 3) {
                val cutoffYear = parts[0].toInt() - 1
                val cutoffMonth = parts[1].toInt()
                val cutoffDay = parts[2].toInt()
                String.format(Locale.US, "%04d.%02d.%02d", cutoffYear, cutoffMonth, cutoffDay)
            } else {
                "2025.11.01"
            }
        } else {
            "2025.11.01"
        }

        val assignedEntries = mutableListOf<AssignedPlacement>()
        val groupedByPlayerAndTournament = parsedEntries.groupBy { Pair(it.playerId, it.mappedTournamentName) }

        for ((_, group) in groupedByPlayerAndTournament) {
            val sortedGroup = group.sortedBy { it.dateStr }
            if (sortedGroup.size == 2) {
                assignedEntries.add(AssignedPlacement(sortedGroup[0].playerId, -1, sortedGroup[0].mappedTournamentName, sortedGroup[0].prizeMoney))
                assignedEntries.add(AssignedPlacement(sortedGroup[1].playerId, 0, sortedGroup[1].mappedTournamentName, sortedGroup[1].prizeMoney))
            } else {
                for (entry in sortedGroup) {
                    val seasonNum = if (entry.dateStr < cutoffDate) -1 else 0
                    assignedEntries.add(AssignedPlacement(entry.playerId, seasonNum, entry.mappedTournamentName, entry.prizeMoney))
                }
            }
        }

        val placementSumMap = mutableMapOf<Triple<Long, Int, Long>, Double>()

        for (entry in assignedEntries) {
            val seasonTournaments = if (entry.seasonNumber == -1) tournamentsMinus1 else tournaments0
            val tournament = resolveTournament(seasonTournaments, entry.mappedTournamentName)
            val key = Triple(entry.playerId, entry.seasonNumber, tournament.id)
            val currentSum = placementSumMap.getOrDefault(key, 0.0)
            placementSumMap[key] = currentSum + entry.prizeMoney
        }

        val placementsToInsert = mutableListOf<TournamentPlacement>()
        for ((key, sumPrizeMoney) in placementSumMap) {
            val (playerId, _, tournamentId) = key
            placementsToInsert.add(
                TournamentPlacement(
                    tournamentId = tournamentId,
                    playerId = playerId,
                    reachedRound = TournamentRound.WINNER.name,
                    prizeMoney = sumPrizeMoney,
                )
            )
        }

        val cpuFallbackPlayers = allPlayers.filter { player ->
            (player.id != USER_ID) && (player.prizeMoney > 0.0) && (player.id !in playersWithCsvPlacements)
        }

        val seasonsWithTournaments = listOf(
            tournamentsMinus1,
            tournaments0,
        )

        for (seasonTournaments in seasonsWithTournaments) {
            val rankingTournaments = seasonTournaments.filter { t ->
                val event = runCatching { TournamentEvent.valueOf(t.type) }.getOrNull()
                event != null && !event.isQualifier && event.effectiveTotalPrizeFund > 0.0
            }
            val totalWeight = rankingTournaments.sumOf { t ->
                TournamentEvent.valueOf(t.type).effectiveTotalPrizeFund
            }

            if (totalWeight > 0.0) {
                for (player in cpuFallbackPlayers) {
                    val totalPrizeMoney = player.prizeMoney * 1000.0
                    val seasonPrizeMoney = totalPrizeMoney * 0.5

                    for (tournament in rankingTournaments) {
                        val event = TournamentEvent.valueOf(tournament.type)
                        val share = seasonPrizeMoney * (event.effectiveTotalPrizeFund / totalWeight)
                        if (share > 0.0) {
                            placementsToInsert.add(
                                TournamentPlacement(
                                    tournamentId = tournament.id,
                                    playerId = player.id,
                                    reachedRound = TournamentRound.WINNER.name,
                                    prizeMoney = share,
                                )
                            )
                        }
                    }
                }
            }
        }

        if (placementsToInsert.isNotEmpty()) {
            tournamentRepository.runInTransaction {
                tournamentRepository.insertPlacements(placementsToInsert)
            }
        }
    }

    private fun mapRawTournamentName(rawName: String): String {
        val pcMatch = Regex("^PC(\\d+)$").matchEntire(rawName)
        if (pcMatch != null) {
            return "Players Championship ${pcMatch.groupValues[1]}"
        }

        val etMatch = Regex("^ET(\\d+)$").matchEntire(rawName)
        if (etMatch != null) {
            return "European Tour ${etMatch.groupValues[1]}"
        }

        val directHits = setOf(
            "World Championship",
            "UK Open",
            "World Matchplay",
            "World Grand Prix",
            "European Championship",
            "Grand Slam of Darts",
            "Players Championship Finals",
            "European Open",
        )
        if (directHits.contains(rawName)) {
            return rawName
        }

        return "UK Open"
    }

    private fun resolveTournament(seasonTournaments: List<Tournament>, mappedName: String): Tournament {
        val pcMatch = Regex("^Players Championship (\\d+)$").matchEntire(mappedName)
        if (pcMatch != null) {
            val num = pcMatch.groupValues[1].toInt()
            val pcList = seasonTournaments
                .filter { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP.name }
                .sortedBy { it.order }
            if (num in 1..pcList.size) {
                return pcList[num - 1]
            }
        }

        val etMatch = Regex("^(?:European Tour|ET)(\\d+)$").matchEntire(mappedName)
        if (etMatch != null) {
            val num = etMatch.groupValues[1].toInt()
            val etList = seasonTournaments
                .filter { it.type == TournamentEvent.EUROPEAN_TOUR.name }
                .sortedBy { it.order }
            if (num in 1..etList.size) {
                return etList[num - 1]
            }
        }

        return seasonTournaments.firstOrNull { it.name.equals(mappedName, ignoreCase = true) }
            ?: seasonTournaments.first { it.name == "UK Open" }
    }
}
