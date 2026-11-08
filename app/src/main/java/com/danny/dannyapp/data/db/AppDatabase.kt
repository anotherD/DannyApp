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

@Database(entities = [Player::class, Career::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun careerDao(): CareerDao

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
            // TODO: Spieler aus DB löschen oder TourCard entziehen oder als inactive markieren?
            // TODO: Nur auf knopfdruck? Für PoC reicht das erstmal so.
            // TODO: (Tipp: Wenn die Liste der Spieler sehr groß wird, kannst du in den SharedPreferences einfach eine Zahl wie CSV_DATA_VERSION = 2 speichern und syncPlayersFromCsv nur dann ausführen, wenn diese Zahl höher ist als beim letzten Start.)
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Läuft bei jedem Start der DB im Hintergrund (extrem schnell für ~250 Spieler)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val playersFromCsv = loadPlayersFromAssets(context, "players.csv")
                        if (playersFromCsv.isNotEmpty()) {
                            getDatabase(context).playerDao().syncPlayersFromCsv(playersFromCsv)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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
