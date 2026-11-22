package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.data.db.USER_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase orchestrating the overall progression of tournaments, including CPU simulation,
 * user match simulation, and continuous round advancement.
 */
class TournamentProgressionUseCase(
    private val completeMatchUseCase: CompleteMatchUseCase,
    private val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase,
    private val tournamentRepository: TournamentRepository,
    private val generateBracketUseCase: GenerateBracketUseCase? = null
) {

    /**
     * Simulates an entire tournament from start to finish.
     * Generates bracket if empty, simulates all CPU matches, realistically simulates user matches,
     * and advances rounds until the tournament is marked completed.
     * Always executes round advancement in this batch mode to ensure the simulation loop completes.
     *
     * @param tournamentId The ID of the tournament to simulate completely.
     * @return `true` if the tournament completed successfully, `false` otherwise.
     */
    suspend fun simulateCompleteTournament(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        var tournament = tournamentRepository.getTournamentById(tournamentId)
        if (tournament.isCompleted) return@withContext true

        val existingMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
        if (existingMatches.isEmpty()) {
            generateBracketUseCase?.invoke(tournamentId)
        }

        var loopCount = 0
        val maxLoopCount = 200

        while (loopCount < maxLoopCount) {
            loopCount++
            tournament = tournamentRepository.getTournamentById(tournamentId)
            if (tournament.isCompleted) return@withContext true

            val state = checkNotNull(advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)) {
                "Turnier-Status konnte für ID $tournamentId nicht ermittelt werden"
            }
            val activeRound = checkNotNull(state.activeRound) { "Aktive Runde fehlt für laufendes Turnier $tournamentId" }

            completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId)

            val currentMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
            val openUserMatches = currentMatches.filter {
                it.round == activeRound.name && !it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID)
            }

            for (match in openUserMatches) {
                val isPlayer1User = (match.player1Id == USER_ID)
                val opponentId = if (isPlayer1User) match.player2Id else match.player1Id
                if (opponentId <= 0L && opponentId != USER_ID) {
                    continue
                }

                completeMatchUseCase.simulateMatch(match, includeUserMatches = true)
            }

            val updatedState = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)
            if (updatedState != null && updatedState.canAdvanceRound) {
                advanceTournamentRoundUseCase(tournamentId)
            } else {
                val openMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
                    .count { it.round == activeRound.name && !it.isCompleted }
                error("Turnier $tournamentId steckt in Runde '${activeRound.name}' fest: $openMatches offene Matches verbleiben")
            }
        }

        val finalTournament = tournamentRepository.getTournamentById(tournamentId)
        check(finalTournament.isCompleted) {
            "Turnier $tournamentId wurde nach $loopCount Durchläufen nicht abgeschlossen (Runde: ${finalTournament.currentRound})"
        }
        return@withContext true
    }
}
