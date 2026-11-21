package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.domain.enums.MatchFormatType
import com.danny.dannyapp.domain.model.MatchDistance
import kotlin.random.Random

/**
 * Typed validation errors for match scores.
 */
sealed interface ScoreValidationError {
    object NegativeScore : ScoreValidationError
    object DrawNotAllowed : ScoreValidationError
    object UnexpectedError : ScoreValidationError
    data class InvalidTwoClearLegs(val targetLegs: Int, val tiebreakWinnerLegs: Int, val tiebreakLoserLegs: Int) : ScoreValidationError
    data class InvalidBestOfLegs(val bestOf: Int, val targetLegs: Int) : ScoreValidationError
    data class InvalidBestOfSets(val bestOf: Int, val targetSets: Int) : ScoreValidationError
}

/**
 * Validates match scores and generates realistic winning scores for tournament matches.
 */
object MatchScoreValidator {

    /**
     * Validates a match score for a given [matchDistance].
     * Returns a [ScoreValidationError] if invalid, or `null` if the score is valid.
     */
    fun validateMatchScore(
        player1Score: Int,
        player2Score: Int,
        matchDistance: MatchDistance
    ): ScoreValidationError? {
        if (player1Score < 0 || player2Score < 0) {
            return ScoreValidationError.NegativeScore
        }
        if (player1Score == player2Score) {
            return ScoreValidationError.DrawNotAllowed
        }

        val targetScore = (matchDistance.bestOf / 2) + 1
        val winningScore = maxOf(player1Score, player2Score)
        val losingScore = minOf(player1Score, player2Score)

        if (matchDistance.format == MatchFormatType.LEGS) {
            val maxLegs = targetScore + 3
            if (matchDistance.twoClearLegsRule) {
                val isValidTwoClearLegs = (winningScore >= targetScore && winningScore - losingScore >= 2) ||
                        (winningScore == maxLegs && winningScore - losingScore >= 1)
                if (!isValidTwoClearLegs) {
                    return ScoreValidationError.InvalidTwoClearLegs(
                        targetLegs = targetScore,
                        tiebreakWinnerLegs = maxLegs,
                        tiebreakLoserLegs = maxLegs - 1
                    )
                }
            } else {
                if (winningScore != targetScore || losingScore >= targetScore) {
                    return ScoreValidationError.InvalidBestOfLegs(
                        bestOf = matchDistance.bestOf,
                        targetLegs = targetScore
                    )
                }
            }
        } else {
            if (winningScore != targetScore || losingScore >= targetScore) {
                return ScoreValidationError.InvalidBestOfSets(
                    bestOf = matchDistance.bestOf,
                    targetSets = targetScore
                )
            }
        }
        return null
    }

    /**
     * Generates a valid score for a match given the winner determination and [matchDistance].
     * Returns a [Triple] of (player1Score, player2Score, winnerPlayerId).
     */
    fun generateScoresForWinner(
        isPlayer1Winner: Boolean,
        player1Id: Long,
        player2Id: Long,
        matchDistance: MatchDistance
    ): Triple<Int, Int, Long> {
        val targetScore = (matchDistance.bestOf / 2) + 1
        val losingScore = Random.nextInt(0, targetScore)
        var winningScore = targetScore

        if (matchDistance.format == MatchFormatType.LEGS &&
            matchDistance.twoClearLegsRule &&
            losingScore == targetScore - 1
        ) {
            winningScore = targetScore + 1
        }

        val player1Score = if (isPlayer1Winner) winningScore else losingScore
        val player2Score = if (isPlayer1Winner) losingScore else winningScore
        val winnerId = if (isPlayer1Winner) player1Id else player2Id

        return Triple(player1Score, player2Score, winnerId)
    }
}
