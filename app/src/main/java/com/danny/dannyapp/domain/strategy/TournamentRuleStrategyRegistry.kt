package com.danny.dannyapp.domain.strategy

import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
import com.danny.dannyapp.data.db.PLACEHOLDER_PREVIOUS_WINNER_ID
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.usecase.BracketGenerator
import com.danny.dannyapp.domain.usecase.QualifierFieldSplit
import com.danny.dannyapp.domain.usecase.TournamentQualificationResult

/**
 * Registry für den Zugriff auf die passende [TournamentRuleStrategy] für jedes [TournamentEvent].
 */
object TournamentRuleStrategyRegistry {

    private val strategies: Map<TournamentEvent, TournamentRuleStrategy> = mapOf(
        TournamentEvent.PLAYERS_CHAMPIONSHIP to StandardKoStrategy(TournamentEvent.PLAYERS_CHAMPIONSHIP),
        TournamentEvent.WORLD_MASTERS to StandardKoStrategy(
            event = TournamentEvent.WORLD_MASTERS,
            usesPrecedingQualifier = true
        ),
        TournamentEvent.WORLD_MATCHPLAY to StandardKoStrategy(TournamentEvent.WORLD_MATCHPLAY),
        TournamentEvent.WORLD_GRAND_PRIX to StandardKoStrategy(TournamentEvent.WORLD_GRAND_PRIX),
        TournamentEvent.EUROPEAN_CHAMPIONSHIP to StandardKoStrategy(TournamentEvent.EUROPEAN_CHAMPIONSHIP),
        TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS to StandardKoStrategy(TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS),
        TournamentEvent.WORLD_CHAMPIONSHIP to StandardKoStrategy(
            event = TournamentEvent.WORLD_CHAMPIONSHIP,
            usesPrecedingQualifier = true
        ),
        TournamentEvent.EUROPEAN_TOUR to EuropeanTourStrategy,
        TournamentEvent.GRAND_SLAM_OF_DARTS to GrandSlamStrategy,
        TournamentEvent.UK_OPEN to OpenDrawMajorStrategy(TournamentEvent.UK_OPEN),
        TournamentEvent.EUROPEAN_OPEN to OpenDrawMajorStrategy(TournamentEvent.EUROPEAN_OPEN),
        TournamentEvent.QUALIFIER_ET to QualifierStrategy(TournamentEvent.QUALIFIER_ET),
        TournamentEvent.QUALIFIER_GRAND_SLAM to QualifierStrategy(TournamentEvent.QUALIFIER_GRAND_SLAM),
        TournamentEvent.QUALIFIER_WM to QualifierStrategy(TournamentEvent.QUALIFIER_WM),
        TournamentEvent.QUALIFIER_WORLD_MASTERS to QualifierStrategy(TournamentEvent.QUALIFIER_WORLD_MASTERS)
    )

    fun getStrategy(event: TournamentEvent): TournamentRuleStrategy {
        return requireNotNull(strategies[event]) {
            "Keine TournamentRuleStrategy für Event $event registriert."
        }
    }
}

/**
 * Hilfsfunktion zum Erzeugen nachfolgender KO-Runden mit Platzhalter-Match-Objekten.
 */
