package com.danny.dannyapp.domain.model

/**
 * Represents a single entry in a group standing table.
 * This class lives in the domain layer and therefore must not depend on any Android UI resources.
 */

data class GroupStandingEntry(
    val groupLetter: String,
    val rank: Int,
    val playerId: Long,
    val played: Int,
    val wins: Int,
    val legsWon: Int,
    val legsLost: Int,
    val legDiff: Int,
    val points: Int
)
