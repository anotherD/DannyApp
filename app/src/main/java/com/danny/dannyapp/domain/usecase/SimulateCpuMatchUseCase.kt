package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.MatchDistance
import com.danny.dannyapp.domain.model.MatchFormatType
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class SimulateCpuMatchUseCase(
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentDao: TournamentDao,
    private val playerDao: PlayerDao
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao()
    )

    suspend operator fun invoke(matchId: Long): TournamentMatch? = withContext(Dispatchers.IO) {
        val match = tournamentMatchDao.getMatchById(matchId) ?: return@withContext null
        return@withContext invoke(match)
    }

    suspend operator fun invoke(match: TournamentMatch): TournamentMatch = withContext(Dispatchers.IO) {
        if (match.isCompleted) {
            return@withContext match
        }

        // Do not simulate if either player is the human user
        if (match.player1Id == USER_ID || match.player2Id == USER_ID) {
            return@withContext match
        }

        val tournament = tournamentDao.getTournamentById(match.tournamentId)
            ?: throw IllegalArgumentException("Tournament not found: ${match.tournamentId}")
        val event = TournamentEvent.valueOf(tournament.type)
        val round = TournamentRound.valueOf(match.round)
        val matchDistance = event.getMatchDistance(round)

        val player1 = playerDao.getPlayerById(match.player1Id)
        val player2 = playerDao.getPlayerById(match.player2Id)

        val skill1 = (player1?.skill ?: 5).coerceIn(1, 10)
        val skill2 = (player2?.skill ?: 5).coerceIn(1, 10)
        val p1WinProb = skill1.toDouble() / (skill1 + skill2)

        val (p1Score, p2Score, winnerId) = when (matchDistance.format) {
            MatchFormatType.LEGS -> simulateLegsMatch(
                matchDistance = matchDistance,
                p1WinProb = p1WinProb,
                player1Id = match.player1Id,
                player2Id = match.player2Id
            )
            MatchFormatType.SETS_BEST_OF_5_LEGS,
            MatchFormatType.SETS_BEST_OF_3_LEGS,
            MatchFormatType.SETS_DOUBLE_IN_OUT -> simulateSetsMatch(
                matchDistance = matchDistance,
                p1WinProb = p1WinProb,
                player1Id = match.player1Id,
                player2Id = match.player2Id
            )
        }

        val updatedMatch = match.copy(
            player1Score = p1Score,
            player2Score = p2Score,
            winnerId = winnerId,
            isCompleted = true
        )

        tournamentMatchDao.updateMatch(updatedMatch)
        return@withContext updatedMatch
    }

    suspend fun simulateAllCpuMatches(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        val updatedList = mutableListOf<TournamentMatch>()

        for (match in matches) {
            if (!match.isCompleted && match.player1Id != USER_ID && match.player2Id != USER_ID) {
                val simulated = invoke(match)
                updatedList.add(simulated)
            } else {
                updatedList.add(match)
            }
        }
        return@withContext updatedList
    }

    private fun simulateLegsMatch(
        matchDistance: MatchDistance,
        p1WinProb: Double,
        player1Id: Long,
        player2Id: Long,
        random: Random = Random.Default
    ): Triple<Int, Int, Long> {
        val targetLegs = (matchDistance.bestOf / 2) + 1
        var legs1 = 0
        var legs2 = 0

        while (true) {
            val p1WinsLeg = random.nextDouble() < p1WinProb
            if (p1WinsLeg) legs1++ else legs2++

            if (matchDistance.twoClearLegsRule) {
                val maxLegs = targetLegs + 3
                if (legs1 >= targetLegs && legs1 - legs2 >= 2) {
                    return Triple(legs1, legs2, player1Id)
                }
                if (legs2 >= targetLegs && legs2 - legs1 >= 2) {
                    return Triple(legs1, legs2, player2Id)
                }
                if (legs1 == maxLegs) {
                    return Triple(legs1, legs2, player1Id)
                }
                if (legs2 == maxLegs) {
                    return Triple(legs1, legs2, player2Id)
                }
            } else {
                if (legs1 == targetLegs) {
                    return Triple(legs1, legs2, player1Id)
                }
                if (legs2 == targetLegs) {
                    return Triple(legs1, legs2, player2Id)
                }
            }
        }
    }

    private fun simulateSetsMatch(
        matchDistance: MatchDistance,
        p1WinProb: Double,
        player1Id: Long,
        player2Id: Long,
        random: Random = Random.Default
    ): Triple<Int, Int, Long> {
        val targetSets = (matchDistance.bestOf / 2) + 1
        val legsPerSet = matchDistance.legsPerSet
        var sets1 = 0
        var sets2 = 0

        while (sets1 < targetSets && sets2 < targetSets) {
            val isDecidingSet = (sets1 == targetSets - 1 && sets2 == targetSets - 1 && matchDistance.twoClearLegsRule)
            val targetLegsInSet = (legsPerSet / 2) + 1
            var legs1 = 0
            var legs2 = 0

            var setWonByP1 = false
            var setEnded = false

            while (!setEnded) {
                val p1WinsLeg = random.nextDouble() < p1WinProb
                if (p1WinsLeg) legs1++ else legs2++

                if (isDecidingSet) {
                    val maxLegs = targetLegsInSet + 3
                    if (legs1 >= targetLegsInSet && legs1 - legs2 >= 2) {
                        setWonByP1 = true
                        setEnded = true
                    } else if (legs2 >= targetLegsInSet && legs2 - legs1 >= 2) {
                        setWonByP1 = false
                        setEnded = true
                    } else if (legs1 == maxLegs) {
                        setWonByP1 = true
                        setEnded = true
                    } else if (legs2 == maxLegs) {
                        setWonByP1 = false
                        setEnded = true
                    }
                } else {
                    if (legs1 == targetLegsInSet) {
                        setWonByP1 = true
                        setEnded = true
                    } else if (legs2 == targetLegsInSet) {
                        setWonByP1 = false
                        setEnded = true
                    }
                }
            }

            if (setWonByP1) sets1++ else sets2++
        }

        val winnerId = if (sets1 == targetSets) player1Id else player2Id
        return Triple(sets1, sets2, winnerId)
    }
}
