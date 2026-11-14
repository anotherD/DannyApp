package com.danny.dannyapp.domain.model

import com.danny.dannyapp.domain.enums.MatchFormatType

data class MatchDistance(
    val bestOf: Int,
    val format: MatchFormatType,
    val legsPerSet: Int = 5,
    val twoClearLegsRule: Boolean = false
)
