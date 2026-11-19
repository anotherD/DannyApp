package com.danny.dannyapp.domain.enums

import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.model.MatchDistance

enum class TournamentEvent(val totalPrizeFund: Double) {
    PLAYERS_CHAMPIONSHIP(150_000.0),
    EUROPEAN_TOUR(230_000.0),
    UK_OPEN(750_000.0),
    EUROPEAN_OPEN(750_000.0),
    WORLD_MATCHPLAY(1_000_000.0),
    WORLD_GRAND_PRIX(750_000.0),
    // 2027-Update angekündigt (PDC 25.09.2026): Erweiterung auf 48 Spieler und £1.000.000 Preisgeld in Dortmund. Detaillierte Preisgeld-Aufschlüsselung pro Runde und Qualifikationsschlüssel sind von der PDC noch nicht veröffentlicht – daher vorerst 32er-Schema.
    EUROPEAN_CHAMPIONSHIP(750_000.0),
    GRAND_SLAM_OF_DARTS(1_000_000.0),
    // 2027-Update angekündigt (PDC 25.09.2026): Erweiterung auf 128 Spieler im Mehrbrett-Modus in Minehead. Detaillierte Preisgeldverteilung und Setzlisten-Regeln für 128 Spieler sind von der PDC noch nicht veröffentlicht – daher vorerst 64er-Schema.
    PLAYERS_CHAMPIONSHIP_FINALS(750_000.0),
    WORLD_CHAMPIONSHIP(5_000_000.0),
    QUALIFIER_ET(0.0),
    QUALIFIER_GRAND_SLAM(0.0),
    QUALIFIER_WM(0.0);

    /** Prüft, ob es sich um ein Qualifier-Turnier handelt. */
    val isQualifier: Boolean
        get() = this == QUALIFIER_ET || this == QUALIFIER_GRAND_SLAM || this == QUALIFIER_WM

    /** Liefert das zugehörige Hauptturnier, falls es sich um einen Qualifier handelt, sonst null. */
    val mainEventForQualifier: TournamentEvent?
        get() = when (this) {
            QUALIFIER_ET -> EUROPEAN_TOUR
            QUALIFIER_GRAND_SLAM -> GRAND_SLAM_OF_DARTS
            QUALIFIER_WM -> WORLD_CHAMPIONSHIP
            else -> null
        }

    /** Liefert die Soll-Teilnehmerzahl für dieses Turnier-Event. */
    val targetParticipantCount: Int
        get() = when (this) {
            PLAYERS_CHAMPIONSHIP -> 128
            EUROPEAN_TOUR -> 48
            UK_OPEN, EUROPEAN_OPEN -> 160
            WORLD_MATCHPLAY, WORLD_GRAND_PRIX, EUROPEAN_CHAMPIONSHIP -> 32
            GRAND_SLAM_OF_DARTS -> 48
            PLAYERS_CHAMPIONSHIP_FINALS -> 64
            WORLD_CHAMPIONSHIP -> 128
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM -> 0
        }

    /** Liefert den zugehörigen Qualifier, falls es sich um ein Hauptturnier mit Qualifier handelt, sonst null. */
    val qualifierEventForMain: TournamentEvent?
        get() = when (this) {
            EUROPEAN_TOUR -> QUALIFIER_ET
            GRAND_SLAM_OF_DARTS -> QUALIFIER_GRAND_SLAM
            WORLD_CHAMPIONSHIP -> QUALIFIER_WM
            else -> null
        }

    /**
     * Liefert die Anzahl der Qualifier-Boards für dieses Event
     * (QUALIFIER_ET → 10, QUALIFIER_GRAND_SLAM -> 8, QUALIFIER_WM -> 2, sonst 0).
     */
    val qualifierBoardCount: Int
        get() = when (this) {
            QUALIFIER_ET -> 10
            QUALIFIER_GRAND_SLAM -> 8
            QUALIFIER_WM -> 2
            else -> 0
        }

    /** Prüft, ob es sich um ein Major-Turnier handelt. */
    val isMajor: Boolean
        get() = this in listOf(
            UK_OPEN, EUROPEAN_OPEN, WORLD_MATCHPLAY, WORLD_GRAND_PRIX,
            EUROPEAN_CHAMPIONSHIP, GRAND_SLAM_OF_DARTS, PLAYERS_CHAMPIONSHIP_FINALS,
            WORLD_CHAMPIONSHIP
        )

