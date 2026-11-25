package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.TournamentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateSeasonCalendarUseCase(
    private val tournamentRepository: TournamentRepository
) {
    suspend operator fun invoke(seasonId: Long, shortTestSeason: Boolean = false): List<Tournament> = withContext(Dispatchers.IO) {
        val existing = tournamentRepository.getTournamentsListForSeason(seasonId)
        if (existing.isNotEmpty()) {
            return@withContext existing
        }

        val tournaments = createSeasonCalendar(seasonId, shortTestSeason)
        tournamentRepository.insertTournaments(tournaments)
        tournamentRepository.getTournamentsListForSeason(seasonId)
    }

    fun createSeasonCalendar(seasonId: Long, shortTestSeason: Boolean = false): List<Tournament> {
        val config = TournamentConfigLoader.safeConfig()
            ?: throw IllegalStateException("TournamentConfigLoader wurde nicht initialisiert.")

        val entries = if (!shortTestSeason) {
            config.calendar
        } else {
            val pcEntries = config.calendar.filter { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP }.take(3).toSet()
            val etEntries = config.calendar.filter { it.type == TournamentEvent.EUROPEAN_TOUR }.take(3)
            val etQualifierNames = etEntries.map { "${it.name} Qualifier" }.toSet()
            val etQualifierEntries = config.calendar.filter { it.type == TournamentEvent.QUALIFIER_ET && it.name in etQualifierNames }.toSet()

            config.calendar.filter { entry ->
                entry.type.isMajor ||
                (entry.type.isQualifier && entry.type != TournamentEvent.QUALIFIER_ET) ||
                entry in pcEntries ||
                entry in etEntries ||
                entry in etQualifierEntries
            }
        }

        return entries.mapIndexed { index, entry ->
            Tournament(
                seasonId = seasonId,
                name = entry.name,
                type = entry.type.name,
                order = index + 1,
                hostCountry = entry.hostCountry,
                hostCity = entry.hostCity
            )
        }
    }
}
