package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatchDao
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
    private val generateBracketUseCase: GenerateBracketUseCase? = null,
    private val tournamentDao: TournamentDao? = null,
    private val tournamentMatchDao: TournamentMatchDao? = null,
    private val context: Context? = null
) {
    constructor(context: Context) : this(
        completeMatchUseCase = CompleteMatchUseCase(context),
        advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(context),
        generateBracketUseCase = GenerateBracketUseCase(context),
        tournamentDao = AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        tournamentMatchDao = AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        context = context
    )

    private val safeTournamentDao get() = tournamentDao ?: context?.let { AppDatabase.getDatabase(it.applicationContext).tournamentDao() }
    private val safeTournamentMatchDao get() = tournamentMatchDao ?: context?.let { AppDatabase.getDatabase(it.applicationContext).tournamentMatchDao() }
    private val safeGenerateBracketUseCase get() = generateBracketUseCase ?: context?.let { GenerateBracketUseCase(it) }

    /**
     * Simulates all remaining CPU matches in the current round and then advances the tournament round.
     * Returns true if the round was advanced or completed, false otherwise.
     */
    suspend fun simulateAndAdvance(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId)
        advanceTournamentRoundUseCase(tournamentId)
    }

    /**
     * Simulates CPU matches and advances rounds continuously until a human user match is reached
     * or the tournament is completed.
     */
    suspend fun simulateUntilUserMatchOrCompleted(tournamentId: Long): Boolean = withContext(Dispatchers.IO) {
        var advancedAny = false
        while (true) {
            completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId)
            val advanced = advanceTournamentRoundUseCase(tournamentId)
            if (!advanced) break
            advancedAny = true
        }
        return@withContext advancedAny
    }

    /**
     * Simulates an entire tournament from start to finish.
     * Generates bracket if empty, simulates all CPU matches, realistically simulates user matches
     * based on [userSkill], and advances rounds until the tournament is marked completed.
     * Always executes round advancement in this batch mode to ensure the simulation loop completes.
     *
     * @param tournamentId The ID of the tournament to simulate completely.
     * @param userSkill The skill level of the user (1..10).
     * @return `true` if the tournament completed successfully, `false` otherwise.
     */
    suspend fun simulateCompleteTournament(tournamentId: Long, userSkill: Int): Boolean = withContext(Dispatchers.IO) {
        val currentTournamentDao = safeTournamentDao ?: return@withContext false
        val currentTournamentMatchDao = safeTournamentMatchDao ?: return@withContext false

        var tournament = currentTournamentDao.getTournamentById(tournamentId) ?: return@withContext false
        if (tournament.isCompleted) return@withContext true

        val existingMatches = currentTournamentMatchDao.getMatchesListForTournament(tournamentId)
        if (existingMatches.isEmpty()) {
            safeGenerateBracketUseCase?.invoke(tournamentId)
        }

        var loopCount = 0
        val maxLoopCount = 200

        while (loopCount < maxLoopCount) {
            loopCount++
            tournament = currentTournamentDao.getTournamentById(tournamentId) ?: return@withContext false
            if (tournament.isCompleted) return@withContext true

            val state = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId) ?: return@withContext false
            val activeRound = state.activeRound ?: return@withContext tournament.isCompleted

            completeMatchUseCase.simulateAllOpenCpuMatches(tournamentId)

            val currentMatches = currentTournamentMatchDao.getMatchesListForTournament(tournamentId)
            val openUserMatches = currentMatches.filter {
                it.round == activeRound.name && !it.isCompleted && (it.player1Id == USER_ID || it.player2Id == USER_ID)
            }

            for (match in openUserMatches) {
                val isPlayer1User = (match.player1Id == USER_ID)
                val opponentId = if (isPlayer1User) match.player2Id else match.player1Id
                if (opponentId <= 0L && opponentId != USER_ID) {
                    continue
                }

                completeMatchUseCase.simulateMatch(match, userSkillOverride = userSkill)
            }

            val updatedState = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)
            if (updatedState != null && updatedState.canAdvanceRound) {
                advanceTournamentRoundUseCase(tournamentId)
            } else {
                tournament = currentTournamentDao.getTournamentById(tournamentId) ?: return@withContext false
                if (tournament.isCompleted) return@withContext true
                break
            }
        }

        val finalTournament = currentTournamentDao.getTournamentById(tournamentId)
        return@withContext finalTournament?.isCompleted == true
    }
}
