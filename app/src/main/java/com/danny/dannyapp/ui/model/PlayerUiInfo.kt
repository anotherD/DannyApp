package com.danny.dannyapp.ui.model

data class PlayerUiInfo(
    val id: Long = 0L,
    val name: String,
    val countryCode: String? = null,
    val seed: Int? = null,
    val skill: Int = 5
)
