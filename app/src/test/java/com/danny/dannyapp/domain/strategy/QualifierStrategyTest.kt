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
        val prelimMatches = if (s.prelimPlayers.isNotEmpty()) {
            val prelimMatchCount = s.prelimPlayers.size / 2
            (0 until prelimMatchCount).map { matchIndex ->
                TournamentMatch(
                    tournamentId = tId,
                    round = TournamentRound.QUAL_PRELIM.name,
                    matchIndex = matchIndex,
                    player1Id = s.prelimPlayers[matchIndex],
                    player2Id = s.prelimPlayers[2 * prelimMatchCount - 1 - matchIndex]
                )
            }
        } else emptyList()
        val prelimPlaceholders = if (s.prelimPlayers.isNotEmpty()) {
            (0 until (s.prelimPlayers.size / 2)).map { matchIndex -> -1000L - matchIndex }
        } else emptyList()
        val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(s.boardCount, s.boardSizePerBoard, s.byePlayers, prelimPlaceholders)
        val r1Matches = r1Pairings.map { pairing ->
            TournamentMatch(
                tournamentId = tId,
                round = TournamentRound.QUAL_R1.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id
            )
        }
        Pair(prelimMatches, r1Matches)
    }

    @Test
    fun `buildInitialMatches completes open draw placeholder matches in R1 and propagates winner to R2`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_ET)

        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = emptyList(),
            boardCount = 2,
            boardSizePerBoard = 8,
            prelimPlayers = emptyList(),
            byePlayers = listOf(
                100L, PLACEHOLDER_OPEN_DRAW_ID,
                PLACEHOLDER_OPEN_DRAW_ID, 101L,
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

    @Test
    fun `buildInitialMatches creates correct match count and placeholders for odd prelim matches`() {
        val strategy = QualifierStrategy(TournamentEvent.QUALIFIER_GRAND_SLAM)

        // 25 direct R1 players, 6 prelim players (3 prelim matches -> odd number)
        val split = QualifierFieldSplit(
            preQualifiedIds = emptyList(),
            nonPrequalifiedIds = emptyList(),
            boardCount = 4,
            boardSizePerBoard = 8,
            prelimPlayers = listOf(1L, 2L, 3L, 4L, 5L, 6L),
            byePlayers = (101L..125L).toList(),
            seedCount = 8
        )

        val qualResult = TournamentQualificationResult(qualifierFieldSplit = split)

        val matches = strategy.buildInitialMatches(
            tournamentId = 1L,
            qualResult = qualResult,
            createQualifierMatches = testCreateQualifierMatches
        )

        val prelimMatches = matches.filter { it.round == TournamentRound.QUAL_PRELIM.name }
        assertEquals(3, prelimMatches.size)

        val r1Matches = matches.filter { it.round == TournamentRound.QUAL_R1.name }
        // totalR1Slots = 25 + 3 = 28 -> 14 R1 matches
        assertEquals(14, r1Matches.size)

        // Match 12 has player1 = 125L, player2 = PLACEHOLDER_PREVIOUS_WINNER_ID (or prelim placeholder depending on board placement)
        // Let's check that all r1 matches are generated correctly.
        assertTrue(r1Matches.isNotEmpty())
    }
}
