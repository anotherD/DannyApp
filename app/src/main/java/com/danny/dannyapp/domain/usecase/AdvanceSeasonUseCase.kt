package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase zum Abschließen der aktuellen Saison und Anlegen der Folgesaison.
 */
class AdvanceSeasonUseCase(
    private val careerRepository: CareerRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val getOrderOfMeritUseCase: GetOrderOfMeritUseCase
) {

    suspend operator fun invoke(careerId: Long, newCareerLevel: Int? = null): Long = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }

        val careerWithProfile = careerRepository.getCareerById(careerId)
        val career = careerWithProfile.career

        val seasons = seasonRepository.getSeasonsListForCareer(careerId)
        val currentSeason = requireNotNull(seasons.maxByOrNull { it.seasonNumber }) {
            "Keine Saison für Karriere $careerId gefunden"
        }

        val tournaments = tournamentRepository.getTournamentsListForSeason(currentSeason.id)
        check(tournaments.isNotEmpty() && tournaments.all { it.isCompleted }) {
            "Saison kann erst fortgesetzt werden, wenn alle Turniere der aktuellen Saison abgeschlossen sind"
        }

        val oomEntries = getOrderOfMeritUseCase(careerId, OrderOfMeritType.OOM)

        val newSeasonId = seasonRepository.advanceToNextSeason(careerId, oomEntries)

        val updatedLevel = newCareerLevel ?: career.level
        val updatedCareer = career.copy(
            season = career.season + 1,
            level = updatedLevel
        )
        careerRepository.updateCareer(updatedCareer)

        newSeasonId
    }
}
