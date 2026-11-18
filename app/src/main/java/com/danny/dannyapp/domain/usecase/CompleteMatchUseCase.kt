package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.enums.SkillLevel
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.MatchDistance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Central domain UseCase serving as Single Source of Truth for match completion and simulation.
 * Manages manual match completion, debug-winner completion, single match simulation (CPU or User),
 * and batch CPU match simulation for active tournament rounds.
 */
class CompleteMatchUseCase(
    private val context: Context,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentDao: TournamentDao,
    private val playerDao: PlayerDao,
    private val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        tournamentMatchDao = AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        tournamentDao = AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        playerDao = AppDatabase.getDatabase(context.applicationContext).playerDao(),
        advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(context)
    )

    /**
     * Completes a match with manually entered scores.
     * Validates scores against the match format and distance rules.
     *
     * @param matchId The ID of the match to complete.
     * @param player1Score The leg or set score for player 1.
     * @param player2Score The leg or set score for player 2.
     * @return Error message string if invalid, or `null` if completed successfully.
     */
    suspend fun completeManualMatch(matchId: Long, player1Score: Int, player2Score: Int): String? = withContext(Dispatchers.IO) {
        val match = tournamentMatchDao.getMatchById(matchId)
            ?: return@withContext context.getString(R.string.match_not_found)

        if (match.isCompleted) {
            return@withContext context.getString(R.string.match_already_completed)
        }

        val tournament = tournamentDao.getTournamentById(match.tournamentId)
            ?: return@withContext context.getString(R.string.tournament_not_found)

        val event = try {
            TournamentEvent.valueOf(tournament.type)
        } catch (_: Exception) {
            return@withContext context.getString(R.string.tournament_not_found)
        }

        val round = try {
            TournamentRound.valueOf(match.round)
        } catch (_: Exception) {
            return@withContext context.getString(R.string.match_not_found)
        }

        val matchDistance = event.getMatchDistance(round)
        val validationError = MatchScoreValidator.validateMatchScore(
            context = context,
            player1Score = player1Score,
            player2Score = player2Score,
            matchDistance = matchDistance
        )

        if (validationError != null) {
            return@withContext validationError
        }

        val winnerId = if (player1Score > player2Score) match.player1Id else match.player2Id
        val updatedMatch = match.copy(
            player1Score = player1Score,
            player2Score = player2Score,
            winnerId = winnerId,
            isCompleted = true
        )

        tournamentMatchDao.updateMatch(updatedMatch)
        advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
        return@withContext null
    }

    /**
     * Completes a user match in debug mode with a preset winner determination.
     *
     * @param matchId The ID of the match to complete.
     * @param userWins `true` if the human user should win, `false` if the opponent wins.
     * @return `true` if the match was updated, `false` otherwise.
     */
    suspend fun completeWithPresetWinner(matchId: Long, userWins: Boolean): Boolean = withContext(Dispatchers.IO) {
        val match = tournamentMatchDao.getMatchById(matchId) ?: return@withContext false
        if (match.isCompleted) return@withContext false

        val tournament = tournamentDao.getTournamentById(match.tournamentId) ?: return@withContext false
        val event = try {
            TournamentEvent.valueOf(tournament.type)
        } catch (_: Exception) {
            return@withContext false
        }

        val round = try {
            TournamentRound.valueOf(match.round)
        } catch (_: Exception) {
            return@withContext false
        }

        val matchDistance = event.getMatchDistance(round)
        val isPlayer1User = (match.player1Id == USER_ID)
        val isPlayer1Winner = if (isPlayer1User) userWins else !userWins

        val (player1Score, player2Score, winnerId) = MatchScoreValidator.generateScoresForWinner(
            isPlayer1Winner = isPlayer1Winner,
            player1Id = match.player1Id,
            player2Id = match.player2Id,
            matchDistance = matchDistance
        )

        val updatedMatch = match.copy(
            player1Score = player1Score,
            player2Score = player2Score,
            winnerId = winnerId,
            isCompleted = true
        )

        tournamentMatchDao.updateMatch(updatedMatch)
        advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
        return@withContext true
    }

    /**
     * Simulates a single match (legs or sets based on match format).
     *
     * @param match The [TournamentMatch] instance to simulate.
     * @param userSkillOverride If provided (1..10), user matches involving [USER_ID] will be simulated
     *                           using this skill level. If `null`, matches involving [USER_ID] are skipped.
     * @return The updated and completed [TournamentMatch].
     */
    suspend fun simulateMatch(match: TournamentMatch, userSkillOverride: Int? = null): TournamentMatch = withContext(Dispatchers.IO) {
        if (match.isCompleted) {
            return@withContext match
        }

        if (!hasRealPlayers(match)) {
            return@withContext match
        }

        // If userSkillOverride is null, skip matches involving the human user
        if (userSkillOverride == null && (match.player1Id == USER_ID || match.player2Id == USER_ID)) {
            return@withContext match
        }

        val tournament = tournamentDao.getTournamentById(match.tournamentId)
            ?: throw IllegalArgumentException("Tournament not found: ${match.tournamentId}")
        val event = TournamentEvent.valueOf(tournament.type)
        val round = TournamentRound.valueOf(match.round)
        val matchDistance = event.getMatchDistance(round)

        val skill1 = getPlayerSkill(match.player1Id, userSkillOverride)
        val skill2 = getPlayerSkill(match.player2Id, userSkillOverride)

        val player1WinProbability = skill1.toDouble() / (skill1 + skill2)

        val (player1Score, player2Score, winnerId) = when (matchDistance.format) {
            MatchFormatType.LEGS -> simulateLegsMatch(
                matchDistance = matchDistance,
                player1WinProbability = player1WinProbability,
                player1Id = match.player1Id,
                player2Id = match.player2Id
            )
            MatchFormatType.SETS_BEST_OF_5_LEGS,
            MatchFormatType.SETS_BEST_OF_3_LEGS,
            MatchFormatType.SETS_DOUBLE_IN_OUT -> simulateSetsMatch(
                matchDistance = matchDistance,
                player1WinProbability = player1WinProbability,
                player1Id = match.player1Id,
                player2Id = match.player2Id
            )
        }

        val updatedMatch = match.copy(
            player1Score = player1Score,
            player2Score = player2Score,
            winnerId = winnerId,
            isCompleted = true
        )

        tournamentMatchDao.updateMatch(updatedMatch)
        advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
        return@withContext updatedMatch
    }



    /**
     * Simulates all open CPU matches in the current active round for the given tournament.
     *
     * @param tournamentId The ID of the tournament.
     * @return List of all tournament matches after simulating CPU matches in the active round.
     */
    suspend fun simulateAllOpenCpuMatches(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        val activeRoundName = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)?.activeRound?.name
            ?: return@withContext matches

        val updatedList = mutableListOf<TournamentMatch>()

        for (match in matches) {
            if (match.round == activeRoundName &&
                !match.isCompleted &&
                match.player1Id != USER_ID &&
                match.player2Id != USER_ID &&
                hasRealPlayers(match)
            ) {
                val simulatedMatch = simulateMatch(match, userSkillOverride = null)
                updatedList.add(simulatedMatch)
            } else {
                updatedList.add(match)
            }
        }
        return@withContext updatedList
    }

    private suspend fun getPlayerSkill(playerId: Long, userSkillOverride: Int?): Int {
        if (playerId == USER_ID) {
            return (userSkillOverride ?: SkillLevel.DEFAULT.value).coerceIn(1, 10)
        }
        val player = playerDao.getPlayerById(playerId)
        return (player?.skill ?: SkillLevel.DEFAULT.value).coerceIn(1, 10)
    }

    private fun hasRealPlayers(match: TournamentMatch): Boolean {
        val isPlayer1Real = match.player1Id > 0L || match.player1Id == USER_ID
        val isPlayer2Real = match.player2Id > 0L || match.player2Id == USER_ID
        return isPlayer1Real && isPlayer2Real
    }

    private fun simulateLegsMatch(
        matchDistance: MatchDistance,
        player1WinProbability: Double,
        player1Id: Long,
        player2Id: Long,
        random: Random = Random.Default
    ): Triple<Int, Int, Long> {
        val targetLegs = (matchDistance.bestOf / 2) + 1
        var player1Legs = 0
        var player2Legs = 0

        while (true) {
            val player1WinsLeg = random.nextDouble() < player1WinProbability
            if (player1WinsLeg) player1Legs++ else player2Legs++

            if (matchDistance.twoClearLegsRule) {
                val maxLegs = targetLegs + 3
                if (player1Legs >= targetLegs && player1Legs - player2Legs >= 2) {
                    return Triple(player1Legs, player2Legs, player1Id)
                }
                if (player2Legs >= targetLegs && player2Legs - player1Legs >= 2) {
                    return Triple(player1Legs, player2Legs, player2Id)
                }
                if (player1Legs == maxLegs) {
                    return Triple(player1Legs, player2Legs, player1Id)
                }
                if (player2Legs == maxLegs) {
                    return Triple(player1Legs, player2Legs, player2Id)
                }
            } else {
                if (player1Legs == targetLegs) {
                    return Triple(player1Legs, player2Legs, player1Id)
                }
                if (player2Legs == targetLegs) {
                    return Triple(player1Legs, player2Legs, player2Id)
                }
            }
        }
    }

    private fun simulateSetsMatch(
        matchDistance: MatchDistance,
        player1WinProbability: Double,
        player1Id: Long,
        player2Id: Long,
        random: Random = Random.Default
    ): Triple<Int, Int, Long> {
        val targetSets = (matchDistance.bestOf / 2) + 1
        val legsPerSet = matchDistance.legsPerSet
        var player1Sets = 0
        var player2Sets = 0

        while (player1Sets < targetSets && player2Sets < targetSets) {
            val isDecidingSet = (player1Sets == targetSets - 1 && player2Sets == targetSets - 1 && matchDistance.twoClearLegsRule)
            val targetLegsInSet = (legsPerSet / 2) + 1
            var player1Legs = 0
            var player2Legs = 0

            var setWonByPlayer1 = false
            var setEnded = false

            while (!setEnded) {
                val player1WinsLeg = random.nextDouble() < player1WinProbability
                if (player1WinsLeg) player1Legs++ else player2Legs++

                if (isDecidingSet) {
                    val maxLegs = targetLegsInSet + 3
                    if (player1Legs >= targetLegsInSet && player1Legs - player2Legs >= 2) {
                        setWonByPlayer1 = true
                        setEnded = true
                    } else if (player2Legs >= targetLegsInSet && player2Legs - player1Legs >= 2) {
                        setWonByPlayer1 = false
                        setEnded = true
                    } else if (player1Legs == maxLegs) {
                        setWonByPlayer1 = true
                        setEnded = true
                    } else if (player2Legs == maxLegs) {
                        setWonByPlayer1 = false
                        setEnded = true
                    }
                } else {
                    if (player1Legs == targetLegsInSet) {
                        setWonByPlayer1 = true
                        setEnded = true
                    } else if (player2Legs == targetLegsInSet) {
                        setWonByPlayer1 = false
                        setEnded = true
                    }
                }
            }

            if (setWonByPlayer1) player1Sets++ else player2Sets++
        }

        val winnerId = if (player1Sets == targetSets) player1Id else player2Id
        return Triple(player1Sets, player2Sets, winnerId)
    }
}
