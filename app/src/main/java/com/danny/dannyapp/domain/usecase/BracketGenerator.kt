package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.domain.model.MatchPairing
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound

object BracketGenerator {

    /**
     * Erzeugt die Initial-Paarungen für ein Turnier basierend auf der sortierten Teilnehmerliste.
     *
     * @param event Der Turniertyp
     * @param oomSortedIds Alle qualifizierten Spieler, sortiert nach aktueller Order of Merit (Index 0 = Platz 1)
     * @param proTourSortedIds Qualifizierte Spieler sortiert nach Pro Tour OOM (relevant für ET, WM, Matchplay)
     * @param qualifierIds Ungesetzte Qualifikanten (Qualifier-Sieger / Nicht-Tourcard-Spieler)
     */
    fun generateInitialMatches(
        event: TournamentEvent,
        oomSortedIds: List<Long>,
        proTourSortedIds: List<Long> = emptyList(),
        qualifierIds: List<Long> = emptyList()
    ): List<MatchPairing> {
        return when (event) {
            TournamentEvent.WORLD_MATCHPLAY -> generateWorldMatchplayRound1(
                top16Oom = oomSortedIds.take(16),
                top16ProTour = proTourSortedIds.take(16)
            )
            TournamentEvent.EUROPEAN_TOUR -> generateEuropeanTourRound1(
                proTourSeeds = proTourSortedIds.take(16),
                qualifiers = qualifierIds.take(16)
            )
            TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN -> generateUkOpenRound1(
                ranks97To128 = oomSortedIds.drop(96).take(32),
                qualifiers = qualifierIds.take(32)
            )
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> generateStrictSeededRound1(
                round = TournamentRound.L32,
                seededIds = qualifierIds.take(32) // Bereits nach ET OOM sortiert (1 vs 32)
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> generateStrictSeededRound1(
                round = TournamentRound.L64,
                seededIds = qualifierIds.take(64) // Bereits nach PC OOM sortiert (1 vs 64)
            )
            TournamentEvent.GRAND_SLAM_OF_DARTS -> generateGrandSlamGroups(
                top16OomHeads = oomSortedIds.take(16),
                pot2MajorQualifiers = proTourSortedIds.take(16),
                pot3Qualifiers = qualifierIds.take(16)
            )
            TournamentEvent.WORLD_GRAND_PRIX -> generateWorldGrandPrixRound1(
                top8Oom = oomSortedIds.take(8),
                ranks9To16Oom = oomSortedIds.drop(8).take(8),
                proTourUnseeded = proTourSortedIds.take(16)
            )
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> generateStandardSeededBracket(
                round = TournamentRound.L128,
                topSeeds = proTourSortedIds.take(32),
                unseededPool = qualifierIds.shuffled()
            )
            TournamentEvent.WORLD_CHAMPIONSHIP -> generateWorldChampionshipRound1(
                top32Oom = oomSortedIds.take(32),
                ranks33To64Oom = oomSortedIds.drop(32).take(32),
                remaining64 = qualifierIds.take(64)
            )
        }
    }

    /**
     * World Matchplay (Offizielles Format 2026):
     * Top 16 OOM sind im Turnierbaum gesetzt (1..16).
     * Die 16 Pro-Tour-Qualifikanten werden ihnen in Runde 1 frei zugelost.
     */
    private fun generateWorldMatchplayRound1(
        top16Oom: List<Long>,
        top16ProTour: List<Long>
    ): List<MatchPairing> {
        val shuffledProTour = top16ProTour.shuffled()
        // Standard-Setzbaum für 16 Seeds (1 vs 16, 8 vs 9, etc. im Baum verteilt)
        val bracketSeedOrder = listOf(0, 15, 7, 8, 3, 12, 4, 11, 1, 14, 6, 9, 2, 13, 5, 10)
        return bracketSeedOrder.mapIndexed { matchIdx, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIdx,
                player1Id = top16Oom.getOrElse(seedIndex) { 0L },
                player2Id = shuffledProTour.getOrElse(matchIdx) { 0L }
            )
        }
    }

    /**
     * European Tour Runde 1 (L48 -> 16 Matches):
     * Top 16 Pro Tour Seeds gegen 16 zugeloste Qualifier.
     * Hinweis: Match-Index 15 (Pro Tour Seed #16) trifft in R2 auf OOM #1 usw.
     */
    private fun generateEuropeanTourRound1(
        proTourSeeds: List<Long>,
        qualifiers: List<Long>
    ): List<MatchPairing> {
        val shuffledQualifiers = qualifiers.shuffled()
        return proTourSeeds.mapIndexed { index, seedId ->
            MatchPairing(
                round = TournamentRound.L48,
                matchIndex = index,
                player1Id = seedId,
                player2Id = shuffledQualifiers.getOrElse(index) { 0L }
            )
        }
    }

