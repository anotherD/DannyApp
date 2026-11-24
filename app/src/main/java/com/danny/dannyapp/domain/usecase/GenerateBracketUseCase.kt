package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.strategy.TournamentRuleStrategyRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case responsible for generating initial bracket matches, seeding, and participants for all tournament types.
 * Nutzt [TournamentQualificationUseCase] als Single Source of Truth für Qualifikationspools und Setzlisten.
 */
class GenerateBracketUseCase(
    private val tournamentRepository: TournamentRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentQualificationUseCase: TournamentQualificationUseCase
) {

    suspend operator fun invoke(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        tournamentRepository.runInTransaction {
            val tournament = tournamentRepository.getTournamentById(tournamentId)
            val season = seasonRepository.getSeasonById(tournament.seasonId)

            val existingMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
            if (existingMatches.isNotEmpty()) {
                return@runInTransaction existingMatches
            }

            val event = TournamentEvent.valueOf(tournament.type)
            val careerId = season.careerId

            // Helfer zur Ermittlung vorheriger Qualifier-Sieger
            suspend fun getQualifierWinnersFromPreceding(): List<Long> {
                val seasonTournaments = tournamentRepository.getTournamentsListForSeason(season.id)
                val qualTournament = tournamentQualificationUseCase.preQualifyMainTournamentUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
                    ?: return emptyList()
                val prevMatches = tournamentRepository.getMatchesListForTournament(qualTournament.id)
                if (prevMatches.isEmpty()) return emptyList()
                return prevMatches.filter { it.round == TournamentRound.QUAL_FINAL.name }.sortedBy { it.matchIndex }.mapNotNull { it.winnerId }
            }

            val strategy = TournamentRuleStrategyRegistry.getStrategy(event)
            val precedingWinners = if (strategy.usesPrecedingQualifier) {
                getQualifierWinnersFromPreceding()
            } else {
                emptyList()
            }

            // Single Source of Truth: Qualifikations- & Teilnehmerpool-Ermittlung
            val qualResult = tournamentQualificationUseCase.resolveTournamentQualification(
                tournament = tournament,
                seasonId = season.id,
                careerId = careerId,
                precedingQualifierWinners = precedingWinners
            )

            val tournamentMatches = strategy.buildInitialMatches(
                tournamentId = tournamentId,
                qualResult = qualResult,
                createQualifierMatches = ::createQualifierMatches
            )

            val participants = qualResult.participants
            val uniqueParticipants = participants.distinctBy { it.playerId }

            if (strategy.awardsPrizeMoneyAndPlacements) {
                val initialPlacements = uniqueParticipants.map { participant ->
                    val (reachedRound, initialPrize) = event.resolveInitialPlacement(participant.qualificationType)
                    TournamentPlacement(
                        tournamentId = tournamentId,
                        playerId = participant.playerId,
                        reachedRound = reachedRound.name,
                        prizeMoney = initialPrize
                    )
                }
                if (initialPlacements.isNotEmpty()) {
                    tournamentRepository.insertPlacements(initialPlacements)
                }
            }

            tournamentRepository.insertMatches(tournamentMatches)

            // Startrunde am Turnier persistieren: erste Runde der Sequenz, für die tatsächlich Matches angelegt wurden
            val fullRoundSequence = event.getRoundSequence()
            val startRound = fullRoundSequence.firstOrNull { round ->
                tournamentMatches.any { match -> match.round == round.name }
            }
            val resolvedStartRound = requireNotNull(startRound) {
                "Keine gültige Startrunde für Turnier $tournamentId ($event) ermittelt"
            }
            tournamentRepository.updateTournament(tournament.copy(currentRound = resolvedStartRound.name))

            tournamentRepository.getMatchesListForTournament(tournamentId)
        }
    }

    /**
     * Erstellt die Vorrunden- und R1-Matches für ein Qualifier-Turnier basierend auf [split].
     */
    private fun createQualifierMatches(
        tournamentId: Long,
        split: QualifierFieldSplit
    ): Pair<List<TournamentMatch>, List<TournamentMatch>> {
        val boardCount = split.boardCount
        val boardSizePerBoard = split.boardSizePerBoard
        val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, boardSizePerBoard, split.nonPrequalifiedIds)
        val r1Matches = r1Pairings.map { pairing ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = pairing.round.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id
            )
        }
        return Pair(emptyList(), r1Matches)
    }
}
