package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Repository zur Verwaltung aller Turnier-Daten (Turniere, Matches, Platzierungen, Teilnehmer).
 */
class TournamentRepository(
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao(),
    )

    constructor(tournamentDao: TournamentDao) : this(
        tournamentDao = tournamentDao,
        tournamentMatchDao = object : TournamentMatchDao {
            override fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> = emptyList()
            override suspend fun getMatchById(id: Long): TournamentMatch? = null
            override suspend fun insertMatch(match: TournamentMatch): Long = 0L
            override suspend fun insertMatches(matches: List<TournamentMatch>) {}
            override suspend fun updateMatch(match: TournamentMatch) {}
            override suspend fun deleteMatch(match: TournamentMatch) {}
            override suspend fun deleteMatchesForTournament(tournamentId: Long) {}
        },
        tournamentPlacementDao = object : TournamentPlacementDao {
            override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getPlacementForPlayerInTournament(tournamentId: Long, playerId: Long): TournamentPlacement? = null
            override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = emptyList()
            override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> = emptyList()
            override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun insertPlacement(placement: TournamentPlacement): Long = 0L
            override suspend fun insertPlacements(placements: List<TournamentPlacement>) {}
            override suspend fun updatePlacement(placement: TournamentPlacement) {}
            override suspend fun deletePlacement(placement: TournamentPlacement) {}
            override suspend fun deletePlacementsForTournament(tournamentId: Long) {}
            override suspend fun upsertPlayerPlacement(tournamentId: Long, playerId: Long, reachedRound: String, prizeMoney: Double) {}
        },
        tournamentParticipantDao = object : TournamentParticipantDao {
            override suspend fun insertParticipants(participants: List<TournamentParticipant>) {}
            override suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = emptyList()
            override suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = emptyList()
        }
    )

    // --- Tournaments ---

    /**
     * Liefert einen [Flow] aller Turniere einer bestimmten Saison.
     */
    fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> {
        return tournamentDao.getTournamentsForSeason(seasonId)
    }

    /**
     * Ruft die Liste aller Turniere einer bestimmten Saison einmalig ab.
     */
    suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> {
        return tournamentDao.getTournamentsListForSeason(seasonId)
    }

    /**
     * Ruft ein Turnier anhand seiner [id] ab.
     *
     * @throws IllegalArgumentException wenn [id] <= 0.
     * @throws IllegalStateException wenn das Turnier nicht gefunden wird.
     */
    suspend fun getTournamentById(id: Long): Tournament {
        require(id > 0L) { "Ungültige Turnier-ID: $id" }
        return requireNotNull(tournamentDao.getTournamentById(id)) { "Turnier mit ID $id nicht gefunden" }
    }

    /**
     * Fügt eine Liste von Turnieren in die Datenbank ein.
     */
    suspend fun insertTournaments(tournaments: List<Tournament>) {
        tournamentDao.insertTournaments(tournaments)
    }

    /**
     * Aktualisiert die Daten eines bestehenden Turniers.
     */
    suspend fun updateTournament(tournament: Tournament) {
        tournamentDao.updateTournament(tournament)
    }

    // --- Matches ---

    /**
     * Liefert einen [Flow] aller Matches eines bestimmten Turniers.
     */
    fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> {
        return tournamentMatchDao.getMatchesForTournament(tournamentId)
    }

    /**
     * Liefert einen [Flow] aller Matches über alle Turniere einer Saison.
     */
    fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> {
        return tournamentMatchDao.getMatchesFlowForSeason(seasonId)
    }

    /**
     * Ruft die Liste aller Matches eines bestimmten Turniers einmalig ab.
     */
    suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> {
        return tournamentMatchDao.getMatchesListForTournament(tournamentId)
    }

    /**
     * Ruft ein Match anhand seiner [id] ab (oder `null`, falls nicht vorhanden).
     */
    suspend fun getMatchById(id: Long): TournamentMatch? {
        return tournamentMatchDao.getMatchById(id)
    }

    /**
     * Fügt eine Liste von Matches in die Datenbank ein.
     */
    suspend fun insertMatches(matches: List<TournamentMatch>) {
        tournamentMatchDao.insertMatches(matches)
    }

    /**
     * Aktualisiert die Daten eines bestehenden Matches.
     */
    suspend fun updateMatch(match: TournamentMatch) {
        tournamentMatchDao.updateMatch(match)
    }

    // --- Placements ---

    /**
     * Ruft die Liste aller Platzierungen eines bestimmten Turniers ab.
     */
    suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> {
        return tournamentPlacementDao.getPlacementsListForTournament(tournamentId)
    }

    /**
     * Ruft die Liste aller Platzierungen für die angegebenen Saison-IDs ab.
     */
    suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> {
        return tournamentPlacementDao.getPlacementsForSeasons(seasonIds)
    }

    /**
     * Liefert einen [Flow] aller Platzierungen für die angegebenen Saison-IDs.
     */
    fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> {
        return tournamentPlacementDao.getPlacementsFlowForSeasons(seasonIds)
    }

    /**
     * Fügt eine Liste von Platzierungen in die Datenbank ein.
     */
    suspend fun insertPlacements(placements: List<TournamentPlacement>) {
        tournamentPlacementDao.insertPlacements(placements)
    }

    /**
     * Erstellt oder aktualisiert eine Spielerplatzierung für ein Turnier.
     */
    suspend fun upsertPlayerPlacement(
        tournamentId: Long,
        playerId: Long,
        reachedRound: String,
        prizeMoney: Double,
    ) {
        tournamentPlacementDao.upsertPlayerPlacement(tournamentId, playerId, reachedRound, prizeMoney)
    }

    // --- Participants ---

    /**
     * Fügt eine Liste von Turnierteilnehmern in die Datenbank ein.
     */
    suspend fun insertParticipants(participants: List<TournamentParticipant>) {
        tournamentParticipantDao.insertParticipants(participants)
    }

    /**
     * Ruft alle Teilnehmer eines bestimmten Turniers ab.
     */
    suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> {
        return tournamentParticipantDao.getParticipantsForTournament(tournamentId)
    }

    /**
     * Ruft alle gesetzten Teilnehmer eines bestimmten Turniers ab.
     */
    suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> {
        return tournamentParticipantDao.getSeededParticipantsForTournament(tournamentId)
    }
}
