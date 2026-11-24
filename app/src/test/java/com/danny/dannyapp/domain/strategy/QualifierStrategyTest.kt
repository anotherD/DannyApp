package com.danny.dannyapp.domain.strategy

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.BracketGenerator
import com.danny.dannyapp.domain.usecase.QualifierFieldSplit
import com.danny.dannyapp.domain.usecase.TournamentQualificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifierStrategyTest {

    private val testCreateQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>> = { tId, s ->
        val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(s.boardCount, s.boardSizePerBoard, s.nonPrequalifiedIds)
        val r1Matches = r1Pairings.map { pairing ->
            TournamentMatch(
                tournamentId = tId,
                round = pairing.round.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id
            )
        }
        Pair(emptyList(), r1Matches)
    }

    @Test
    fun `buildInitialMatches completes open draw placeholder matches in R1 and propagates winner to R2`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_ET)

        // 13 players for 2 boards of 8 slots (16 total slots) -> 3 byes total.
        // Board 0 gets 2 byes (Match 0 player2=BYE, Match 3 player1=BYE).
        // Board 1 gets 1 bye (Match 0 player2=BYE).
        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = (101L..113L).toList(),
            boardCount = 2,
            boardSizePerBoard = 8,
            byePlayers = emptyList(),
            seedCount = 4
        )

        val qualResult = TournamentQualificationResult(qualifierFieldSplit = split)

        val matches = strategy.buildInitialMatches(
            tournamentId = 1L,
            qualResult = qualResult,
            createQualifierMatches = testCreateQualifierMatches
        )

        // Verify Board 1 Match 1 (matchIndex = 5) - regular match
        val m5 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 5 }
        assertTrue(!m5.isCompleted)

        // Verify Board 0 Match 0 (matchIndex = 0) - has PLACEHOLDER_OPEN_DRAW_ID as player2Id
        val b0m0 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 0 }
        assertTrue(b0m0.isCompleted)
        assertEquals(b0m0.player1Id, b0m0.winnerId)

        // Verify Board 0 Match 3 (matchIndex = 3) - has PLACEHOLDER_OPEN_DRAW_ID as player1Id
        val b0m3 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 3 }
        assertTrue(b0m3.isCompleted)
        assertEquals(b0m3.player2Id, b0m3.winnerId)
    }

    @Test
    fun `buildInitialMatches creates correct R1 match count for qualifier field`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_GRAND_SLAM)

        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = (101L..128L).toList(),
            boardCount = 4,
            boardSizePerBoard = 8,
            byePlayers = emptyList(),
            seedCount = 8
        )

        val qualResult = TournamentQualificationResult(qualifierFieldSplit = split)

        val matches = strategy.buildInitialMatches(
            tournamentId = 1L,
            qualResult = qualResult,
            createQualifierMatches = testCreateQualifierMatches
        )

        val r1Matches = matches.filter { it.round == TournamentRound.QUAL_R1.name }
        assertEquals(16, r1Matches.size)
        assertTrue(r1Matches.isNotEmpty())
    }
}
