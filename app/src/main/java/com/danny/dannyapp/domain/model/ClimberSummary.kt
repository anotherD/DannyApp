package com.danny.dannyapp.domain.model

data class ClimberSummary(
    val playerId: Long,
    val playerName: String,
    val countryCode: String,
    val rankDelta: Int,
    val prizeMoneyDelta: Double
)
