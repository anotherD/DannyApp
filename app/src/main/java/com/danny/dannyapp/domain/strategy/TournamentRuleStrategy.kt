package com.danny.dannyapp.domain.strategy

import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.usecase.QualifierFieldSplit
import com.danny.dannyapp.domain.usecase.TournamentQualificationResult

/**
 * Interface für karten- und turnierspezifische Regel-Strategien.
 * Kapselt die Logik zur Qualifikationsübernahme, Preisgeldberechtigung und Match-Generierung.
 */
interface TournamentRuleStrategy {
    /** Der Turnierevent-Typ, für den diese Strategie gilt. */
    val event: TournamentEvent


    /**
     * Gibt an, ob dieses Turnier Qualifier-Sieger aus einem vorherigen Qualifier-Turnier übernimmt.
     */
    val usesPrecedingQualifier: Boolean get() = false

    /**
     * Gibt an, ob für dieses Turnier Preisgeld und Platzierungen vergeben werden.
     * Standardmäßig true für Hauptturniere (`!event.isQualifier`).
     */
    val awardsPrizeMoneyAndPlacements: Boolean get() = !event.isQualifier

    /**
     * Erstellt die initialen Turnier-Matches basierend auf den Qualifikationsergebnissen.
     *
     * @param tournamentId Die ID des Turniers.
     * @param qualResult Das struktuierte Qualifikationsergebnis (Single Source of Truth).
     * @param createQualifierMatches Helferfunktion zur Erstellung von Qualifier-Matches.
     * @return Liste der generierten [TournamentMatch]-Objekte.
     */
    fun buildInitialMatches(
        tournamentId: Long,
        qualResult: TournamentQualificationResult,
        createQualifierMatches: (Long, QualifierFieldSplit) -> Pair<List<TournamentMatch>, List<TournamentMatch>>
    ): List<TournamentMatch>
}
