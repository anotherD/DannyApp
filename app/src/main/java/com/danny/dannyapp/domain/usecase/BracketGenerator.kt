package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.PLACEHOLDER_OPEN_DRAW_ID
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
            TournamentEvent.WORLD_MASTERS -> generateWorldMastersRound1(
                top16Oom = oomSortedIds.take(16),
                unseeded16 = (oomSortedIds.drop(16).take(8) + qualifierIds).take(16)
            )
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
            TournamentEvent.QUALIFIER_WM,
            TournamentEvent.QUALIFIER_WORLD_MASTERS -> generateQualifierInitialMatches(
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
     * World Masters R1:
     * Top 16 OOM sind im Turnierbaum gesetzt (1..16).
     * Die 16 ungesetzten Spieler (OOM 17-24 + 8 Qualifier-Sieger) werden ihnen in Runde 1 frei zugelost.
     */
    private fun generateWorldMastersRound1(
        top16Oom: List<Long>,
        unseeded16: List<Long>
    ): List<MatchPairing> {
        require(top16Oom.size == 16 && unseeded16.size == 16) {
            "World Masters R1 erfordert 16 Top-OOM-Seeds und 16 ungesetzte Spieler"
        }
        val shuffledUnseeded = unseeded16.shuffled()
        return BRACKET_SEED_ORDER_16.mapIndexed { matchIndex, seedIndex ->
            MatchPairing(
                round = TournamentRound.L32,
                matchIndex = matchIndex,
                player1Id = top16Oom[seedIndex],
                player2Id = shuffledUnseeded[matchIndex]
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
     * Verteilt Freilose gleichmäßig auf alle Boards, priorisiert gesetzte Spieler (Seed Top/Bottom)
     * und stellt sicher, dass niemals zwei Freilose gegeneinander gepaart werden.
     *
     * @param boardCount Anzahl der Boards (K)
     * @param boardSizePerBoard Anzahl Startplätze pro Board in QUAL_R1 (8, 16 oder 32)
     * @param rankedPlayerIds Qualifizierte Tour-Card-Spieler sortiert nach OOM (ohne Placeholder-Padding)
     */
    fun buildQualifierRound1Pairings(
        boardCount: Int,
        boardSizePerBoard: Int,
        rankedPlayerIds: List<Long>
    ): List<MatchPairing> {
        require(boardCount > 0)
        require(boardSizePerBoard >= 8 && (boardSizePerBoard and (boardSizePerBoard - 1)) == 0)
        val totalR1Slots = boardCount * boardSizePerBoard
        val totalByes = totalR1Slots - rankedPlayerIds.size
        require(totalByes in 0 until (totalR1Slots / 2))
        require(rankedPlayerIds.size >= 2 * boardCount)

        val r1MatchesPerBoard = boardSizePerBoard / 2
        val baseByesPerBoard = totalByes / boardCount
        val extraByeCount = totalByes % boardCount
        val unseededPool = rankedPlayerIds.drop(2 * boardCount).shuffled().toMutableList()

        val pairings = mutableListOf<MatchPairing>()

        for (boardIndex in 0 until boardCount) {
            val extraBye = if (boardIndex < extraByeCount) 1 else 0
            val byesOnBoard = baseByesPerBoard + extraBye
            val seedTop = rankedPlayerIds[boardIndex]
            val seedBottom = rankedPlayerIds[2 * boardCount - 1 - boardIndex]

            val unseededCountForBoard = boardSizePerBoard - 2 - byesOnBoard
            val boardUnseeded = mutableListOf<Long>()
            repeat(unseededCountForBoard) {
                boardUnseeded.add(unseededPool.removeAt(0))
            }

            val boardBaseMatchIndex = r1MatchesPerBoard * boardIndex

            val matchZeroPlayer2Id = if (byesOnBoard >= 1) {
                PLACEHOLDER_OPEN_DRAW_ID
            } else {
                boardUnseeded.removeAt(0)
            }
            pairings.add(
                MatchPairing(
                    round = TournamentRound.QUAL_R1,
                    matchIndex = boardBaseMatchIndex,
                    player1Id = seedTop,
                    player2Id = matchZeroPlayer2Id
                )
            )

            val remainingByes = (byesOnBoard - 2).coerceAtLeast(0)
            val middleMatches = (1 until (r1MatchesPerBoard - 1)).toList()
            val byeMiddleMatches = middleMatches.shuffled().take(remainingByes).toSet()

            for (matchIndexInBoard in 1 until (r1MatchesPerBoard - 1)) {
                val currentMatchIndex = boardBaseMatchIndex + matchIndexInBoard
                if (matchIndexInBoard in byeMiddleMatches) {
                    val middlePlayer1Id = boardUnseeded.removeAt(0)
                    pairings.add(
                        MatchPairing(
                            round = TournamentRound.QUAL_R1,
                            matchIndex = currentMatchIndex,
                            player1Id = middlePlayer1Id,
                            player2Id = PLACEHOLDER_OPEN_DRAW_ID
                        )
                    )
                } else {
                    val middlePlayer1Id = boardUnseeded.removeAt(0)
                    val middlePlayer2Id = boardUnseeded.removeAt(0)
                    pairings.add(
                        MatchPairing(
                            round = TournamentRound.QUAL_R1,
                            matchIndex = currentMatchIndex,
                            player1Id = middlePlayer1Id,
                            player2Id = middlePlayer2Id
                        )
                    )
                }
            }

            val matchLastPlayer1Id = if (byesOnBoard >= 2) {
                PLACEHOLDER_OPEN_DRAW_ID
            } else {
                boardUnseeded.removeAt(0)
            }
            pairings.add(
                MatchPairing(
                    round = TournamentRound.QUAL_R1,
                    matchIndex = boardBaseMatchIndex + (r1MatchesPerBoard - 1),
                    player1Id = matchLastPlayer1Id,
                    player2Id = seedBottom
                )
            )
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
            TournamentEvent.QUALIFIER_GRAND_SLAM, TournamentEvent.QUALIFIER_WORLD_MASTERS -> 8
            TournamentEvent.QUALIFIER_WM -> 2
            else -> 8
        }
        require(boardCount > 0) { "Kein Qualifier-Event oder ungültiger boardCount: $event" }

        val totalNonPrequalified = oomSortedIds.size
        if (totalNonPrequalified == 0) return emptyList()

        val minSlotsPerBoard = ceil(totalNonPrequalified.toDouble() / boardCount.toDouble()).toInt().coerceAtLeast(8)
        var boardSize = 8
        while (boardSize < minSlotsPerBoard) {
            boardSize *= 2
        }

        return buildQualifierRound1Pairings(boardCount, boardSize, oomSortedIds)
    }
}
