package com.danny.dannyapp.ui.model

enum class RoundStatus {
    COMPLETED,
    ACTIVE,
    PENDING
}

data class EntrantGroupInfo(
    val title: String,
    val players: List<PlayerUiInfo>
)
