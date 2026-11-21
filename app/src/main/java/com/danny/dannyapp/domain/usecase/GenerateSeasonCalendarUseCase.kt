package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateSeasonCalendarUseCase(
    private val tournamentRepository: TournamentRepository,
    private val context: Context? = null
) {
    suspend operator fun invoke(seasonId: Long): List<Tournament> = withContext(Dispatchers.IO) {
        val existing = tournamentRepository.getTournamentsListForSeason(seasonId)
        if (existing.isNotEmpty()) {
            return@withContext existing
        }

        val tournaments = createSeasonCalendar(seasonId)
        tournamentRepository.insertTournaments(tournaments)
        tournamentRepository.getTournamentsListForSeason(seasonId)
    }

    fun createSeasonCalendar(seasonId: Long): List<Tournament> {
        val config = if (context != null) {
            TournamentConfigLoader.loadFromAssets(context)
        } else {
            TournamentConfigLoader.safeConfig()
                ?: throw IllegalStateException("TournamentConfigLoader wurde nicht initialisiert.")
        }

        return config.calendar.map { entry ->
            Tournament(
                seasonId = seasonId,
                name = entry.name,
                type = entry.type.name,
                order = entry.order,
                hostCountry = entry.hostCountry,
                hostCity = entry.hostCity
            )
        }
    }
}
