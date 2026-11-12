package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["name"], unique = true)] // Verhindert doppelte Profile!
)
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String = "",
    @ColumnInfo(name = "player_name")
    val playerName: String = "",
    @ColumnInfo(name = "country_code")
    val countryCode: String = "DEU",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Name darf nicht leer sein" }
    }

    val displayPlayerName: String
        get() = playerName.ifBlank { name }
}
