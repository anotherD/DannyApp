package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "skill_level")
    val skillLevel: Int, // Spielstärke (1-10)
    @ColumnInfo(name = "has_tour_card")
    val hasTourCard: Boolean, // Tour Card (ja/nein)
    @ColumnInfo(name = "nation")
    val nation: String // Nation
)
