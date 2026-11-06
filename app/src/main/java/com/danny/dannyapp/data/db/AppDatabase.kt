package com.danny.dannyapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danny.dannyapp.data.Career
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Player::class, Career::class], version = 2, exportSchema = false)
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
                .fallbackToDestructiveMigration()
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
                    Player(name = "Luke Littler", skillLevel = 10, hasTourCard = true, countryCode = "ENG"),
                    Player(name = "Michael van Gerwen", skillLevel = 10, hasTourCard = true, countryCode = "NED"),
                    Player(name = "Gary Anderson", skillLevel = 9, hasTourCard = true, countryCode = "SCO"),
                    Player(name = "Max Hopp", skillLevel = 6, hasTourCard = false, countryCode = "GER"),
                    Player(name = "Anfänger Bot", skillLevel = 2, hasTourCard = false, countryCode = "GER")
                )
                playerDao.insertAll(initialPlayers)
            }
        }
    }
}
