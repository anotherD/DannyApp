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

    private data class EtEventInfo(val name: String, val hostCountry: String)

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
        val rawList = mutableListOf<Triple<String, String, String>>() // name, type, hostCountry
        var pcIndex = 1
        var etIndex = 1

        val pcHostCountries = listOf(
            "ENG", "ENG",               // PC 01 – 02 (Milton Keynes)
            "ENG", "ENG", "ENG", "ENG", // PC 03 – 06 (Wigan)
            "ENG", "ENG",               // PC 07 – 08 (Leicester)
            "ENG", "ENG", "ENG", "ENG", // PC 09 – 12 (Wigan / Leicester)
            "GER", "ENG", "GER", "ENG", "GER", // PC 13 – 17 (Hildesheim / Wigan)
            "ENG", "GER", "ENG",        // PC 18 – 20 (Wigan / Hildesheim)
            "ENG", "GER", "ENG", "GER", // PC 21 – 24 (Leicester / Hildesheim)
            "ENG", "ENG",               // PC 25 – 26 (Leicester)
            "ENG", "NED", "ENG", "NED", // PC 27 – 30 (Leicester / Den Bosch)
            "ENG", "NED", "ENG", "NED"  // PC 31 – 34 (Wigan / Den Bosch)
        )

        val etList = listOf(
            EtEventInfo("Belgian Darts Open", "BEL"),
            EtEventInfo("German Darts Championship", "GER"),
            EtEventInfo("Austrian Darts Open", "AUT"),
            EtEventInfo("International Darts Open", "GER"),
            EtEventInfo("European Darts Grand Prix", "GER"),
            EtEventInfo("Dutch Darts Championship", "NED"),
            EtEventInfo("European Darts Open", "GER"),
            EtEventInfo("French Darts Open", "FRA"),
            EtEventInfo("Baltic Sea Darts Open", "GER"),
            EtEventInfo("Hungarian Darts Trophy", "HUN"),
            EtEventInfo("Czech Darts Open", "CZE"),
            EtEventInfo("Swiss Darts Trophy", "SUI"),
            EtEventInfo("German Darts Grand Prix", "GER"),
            EtEventInfo("Elten Safety Darts Trophy", "GER"),
            EtEventInfo("Flanders Darts Trophy", "BEL")
        )

        fun addPc() {
            val host = pcHostCountries.getOrElse(pcIndex - 1) { "ENG" }
            rawList.add(Triple("Players Championship $pcIndex", TournamentEvent.PLAYERS_CHAMPIONSHIP.name, host))
            pcIndex++
        }

        fun addEt() {
            val info = etList.getOrElse(etIndex - 1) { EtEventInfo("European Tour $etIndex", "GER") }
            rawList.add(Triple("${info.name} Qualifier", TournamentEvent.QUALIFIER_ET.name, info.hostCountry))
            rawList.add(Triple(info.name, TournamentEvent.EUROPEAN_TOUR.name, info.hostCountry))
            etIndex++
        }

        // 1..6: PC 1..4, ET 1..2
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()

        // UK Open (Major)
        rawList.add(Triple("UK Open", TournamentEvent.UK_OPEN.name, "ENG"))

        // PC 5..10, ET 3..4
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()

        // European Open (Major)
        rawList.add(Triple("European Open", TournamentEvent.EUROPEAN_OPEN.name, "AUT"))

        // ET 5..8, PC 11..16
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

        // World Matchplay (Major)
        rawList.add(Triple("World Matchplay", TournamentEvent.WORLD_MATCHPLAY.name, "ENG"))

        // PC 17..24, ET 9..12
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

        // World Grand Prix (Major)
        rawList.add(Triple("World Grand Prix", TournamentEvent.WORLD_GRAND_PRIX.name, "ENG"))

        // PC 25..28, ET 13
        addPc()
        addPc()
        addEt()
        addPc()
        addPc()

        // European Championship (Major)
        rawList.add(Triple("European Championship", TournamentEvent.EUROPEAN_CHAMPIONSHIP.name, "GER"))

        // ET 14, PC 29..30
        addEt()
        addPc()
        addPc()

        // Grand Slam of Darts (Major) - preceded by qualifier
        rawList.add(Triple("Grand Slam Qualifier", TournamentEvent.QUALIFIER_GRAND_SLAM.name, "ENG"))
        rawList.add(Triple("Grand Slam of Darts", TournamentEvent.GRAND_SLAM_OF_DARTS.name, "ENG"))

        // ET 15, PC 31..34
        addEt()
        addPc()
        addPc()
        addPc()
        addPc()

        // Players Championship Finals (Major)
        rawList.add(Triple("Players Championship Finals", TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS.name, "ENG"))

        // World Championship Qualifier (QUALIFIER_WM) & World Championship (Major)
        rawList.add(Triple("World Championship Qualifier", TournamentEvent.QUALIFIER_WM.name, "ENG"))
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
