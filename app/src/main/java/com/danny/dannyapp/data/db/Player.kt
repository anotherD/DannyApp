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
    @ColumnInfo(name = "skill_level")
    val skillLevel: Int, // Spielstärke (1-10)
    @ColumnInfo(name = "has_tour_card")
    val hasTourCard: Boolean, // Tour Card (ja/nein)
    @ColumnInfo(name = "country_code")
    val countryCode: String, // 3-Stelliger Sport/ISO-Code (z.B. "GER", "ENG", "NED")
    @ColumnInfo(name = "price_money")
    val prizeMoney: Double = 0.0, // Preisgeld
    @ColumnInfo(name = "tour_card_year")
    val tourCardYear: Int = 0 // Tour Card Jahr / Dauer
)