internal fun MutableList<TournamentMatch>.appendSubsequentKoRounds(
    tournamentId: Long,
    event: TournamentEvent,
    startRound: TournamentRound,
    startMatchCount: Int,
    customRoundSequence: List<TournamentRound>? = null
) {
    val roundSequence = customRoundSequence ?: event.getRoundSequence()
    val startIndex = roundSequence.indexOf(startRound)
    if (startIndex == -1) return
    var currentCount = startMatchCount
    for (roundIdx in startIndex + 1 until roundSequence.size) {
        val roundEnum = roundSequence[roundIdx]
        currentCount /= 2
        for (matchIndex in 0 until currentCount) {
            add(
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

/**
 * Standard-KO-Strategie für Turniere mit klassischem KO-Baum.
 */
class StandardKoStrategy(
    override val event: TournamentEvent,
    override val usesPrecedingQualifier: Boolean = false
) : TournamentRuleStrategy {

    private fun determineSeededPlayerIds(): Int {
        return when (event) {
            TournamentEvent.WORLD_MATCHPLAY,
            TournamentEvent.WORLD_GRAND_PRIX,
            TournamentEvent.WORLD_MASTERS -> 16
            TournamentEvent.PLAYERS_CHAMPIONSHIP,
            TournamentEvent.EUROPEAN_CHAMPIONSHIP,
            TournamentEvent.WORLD_CHAMPIONSHIP -> 32
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> 64
            else -> throw IllegalArgumentException("Unsupported event for seeding: $event")
        }
    }

    override fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch> {
        val seedCount = determineSeededPlayerIds()
        val (startRound, pairings) = when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> {
                TournamentRound.L128 to BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
            }
            TournamentEvent.WORLD_MATCHPLAY, TournamentEvent.WORLD_GRAND_PRIX, TournamentEvent.WORLD_MASTERS -> {
                TournamentRound.L32 to BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    proTourSortedIds = qualResult.proTourSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
            }
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> {
                TournamentRound.L32 to BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = qualResult.qualifierIds
                )
            }
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> {
                TournamentRound.L64 to BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = emptyList(),
                    qualifierIds = qualResult.qualifierIds
                )
            }
            TournamentEvent.WORLD_CHAMPIONSHIP -> {
                TournamentRound.L128 to BracketGenerator.generateInitialMatches(
                    event = event,
                    oomSortedIds = qualResult.oomSortedIds,
                    qualifierIds = qualResult.qualifierIds
                )
            }
            else -> throw IllegalArgumentException("Unsupported event for StandardKoStrategy: $event")
        }

        val matchCount = pairings.size
        if (seedCount == 16 && matchCount == 16) {
            require(STANDARD_32_PLAYER_SEED_POSITIONS.size == 16) {
                "Für seedCount == 16 bei 32 Teilnehmern (matchCount == 16) müssen alle 16 Einträge von STANDARD_32_PLAYER_SEED_POSITIONS verwendet werden."
            }
        }

        val tournamentMatches = mutableListOf<TournamentMatch>()
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
        tournamentMatches.appendSubsequentKoRounds(tournamentId, event, startRound, startMatches.size)
        return tournamentMatches
    }

    companion object {
        val STANDARD_32_PLAYER_SEED_POSITIONS: List<Int> = BracketGenerator.BRACKET_SEED_ORDER_16
    }
}

/**
 * Strategie für die European Tour (L48 -> L32 mit gesetzten Top-16 OOM-Spielern).
 */
object EuropeanTourStrategy : TournamentRuleStrategy {
    override val event: TournamentEvent = TournamentEvent.EUROPEAN_TOUR
    override val usesPrecedingQualifier: Boolean = true

    override fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch> {
        val tournamentMatches = mutableListOf<TournamentMatch>()

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
        tournamentMatches.appendSubsequentKoRounds(tournamentId, event, TournamentRound.L32, l32Matches.size)

        return tournamentMatches
    }
}

/**
 * Strategie für Open-Draw-Majors (UK Open / European Open) ohne Vorab-Generierung von Folgerunden.
 */
class OpenDrawMajorStrategy(
    override val event: TournamentEvent
) : TournamentRuleStrategy {

    override fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch> {
        val pairings = BracketGenerator.generateInitialMatches(
            event = event,
            oomSortedIds = qualResult.oomSortedIds,
            qualifierIds = qualResult.qualifierIds
        )
        return pairings.map { pairing ->
            TournamentMatch(
                tournamentId = tournamentId,
                round = pairing.round.name,
                matchIndex = pairing.matchIndex,
                player1Id = pairing.player1Id,
                player2Id = pairing.player2Id,
                groupName = pairing.groupName
            )
        }
    }
}

/**
 * Strategie für den Grand Slam of Darts (Gruppenphase A-H + L16).
 */
object GrandSlamStrategy : TournamentRuleStrategy {
    override val event: TournamentEvent = TournamentEvent.GRAND_SLAM_OF_DARTS
    override val usesPrecedingQualifier: Boolean = true

    override fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch> {
        val tournamentMatches = mutableListOf<TournamentMatch>()

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
            tournamentMatches.add(
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = TournamentRound.GROUP.name,
                    matchIndex = 16 + groupIdx,
                    player1Id = pot2Player,
                    player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID,
                    groupName = groupLetter
                )
            )
            tournamentMatches.add(
                TournamentMatch(
                    tournamentId = tournamentId,
                    round = TournamentRound.GROUP.name,
                    matchIndex = 32 + groupIdx,
                    player1Id = pot2Player,
                    player2Id = PLACEHOLDER_PREVIOUS_WINNER_ID,
                    groupName = groupLetter
                )
            )
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
        tournamentMatches.appendSubsequentKoRounds(tournamentId, event, TournamentRound.L16, l16Matches.size)

        return tournamentMatches
    }
}

