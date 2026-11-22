package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.enums.TournamentRound
import com.danny.dannyapp.domain.model.MatchPairing
import kotlin.math.ceil

object BracketGenerator {

    /**
     * Erzeugt die Initial-Paarungen für ein Turnier basierend auf der sortierten Teilnehmerliste.
     *
     * @param event Der Turniertyp
     * @param oomSortedIds Alle qualifizierten Spieler sortiert nach aktueller Order of Merit (Index 0 = Platz 1)
     * @param proTourSortedIds Qualifizierte Spieler sortiert nach Pro Tour OOM (relevant für ET, WM, Matchplay)
     * @param qualifierIds Ungesetzte Qualifikanten (Qualifier-Sieger / Nicht-Tourcard-Spieler)
     * @param dynamicBoardCount Dynamische Anzahl von Boards für Qualifier
     */
    fun generateInitialMatches(
        event: TournamentEvent,
        oomSortedIds: List<Long>,
        proTourSortedIds: List<Long> = emptyList(),
        qualifierIds: List<Long> = emptyList(),
        dynamicBoardCount: Int? = null
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
                oomSortedIds = oomSortedIds,
                dynamicBoardCount = dynamicBoardCount
            )
        }
    }

    /**
     * Erzeugt die Standard-Setzbaum-Reihenfolge (Faltungsalgorithmus) für n Seeds,
     * sodass Seed 1 und Seed 2 frühestens im Finale aufeinander treffen.
     *
     * @param seedCount Die Anzahl der gesetzten Spieler
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
        require(top16Oom.size == 16 && top16ProTour.size == 16) {
            "World Matchplay R1 erfordert jeweils 16 Spieler von OOM und ProTour"
        }
        val shuffledProTour = top16ProTour.shuffled()
        return BRACKET_SEED_ORDER_16.mapIndexed { matchIndex, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIndex,
                player1Id = top16Oom[seedIndex],
                player2Id = shuffledProTour[matchIndex]
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
        require(proTourSeeds.size == 16 && qualifiers.size == 16) {
            "European Tour R1 erfordert jeweils 16 ProTour-Seeds und Qualifier"
        }
        val shuffledQualifiers = qualifiers.shuffled()
        return proTourSeeds.mapIndexed { index, seedId ->
            MatchPairing(
                round = TournamentRound.L48,
                matchIndex = index,
                player1Id = seedId,
                player2Id = shuffledQualifiers[index]
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
        require(ranks97To128.size == 32 && qualifiers.size == 32) {
            "UK Open / European Open R1 erfordert jeweils 32 Spieler von OOM 97-128 und Qualifikanten"
        }
        val pool = (ranks97To128 + qualifiers).shuffled()
        return pool.chunked(2).mapIndexed { index, pair ->
            MatchPairing(
                round = TournamentRound.R1_160,
                matchIndex = index,
                player1Id = pair[0],
                player2Id = pair[1]
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
        require(participantCount == 32 || participantCount == 64) {
            "Strict seeded round erfordert 32 oder 64 gesetzte Spieler, erhalten: $participantCount"
        }
        val seedOrder = buildStandardSeedOrder(participantCount / 2)
        return (0 until participantCount / 2).map { matchIndex ->
            val seedIndex = seedOrder[matchIndex]
            MatchPairing(
                round = round,
                matchIndex = matchIndex,
                player1Id = seededIds[seedIndex],
                player2Id = seededIds[participantCount - 1 - seedIndex]
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
        require(top16OomHeads.size == 16 && pot3Qualifiers.size == 16) {
            "Grand Slam Gruppenauslosung erfordert 16 Gruppenköpfe und 16 Qualifier"
        }
        val shuffledPot3 = pot3Qualifiers.shuffled()
        val seedOrder16 = BRACKET_SEED_ORDER_16
        return (0 until 16).map { groupIndex ->
            val groupLetter = ('A' + groupIndex).toString()
            val seedIndex = seedOrder16[groupIndex]
            MatchPairing(
                round = TournamentRound.GROUP, // Kennzeichnet Gruppenspiel-Phase
                matchIndex = groupIndex,
                player1Id = top16OomHeads[seedIndex],
                player2Id = shuffledPot3[groupIndex],
                groupName = groupLetter
            )
        }
    }

    /**
     * World Grand Prix Runde 1 (32 Spieler -> 16 Matches):
     * Top 8 OOM gesetzt, Plätze 9-16 OOM und 16 ProTour Unseeded Spieler.
     */
    private fun generateWorldGrandPrixRound1(
        top8Oom: List<Long>,
        ranks9To16Oom: List<Long>,
        proTourUnseeded: List<Long>
    ): List<MatchPairing> {
        require(top8Oom.size == 8 && ranks9To16Oom.size == 8 && proTourUnseeded.size == 16) {
            "World Grand Prix R1 erfordert 8 Top-OOM, 8 OOM 9-16 und 16 ProTour Unseeded Spieler"
        }
        val all16Seeds = top8Oom + ranks9To16Oom
        val unseeded = proTourUnseeded.shuffled()
        return BRACKET_SEED_ORDER_16.mapIndexed { matchIndex, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIndex,
                player1Id = all16Seeds[seedIndex],
                player2Id = unseeded[matchIndex]
            )
        }
    }

    /**
     * Standard-Setzbaum Paarungen für Turniere mit 128 Spielern (z. B. Players Championship).
     * 32 Top-Seeds und 96 Unseeded-Spieler in 64 Erstrunden-Matches.
     */
    private fun generateStandardSeededBracket(
        round: TournamentRound,
        topSeeds: List<Long>,
        unseededPool: List<Long>
    ): List<MatchPairing> {
        require(topSeeds.size == 32 && unseededPool.size == 96) {
            "Standard Seeded Bracket erfordert 32 Top-Seeds und 96 Unseeded Spieler"
        }
        val seedOrder32 = buildStandardSeedOrder(32)
        val unseeded = unseededPool.shuffled().toMutableList()
        val pairings = mutableListOf<MatchPairing>()

        for (seedPairIndex in 0 until 32) {
            val seedIndex = seedOrder32[seedPairIndex]
            val seededPlayer = topSeeds[seedIndex]
            val unseededOpponent = unseeded.removeAt(0)

            // Match 2 * seedPairIndex (Seed vs Unseeded)
            pairings.add(
                MatchPairing(
                    round = round,
                    matchIndex = 2 * seedPairIndex,
                    player1Id = seededPlayer,
                    player2Id = unseededOpponent
                )
            )

            // Match 2 * seedPairIndex + 1 (Unseeded vs Unseeded)
            val unseededPlayer1 = unseeded.removeAt(0)
            val unseededPlayer2 = unseeded.removeAt(0)
            pairings.add(
                MatchPairing(
                    round = round,
                    matchIndex = 2 * seedPairIndex + 1,
                    player1Id = unseededPlayer1,
                    player2Id = unseededPlayer2
                )
            )
        }
        return pairings.sortedBy { it.matchIndex }
    }

    /**
     * World Championship Runde 1 Paarungen (128 Spieler -> 64 Matches):
     * 32 Top-OOM, 32 OOM 33-64 und 64 Qualifier/verbleibende Spieler.
     */
    private fun generateWorldChampionshipRound1(
        top32Oom: List<Long>,
        ranks33To64Oom: List<Long>,
        remaining64: List<Long>
    ): List<MatchPairing> {
        require(top32Oom.size == 32 && ranks33To64Oom.size == 32 && remaining64.size == 64) {
            "World Championship R1 erfordert 32 Top-OOM, 32 OOM 33-64 und 64 verbleibende Spieler"
        }
        val seedOrder32 = buildStandardSeedOrder(32)
        val shuffledRanks33To64 = ranks33To64Oom.shuffled()
        val unseeded64 = remaining64.shuffled()

        val pairings = mutableListOf<MatchPairing>()
        for (seedPairIndex in 0 until 32) {
            val seedIndex = seedOrder32[seedPairIndex]
            val seededPlayer = top32Oom[seedIndex]
            pairings.add(
                MatchPairing(
                    round = TournamentRound.L128,
                    matchIndex = 2 * seedPairIndex,
                    player1Id = seededPlayer,
                    player2Id = unseeded64[2 * seedPairIndex]
                )
            )
            val player33To64 = shuffledRanks33To64[seedPairIndex]
            pairings.add(
                MatchPairing(
                    round = TournamentRound.L128,
                    matchIndex = 2 * seedPairIndex + 1,
                    player1Id = player33To64,
                    player2Id = unseeded64[2 * seedPairIndex + 1]
                )
            )
        }
        return pairings.sortedBy { it.matchIndex }
    }

    /**
     * Erzeugt die Erstrunden-Paarungen für Qualifier-Turniere über die Board-Setzliste.
     * Freilose (byePlayers) und Vorrundensieger (prelimWinners) werden getrennt übergeben.
     * Die Top 2 · K Seeds behalten ihre festen Positionen; alle übrigen Freilos-Spieler und
     * Vorrundensieger werden frei gemischt auf die freien Slots jedes Boards verteilt.
     *
     * @param boardCount Anzahl der Boards (K)
     * @param boardSizePerBoard Anzahl Startplätze pro Board in QUAL_R1 (8, 16 oder 32)
     * @param byePlayers Gesetzte Freilos-Spieler, sortiert
     * @param prelimWinners Sieger der Vorrunde
     */
    fun buildQualifierRound1Pairings(
        boardCount: Int,
        boardSizePerBoard: Int,
        byePlayers: List<Long>,
        prelimWinners: List<Long>
    ): List<MatchPairing> {
        require(byePlayers.size >= 2 * boardCount) {
            "Qualifier R1 erfordert mindestens ${2 * boardCount} gesetzte Freilos-Spieler"
        }
        val r1MatchesPerBoard = boardSizePerBoard / 2
        val expectedFreeSlots = boardCount * (boardSizePerBoard - 2)
        val unseededByes = byePlayers.drop(2 * boardCount)
        val freePool = (unseededByes + prelimWinners).shuffled()
        require(freePool.size == expectedFreeSlots) {
            "Qualifier R1 erfordert genau $expectedFreeSlots freie Spieler, erhalten: ${freePool.size}"
        }

        val pairings = mutableListOf<MatchPairing>()
        for (boardIndex in 0 until boardCount) {
            val seedTop = byePlayers[boardIndex]
            val seedBottom = byePlayers[2 * boardCount - 1 - boardIndex]
            val slots = LongArray(boardSizePerBoard - 2) { slotIndex -> freePool[boardIndex * (boardSizePerBoard - 2) + slotIndex] }

            val boardBaseMatchIndex = r1MatchesPerBoard * boardIndex
            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = boardBaseMatchIndex + 0, player1Id = seedTop, player2Id = slots[0]))

            var slotIdx = 1
            for (mInBoard in 1 until r1MatchesPerBoard - 1) {
                pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = boardBaseMatchIndex + mInBoard, player1Id = slots[slotIdx++], player2Id = slots[slotIdx++]))
            }

            pairings.add(MatchPairing(round = TournamentRound.QUAL_R1, matchIndex = boardBaseMatchIndex + (r1MatchesPerBoard - 1), player1Id = slots[slotIdx], player2Id = seedBottom))
        }
        return pairings
    }

    /**
     * Erzeugt die Initial-Match-Paarungen für Qualifier-Turniere mit dynamischer Board-Größe (8, 16, 32).
     */
    fun generateQualifierInitialMatches(
        event: TournamentEvent,
        oomSortedIds: List<Long>,
        dynamicBoardCount: Int? = null
    ): List<MatchPairing> {
        val boardCount = dynamicBoardCount ?: when (event) {
            TournamentEvent.QUALIFIER_ET -> 10
            TournamentEvent.QUALIFIER_GRAND_SLAM -> 8
            TournamentEvent.QUALIFIER_WM -> 2
            else -> 8
        }
        require(boardCount > 0) { "Kein Qualifier-Event oder ungültiger boardCount: $event" }

        val totalNonPrequalified = oomSortedIds.size
        if (totalNonPrequalified == 0) return emptyList()

        val minSlotsPerBoard = ceil(totalNonPrequalified.toDouble() / (2.0 * boardCount)).toInt().coerceAtLeast(8)
        var boardSize = 8
        while (boardSize < minSlotsPerBoard) {
            boardSize *= 2
        }
        val totalR1Slots = boardSize * boardCount

        val prelimPlayers = if (totalNonPrequalified > totalR1Slots) {
            oomSortedIds.takeLast(2 * (totalNonPrequalified - totalR1Slots))
        } else {
            emptyList()
        }

        val byePlayers = if (totalNonPrequalified > totalR1Slots) {
            oomSortedIds.take((2 * totalR1Slots - totalNonPrequalified).coerceAtLeast(0))
        } else {
            oomSortedIds
        }

        val prelimMatches = mutableListOf<MatchPairing>()
        val r1Pairings = if (prelimPlayers.isNotEmpty()) {
            val prelimMatchCount = prelimPlayers.size / 2
            for (matchIndex in 0 until prelimMatchCount) {
                prelimMatches.add(
                    MatchPairing(
                        round = TournamentRound.QUAL_PRELIM,
                        matchIndex = matchIndex,
                        player1Id = prelimPlayers[matchIndex],
                        player2Id = prelimPlayers[2 * prelimMatchCount - 1 - matchIndex]
                    )
                )
            }
            val prelimPlaceholders = (0 until prelimMatchCount).map { matchIndex -> -1000L - matchIndex }
            buildQualifierRound1Pairings(boardCount, boardSize, byePlayers, prelimPlaceholders)
        } else {
            buildQualifierRound1Pairings(boardCount, boardSize, byePlayers, emptyList())
        }
        return prelimMatches + r1Pairings
    }
}
