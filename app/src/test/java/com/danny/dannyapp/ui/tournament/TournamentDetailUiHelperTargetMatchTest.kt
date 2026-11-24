package com.danny.dannyapp.ui.tournament

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PlayerUiInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentDetailUiHelperTargetMatchTest {

    private fun createMatchUi(
        round: String,
        matchIndex: Int,
        player1Id: Long = 10L,
        player2Id: Long = 20L,
        winnerId: Long? = null,
        isCompleted: Boolean = false
    ): MatchUiModel {
        val match = TournamentMatch(
            id = (matchIndex + 1).toLong(),
            tournamentId = 1L,
            round = round,
            matchIndex = matchIndex,
            player1Id = player1Id,
            player2Id = player2Id,
            winnerId = winnerId,
            isCompleted = isCompleted
        )
        return MatchUiModel(
            match = match,
            player1 = PlayerUiInfo(id = player1Id, name = "Player $player1Id"),
            player2 = PlayerUiInfo(id = player2Id, name = "Player $player2Id"),
            isUserMatch = player1Id == USER_ID || player2Id == USER_ID,
            isCompleted = isCompleted,
            winnerId = winnerId,
            scoreText = "",
            roundDisplayName = round
        )
    }

    @Test
    fun `resolveBracketTargetMatchIndex for open draw returns minus 1 when next round is empty`() {
        val r1 = listOf(
            createMatchUi(TournamentRound.R1_160.name, 0, winnerId = 10L, isCompleted = true)
        )
        val r2 = emptyList<MatchUiModel>()
        val rounds = listOf(r1, r2)

        val target = TournamentDetailUiHelper.resolveBracketTargetMatchIndex(rounds, 0, 0)
        assertEquals(-1, target)
    }

    @Test
    fun `resolveBracketTargetMatchIndex for open draw returns minus 1 when next round has same size but not drawn yet`() {
        val r1 = listOf(
            createMatchUi(TournamentRound.R1_160.name, 0, winnerId = 10L, isCompleted = true)
        )
        val r2 = listOf(
            createMatchUi(TournamentRound.R2_128.name, 0, player1Id = 0L, player2Id = 0L)
        )
        val rounds = listOf(r1, r2)

        val target = TournamentDetailUiHelper.resolveBracketTargetMatchIndex(rounds, 0, 0)
        assertEquals(-1, target)
    }

    @Test
    fun `resolveBracketTargetMatchIndex for open draw returns target index when winner found in next round`() {
        val r1 = listOf(
            createMatchUi(TournamentRound.R1_160.name, 0, winnerId = USER_ID, isCompleted = true),
            createMatchUi(TournamentRound.R1_160.name, 1, winnerId = 15L, isCompleted = true)
        )
        val r2 = listOf(
            createMatchUi(TournamentRound.R2_128.name, 0, player1Id = 100L, player2Id = 101L),
            createMatchUi(TournamentRound.R2_128.name, 1, player1Id = USER_ID, player2Id = 102L)
        )
        val rounds = listOf(r1, r2)

        val target0 = TournamentDetailUiHelper.resolveBracketTargetMatchIndex(rounds, 0, 0)
        assertEquals(1, target0)

        val target1 = TournamentDetailUiHelper.resolveBracketTargetMatchIndex(rounds, 0, 1)
        assertEquals(-1, target1)
    }

    @Test
    fun `resolveBracketTargetMatchIndex maintains European Tour L48 to L32 mapping`() {
        val rL48 = (0 until 16).map { idx ->
            createMatchUi(TournamentRound.L48.name, idx)
        }
        val rL32 = (0 until 16).map { idx ->
            createMatchUi(TournamentRound.L32.name, idx)
        }
        val rounds = listOf(rL48, rL32)

        val expectedTarget0 = AdvanceTournamentRoundUseCase.resolveNextRoundSlot(TournamentEvent.EUROPEAN_TOUR, TournamentRound.L48, 0).first
        val target = TournamentDetailUiHelper.resolveBracketTargetMatchIndex(rounds, 0, 0)
        assertEquals(expectedTarget0, target)
    }
}