/**
 * Strategie für Qualifier-Turniere (QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM).
 */
class QualifierStrategy(
    override val event: TournamentEvent
) : TournamentRuleStrategy {

    override fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch> {
        val split = qualResult.qualifierFieldSplit
            ?: throw IllegalStateException("QualifierFieldSplit missing for qualifier tournament $tournamentId")

        val (prelimMatches, r1Matches) = createQualifierMatches(tournamentId, split)

        val processedR1Matches = r1Matches.map { match ->
            check(!(match.player1Id == PLACEHOLDER_OPEN_DRAW_ID && match.player2Id == PLACEHOLDER_OPEN_DRAW_ID)) {
                "Ungültiges Qualifier-Match ${match.matchIndex} in Runde ${match.round}: Freilos gegen Freilos ist nicht zulässig"
            }
            if (match.player2Id == PLACEHOLDER_OPEN_DRAW_ID && match.player1Id != PLACEHOLDER_OPEN_DRAW_ID && match.player1Id != PLACEHOLDER_PREVIOUS_WINNER_ID) {
                match.copy(
                    isCompleted = true,
                    winnerId = match.player1Id,
                    player1Score = 0,
                    player2Score = 0
                )
            } else if (match.player1Id == PLACEHOLDER_OPEN_DRAW_ID && match.player2Id != PLACEHOLDER_OPEN_DRAW_ID && match.player2Id != PLACEHOLDER_PREVIOUS_WINNER_ID) {
                match.copy(
                    isCompleted = true,
                    winnerId = match.player2Id,
                    player1Score = 0,
                    player2Score = 0
                )
            } else {
                match
            }
        }

        val tournamentMatches = mutableListOf<TournamentMatch>()
        tournamentMatches.addAll(prelimMatches)
        tournamentMatches.addAll(processedR1Matches)
        val r1MatchesPerBoard = if (split.boardCount > 0) r1Matches.size / split.boardCount else 4
        val roundSequence = event.getQualifierRoundSequence(r1MatchesPerBoard)
        tournamentMatches.appendSubsequentKoRounds(tournamentId, event, TournamentRound.QUAL_R1, r1Matches.size, roundSequence)

        for (r1Match in processedR1Matches) {
            if (r1Match.isCompleted && r1Match.winnerId != null) {
                val targetMatchIndex = r1Match.matchIndex / 2
                val r2MatchIndexInList = tournamentMatches.indexOfFirst {
                    it.round == TournamentRound.QUAL_R2.name && it.matchIndex == targetMatchIndex
                }
                if (r2MatchIndexInList != -1) {
                    val r2Match = tournamentMatches[r2MatchIndexInList]
                    val updatedR2Match = if (r1Match.matchIndex % 2 == 0) {
                        r2Match.copy(player1Id = r1Match.winnerId)
                    } else {
                        r2Match.copy(player2Id = r1Match.winnerId)
                    }
                    tournamentMatches[r2MatchIndexInList] = updatedR2Match
                }
            }
        }

        return tournamentMatches
    }
}
