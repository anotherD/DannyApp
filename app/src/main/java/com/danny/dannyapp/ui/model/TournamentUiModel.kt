package com.danny.dannyapp.ui.model

import com.danny.dannyapp.data.db.Tournament

enum class TournamentStatus {
    COMPLETED, ACTIVE, PLANNED
}

data class TournamentUiModel(
    val tournament: Tournament,
    val status: TournamentStatus,
    val isNextPlanned: Boolean,
    val winnerName: String?,
    val qualifierWinners: List<PlayerUiInfo>?,
    val userReachedRound: String?,
    val activeRoundName: String?
)
