package com.danny.dannyapp.domain.model

data class MajorWinnerSummary(
    val tournamentName: String,
    val winnerId: Long,
    val winnerName: String,
    val winnerCountryCode: String,
    val prizeMoney: Double = 0.0
)
