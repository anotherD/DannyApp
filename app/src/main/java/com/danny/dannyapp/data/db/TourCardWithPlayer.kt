package com.danny.dannyapp.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class TourCardWithPlayer(
    @Embedded val tourCard: TourCard,
    @Relation(
        parentColumn = "player_id",
        entityColumn = "id"
    )
    val player: Player? = null
)
