package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.MatchPairing

object BracketGenerator {

    /**
     * Erzeugt die Initial-Paarungen für ein Turnier basierend auf der sortierten Teilnehmerliste.
     *
     * @param event Der Turniertyp
     * @param oomSortedIds Alle qualifizierten Spieler sortiert nach aktueller Order of Merit (Index 0 = Platz 1)
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
            TournamentEvent.QUALIFIER_ET,
            TournamentEvent.QUALIFIER_GRAND_SLAM,
            TournamentEvent.QUALIFIER_WM -> generateQualifierInitialMatches(
                event = event,
                oomSortedIds = oomSortedIds
            )
        }
    }

    /**
     * Erzeugt die Standard-Setzbaum-Reihenfolge (Faltungsalgorithmus) für n Seeds,
     * sodass Seed 1 und Seed 2 frühestens im Finale aufeinandertreffen.
     */
    fun buildStandardSeedOrder(seedCount: Int): List<Int> {
        var order = listOf(0)
        var currentSize = 1
        while (currentSize < seedCount) {
            val nextSize = currentSize * 2
            order = order.flatMap { seed ->
                listOf(seed, nextSize - 1 - seed)
            }
            currentSize = nextSize
        }
        return order
    }

    /**
     * Offizielle Setzbaumreihenfolge für 16 Seeds nach Faltungsalgorithmus.
     */
    val BRACKET_SEED_ORDER_16 = buildStandardSeedOrder(16)

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
        return BRACKET_SEED_ORDER_16.mapIndexed { matchIdx, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIdx,
                player1Id = top16Oom.getOrElse(seedIndex) { 0L },
                player2Id = shuffledProTour.getOrElse(matchIdx) { 0L }
            )
        }
    }

    /**
     * European Tour Runde 1 (L48 → 16 Matches):
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
        val participantCount = seededIds.size
        val seedOrder = buildStandardSeedOrder(participantCount / 2)
        return (0 until participantCount / 2).map { matchIdx ->
            val seedIndex = seedOrder[matchIdx]
            MatchPairing(
                round = round,
                matchIndex = matchIdx,
                player1Id = seededIds.getOrElse(seedIndex) { 0L },
                player2Id = seededIds.getOrElse(participantCount - 1 - seedIndex) { 0L }
            )
        }
    }

    /**
     * Grand Slam of Darts (16 Gruppen A-P zu je 3 Spielern):
     * Tag 1: Topf 1 (Head) vs Topf 3 (Qualifier)
     * verteilt die 16 Gruppenköpfe über BRACKET_SEED_ORDER_16.
     */
    private fun generateGrandSlamGroups(
        top16OomHeads: List<Long>,
        pot3Qualifiers: List<Long>
    ): List<MatchPairing> {
        val shuffledPot3 = pot3Qualifiers.shuffled()
        val seedOrder16 = BRACKET_SEED_ORDER_16
        return (0 until 16).map { groupIdx ->
            val groupLetter = ('A' + groupIdx).toString()
            val seedIndex = seedOrder16.getOrElse(groupIdx) { groupIdx }
            MatchPairing(
                round = TournamentRound.GROUP, // Kennzeichnet Gruppenspiel-Phase
                matchIndex = groupIdx,
                player1Id = top16OomHeads.getOrElse(seedIndex) { 0L },
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
        val all16Seeds = top8Oom + ranks9To16Oom
        val unseeded = proTourUnseeded.shuffled()
        return BRACKET_SEED_ORDER_16.mapIndexed { matchIdx, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIdx,
                player1Id = all16Seeds.getOrElse(seedIndex) { 0L },
                player2Id = unseeded.getOrElse(matchIdx) { 0L }
            )
        }
    }

    private fun generateStandardSeededBracket(
        round: TournamentRound,
        topSeeds: List<Long>,
        unseededPool: List<Long>
    ): List<MatchPairing> {
        val seedOrder32 = buildStandardSeedOrder(32)
        val unseeded = unseededPool.shuffled().toMutableList()
        val pairings = mutableListOf<MatchPairing>()

        for (i in 0 until 32) {
            val seedIndex = seedOrder32[i]
            val seededPlayer = topSeeds.getOrElse(seedIndex) { 0L }
            val unseededOpponent = if (unseeded.isNotEmpty()) unseeded.removeAt(0) else 0L

            // Match 2 * i (Seed vs Unseeded)
            pairings.add(
                MatchPairing(
                    round = round,
                    matchIndex = 2 * i,
                    player1Id = seededPlayer,
                    player2Id = unseededOpponent
                )
            )

            // Match 2 * i + 1 (Unseeded vs Unseeded)
            val player1Id = if (unseeded.isNotEmpty()) unseeded.removeAt(0) else 0L
            val player2Id = if (unseeded.isNotEmpty()) unseeded.removeAt(0) else 0L
            pairings.add(
                MatchPairing(
                    round = round,
                    matchIndex = 2 * i + 1,
                    player1Id = player1Id,
                    player2Id = player2Id
                )
            )
        }
        return pairings.sortedBy { it.matchIndex }
    }

    private fun generateWorldChampionshipRound1(
        top32Oom: List<Long>,
        ranks33To64Oom: List<Long>,
        remaining64: List<Long>
    ): List<MatchPairing> {
        val seedOrder32 = buildStandardSeedOrder(32)
        val shuffledRanks33To64 = ranks33To64Oom.shuffled()
        val unseeded64 = remaining64.shuffled()

        val pairings = mutableListOf<MatchPairing>()
        for (i in 0 until 32) {
            val seedIndex = seedOrder32[i]
            val seededPlayer = top32Oom.getOrElse(seedIndex) { 0L }
            pairings.add(
                MatchPairing(
                    round = TournamentRound.L128,
                    matchIndex = 2 * i,
                    player1Id = seededPlayer,
                    player2Id = unseeded64.getOrElse(2 * i) { 0L }
                )
            )
            val player33To64 = shuffledRanks33To64.getOrElse(i) { 0L }
            pairings.add(
                MatchPairing(
                    round = TournamentRound.L128,
                    matchIndex = 2 * i + 1,
                    player1Id = player33To64,
                    player2Id = unseeded64.getOrElse(2 * i + 1) { 0L }
                )
            )
        }
        return pairings.sortedBy { it.matchIndex }
    }

    /**
     * Erzeugt die Erstrunden-Paarungen für Qualifier-Turniere über die Board-Setzliste.
     * Freilose (byePlayers) und Vorrundensieger (prelimWinners) werden getrennt übergeben.
     * Die Top 2 · K Seeds behalten ihre festen Positionen; alle übrigen Freilos-Spieler und
     * Vorrundensieger werden vor der Verteilung auf die 6 freien Slots je Board frei zugelost.
     *
     * @param boardCount Anzahl der Boards (K)
     * @param byePlayers Gesetzte Freilos-Spieler, sortiert
     * @param prelimWinners Sieger der Vorrunde
     */
    fun buildQualifierRound1Pairings(
        boardCount: Int,
        byePlayers: List<Long>,
        prelimWinners: List<Long>
    ): List<MatchPairing> {
        val pairings = mutableListOf<MatchPairing>()
        val unseededByes = byePlayers.drop(2 * boardCount)
        val freePool = (unseededByes + prelimWinners).shuffled()

        for (boardIndex in 0 until boardCount) {
            val seedTop = byePlayers.getOrNull(boardIndex) ?: 0L
            val seedBottom = byePlayers.getOrNull(2 * boardCount - 1 - boardIndex) ?: 0L
            val slots = LongArray(6) { slotIndex -> freePool.getOrElse(boardIndex * 6 + slotIndex) { 0L } }

            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = 4 * boardIndex + 0, player1Id = seedTop, player2Id = slots[0]))
            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = 4 * boardIndex + 1, player1Id = slots[1], player2Id = slots[2]))
            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = 4 * boardIndex + 2, player1Id = slots[3], player2Id = slots[4]))
            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = 4 * boardIndex + 3, player1Id = seedBottom, player2Id = slots[5]))
        }
        return pairings
    }

    /**
     * Erzeugt die Erstrunden-Paarungen für Qualifier-Turniere (QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM)
     * basierend auf dem offiziellen K-Board-System.
     *
     * K = 10 (ET), K = 8 (Grand Slam), K = 4 (WM).
     * Hauptfeld = 8 · K Plätze (80 bei ET, 64 bei Grand Slam, 32 bei WM).
     */
    fun generateQualifierInitialMatches(
        event: TournamentEvent,
        oomSortedIds: List<Long>
    ): List<MatchPairing> {
        val boardCount = if (event.qualifierBoardCount > 0) event.qualifierBoardCount else 4

        val participantCount = oomSortedIds.size
        if (participantCount == 0) return emptyList()

        if (participantCount > 8 * boardCount) {
            val prelimMatchCount = participantCount - 8 * boardCount
            val prelimPlayers = oomSortedIds.takeLast(2 * prelimMatchCount)
            return (0 until prelimMatchCount).map { matchIndex ->
                MatchPairing(
                    round = TournamentRound.QUAL_PRELIM,
                    matchIndex = matchIndex,
                    player1Id = prelimPlayers[matchIndex],
                    player2Id = prelimPlayers[2 * prelimMatchCount - 1 - matchIndex]
                )
            }
        } else {
            return buildQualifierRound1Pairings(boardCount, oomSortedIds, emptyList())
        }
    }


}
