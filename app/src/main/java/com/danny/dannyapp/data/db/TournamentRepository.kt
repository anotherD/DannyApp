package com.danny.dannyapp.data.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Repository zur Verwaltung aller Turnier-Daten (Turniere, Matches, Platzierungen, Teilnehmer).
 */
class TournamentRepository(
    private val tournamentDao: TournamentDao,
    private val tournamentMatchDao: TournamentMatchDao,
    private val tournamentPlacementDao: TournamentPlacementDao,
    private val tournamentParticipantDao: TournamentParticipantDao,
    private val database: AppDatabase? = null,
) {

    /**
     * Führt einen Code-Block innerhalb einer Datenbanktransaktion aus.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

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
     * Ruft die Liste aller Turniere für die angegebenen Saison-IDs ab.
     */
    suspend fun getTournamentsListForSeasons(seasonIds: List<Long>): List<Tournament> {
        if (seasonIds.isEmpty()) return emptyList()
        return tournamentDao.getTournamentsListForSeasons(seasonIds)
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
     * Ruft die Liste aller Matches einer bestimmten Saison einmalig ab.
     */
    suspend fun getMatchesListForSeason(seasonId: Long): List<TournamentMatch> {
        return tournamentMatchDao.getMatchesListForSeason(seasonId)
    }

    /**
     * Ruft ein Match anhand seiner [id] ab.
     *
     * @throws IllegalArgumentException wenn [id] <= 0.
     * @throws IllegalStateException wenn das Match nicht gefunden wird.
     */
    suspend fun getMatchById(id: Long): TournamentMatch {
        require(id > 0L) { "Ungültige Match-ID: $id" }
        return requireNotNull(tournamentMatchDao.getMatchById(id)) { "Match mit ID $id nicht gefunden" }
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
