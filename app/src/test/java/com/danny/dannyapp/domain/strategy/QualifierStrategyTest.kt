package com.danny.dannyapp.domain.strategy

import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.QualifierFieldSplit
import com.danny.dannyapp.domain.usecase.TournamentQualificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifierStrategyTest {

    @Test
    fun `buildInitialMatches completes open draw placeholder matches in R1 and propagates winner to R2`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_ET)

        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = emptyList(),
            boardCount = 2,
            boardSizePerBoard = 8,
            prelimPlayers = emptyList(),
            byePlayers = listOf(1L, 2L, 3L, 4L),
            seedCount = 4
        )

        val qualResult = TournamentQualificationResult(qualifierFieldSplit = split)

        // 8 R1 matches total (4 per board)
        // Match 0: even matchIndex -> player2 is PLACEHOLDER_OPEN_DRAW_ID
        // Match 1: odd matchIndex -> player1 is PLACEHOLDER_OPEN_DRAW_ID
        // Match 2: normal match
        val r1Matches = listOf(
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 0, player1Id = 100L, player2Id = PLACEHOLDER_OPEN_DRAW_ID),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 1, player1Id = PLACEHOLDER_OPEN_DRAW_ID, player2Id = 101L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 2, player1Id = 102L, player2Id = 103L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 3, player1Id = 104L, player2Id = 105L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 4, player1Id = 106L, player2Id = 107L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 5, player1Id = 108L, player2Id = 109L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 6, player1Id = 110L, player2Id = 111L),
            TournamentMatch(tournamentId = 1L, round = TournamentRound.QUAL_R1.name, matchIndex = 7, player1Id = 112L, player2Id = 113L)
        )

        val matches = strategy.buildInitialMatches(
            tournamentId = 1L,
            qualResult = qualResult,
            createQualifierMatches = { _, _ -> Pair(emptyList(), r1Matches) }
        )

        // Verify Match 0 (R1, index 0)
        val m0 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 0 }
        assertTrue(m0.isCompleted)
        assertEquals(100L, m0.winnerId)
        assertEquals(0, m0.player1Score)
        assertEquals(0, m0.player2Score)

        // Verify Match 1 (R1, index 1)
        val m1 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 1 }
        assertTrue(m1.isCompleted)
        assertEquals(101L, m1.winnerId)
        assertEquals(0, m1.player1Score)
        assertEquals(0, m1.player2Score)

        // Verify Match 2 (R1, index 2) - should not be completed automatically
        val m2 = matches.first { it.round == TournamentRound.QUAL_R1.name && it.matchIndex == 2 }
        assertTrue(!m2.isCompleted)

        // Verify R2 Match 0 (target for R1 index 0 and index 1)
        val r2m0 = matches.first { it.round == TournamentRound.QUAL_R2.name && it.matchIndex == 0 }
        assertEquals(100L, r2m0.player1Id)
        assertEquals(101L, r2m0.player2Id)
    }
}
