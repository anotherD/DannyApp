package com.danny.dannyapp.domain.model

enum class TournamentEvent(val totalPrizeFund: Double) {
    PLAYERS_CHAMPIONSHIP(150_000.0),
    EUROPEAN_TOUR(230_000.0),
    UK_OPEN(750_000.0),
    EUROPEAN_OPEN(750_000.0),
    WORLD_MATCHPLAY(1_000_000.0),
    WORLD_GRAND_PRIX(750_000.0),
    EUROPEAN_CHAMPIONSHIP(750_000.0),
    GRAND_SLAM_OF_DARTS(1_000_000.0),
    PLAYERS_CHAMPIONSHIP_FINALS(750_000.0),
    WORLD_CHAMPIONSHIP(5_000_000.0),
    QUALIFIER_ET(0.0),
    QUALIFIER_GRAND_SLAM(0.0);

    /**
     * Liefert die offizielle Match-Distanz (Best-of-Legs oder Best-of-Sets) für die jeweilige Runde.
     */
    fun getMatchDistance(round: TournamentRound): MatchDistance {
        return when (this) {
            PLAYERS_CHAMPIONSHIP, EUROPEAN_TOUR, QUALIFIER_ET -> when (round) {
                TournamentRound.SF -> MatchDistance(13, MatchFormatType.LEGS, 1)
                TournamentRound.F -> MatchDistance(15, MatchFormatType.LEGS, 1)
                else -> MatchDistance(11, MatchFormatType.LEGS, 1) // L128/L48 bis QF
            }
            UK_OPEN, EUROPEAN_OPEN -> when (round) {
                TournamentRound.R1_160,
                TournamentRound.R2_128,
                TournamentRound.R3_96 -> MatchDistance(11, MatchFormatType.LEGS, 1)
                TournamentRound.SF,
                TournamentRound.F -> MatchDistance(21, MatchFormatType.LEGS, 1)
                else -> MatchDistance(19, MatchFormatType.LEGS, 1) // L64 bis QF
            }
            WORLD_MATCHPLAY -> when (round) {
                TournamentRound.L32 -> MatchDistance(19, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
                TournamentRound.L16 -> MatchDistance(21, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
                TournamentRound.QF -> MatchDistance(31, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
                TournamentRound.SF -> MatchDistance(33, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
                TournamentRound.F -> MatchDistance(35, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
                else -> MatchDistance(19, MatchFormatType.LEGS, 1, twoClearLegsRule = true)
            }
            WORLD_GRAND_PRIX -> when (round) {
                TournamentRound.L32 -> MatchDistance(3, MatchFormatType.SETS_DOUBLE_IN_OUT, 3, twoClearLegsRule = true)
                TournamentRound.L16,
                TournamentRound.QF -> MatchDistance(5, MatchFormatType.SETS_DOUBLE_IN_OUT, 3, twoClearLegsRule = true)
                TournamentRound.SF -> MatchDistance(9, MatchFormatType.SETS_DOUBLE_IN_OUT, 3, twoClearLegsRule = true)
                TournamentRound.F -> MatchDistance(11, MatchFormatType.SETS_DOUBLE_IN_OUT, 3, twoClearLegsRule = true)
                else -> MatchDistance(3, MatchFormatType.SETS_DOUBLE_IN_OUT, 3, twoClearLegsRule = true)
            }
            EUROPEAN_CHAMPIONSHIP, PLAYERS_CHAMPIONSHIP_FINALS -> when (round) {
                TournamentRound.L64,
                TournamentRound.L32 -> MatchDistance(11, MatchFormatType.LEGS, 1)
                TournamentRound.SF,
                TournamentRound.F -> MatchDistance(21, MatchFormatType.LEGS, 1)
                else -> MatchDistance(19, MatchFormatType.LEGS, 1) // L16 & QF
            }
            GRAND_SLAM_OF_DARTS, QUALIFIER_GRAND_SLAM -> when (round) {
                TournamentRound.GROUP -> MatchDistance(9, MatchFormatType.LEGS, 1)
                TournamentRound.L16 -> MatchDistance(19, MatchFormatType.LEGS, 1)
                else -> MatchDistance(31, MatchFormatType.LEGS, 1) // QF, SF, F
            }
            WORLD_CHAMPIONSHIP -> when (round) {
                TournamentRound.L128,
                TournamentRound.L64 -> MatchDistance(5, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
                TournamentRound.L32,
                TournamentRound.L16 -> MatchDistance(7, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
                TournamentRound.QF -> MatchDistance(9, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
                TournamentRound.SF -> MatchDistance(11, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
                TournamentRound.F -> MatchDistance(13, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
                else -> MatchDistance(5, MatchFormatType.SETS_BEST_OF_5_LEGS, 5, twoClearLegsRule = true)
            }
        }
    }

    /**
     * Gibt das für die Order of Merit gewertete Preisgeld zurück.
     * @param round Die erreichte Runde (bzw. Ausscheiderunde)
     * @param isPreQualifiedSeedRelevant True, wenn der Spieler auf der European Tour vorqualifiziert war (Top 16 OOM / Top 16 PT OOM)
     * @param lostFirstMatch True, wenn der Spieler sein allererstes Match im Turnier verloren hat
     */
    fun getRankingPrizeMoney(
        round: TournamentRound,
        isPreQualifiedSeedRelevant: Boolean = false,
        lostFirstMatch: Boolean = false
    ): Double {
        // Sonderregel European Tour (PDC Rule 4.5):
        // Vorqualifizierte Spieler erhalten bei Auftaktniederlage £0 für alle Ranglisten
        if (this == EUROPEAN_TOUR && isPreQualifiedSeedRelevant && lostFirstMatch) {
            return 0.0
        }

        return when (this) {
            PLAYERS_CHAMPIONSHIP -> when (round) {
                TournamentRound.WINNER -> 15_000.0
                TournamentRound.F -> 10_000.0
                TournamentRound.SF -> 6_500.0
                TournamentRound.QF -> 4_000.0
                TournamentRound.L16 -> 3_000.0
                TournamentRound.L32 -> 2_000.0
                TournamentRound.L64 -> 1_250.0
                TournamentRound.L128 -> 0.0
                else -> 0.0
            }
            EUROPEAN_TOUR -> when (round) {
                TournamentRound.WINNER -> 35_000.0
                TournamentRound.F -> 15_000.0
                TournamentRound.SF -> 10_000.0
                TournamentRound.QF -> 8_000.0
                TournamentRound.L16 -> 5_000.0
                TournamentRound.L32 -> 3_500.0
                TournamentRound.L48 -> 2_000.0
                else -> 0.0
            }
            UK_OPEN, EUROPEAN_OPEN -> when (round) {
                TournamentRound.WINNER -> 120_000.0
                TournamentRound.F -> 60_000.0
                TournamentRound.SF -> 35_000.0
                TournamentRound.QF -> 20_000.0
                TournamentRound.L16 -> 12_500.0
                TournamentRound.L32 -> 7_500.0
                TournamentRound.L64 -> 3_000.0
                TournamentRound.R3_96 -> 2_000.0
                TournamentRound.R2_128 -> 1_250.0
                TournamentRound.R1_160 -> 0.0
                else -> 0.0
            }
            WORLD_MATCHPLAY -> when (round) {
                TournamentRound.WINNER -> 225_000.0
                TournamentRound.F -> 125_000.0
                TournamentRound.SF -> 65_000.0
                TournamentRound.QF -> 35_000.0
                TournamentRound.L16 -> 22_500.0
                TournamentRound.L32 -> 12_500.0
                else -> 0.0
            }
            WORLD_GRAND_PRIX, EUROPEAN_CHAMPIONSHIP -> when (round) {
                TournamentRound.WINNER -> 150_000.0
                TournamentRound.F -> 80_000.0
                TournamentRound.SF -> 50_000.0
                TournamentRound.QF -> 35_000.0
                TournamentRound.L16 -> 20_000.0
                TournamentRound.L32 -> 7_500.0
                else -> 0.0
            }
            GRAND_SLAM_OF_DARTS -> when (round) {
                TournamentRound.WINNER -> 200_000.0
                TournamentRound.F -> 100_000.0
                TournamentRound.SF -> 60_000.0
                TournamentRound.QF -> 35_000.0
                TournamentRound.L16 -> 20_000.0
                TournamentRound.GROUP_2ND -> 12_500.0
                TournamentRound.GROUP_3RD -> 5_000.0
                else -> 0.0
            }
            PLAYERS_CHAMPIONSHIP_FINALS -> when (round) {
                TournamentRound.WINNER -> 130_000.0
                TournamentRound.F -> 70_000.0
                TournamentRound.SF -> 40_000.0
                TournamentRound.QF -> 27_500.0
                TournamentRound.L16 -> 15_000.0
                TournamentRound.L32 -> 7_000.0
                TournamentRound.L64 -> 4_000.0
                else -> 0.0
            }
            WORLD_CHAMPIONSHIP -> when (round) {
                TournamentRound.WINNER -> 1_000_000.0
                TournamentRound.F -> 400_000.0
                TournamentRound.SF -> 200_000.0
                TournamentRound.QF -> 100_000.0
                TournamentRound.L16 -> 60_000.0
                TournamentRound.L32 -> 35_000.0
                TournamentRound.L64 -> 25_000.0
                TournamentRound.L128 -> 15_000.0
                else -> 0.0
            }
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM -> 0.0
        }
    }
}
