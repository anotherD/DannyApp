package com.danny.dannyapp.domain.usecase

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TournamentProgressionUseCase(
    private val simulateCpuMatchUseCase: SimulateCpuMatchUseCase,
    private val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase
) {
    constructor(context: Context) : this(
        SimulateCpuMatchUseCase(context),
        AdvanceTournamentRoundUseCase(context)
    )

    /**
     * Simulates all remaining CPU matches in the current round and then advances the tournament round.
     * Returns true if the round was advanced or completed, false otherwise.
     */
    suspend fun simulateAndAdvance(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        simulateCpuMatchUseCase.simulateAllCpuMatches(tournamentId)
        advanceTournamentRoundUseCase(tournamentId)
    }

    /**
     * Simulates CPU matches and advances rounds continuously until a human user match is reached
     * or the tournament is completed.
     */
    suspend fun simulateUntilUserMatchOrCompleted(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        var advancedAny = false
        while (true) {
            simulateCpuMatchUseCase.simulateAllCpuMatches(tournamentId)
            val advanced = advanceTournamentRoundUseCase(tournamentId)
            if (!advanced) break
            advancedAny = true
        }
        return@withContext advancedAny
    }
}