    /**
     * UK Open / European Open Runde 1 (R1_160 -> 32 Matches):
     * Freie Auslosung aus OOM-Plätzen 97-128 + 32 Qualifikanten (64 Spieler).
     */
    private fun generateUkOpenRound1(
        ranks97To128: List<Long>,
        qualifiers: List<Long>
    ): List<MatchPairing> {
        val pool = (ranks97To128 + qualifiers).shuffled()
        return pool.chunked(2).mapIndexed { index, pair ->
            MatchPairing(
                round = TournamentRound.R1_160,
                matchIndex = index,
                player1Id = pair.getOrElse(0) { 0L },
                player2Id = pair.getOrElse(1) { 0L }
            )
        }
    }

    /**
     * Strikte Setzung nach Rangliste (1 vs N, 2 vs N-1, ...):
     * Genutzt für European Championship (1..32) & PC Finals (1..64).
     */
    private fun generateStrictSeededRound1(
        round: TournamentRound,
        seededIds: List<Long>
    ): List<MatchPairing> {
        val n = seededIds.size
        return (0 until n / 2).map { i ->
            MatchPairing(
                round = round,
                matchIndex = i,
                player1Id = seededIds[i],
                player2Id = seededIds[n - 1 - i]
            )
        }
    }

    /**
     * Grand Slam of Darts (16 Gruppen A-P zu je 3 Spielern):
     * Tag 1: Topf 1 (Head) vs Topf 3 (Qualifier)
     * Tag 2: Topf 2 (Major-Qualifikant) vs Verlierer Tag 1 (wird nach Tag 1 dynamisch erzeugt)
     * Tag 3: Topf 2 (Major-Qualifikant) vs Sieger Tag 1 (wird nach Tag 1 dynamisch erzeugt)
     */
    private fun generateGrandSlamGroups(
        top16OomHeads: List<Long>,
        pot2MajorQualifiers: List<Long>,
        pot3Qualifiers: List<Long>
    ): List<MatchPairing> {
        val shuffledPot3 = pot3Qualifiers.shuffled()
        return (0 until 16).map { groupIdx ->
            val groupLetter = ('A' + groupIdx).toString()
            MatchPairing(
                round = TournamentRound.GROUP, // Kennzeichnet Gruppenspiel-Phase
                matchIndex = groupIdx,
                player1Id = top16OomHeads.getOrElse(groupIdx) { 0L },
                player2Id = shuffledPot3.getOrElse(groupIdx) { 0L },
                groupName = groupLetter
            )
        }
    }

    private fun generateWorldGrandPrixRound1(
        top8Oom: List<Long>,
        ranks9To16Oom: List<Long>,
        proTourUnseeded: List<Long>
    ): List<MatchPairing> {
        val all16Seeds = top8Oom + ranks9To16Oom.shuffled()
        val unseeded = proTourUnseeded.shuffled()
        return all16Seeds.mapIndexed { index, seedId ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = index,
                player1Id = seedId,
                player2Id = unseeded.getOrElse(index) { 0L }
            )
        }
    }

    private fun generateStandardSeededBracket(
        round: TournamentRound,
        topSeeds: List<Long>,
        unseededPool: List<Long>
    ): List<MatchPairing> {
        val allPlayers = (topSeeds + unseededPool).toMutableList()
        val remaining = allPlayers.drop(topSeeds.size).shuffled().toMutableList()
        return (0 until allPlayers.size / 2).map { index ->
            val p1 = if (index < topSeeds.size) topSeeds[index] else remaining.removeAt(0)
            val p2 = remaining.removeAt(0)
            MatchPairing(round = round, matchIndex = index, player1Id = p1, player2Id = p2)
        }
    }

    private fun generateWorldChampionshipRound1(
        top32Oom: List<Long>,
        ranks33To64Oom: List<Long>,
        remaining64: List<Long>
    ): List<MatchPairing> {
        val seeded64 = top32Oom + ranks33To64Oom.shuffled()
        val unseeded64 = remaining64.shuffled()
        return seeded64.mapIndexed { index, seedId ->
            MatchPairing(
                round = TournamentRound.L128,
                matchIndex = index,
                player1Id = seedId,
                player2Id = unseeded64.getOrElse(index) { 0L }
            )
        }
    }
}
