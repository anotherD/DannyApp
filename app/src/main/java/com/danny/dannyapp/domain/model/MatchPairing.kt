package com.danny.dannyapp.domain.model

import com.danny.dannyapp.domain.enums.TournamentRound

data class MatchPairing(
    val round: TournamentRound,
    val matchIndex: Int,
    val player1Id: Long,
    val player2Id: Long,
    val groupName: String? = null
)
