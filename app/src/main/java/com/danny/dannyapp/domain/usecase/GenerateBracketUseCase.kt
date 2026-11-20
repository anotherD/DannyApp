package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
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
    constructor(context: Context) : this(
        TournamentRepository(context),
        SeasonRepository(context),
        TournamentQualificationUseCase(context)
    )

    suspend operator fun invoke(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        val tournament = tournamentRepository.getTournamentById(tournamentId)
        val season = seasonRepository.getSeasonById(tournament.seasonId)

        val existingMatches = tournamentRepository.getMatchesListForTournament(tournamentId)
        if (existingMatches.isNotEmpty()) {
            return@withContext existingMatches
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

        val precedingWinners = when (event) {
            TournamentEvent.EUROPEAN_TOUR -> getQualifierWinnersFromPreceding(10)
            TournamentEvent.GRAND_SLAM_OF_DARTS -> getQualifierWinnersFromPreceding(null)
            TournamentEvent.WORLD_CHAMPIONSHIP -> getQualifierWinnersFromPreceding(2)
            else -> emptyList()
        }

        // Single Source of Truth: Qualifikations- & Teilnehmerpool-Ermittlung
        val qualResult = tournamentQualificationUseCase.resolveTournamentQualification(
            tournament = tournament,
            seasonId = season.id,
            careerId = careerId,
            precedingQualifierWinners = precedingWinners
        )

        val tournamentMatches = mutableListOf<TournamentMatch>()

        fun appendSubsequentKoRounds(startRound: TournamentRound, startMatchCount: Int) {
            val roundSequence = event.getRoundSequence()
            val startIndex = roundSequence.indexOf(startRound)
            if (startIndex == -1) return
            var currentCount = startMatchCount
            for (roundIdx in startIndex + 1 until roundSequence.size) {
                val roundEnum = roundSequence[roundIdx]
                currentCount /= 2
                for (matchIndex in 0 until currentCount) {
                    tournamentMatches.add(
                        TournamentMatch(
                            tournamentId = tournamentId,
                            round = roundEnum.name,
                            matchIndex = matchIndex,
                            player1Id = PLACEHOLDER_PREVIOUS_WINNER_ID,
                            player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID
                        )
                    )
                }
            }
        }

        when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L128.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L128, startMatches.size)
            }

            TournamentEvent.QUALIFIER_ET, TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WM -> {
                val split = qualResult.qualifierFieldSplit
                    ?: throw IllegalStateException("QualifierFieldSplit missing for qualifier tournament $tournamentId")
                val (prelimMatches, r1Matches) = createQualifierMatches(tournamentId, split)
                tournamentMatches.addAll(prelimMatches)
                tournamentMatches.addAll(r1Matches)
                appendSubsequentKoRounds(TournamentRound.QUAL_R1, r1Matches.size)
            }

            TournamentEvent.EUROPEAN_TOUR -> {
                val l48Pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val l48Matches = l48Pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L48.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(l48Matches)

                val seedOrder = BracketGenerator.BRACKET_SEED_ORDER_16
                require(qualResult.oomSortedIds.size == 16) { "OOM sorted IDs size must be 16 for European Tour, got ${qualResult.oomSortedIds.size}" }
                val l32Matches = (0 until 16).map { matchIndex ->
                    val seedIndex = seedOrder[matchIndex]
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = matchIndex,
                        player1Id = qualResult.oomSortedIds[seedIndex],
                        player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID
                    )
                }
                tournamentMatches.addAll(l32Matches)
                appendSubsequentKoRounds(TournamentRound.L32, l32Matches.size)
            }

            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                tournamentMatches.addAll(pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = pairing.round.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id,
                        groupName = pairing.groupName
                    )
                })
            }

            TournamentEvent.WORLD_MATCHPLAY, TournamentEvent.WORLD_GRAND_PRIX -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L32.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L32, startMatches.size)
            }

            TournamentEvent.EUROPEAN_CHAMPIONSHIP, TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                val startRound = if (event == TournamentEvent.EUROPEAN_CHAMPIONSHIP) TournamentRound.L32 else TournamentRound.L64
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = startRound.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(startRound, startMatches.size)
            }

            TournamentEvent.GRAND_SLAM_OF_DARTS -> {
                val groupPairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val groupMatches = groupPairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.GROUP.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id,
                        groupName = pairing.groupName
                    )
                }
                tournamentMatches.addAll(groupMatches)

                val shuffledPot2 = qualResult.proTourSortedIds.shuffled()
                for (groupIdx in 0 until 16) {
                    val groupLetter = ('A' + groupIdx).toString()
                    val pot2Player = shuffledPot2[groupIdx]
                    tournamentMatches.add(TournamentMatch(tournamentId = tournamentId, round = TournamentRound.GROUP.name, matchIndex = 16 + groupIdx, player1Id = pot2Player, player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID, groupName = groupLetter))
                    tournamentMatches.add(TournamentMatch(tournamentId = tournamentId, round = TournamentRound.GROUP.name, matchIndex = 32 + groupIdx, player1Id = pot2Player, player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID, groupName = groupLetter))
                }

                val l16Matches = (0 until 8).map { matchIndex ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L16.name,
                        matchIndex = matchIndex,
                        player1Id = -9000L - (2 * matchIndex),
                        player2Id = -9000L - (2 * matchIndex + 1)
                    )
                }
                tournamentMatches.addAll(l16Matches)
                appendSubsequentKoRounds(TournamentRound.L16, l16Matches.size)
            }

            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                val pairings = BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
                val startMatches = pairings.map { pairing ->
                    TournamentMatch(
                        tournamentId = tournamentId,
                        round = TournamentRound.L128.name,
                        matchIndex = pairing.matchIndex,
                        player1Id = pairing.player1Id,
                        player2Id = pairing.player2Id
                    )
                }
                tournamentMatches.addAll(startMatches)
                appendSubsequentKoRounds(TournamentRound.L128, startMatches.size)
            }
        }

        val participants = qualResult.participants
        val uniqueParticipants = participants.distinctBy { it.playerId }

        if (!event.isQualifier) {
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
