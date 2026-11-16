package com.danny.dannyapp.ui.model

import com.danny.dannyapp.data.db.TournamentMatch

data class MatchUiModel(
    val match: TournamentMatch,
    val player1: PlayerUiInfo,
    val player2: PlayerUiInfo,
    val isUserMatch: Boolean,
    val isCompleted: Boolean,
    val winnerId: Long?,
    val scoreText: String,
    val roundDisplayName: String,
    val targetBoard: Int? = null
)
