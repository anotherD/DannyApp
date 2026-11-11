package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.domain.model.TournamentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateSeasonCalendarUseCase(
    private val tournamentDao: TournamentDao
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao()
    )

    suspend operator fun invoke(seasonId: Long): List<Tournament> = withContext(Dispatchers.IO) {
        val existing = tournamentDao.getTournamentsListForSeason(seasonId)
        if (existing.isNotEmpty()) {
            return@withContext existing
        }

        val tournaments = createSeasonCalendar(seasonId)
        tournamentDao.insertTournaments(tournaments)
        tournamentDao.getTournamentsListForSeason(seasonId)
    }

    fun createSeasonCalendar(seasonId: Long): List<Tournament> {
        val tournaments = mutableListOf<Tournament>()
        var pcIndex = 1
        var etIndex = 1

        // 1..6: PC 1..4, ET 1..2
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 1)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 2)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 3)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 4)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 5)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 6)); etIndex++

        // 7: UK Open (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "UK Open", type = TournamentEvent.UK_OPEN.name, order = 7))

        // 8..15: PC 5..10, ET 3..4
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 8)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 9)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 10)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 11)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 12)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 13)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 14)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 15)); pcIndex++

        // 16: European Open (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "European Open", type = TournamentEvent.EUROPEAN_OPEN.name, order = 16))

        // 17..26: ET 5..8, PC 11..16
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 17)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 18)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 19)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 20)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 21)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 22)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 23)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 24)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 25)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 26)); etIndex++

        // 27: World Matchplay (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "World Matchplay", type = TournamentEvent.WORLD_MATCHPLAY.name, order = 27))

        // 28..39: PC 17..24, ET 9..12
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 28)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 29)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 30)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 31)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 32)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 33)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 34)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 35)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 36)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 37)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 38)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 39)); etIndex++

        // 40: World Grand Prix (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "World Grand Prix", type = TournamentEvent.WORLD_GRAND_PRIX.name, order = 40))

        // 41..45: PC 25..28, ET 13
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 41)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 42)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 43)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 44)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 45)); pcIndex++

        // 46: European Championship (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "European Championship", type = TournamentEvent.EUROPEAN_CHAMPIONSHIP.name, order = 46))

        // 47..49: ET 14, PC 29..30
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 47)); etIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 48)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 49)); pcIndex++

        // 50: Grand Slam of Darts (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "Grand Slam of Darts", type = TournamentEvent.GRAND_SLAM_OF_DARTS.name, order = 50))

        // 51..55: ET 15, PC 31..34
        tournaments.add(Tournament(seasonId = seasonId, name = "European Tour $etIndex", type = TournamentEvent.EUROPEAN_TOUR.name, order = 51))
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 52)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 53)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 54)); pcIndex++
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship $pcIndex", type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name, order = 55)); pcIndex++

        // 56: Players Championship Finals (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "Players Championship Finals", type = TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS.name, order = 56))

        // 57: World Championship (Major)
        tournaments.add(Tournament(seasonId = seasonId, name = "World Championship", type = TournamentEvent.WORLD_CHAMPIONSHIP.name, order = 57))

        return tournaments
    }
}