    /**
     * Liefert die zentrale Rundenfolge für dieses Turnier (identisch zu
     * `TournamentPreviewHelper.getRoundSequence`), damit Use-Cases und Validator
     * dieselbe Reihenfolge nutzen.
     */
    fun getRoundSequence(): List<TournamentRound> {
        return when (this) {
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_FINAL
            )
            PLAYERS_CHAMPIONSHIP -> listOf(
                TournamentRound.L128, TournamentRound.L64, TournamentRound.L32,
                TournamentRound.L16, TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            EUROPEAN_TOUR -> listOf(
                TournamentRound.L48, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            UK_OPEN, EUROPEAN_OPEN -> listOf(
                TournamentRound.R1_160, TournamentRound.R2_128, TournamentRound.R3_96,
                TournamentRound.L64, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            WORLD_MATCHPLAY, WORLD_GRAND_PRIX, EUROPEAN_CHAMPIONSHIP -> listOf(
                TournamentRound.L32, TournamentRound.L16, TournamentRound.QF,
                TournamentRound.SF, TournamentRound.F
            )
            GRAND_SLAM_OF_DARTS -> listOf(
                TournamentRound.GROUP, TournamentRound.L16, TournamentRound.QF,
                TournamentRound.SF, TournamentRound.F
            )
            PLAYERS_CHAMPIONSHIP_FINALS -> listOf(
                TournamentRound.L64, TournamentRound.L32, TournamentRound.L16,
                TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
            WORLD_CHAMPIONSHIP -> listOf(
                TournamentRound.L128, TournamentRound.L64, TournamentRound.L32,
                TournamentRound.L16, TournamentRound.QF, TournamentRound.SF, TournamentRound.F
            )
        }
    }

    /**
     * Liefert den Gesamtpreisgeld-Topf aus der zentralen JSON-Konfiguration.
     * Fallback auf den eingebauten Wert, falls die Konfiguration noch nicht geladen wurde.
     */
    val effectiveTotalPrizeFund: Double
        get() = TournamentConfigLoader.safeConfig()
            ?.events?.get(this)?.totalPrizeFund ?: totalPrizeFund

    /**
     * Liefert die offizielle Match-Distanz (Best-of-Legs oder Best-of-Sets) für die jeweilige Runde.
     * Werte werden zentral aus der JSON-Konfiguration gelesen; bei fehlender Konfiguration
     * greift des eingebauten Fallbackwertes.
     */
    fun getMatchDistance(round: TournamentRound): MatchDistance {
        val configured = TournamentConfigLoader.safeConfig()
            ?.events?.get(this)?.distances?.get(round)
        if (configured != null) return configured

        return when (this) {
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM -> MatchDistance(11, MatchFormatType.LEGS, 1)
            PLAYERS_CHAMPIONSHIP, EUROPEAN_TOUR -> when (round) {
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
                TournamentRound.L32 -> MatchDistance(3, MatchFormatType.SETS_DOUBLE_IN_OUT, 5, twoClearLegsRule = true)
                TournamentRound.L16,
                TournamentRound.QF -> MatchDistance(5, MatchFormatType.SETS_DOUBLE_IN_OUT, 5, twoClearLegsRule = true)
                TournamentRound.SF -> MatchDistance(9, MatchFormatType.SETS_DOUBLE_IN_OUT, 5, twoClearLegsRule = true)
                TournamentRound.F -> MatchDistance(11, MatchFormatType.SETS_DOUBLE_IN_OUT, 5, twoClearLegsRule = true)
                else -> MatchDistance(3, MatchFormatType.SETS_DOUBLE_IN_OUT, 5, twoClearLegsRule = true)
            }
            EUROPEAN_CHAMPIONSHIP, PLAYERS_CHAMPIONSHIP_FINALS -> when (round) {
                TournamentRound.L64,
                TournamentRound.L32 -> MatchDistance(11, MatchFormatType.LEGS, 1)
                TournamentRound.SF,
                TournamentRound.F -> MatchDistance(21, MatchFormatType.LEGS, 1)
                else -> MatchDistance(19, MatchFormatType.LEGS, 1) // L16 & QF
            }
            GRAND_SLAM_OF_DARTS -> when (round) {
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
     * Werte werden zentral aus der JSON-Konfiguration gelesen; bei fehlender Konfiguration
     * greifen die eingebauten Fallback-Werte.
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

        val configured = TournamentConfigLoader.safeConfig()
            ?.events?.get(this)?.prizes?.get(round)
        if (configured != null) return configured

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
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM -> 0.0
        }
    }

    /**
     * Prüft, ob dieses Turnier eine offene Runden-Auslosung besitzt (UK Open oder European Open)
     * und die übergebene Runde eine der offen ausgelosten Runden ist.
     */
    fun isOpenDrawRound(round: TournamentRound): Boolean {
        if (this != UK_OPEN && this != EUROPEAN_OPEN) return false
        return round in listOf(
            TournamentRound.R2_128,
            TournamentRound.R3_96,
            TournamentRound.L64,
            TournamentRound.L32,
            TournamentRound.L16,
            TournamentRound.QF,
            TournamentRound.SF,
            TournamentRound.F
        )
    }

    /**
     * Ermittelt die Einstiegsrunde und das sofort garantierte Einstiegs-Ranglistenpreisgeld
     * für einen Teilnehmer beim Turnierstart anhand seines Qualifikationstyps.
     */
    fun resolveInitialPlacement(qualificationType: String?): Pair<TournamentRound, Double> {
        return when (this) {
            PLAYERS_CHAMPIONSHIP -> TournamentRound.L128 to 0.0
            EUROPEAN_TOUR -> when (qualificationType) {
                "OOM" -> TournamentRound.L32 to 0.0
                "PT_OOM" -> TournamentRound.L48 to 0.0
                else -> TournamentRound.L48 to getRankingPrizeMoney(TournamentRound.L48)
            }
            UK_OPEN, EUROPEAN_OPEN -> when (qualificationType) {
                "OOM_TOP_32" -> TournamentRound.L64 to getRankingPrizeMoney(TournamentRound.L64)
                "OOM_33_64" -> TournamentRound.R3_96 to getRankingPrizeMoney(TournamentRound.R3_96)
                "OOM_65_96" -> TournamentRound.R2_128 to getRankingPrizeMoney(TournamentRound.R2_128)
                else -> TournamentRound.R1_160 to getRankingPrizeMoney(TournamentRound.R1_160)
            }
            GRAND_SLAM_OF_DARTS -> TournamentRound.GROUP_3RD to getRankingPrizeMoney(TournamentRound.GROUP_3RD)
            else -> {
                val startRound = getRoundSequence().firstOrNull() ?: TournamentRound.L32
                startRound to getRankingPrizeMoney(startRound)
            }
        }
    }
}
