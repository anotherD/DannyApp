package com.danny.dannyapp.domain.model

data class MatchPairing(
    val round: TournamentRound,
    val matchIndex: Int,
    val player1Id: Long,
    val player2Id: Long,
    val groupName: String? = null
)
