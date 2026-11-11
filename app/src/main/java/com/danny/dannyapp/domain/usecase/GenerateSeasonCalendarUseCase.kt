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
        val rawList = mutableListOf<Triple<String, String, String?>>() // name, type, hostCountry
        var pcIndex = 1
        var etIndex = 1

        val etHostCountries = listOf(
            "GER", "GER", "AUT", "NED", "BEL",
            "GER", "CZE", "HUN", "SUI", "GER",
            "AUT", "NED", "BEL", "GER", "GER"
        )

        fun addPc() {
            rawList.add(Triple("Players Championship $pcIndex", TournamentEvent.PLAYERS_CHAMPIONSHIP.name, null))
            pcIndex++
        }

        fun addEt() {
            val host = etHostCountries.getOrElse(etIndex - 1) { "GER" }
            rawList.add(Triple("ET $etIndex Tour Card Qualifier", TournamentEvent.QUALIFIER_ET.name, host))
            rawList.add(Triple("European Tour $etIndex", TournamentEvent.EUROPEAN_TOUR.name, host))
            etIndex++
        }

        // 1..6: PC 1..4, ET 1..2
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()

        // 7: UK Open (Major)
        rawList.add(Triple("UK Open", TournamentEvent.UK_OPEN.name, "ENG"))

        // 8..15: PC 5..10, ET 3..4
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()

        // 16: European Open (Major)
        rawList.add(Triple("European Open", TournamentEvent.EUROPEAN_OPEN.name, "AUT"))

        // 17..26: ET 5..8, PC 11..16
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()

        // 27: World Matchplay (Major)
        rawList.add(Triple("World Matchplay", TournamentEvent.WORLD_MATCHPLAY.name, "ENG"))

        // 28..39: PC 17..24, ET 9..12
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()

        // 40: World Grand Prix (Major)
        rawList.add(Triple("World Grand Prix", TournamentEvent.WORLD_GRAND_PRIX.name, "IRL"))

        // 41..45: PC 25..28, ET 13
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()

        // 46: European Championship (Major)
        rawList.add(Triple("European Championship", TournamentEvent.EUROPEAN_CHAMPIONSHIP.name, "GER"))

        // 47..49: ET 14, PC 29..30
        addEt()
        addPc()
        addPc()

        // 50: Grand Slam of Darts (Major) - preceded by qualifier
        rawList.add(Triple("Grand Slam Qualifier", TournamentEvent.QUALIFIER_GRAND_SLAM.name, "ENG"))
        rawList.add(Triple("Grand Slam of Darts", TournamentEvent.GRAND_SLAM_OF_DARTS.name, "ENG"))

        // 51..55: ET 15, PC 31..34
        addEt()
        addPc()
        addPc()
        addPc()
        addPc()

        // 56: Players Championship Finals (Major)
        rawList.add(Triple("Players Championship Finals", TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS.name, "ENG"))

        // 57: World Championship (Major)
        rawList.add(Triple("World Championship", TournamentEvent.WORLD_CHAMPIONSHIP.name, "ENG"))

        return rawList.mapIndexed { index, (name, type, host) ->
            Tournament(
                seasonId = seasonId,
                name = name,
                type = type,
                order = index + 1,
                hostCountry = host
            )
        }
    }
}
