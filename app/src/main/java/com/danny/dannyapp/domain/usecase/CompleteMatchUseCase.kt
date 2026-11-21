package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentRepository
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
sealed interface ManualScoreResult {
    object Success : ManualScoreResult
    data class ValidationError(val message: String) : ManualScoreResult
}

/**
 * Central domain UseCase serving as Single Source of Truth for match completion and simulation.
 * Manages manual match completion, debug-winner completion, single match simulation (CPU or User),
 * and batch CPU match simulation for active tournament rounds.
 */
class CompleteMatchUseCase(
    private val context: Context,
    private val tournamentRepository: TournamentRepository,
    private val playerRepository: PlayerRepository,
    private val advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase
) {

    /**
     * Completes a match with manually entered scores.
     * Validates scores against the match format and distance rules.
     *
     * @param matchId The ID of the match to complete.
     * @param player1Score The leg or set score for player 1.
     * @param player2Score The leg or set score for player 2.
     * @return [ManualScoreResult.Success] if completed successfully, or [ManualScoreResult.ValidationError] if invalid.
     */
    suspend fun completeManualMatch(matchId: Long, player1Score: Int, player2Score: Int): ManualScoreResult = withContext(Dispatchers.IO) {
        tournamentRepository.runInTransaction {
            val match = tournamentRepository.getMatchById(matchId)
            check(!match.isCompleted) { "Match $matchId ist bereits abgeschlossen" }

            val tournament = tournamentRepository.getTournamentById(match.tournamentId)

            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)

            val matchDistance = event.getMatchDistance(round)
            val validationError = MatchScoreValidator.validateMatchScore(
                context = context,
                player1Score = player1Score,
                player2Score = player2Score,
                matchDistance = matchDistance
            )

            if (validationError != null) {
                return@runInTransaction ManualScoreResult.ValidationError(validationError)
            }

            val winnerId = if (player1Score > player2Score) match.player1Id else match.player2Id
            val updatedMatch = match.copy(
                player1Score = player1Score,
                player2Score = player2Score,
                winnerId = winnerId,
                isCompleted = true
            )

            tournamentRepository.updateMatch(updatedMatch)
            advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
            ManualScoreResult.Success
        }
    }

    /**
     * Completes a user match in debug mode with a preset winner determination.
     *
     * @param matchId The ID of the match to complete.
     * @param userWins `true` if the human user should win, `false` if the opponent wins.
     * @return `true` if the match was updated.
     */
    suspend fun completeWithPresetWinner(matchId: Long, userWins: Boolean): Boolean = withContext(Dispatchers.IO) {
        tournamentRepository.runInTransaction {
            val match = tournamentRepository.getMatchById(matchId)
            check(!match.isCompleted) { "Match $matchId ist bereits abgeschlossen" }

            val tournament = tournamentRepository.getTournamentById(match.tournamentId)
            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)

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

            tournamentRepository.updateMatch(updatedMatch)
            advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
            true
        }
    }

    /**
     * Simulates a single match (legs or sets based on match format).
     *
     * @param match The [TournamentMatch] instance to simulate.
     * @param includeUserMatches Whether to include matches involving [USER_ID]. If `false`, user matches are skipped.
     * @return The updated and completed [TournamentMatch].
     */
    suspend fun simulateMatch(match: TournamentMatch, includeUserMatches: Boolean = false): TournamentMatch = withContext(Dispatchers.IO) {
        if (match.isCompleted) {
            return@withContext match
        }

        if (!includeUserMatches && (match.player1Id == USER_ID || match.player2Id == USER_ID)) {
            return@withContext match
        }

        require(hasRealPlayers(match)) { "Match ${match.id} enthält unaufgelöste Platzhalter-IDs" }

        tournamentRepository.runInTransaction {
            val tournament = tournamentRepository.getTournamentById(match.tournamentId)
            val event = TournamentEvent.valueOf(tournament.type)
            val round = TournamentRound.valueOf(match.round)
            val matchDistance = event.getMatchDistance(round)

            val skill1 = getPlayerSkill(match.player1Id)
            val skill2 = getPlayerSkill(match.player2Id)

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

            tournamentRepository.updateMatch(updatedMatch)
            advanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted(updatedMatch)
            updatedMatch
        }
    }

    /**
     * Simulates all open CPU matches in the current active round for the given tournament.
     *
     * @param tournamentId The ID of the tournament.
     * @return List of all tournament matches after simulating CPU matches in the active round.
     */
    suspend fun simulateAllOpenCpuMatches(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        tournamentRepository.runInTransaction {
            val matches = tournamentRepository.getMatchesListForTournament(tournamentId)
            val activeRoundName = advanceTournamentRoundUseCase.evaluateTournamentState(tournamentId)?.activeRound?.name
                ?: return@runInTransaction matches

            val updatedList = mutableListOf<TournamentMatch>()

            for (match in matches) {
                if (match.round == activeRoundName &&
                    !match.isCompleted &&
                    match.player1Id != USER_ID &&
                    match.player2Id != USER_ID &&
                    hasRealPlayers(match)
                ) {
                    val simulatedMatch = simulateMatch(match, includeUserMatches = false)
                    updatedList.add(simulatedMatch)
                } else {
                    updatedList.add(match)
                }
            }
            updatedList
        }
    }

    /**
     * Retrieves and validates the skill level for a given player ID.
     *
     * @param playerId The ID of the player.
     * @return The validated skill integer value (1..10).
     */
    private suspend fun getPlayerSkill(playerId: Long): Int {
        if (playerId == USER_ID) {
            return SkillLevel.DEFAULT.value
        }
        val player = playerRepository.getPlayerById(playerId)
        return SkillLevel.fromValue(player.skill).value
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
