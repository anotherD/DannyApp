package com.danny.dannyapp.domain.model

data class OrderOfMeritEntry(
    val playerId: Long,
    val name: String,
    val countryCode: String,
    val skill: Int,
    val prizeMoney: Double,
    val rank: Int,
    val isUser: Boolean
)
