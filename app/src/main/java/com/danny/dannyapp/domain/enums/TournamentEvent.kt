package com.danny.dannyapp.domain.enums

import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.model.MatchDistance

enum class TournamentEvent(val totalPrizeFund: Double) {
    // PDC World Masters: Vorrunden-Preisgelder (£60.000 der realen PDC für Vorrunden-Verlierer außerhalb des 32er-Hauptfelds) entfallen in der App.
    WORLD_MASTERS(440_000.0),
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
    QUALIFIER_WORLD_MASTERS(0.0),
    QUALIFIER_ET(0.0),
    QUALIFIER_GRAND_SLAM(0.0),
    QUALIFIER_WM(0.0);

    /** Prüft, ob es sich um ein Qualifier-Turnier handelt. */
    val isQualifier: Boolean
        get() = this == QUALIFIER_ET || this == QUALIFIER_GRAND_SLAM || this == QUALIFIER_WM || this == QUALIFIER_WORLD_MASTERS

    /** Liefert das zugehörige Hauptturnier, falls es sich um einen Qualifier handelt, sonst null. */
    val mainEventForQualifier: TournamentEvent?
        get() = when (this) {
            QUALIFIER_ET -> EUROPEAN_TOUR
            QUALIFIER_GRAND_SLAM -> GRAND_SLAM_OF_DARTS
            QUALIFIER_WM -> WORLD_CHAMPIONSHIP
            QUALIFIER_WORLD_MASTERS -> WORLD_MASTERS
            else -> null
        }

    /** Liefert die Soll-Teilnehmerzahl für dieses Turnier-Event. */
    val targetParticipantCount: Int
        get() = when (this) {
            PLAYERS_CHAMPIONSHIP -> 128
            EUROPEAN_TOUR -> 48
            UK_OPEN, EUROPEAN_OPEN -> 160
            WORLD_MATCHPLAY, WORLD_GRAND_PRIX, EUROPEAN_CHAMPIONSHIP, WORLD_MASTERS -> 32
            GRAND_SLAM_OF_DARTS -> 48
            PLAYERS_CHAMPIONSHIP_FINALS -> 64
            WORLD_CHAMPIONSHIP -> 128
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM, QUALIFIER_WORLD_MASTERS -> 0
        }

    /** Liefert den zugehörigen Qualifier, falls es sich um ein Hauptturnier mit Qualifier handelt, sonst null. */
    val qualifierEventForMain: TournamentEvent?
        get() = when (this) {
            WORLD_MASTERS -> QUALIFIER_WORLD_MASTERS
            EUROPEAN_TOUR -> QUALIFIER_ET
            GRAND_SLAM_OF_DARTS -> QUALIFIER_GRAND_SLAM
            WORLD_CHAMPIONSHIP -> QUALIFIER_WM
            else -> null
        }

    /** Prüft, ob es sich um ein Major-Turnier handelt. */
    val isMajor: Boolean
        get() = this in listOf(
            WORLD_MASTERS, UK_OPEN, EUROPEAN_OPEN, WORLD_MATCHPLAY, WORLD_GRAND_PRIX,
            EUROPEAN_CHAMPIONSHIP, GRAND_SLAM_OF_DARTS, PLAYERS_CHAMPIONSHIP_FINALS,
            WORLD_CHAMPIONSHIP
        )

    /**
     * Liefert die Runden-Sequenz für Qualifier basierend auf der Anzahl der Erstrunden-Matches pro Board.
     * - r1MatchesPerBoard <= 4: QUAL_PRELIM, QUAL_R1, QUAL_R2, QUAL_FINAL
     * - r1MatchesPerBoard == 8: QUAL_PRELIM, QUAL_R1, QUAL_R2, QUAL_R3, QUAL_FINAL
     * - r1MatchesPerBoard >= 16: QUAL_PRELIM, QUAL_R1, QUAL_R2, QUAL_R3, QUAL_FINAL (max. bis QUAL_R3 = 32er-Board)
     */
    fun getQualifierRoundSequence(r1MatchesPerBoard: Int): List<TournamentRound> {
        return when {
            r1MatchesPerBoard <= 4 -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_FINAL
            )
            else -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_R3,
                TournamentRound.QUAL_FINAL
            )
        }
    }

    /**
     * Liefert die zentrale Rundenfolge für dieses Turnier (identisch zu
     * `TournamentPreviewHelper.getRoundSequence`), damit Use-Cases und Validator
     * dieselbe Reihenfolge nutzen.
     */
    fun getRoundSequence(): List<TournamentRound> {
        return when (this) {
            QUALIFIER_ET, QUALIFIER_GRAND_SLAM, QUALIFIER_WM, QUALIFIER_WORLD_MASTERS -> listOf(
                TournamentRound.QUAL_PRELIM,
                TournamentRound.QUAL_R1,
                TournamentRound.QUAL_R2,
                TournamentRound.QUAL_R3,
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
            WORLD_MATCHPLAY, WORLD_GRAND_PRIX, EUROPEAN_CHAMPIONSHIP, WORLD_MASTERS -> listOf(
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
     * Liefert die offizielle Match-Distanz (Best-of-Legs oder Best-of-Sets) für die jeweilige Runde,
     * skaliert um das optionale [preset].
     * Werte werden zentral aus der JSON-Konfiguration gelesen (Fail-Fast).
     */
    fun getMatchDistance(
        round: TournamentRound,
        preset: MatchDistancePreset = MatchDistancePreset.DEFAULT
    ): MatchDistance {
        val config = TournamentConfigLoader.safeConfig()
            ?: error("TournamentConfigLoader wurde nicht initialisiert")
        val eventConfig = config.events[this]
            ?: error("Keine Event-Konfiguration für $this gefunden")
        val baseDistance = eventConfig.distances[round]
            ?: error("Keine Match-Distanz für Runde $round in Event $this gefunden")
        return preset.scaleDistance(baseDistance)
    }

    /**
     * Gibt das für die Order of Merit gewertete Preisgeld zurück.
     * Werte werden zentral aus der JSON-Konfiguration gelesen (Fail-Fast).
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
        if (this.isQualifier) {
            return 0.0
        }

        val config = TournamentConfigLoader.safeConfig()
            ?: error("TournamentConfigLoader wurde nicht initialisiert")
        val eventConfig = config.events[this]
            ?: error("Keine Event-Konfiguration für $this gefunden")
        return eventConfig.prizes[round]
            ?: error("Kein Preisgeld für Runde $round in Event $this gefunden")
    }

    /**
     * Prüft, ob dieses Turnier eine offene Runden-Auslosung besitzt (UK Open oder European Open)
     * und die übergebene Runde eine der offen ausgelosten Runden ist.
     */
    fun isOpenDrawRound(round: TournamentRound): Boolean {
        return !(this != UK_OPEN && this != EUROPEAN_OPEN) && round in listOf(
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
