package com.danny.dannyapp.domain.model

import com.danny.dannyapp.domain.enums.TournamentRound

/**
 * Phase or status of a specific tournament round.
 */
enum class TournamentRoundPhase {
    COMPLETED,
    ACTIVE,
    PENDING
}

/**
 * Encapsulates the tournament progress evaluation and serves as the single source of truth
 * for active round determination, round phases, and advancement eligibility.
 *
 * @property activeRound The currently active round, or `null` if the tournament is already completed.
 * @property roundPhases Map containing the status ([TournamentRoundPhase]) for each relevant round of the tournament.
 * @property canAdvanceRound `true` if the tournament is not completed, all matches of the [activeRound] are completed,
 *                           and round advancement or tournament completion has not yet been processed.
 * @property isFinalRound `true` if [activeRound] is the final round of the tournament ([TournamentRound.F] or [TournamentRound.QUAL_FINAL]).
 * @property hasOpenCpuMatchesInActiveRound `true` if there are still unplayed matches between two real CPU players in [activeRound].
 */
data class TournamentProgressState(
    val activeRound: TournamentRound?,
    val roundPhases: Map<TournamentRound, TournamentRoundPhase>,
    val canAdvanceRound: Boolean,
    val isFinalRound: Boolean,
    val hasOpenCpuMatchesInActiveRound: Boolean
)
