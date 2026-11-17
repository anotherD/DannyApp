package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound


/**
 * Phase of a tournament in the season timeline.
 */
enum class TournamentPhaseState {
    COMPLETED,
    ACTIVE,
    PLANNED
}

/**
 * Summarises the important status information for a tournament.
 *
 * All fields are domain‑layer only; no Android UI resources are referenced.
 */
data class TournamentSummary(
    val tournament: Tournament,
    val phaseState: TournamentPhaseState,
    val isNextPlanned: Boolean,
    val winnerId: Long?,
    val qualifierWinnerIds: List<Long>,
    val userReachedRound: String?,
    val userPrizeMoney: Double,
    val activeRound: TournamentRound?
)

/**
 * Use‑case that builds a list of [TournamentSummary] for a whole season.
 *
 * The implementation follows the rules described in the user request.
 */
class GetTournamentSummaryUseCase {

    /**
     * Creates a summary for every tournament in the given season.
     *
     * @param tournaments List of tournaments belonging to the season.
     * @param matchesByTournament Map of tournamentId → list of its matches.
     * @param placementsByTournament Map of tournamentId → list of player placements.
     * @param activeRoundsByTournament Map of tournamentId → currently active round (or null).
     * @return Ordered list of [TournamentSummary] according to tournament order.
     */
    fun buildSeasonSummaries(
        tournaments: List<Tournament>,
        matchesByTournament: Map<Long, List<TournamentMatch>>,
        placementsByTournament: Map<Long, List<TournamentPlacement>>,
        activeRoundsByTournament: Map<Long, TournamentRound?>
    ): List<TournamentSummary> {
        // Sort by the defined order field.
        val sortedTournaments = tournaments.sortedBy { it.order }

        // Determine if any tournament is currently ACTIVE.
        val anyActive = sortedTournaments.any { !it.isCompleted && (matchesByTournament[it.id]?.isNotEmpty() == true) }

        // Find the first PLANNED tournament (if needed for isNextPlanned).
        val firstPlannedId = if (!anyActive) {
            sortedTournaments.firstOrNull { !it.isCompleted && (matchesByTournament[it.id]?.isEmpty() != false) }?.id
        } else null

        return sortedTournaments.map { tournament ->
            val matches = matchesByTournament[tournament.id] ?: emptyList()
            val placements = placementsByTournament[tournament.id] ?: emptyList()
            val phaseState = when {
                tournament.isCompleted -> TournamentPhaseState.COMPLETED
                matches.isNotEmpty() -> TournamentPhaseState.ACTIVE
                else -> TournamentPhaseState.PLANNED
            }

            val isNextPlanned = tournament.id == firstPlannedId

            var winnerId: Long? = null
            var qualifierWinnerIds: List<Long> = emptyList()

            if (phaseState == TournamentPhaseState.COMPLETED) {
                val event = try { TournamentEvent.valueOf(tournament.type) } catch (_: Exception) { null }
                if (event != null && event in listOf(
                        TournamentEvent.QUALIFIER_ET,
                        TournamentEvent.QUALIFIER_GRAND_SLAM,
                        TournamentEvent.QUALIFIER_WM
                    )) {
                    // Multiple qualifier winners – collect from QUAL_FINAL round.
                    qualifierWinnerIds = matches
                        .filter { it.round == TournamentRound.QUAL_FINAL.name && it.winnerId != null }
                        .sortedBy { it.matchIndex }
                        .mapNotNull { it.winnerId }
                } else {
                    // Single winner tournament – fetch winner from final round.
                    val finalMatch = matches
                        .find { it.round == TournamentRound.F.name && it.winnerId != null }
                        ?: matches.lastOrNull { it.winnerId != null }
                    winnerId = finalMatch?.winnerId
                }
            }

            // User specific data.
            val userPlacement = placements.find { it.playerId == USER_ID }
            val userReachedRound = userPlacement?.reachedRound
            val userPrizeMoney = userPlacement?.prizeMoney ?: 0.0

            val activeRound = if (phaseState == TournamentPhaseState.ACTIVE) {
                activeRoundsByTournament[tournament.id]
            } else null

            TournamentSummary(
                tournament = tournament,
                phaseState = phaseState,
                isNextPlanned = isNextPlanned,
                winnerId = winnerId,
                qualifierWinnerIds = qualifierWinnerIds,
                userReachedRound = userReachedRound,
                userPrizeMoney = userPrizeMoney,
                activeRound = activeRound
            )
        }
    }
}
