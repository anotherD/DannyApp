package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.GroupStandingEntry




/**
 * Use‑case that calculates the group standings for the Grand Slam of Darts.
 *
 * The calculation is performed completely in the domain layer – no Android UI resources are used.
 * It treats the passed matches as the single source of truth and derives the table for every
 * group ("A" … "P").
 */
class CalculateGroupStandingsUseCase {

    /**
     * Returns a map where the key is the group letter and the value is a list of
     * [GroupStandingEntry] sorted by the tournament rules.
     */
    operator fun invoke(groupMatches: List<TournamentMatch>): Map<String, List<GroupStandingEntry>> {
        // Filter only matches that belong to the GROUP round and have a non-blank group name,
        // so that an empty-string key "" can never appear in the result map.
        val filtered = groupMatches.filter {
            it.round == TournamentRound.GROUP.name && !it.groupName.isNullOrBlank()
        }

        // Group matches by their group name (A‑P).
        val matchesByGroup = filtered.groupBy { it.groupName!! }

        val result = mutableMapOf<String, List<GroupStandingEntry>>()

        for ((groupLetter, matches) in matchesByGroup) {
            // All player IDs that appear in the group, even if they have not played yet.
            val playerIds = matches.flatMap { listOf(it.player1Id, it.player2Id) }
                .filter { it != 0L }
                .distinct()

            // Mutable stats per player.
            data class Stats(
                var played: Int = 0,
                var wins: Int = 0,
                var legsWon: Int = 0,
                var legsLost: Int = 0
            )

            val statsMap = playerIds.associateWith { Stats() }.toMutableMap()

            // Evaluate completed matches.
            for (match in matches) {
                if (!match.isCompleted) continue
                val p1Stats = statsMap[match.player1Id]
                val p2Stats = statsMap[match.player2Id]
                if (p1Stats != null && p2Stats != null) {
                    // Both players participated in this match.
                    p1Stats.played++
                    p2Stats.played++

                    p1Stats.legsWon += match.player1Score
                    p1Stats.legsLost += match.player2Score
                    p2Stats.legsWon += match.player2Score
                    p2Stats.legsLost += match.player1Score

                    if (match.winnerId == match.player1Id) {
                        p1Stats.wins++
                    } else if (match.winnerId == match.player2Id) {
                        p2Stats.wins++
                    }
                }
            }

            // Build the list of entries.
            val entries = playerIds.map { id ->
                val s = statsMap[id] ?: Stats()
                GroupStandingEntry(
                    groupLetter = groupLetter,
                    rank = 0, // temporary, will be filled after sorting
                    playerId = id,
                    played = s.played,
                    wins = s.wins,
                    legsWon = s.legsWon,
                    legsLost = s.legsLost,
                    legDiff = s.legsWon - s.legsLost,
                    points = s.wins * 2
                )
            }.sortedWith(
                compareByDescending<GroupStandingEntry> { it.wins }
                    .thenByDescending { it.legDiff }
                    .thenByDescending { it.legsWon }
                    .thenBy { it.playerId }
            ).mapIndexed { index, entry ->
                entry.copy(rank = index + 1)
            }

            result[groupLetter] = entries
        }

        return result
    }
}
