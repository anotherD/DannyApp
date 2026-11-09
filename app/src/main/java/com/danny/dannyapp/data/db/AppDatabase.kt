package com.danny.dannyapp.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danny.dannyapp.data.Country
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Player::class,
        Career::class,
        UserProfile::class,
        Season::class,
        TourCard::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun careerDao(): CareerDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun seasonDao(): SeasonDao
    abstract fun tourCardDao(): TourCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "danny_app_database"
                )
                // TODO: Implement proper database migrations before production release to avoid data loss
                .fallbackToDestructiveMigration(true)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : Callback() {

            companion object {
                private const val PREFS_NAME = "app_db_prefs"
                private const val KEY_CSV_DATA_VERSION = "csv_data_version"
                private const val CURRENT_CSV_VERSION = 1
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val lastSyncedVersion = prefs.getInt(KEY_CSV_DATA_VERSION, 0)
                        val playerCount = getDatabase(context).playerDao().getPlayerCount()

                        if (lastSyncedVersion < CURRENT_CSV_VERSION || playerCount == 0) {
                            val playersFromCsv = loadPlayersFromAssets(context, "players.csv")
                            if (playersFromCsv.isNotEmpty()) {
                                getDatabase(context).playerDao().syncPlayersFromCsv(playersFromCsv)
                                prefs.edit().putInt(KEY_CSV_DATA_VERSION, CURRENT_CSV_VERSION).apply()
                                Log.i("CSVImport", "Spieler aus CSV erfolgreich synchronisiert (Version $CURRENT_CSV_VERSION).")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CSVImport", "Fehler beim Synchronisieren der Spieler aus CSV", e)
                    }
                }
            }

            private fun loadPlayersFromAssets(context: Context, fileName: String): List<Player> {
                val playerList = mutableListOf<Player>()
                context.assets.open(fileName).bufferedReader().useLines { lines ->
                    val iterator = lines.iterator()
                    if (!iterator.hasNext()) return@useLines

                    // 1. Kopfzeile auslesen und Index-Map erstellen (BOM-bereinigt, lowercase)
                    val headerLine = iterator.next()
                    val cleanHeader = if (headerLine.startsWith("\uFEFF")) headerLine.substring(1) else headerLine
                    val columnIndexMap = cleanHeader.split(";")
                        .mapIndexed { index, columnName -> columnName.trim().lowercase() to index }
                        .toMap()

                    // Hilfsfunktion, um sicher per Spaltenname auf den Wert zuzugreifen
                    fun List<String>.getCol(colName: String): String? {
                        val index = columnIndexMap[colName.lowercase()] ?: return null
                        return this.getOrNull(index)?.trim()
                    }

                    // 2. Alle Datenzeilen durchgehen
                    while (iterator.hasNext()) {
                        val line = iterator.next()
                        if (line.isBlank()) continue // Leere Zeilen am Dateiende überspringen

                        val tokens = line.split(";")

                        // Pflichtfeld: Wenn kein Name da ist, Zeile überspringen
                        val name = tokens.getCol("name")
                        if (name.isNullOrEmpty()) continue

                        // Werte dynamisch anhand der Spaltennamen auslesen (robust gegen Int/Double/Strings)
                        val countryCode = tokens.getCol("country").orEmpty()

                        if (countryCode.isEmpty()) {
                            Log.w("CSVImport", "Spieler '$name': Kein Landeskürzel in CSV angegeben.")
                        } else if (!Country.isValidCode(countryCode)) {
                            Log.w("CSVImport", "Spieler '$name': Ungültiges/unbekanntes Landeskürzel '$countryCode' in CSV gefunden!")
                        }

                        val skillStr = tokens.getCol("skill_level")
                        val skill = skillStr?.toDoubleOrNull()?.toInt() ?: skillStr?.toIntOrNull() ?: 5

                        val tourYearStr = tokens.getCol("tour_card_year")
                        val tourCardYear = tourYearStr?.toDoubleOrNull()?.toInt() ?: tourYearStr?.toIntOrNull() ?: 0

                        val prizeMoneyStr = tokens.getCol("price_money")
                        val prizeMoney = prizeMoneyStr?.toDoubleOrNull() ?: 0.0

                        playerList.add(
                            Player(
                                name = name,
                                skill = skill,
                                countryCode = countryCode,
                                prizeMoney = prizeMoney,
                                tourCardYear = tourCardYear
                            )
                        )
                    }
                }
                return playerList
            }
        }
    }
}
