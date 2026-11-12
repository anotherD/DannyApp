package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "players",
    indices = [Index(value = ["name"], unique = true)] // Verhindert doppelte Spieler!
)
data class Player(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "skill")
    val skill: Int, // Spielstärke (1-10)
    @ColumnInfo(name = "country_code")
    val countryCode: String, // 3-Stelliger Sport/ISO-Code (z.B. "DEU", "ENG", "NLD")
    @ColumnInfo(name = "price_money")
    val prizeMoney: Double = 0.0, // Preisgeld
    @ColumnInfo(name = "tour_card_year")
    val tourCardYear: Int = 0, // Tour Card Jahr (0 = keine, 1 = 1. Jahr, 2 = 2. Jahr)
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    val hasTourCard: Boolean
        get() = tourCardYear > 0

    init {
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
        require(skill in Player.MIN_LEVEL..Player.MAX_LEVEL) { "Skill level must be between 1 and 10, got $skill" }
        require(tourCardYear in 0..2) { "Tour card year must be 0, 1, or 2, got $tourCardYear" }
    }

    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 10
    }
}
