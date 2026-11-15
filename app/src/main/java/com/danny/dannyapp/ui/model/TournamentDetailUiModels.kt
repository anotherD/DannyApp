package com.danny.dannyapp.ui.model

data class EntrantGroupInfo(
    val title: String,
    val players: List<PlayerUiInfo>
)

data class GrandSlamGroupItem(
    val rank: Int,
    val player: PlayerUiInfo,
    val played: Int,
    val legDiff: Int,
    val points: Int
)
