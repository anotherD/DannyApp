package com.danny.dannyapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Player::class], version = 1, exportSchema = false)
@TypeConverters(CountryCodeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao

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
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    prepopulateDatabase(getDatabase(context).playerDao())
                }
            }

            private suspend fun prepopulateDatabase(playerDao: PlayerDao) {
                val initialPlayers = listOf(
                    Player(name = "Luke Littler", skillLevel = 10, hasTourCard = true, country = Country.ENGLAND),
                    Player(name = "Michael van Gerwen", skillLevel = 10, hasTourCard = true, country = Country.NETHERLANDS),
                    Player(name = "Gary Anderson", skillLevel = 9, hasTourCard = true, country = Country.SCOTLAND),
                    Player(name = "Max Hopp", skillLevel = 6, hasTourCard = false, country = Country.GERMANY),
                    Player(name = "Anfänger Bot", skillLevel = 2, hasTourCard = false, country = Country.GERMANY)
                )
                playerDao.insertAll(initialPlayers)
            }
        }
    }
}
