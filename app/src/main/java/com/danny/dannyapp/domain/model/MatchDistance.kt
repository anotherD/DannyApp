package com.danny.dannyapp.domain.model

data class MatchDistance(
    val bestOf: Int,
    val format: MatchFormatType,
    val legsPerSet: Int = 5,
    val twoClearLegsRule: Boolean = false
)
