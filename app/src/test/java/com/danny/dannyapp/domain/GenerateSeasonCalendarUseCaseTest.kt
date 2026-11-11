package com.danny.dannyapp.domain

import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateSeasonCalendarUseCaseTest {

    @Test
    fun testGenerateSeasonCalendarCreates73TournamentsInOrder() = runBlocking {
        val fakeTournamentDao = FakeTournamentDao()
        val useCase = GenerateSeasonCalendarUseCase(fakeTournamentDao)

        val tournaments = useCase(seasonId = 1L)

        // Total count must be 73 (34 PC + 15 ET + 15 QUALIFIER_ET + 8 Majors + 1 QUALIFIER_GRAND_SLAM)
        assertEquals(73, tournaments.size)

        // Verify order from 1 to 73
        tournaments.forEachIndexed { index, tournament ->
            assertEquals(index + 1, tournament.order)
            assertEquals(1L, tournament.seasonId)
        }

        val pcCount = tournaments.count { it.type == TournamentEvent.PLAYERS_CHAMPIONSHIP.name }
        val etCount = tournaments.count { it.type == TournamentEvent.EUROPEAN_TOUR.name }
        val qualEtCount = tournaments.count { it.type == TournamentEvent.QUALIFIER_ET.name }
        val qualGsCount = tournaments.count { it.type == TournamentEvent.QUALIFIER_GRAND_SLAM.name }
        val majorTypes = setOf(
            TournamentEvent.UK_OPEN.name,
            TournamentEvent.EUROPEAN_OPEN.name,
            TournamentEvent.WORLD_MATCHPLAY.name,
            TournamentEvent.WORLD_GRAND_PRIX.name,
            TournamentEvent.EUROPEAN_CHAMPIONSHIP.name,
            TournamentEvent.GRAND_SLAM_OF_DARTS.name,
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS.name,
            TournamentEvent.WORLD_CHAMPIONSHIP.name
        )
        val majorCount = tournaments.count { it.type in majorTypes }

        assertEquals(34, pcCount)
        assertEquals(15, etCount)
        assertEquals(15, qualEtCount)
        assertEquals(1, qualGsCount)
        assertEquals(8, majorCount)

        // Verify first and last event names
        assertEquals("Players Championship 1", tournaments.first().name)
        assertEquals("World Championship", tournaments.last().name)

        // Verify idempotency: calling invoke again returns existing list
        val repeatedTournaments = useCase(seasonId = 1L)
        assertEquals(73, repeatedTournaments.size)
    }

    private class FakeTournamentDao : TournamentDao {
        private val list = mutableListOf<Tournament>()
        private var idCounter = 1L

        override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> =
            flowOf(list.filter { it.seasonId == seasonId })

        override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> =
            list.filter { it.seasonId == seasonId }

        override suspend fun getTournamentById(id: Long): Tournament? =
            list.find { it.id == id }

        override suspend fun insertTournament(tournament: Tournament): Long {
            val assignedId = if (tournament.id == 0L) idCounter++ else tournament.id
            val item = tournament.copy(id = assignedId)
            list.add(item)
            return assignedId
        }

        override suspend fun insertTournaments(tournaments: List<Tournament>) {
            tournaments.forEach { insertTournament(it) }
        }

        override suspend fun updateTournament(tournament: Tournament) {}
        override suspend fun deleteTournament(tournament: Tournament) {}
        override suspend fun deleteTournamentById(id: Long) {}
        override suspend fun deleteTournamentsForSeason(seasonId: Long) {
            list.removeIf { it.seasonId == seasonId }
        }
    }
}
