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
            suspend fun getQualifierWinnersFromPreceding(targetCount: Int? = null): List<Long> {
                val seasonTournaments = tournamentRepository.getTournamentsListForSeason(season.id)
                val qualTournament = tournamentQualificationUseCase.findQualifierTournamentForMain(tournament, seasonTournaments)
                    ?: return emptyList()
                val prevMatches = tournamentRepository.getMatchesListForTournament(qualTournament.id)
                if (prevMatches.isEmpty()) return emptyList()

                val rounds = prevMatches.map { it.round }.distinct()
                val finalRoundName = rounds.lastOrNull { round ->
                    prevMatches.filter { it.round == round }.all { it.isCompleted && it.winnerId != null }
                } ?: rounds.lastOrNull() ?: return emptyList()

                val finalRoundMatches = prevMatches.filter { it.round == finalRoundName }
                val winners = finalRoundMatches.mapNotNull { it.winnerId }
                if (targetCount != null && winners.size >= targetCount) {
                    return winners.take(targetCount)
                }
                return winners
            }

            val strategy = TournamentRuleStrategyRegistry.getStrategy(event)
            val precedingWinners = if (strategy.usesPrecedingQualifier) {
                getQualifierWinnersFromPreceding(strategy.precedingQualifierWinnerLimit)
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
            if (startRound != null) {
                tournamentRepository.updateTournament(tournament.copy(currentRound = startRound.name))
            }

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
        val prelimMatches = mutableListOf<TournamentMatch>()
        val boardCount = split.boardCount
        val r1Matches = if (split.prelimPlayers.isNotEmpty()) {
            val prelimMatchCount = split.prelimPlayers.size / 2
            val prelimPlayers = split.prelimPlayers
            for (matchIndex in 0 until prelimMatchCount) {
                prelimMatches.add(
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.QUAL_PRELIM.name,
                        matchIndex = matchIndex,
                        player1Id = prelimPlayers[matchIndex],
                        player2Id = prelimPlayers[2 * prelimMatchCount - 1 - matchIndex]
                    )
                )
            }
            val prelimPlaceholders = (0 until prelimMatchCount).map { matchIndex -> -1000L - matchIndex }
            val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, split.byePlayers, prelimPlaceholders)
            r1Pairings.map { pairing ->
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = TournamentRound.QUAL_R1.name,
                    matchIndex = pairing.matchIndex,
                    player1Id = pairing.player1Id,
                    player2Id = pairing.player2Id
                )
            }
        } else {
            val r1Pairings = BracketGenerator.buildQualifierRound1Pairings(boardCount, split.byePlayers, emptyList())
            r1Pairings.map { pairing ->
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = TournamentRound.QUAL_R1.name,
                    matchIndex = pairing.matchIndex,
                    player1Id = pairing.player1Id,
                    player2Id = pairing.player2Id
                )
            }
        }
        return Pair(prelimMatches, r1Matches)
    }
}
