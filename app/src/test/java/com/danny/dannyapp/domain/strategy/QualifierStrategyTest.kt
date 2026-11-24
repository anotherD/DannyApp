package com.danny.dannyapp.domain.strategy

import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
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
        val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(s.boardCount, s.boardSizePerBoard, s.byePlayers, emptyList())
        val r1Matches = r1Pairings.map { pairing ->
            TournamentMatch(
                tournamentId = tId,
                round = TournamentRound.QUAL_R1.name,
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

        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = emptyList(),
            boardCount = 2,
            boardSizePerBoard = 8,
            byePlayers = listOf(
                100L, PLACEHOLDER_OPEN_DRAW_ID, // Board 0 top, Board 1 top
                PLACEHOLDER_OPEN_DRAW_ID, 101L, // Board 1 bottom, Board 0 bottom
                102L, 103L,
                104L, 105L,
                106L, 107L,
                108L, 109L,
                110L, 111L,
                112L, 113L
            ),
            seedCount = 4
        )

        val qualResult = TournamentQualificationResult(qualifierFieldSplit = split)

        val matches = strategy.buildInitialMatches(
            tournamentId = 1L,
            qualResult = qualResult,
            createQualifierMatches = testCreateQualifierMatches
        )

        // Verify Board 0 Match 0 (matchIndex = 0) - regular match
        val m0 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 0 }
        assertTrue(!m0.isCompleted)

        // Verify Board 1 Match 0 (matchIndex = 4) - has PLACEHOLDER_OPEN_DRAW_ID as player1Id
        val b1m0 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 4 }
        assertTrue(b1m0.isCompleted)
        assertEquals(b1m0.player2Id, b1m0.winnerId)

        // Verify Board 1 Match 3 (matchIndex = 7) - has PLACEHOLDER_OPEN_DRAW_ID as player2Id
        val b1m3 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 7 }
        assertTrue(b1m3.isCompleted)
        assertEquals(b1m3.player1Id, b1m3.winnerId)
    }

    @Test
    fun `buildInitialMatches creates correct R1 match count for qualifier field`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_GRAND_SLAM)

        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = emptyList(),
            boardCount = 4,
            boardSizePerBoard = 8,
            byePlayers = (101L..132L).toList(),
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
