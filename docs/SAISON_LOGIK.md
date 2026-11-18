# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons, Tour Cards sowie der Turnierbaum- und Fortschrittssteuerung im Karrieremodus.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder** in der Tabelle `tour_cards`:
* **1 Tour Card für den menschlichen User:** `playerId = USER_ID (-1L)`, `tourCardYear = 2` (fest vorgegeben).
* **127 Tour Cards für CPU-Spieler:** `playerId` verweist auf `players.id`, `tourCardYear` ist `1` oder `2`.

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert (`SeasonRepository`). Die 127 CPU-Tourcards werden wie folgt ermittelt:
1. Alle CPU-Spieler aus `players` mit `tour_card_year > 0` werden geladen und sortiert nach `skill DESC, prizeMoney DESC, name ASC`.
2. **Mehr als 127 Kandidaten (`> 127`):** Nur die besten 127 Kandidaten erhalten eine Tour Card; schwächere fallen heraus.
3. **Weniger als 127 Kandidaten (`< 127`):** Alle Kandidaten übernehmen ihre Tour Card; die Differenz wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) nach Skill aufgefüllt (`tourCardYear = 2`).

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Beim Saisonwechsel wird die bisherige Saison als abgeschlossen markiert (`isCompleted = true`) und eine neue Saison (`seasonNumber + 1`) angelegt:
* **Geplante Order-of-Merit-Logik:** Spieler im 1. Jahr steigen ins 2. Jahr auf (`tourCardYear = 2`). Spieler im 2. Jahr verlieren ihre Card, sofern sie nicht in den Top 64 der 2-Jahres-Order-of-Merit stehen. Freie Plätze werden über die Q-School aufgefüllt (`tourCardYear = 1`).
* **Aktuelle Übergangslogik:** Nutzt dieselbe Übernahme- und Auffülllogik wie Saison 1.

---

## 🏆 4. Turnierteilnahmen & Entkopplung der Match-Zuweisung

* **Entkopplung in `tournament_matches`:** In `player_1_id` und `player_2_id` werden direkt die Stamm-IDs aus `players` (bzw. `USER_ID = -1L`) gespeichert. Turnierteilnehmer müssen nicht zwingend in `tour_cards` existieren (z. B. Nicht-Tourcard-Qualifier bei Majors oder European Tour).
* **Teilnehmerpools & Qualifikation:** Die exakten Teilnehmerzahlen und Qualifikationswege aller Turniertypen (128er-PC, 48er ET, 160er UK/European Open usw.) sind in [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md) definiert.
* **Preisgelder:** Werden pro Turnier in `tournament_placements` erfasst und verweisen direkt auf `players.id` bzw. `USER_ID = -1L`.

---

## 🌳 5. Persistente Turnierbaum-Speicherung & Fortschrittslogik

* **Vollständige Baum-Generierung (`GenerateBracketUseCase`):**
  * Für alle Turniere mit festem Turnierbaum werden beim Turnierstart bereits **alle Runden** (von Runde 1 bis Finale bzw. Gruppenphase bis Finale) in `tournament_matches` angelegt.
  * Noch nicht feststehende Paarungen nutzen Platzhalter-IDs (`PLACEHOLDER_PREVIOUS_WINNER_ID = -999L`, Gruppen-Sieger-IDs oder Vorrunden-Platzhalter).
  * `UK_OPEN` und `EUROPEAN_OPEN` nutzen ab Runde 2 eine offene Runden-für-Runden-Auslosung.
  * **Persistente Startrunde:** Unmittelbar nach der Bracket-Erzeugung wird die erste tatsächlich angelegte Runde als aktive Startrunde direkt am Turnier gespeichert.
  * **Gewinner-basiertes Preisgeld-Modell:** Alle qualifizierten Teilnehmer erhalten ihr garantiertes Einstiegspreisgeld (inkl. gestaffelt einsteigender gesetzter Spieler bei `UK_OPEN` und `EUROPEAN_OPEN`, aber unter Beachtung der £0-Auftaktniederlagen-Regel für vorqualifizierte European-Tour-Seeds) **sofort beim Turnierstart** gutgeschrieben.
* **Sofortige Gewinner-Hochstufung (`propagateWinnerOnMatchCompleted`):**
  * Sobald ein Match abgeschlossen ist, wird der Gewinner sofort in den entsprechenden Slot der Folgerunde eingetragen und im Preisgeld-Placement unmittelbar auf die erreichte Folgerunde hochgestuft (wodurch z. B. vorqualifizierte ET-Seeds nach ihrem Auftaktsieg automatisch von £0 auf das reguläre Rundenpreisgeld steigen).
  * Beim *Grand Slam of Darts* werden nach Abschluss der Gruppenphase Gruppenzweite und Gruppensieger automatisch auf ihre entsprechenden Platzierungs-Preisgelder hochgestuft.
* **Zentrale Zustandsmaschine & Rundenfortschritt (`AdvanceTournamentRoundUseCase`):**
  * Ermittelt über `evaluateTournamentState(...)` als *Single Source of Truth* den Rundenstatus: Die **aktive Runde wird vorrangig direkt vom Turnier selbst gelesen** (dort bei jedem Rundenwechsel fortgeschrieben); für ältere Datensätze ohne gespeicherte Runde greift ein Fallback auf die erste Runde mit offenen Matches.
  * Alle Runden vor der aktiven Runde gelten als abgeschlossen, alle danach als ausstehend – einheitlich für alle Turnierarten inkl. Qualifier, ohne gesonderte Sonderbehandlung.
  * Die Preisgelder jeder abgeschlossenen Runde werden **sofort** beim Rundenwechsel verbucht; nach dem Finale wird das Sieger-Preisgeld eingetragen, das Turnier als abgeschlossen markiert und die aktive Runde am Turnier gelöscht.
  * Bei Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) wird das Turnier nach Abschluss des Qualifikationsfinales ohne Preisgeldbuchung beendet.
  * Das Weiterschalten erfolgt entweder manuell nach Abschluss aller Matches einer Runde oder bei aktivem Schnellfortschritt automatisch.
* **Zentrale Turniersimulation (`TournamentProgressionUseCase`):**
  * Steuert über `simulateCompleteTournament(...)` die komplette Turniersimulation zentral von der Bracket-Erstellung über realistische CPU- und User-Matches bis zum Turnierabschluss.
