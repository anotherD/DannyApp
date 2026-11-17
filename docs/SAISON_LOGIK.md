# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons und Tour Cards im Karrieremodus. Es dient als zentrale Referenz für Entwickler und KI-Assistenten.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder**:
* **1 Tour Card für den menschlichen User:**
  - `playerId = USER_ID (-1L)` (repräsentiert den User der Karriere)
  - `tourCardYear = 2` (fest vorgegeben)
  - Computed Kotlin Property: `val isUser: Boolean get() = playerId == USER_ID`
* **127 Tour Cards für CPU-Spieler:**
  - `playerId` entspricht der ID aus der `players`-Tabelle.
  - `tourCardYear` ist `1` oder `2`.

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert (`SeasonRepository.createSeason(careerId)`). 

Die 127 CPU-Tourcards werden in folgenden Schritten ermittelt:

### Schritt 1: CPU-Kandidaten filtern & sortieren
Alle CPU-Spieler aus der `players`-Tabelle mit `tour_card_year > 0` werden geladen und sortiert nach:
1. `skill DESC` (Spielstärke 1–10)
2. `prizeMoney DESC`
3. `name ASC`

### Schritt 2: Vergabe & Ausgleich auf exakt 127 CPU-Plätze

```mermaid
flowchart TD
    A[Saison 1 erstellen] --> B[1 Tour Card fuer User anlegen\nplayerId=USER_ID (-1L), tourCardYear=2]
    B --> C[CPU-Spieler mit tour_card_year > 0 laden]
    C --> D{Anzahl CPU-Kandidaten?}

    D -- > 127 -- > E[Kuerzungslogik:\nTop 127 nehmen. Schwächste Kandidaten ab Platz 128 verfallen für diese Saison]
    D -- = 127 -- > F[Exakt 127 Kandidaten uebernehmen]
    D -- < 127 -- > G[Auffuelllogik:\nAlle Kandidaten nehmen + Differenz aus besten Nicht-Tourcard-Spielern nachrücken lassen (tourCardYear = 2)]

    E --> H[Exakt 128 TourCards in DB speichern]
    F --> H
    G --> H
```

* **Fall A: Mehr als 127 Kandidaten (> 127)**
  - Es werden nur die besten 127 Kandidaten übernommen.
  - Schwächere Tourcard-Spieler erhalten für diese Saison keine Tourcard.
* **Fall B: Exakt 127 Kandidaten (= 127)**
  - Alle 127 Kandidaten erhalten eine Tourcard.
* **Fall C: Weniger als 127 Kandidaten (< 127)**
  - Alle vorhandenen Kandidaten übernehmen ihre Tourcard.
  - Die fehlende Differenz ($127 - \text{Anzahl}$) wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) der `players`-Tabelle nach Skill aufgefüllt.
  - Diese nachgerückten Spieler erhalten für die Saison `tourCardYear = 2`.

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Beim Saisonwechsel (`createSeason(careerId)`):
1. Die bisherige Saison wird als abgeschlossen markiert (`isCompleted = true`).
2. Eine neue Saison mit `seasonNumber = currentSeason.seasonNumber + 1` wird erstellt.
3. **Zukunft (Order of Merit):** Die Tourcards werden anhand der 2-Jahres-Geldrangliste ermittelt:
   - Spieler im 1. Jahr steigen ins 2. Jahr auf (`tourCardYear = 2`).
   - Spieler im 2. Jahr verlieren ihre Card, außer sie stehen in den Top X der Order of Merit.
   - Nachrücker aus Q-School / Development Tour erhalten `tourCardYear = 1`.
4. **Vorübergehende Logik:** Gleiche Übernahme- & Auffülllogik wie in Saison 1.

---

## 🏆 4. Turnierteilnahmen & Entkopplung der Match-Zuweisung

* **Tabelle `tour_cards`:** Speichert **ausschließlich** die 128 aktiven Tourcard-Inhaber der jeweiligen Saison (inklusive `USER_ID = -1L`).
* **Entkopplung in `tournament_matches`:** In der Tabelle `tournament_matches` werden in `player_1_id` und `player_2_id` **direkt die Stamm-IDs** aus der Tabelle `players` (bzw. `USER_ID = -1L` für den menschlichen User) eingetragen. Die Match-Zuweisung ist somit **vollständig von der `tour_cards`-Tabelle entkoppelt**. Kein Turnierteilnehmer muss zwingend in `tour_cards` existieren.
* **128-Spieler Tour Card Turniere (z. B. Players Championship):** Das Teilnehmerfeld (128 Spieler) setzt sich zu 100 % aus den aktiven Tourcard-Holdern der aktuellen Saison zusammen.
* **160-Spieler Turniere (UK Open & PDC European Open):**
  - **128 Tourcard-Holder:** Werden aus `tour_cards` geladen.
  - **32 freie Plätze (Nachrücker/Qualifier):** Werden dynamisch aus der Stammdaten-Tabelle `players` ermittelt, indem alle Spieler geladen werden, die keine aktive Tourcard für die aktuelle `season_id` besitzen (`tour_card_year == 0`), sortiert nach `skill DESC`:
  ```sql
  SELECT * FROM players 
  WHERE tour_card_year = 0 
    AND id NOT IN (SELECT player_id FROM tour_cards WHERE season_id = :seasonId)
  ORDER BY skill DESC, prize_money DESC
  LIMIT 32
  ```
* **Preisgelder:** Werden pro Turnier in der Tabelle `tournament_placements` (bzw. `event_placements`) erfasst und verweisen ebenfalls direkt auf die `players.id` bzw. `USER_ID = -1L`.

---

## 🌳 5. Persistente Turnierbaum-Speicherung & Fortschrittslogik

* **Vollständige Baum-Generierung (`GenerateBracketUseCase`):**
  - Für alle Turniere mit festem Turnierbaum (alle außer `UK_OPEN` und `EUROPEAN_OPEN`) werden beim ersten Aufruf von `GenerateBracketUseCase` bereits **alle Runden des Turniers** (von Runde 1 bis Finale bzw. Gruppenphase bis Finale) in `tournament_matches` angelegt und persistiert.
  - Dabei werden Platzhalter wie `PLACEHOLDER_PREVIOUS_WINNER_ID` (`-999L`), Open-Draw-IDs oder Vorrunden-Platzhalter verwendet.
  - `UK_OPEN` und `EUROPEAN_OPEN` behalten ihre offene Runden-für-Runden-Auslosung bei.
* **Rundenermittlung (`AdvanceTournamentRoundUseCase.invoke`):**
  - Zuerst wird geprüft, ob **alle** Runden der Sequenz abgeschlossen sind (`allRoundsCompleted`). Wenn ja:
    - Qualifier-Turniere (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`): Turnier als abgeschlossen markieren (`isCompleted = true`) und `true` zurückgeben.
    - Normale Turniere: Verlierer-Placements aller abgeschlossenen Runden iterieren (Dedupe über `existingPlacementPlayerIds`), Finale-Sieger erhält `WINNER`-Placement, Turnier als abgeschlossen markieren und `true` zurückgeben.
  - Falls nicht alle Runden abgeschlossen sind: erste Runde in der Sequenz ermitteln, die **mindestens ein unvollendetes Match** enthält (`matches.any { !it.isCompleted }`). Falls keine solche Runde existiert, letzte Runde der Sequenz verwenden.
  - **Grand Slam of Darts Tag-1-Prüfung:** Wenn alle Day-1-Gruppen-Matches abgeschlossen sind und Day-2-Matches noch Platzhalter für den Vorgänger-Sieger enthalten, werden diese automatisch befüllt und `true` zurückgegeben.
* **Slot-Befüllung (`propagateWinnerOnMatchCompleted`):** Nach Abschluss eines Matches (CPU-Match über `SimulateCpuMatchUseCase`, User-Match über `setUserMatchWinner` / `setManualUserMatchScore`, oder Kalender-Simulation) wird der Sieger sofort per `propagateWinnerOnMatchCompleted` in den nächsten Turnierbaum-Slot eingetragen (außer bei offener Auslosung wie `UK_OPEN` und `EUROPEAN_OPEN`).
* **Verlierer-Placements:** Für Verlierer aller **abgeschlossenen** Runden wird ein `TournamentPlacement` angelegt, sofern noch kein Placement für diesen Spieler existiert. Dedupe erfolgt über `existingPlacementPlayerIds`. Das Finale erhält ein separates `WINNER`-Placement.
* **Rundenwechsel (Manuell vs. Automatisch):** Eine Runde gilt erst dann als abgeschlossen und bereit zum Weiterschalten, wenn `isCurrentRoundCompleted == true`.
  - `canAdvanceRound` ist vereinfacht auf `(currentRoundKey == activeRoundKeyInDb) && isCurrentRoundCompleted` reduziert.
  - Bei `autoAdvanceRound == false` wird nach Abschluss aller Matches einer Runde der Button **„Nächste Runde“** (`canAdvanceRound = true`) angezeigt. Erst nach Klick wird die nächste Runde betreten.
  - Bei `autoAdvanceRound == true` schaltet das System nach Rundenabschluss automatisch auf die nächste Runde weiter. Der Rückgabewert von `advanceTournamentRoundUseCase` wird ignoriert; stattdessen wird die neue aktive Runde aus den Matches mit unvollendeten Matches bestimmt.
  - `TournamentDetailState.Active` enthält das neue Feld `isFinalRound` (`true` wenn `activeRoundKeyInDb == TournamentRound.F.name || activeRoundKeyInDb == TournamentRound.QUAL_FINAL.name`).
* **Button-Beschriftung (`TournamentDetailActivity`):** Im Active-Zweig von `renderState` wird `btnAdvanceRound` mit `R.string.btn_finish_tournament` beschriftet, wenn `state.isFinalRound == true`, andernfalls mit `R.string.btn_advance_round`.
* **Simulation (`simulateCompleteTournament` in `TournamentDetailViewModel` und `TournamentCalendarViewModel`):** Die `while (true)`-Schleife bricht nicht mehr bei `advanced == false` ab, sondern erst wenn **beides** zutrifft: kein Fortschritt (`!advanced`) **und** keine spielbaren unvollendeten Matches (`remainingPlayable.isEmpty()`), oder das Turnier ist als abgeschlossen markiert (`isCompleted == true`).

* **Fehlerhistorie (behoben):**
  1. `propagateWinnerOnMatchCompleted` befüllte die Folgerunde bereits beim Match-Abschluss, sodass `invoke()` die gerade abgeschlossene Runde übersprang, in der neuen Runde `isCompleted == false` sah und `false` zurückgab. → Rundenermittlung auf „erste Runde mit unvollendeten Matches, sonst letzte Runde“ umgestellt.
  2. Die gleiche Propagation bewirkte, dass `advanceRound(tournamentId)` bei Zwischenrunden `false` lieferte, obwohl noch unvollständige Matches in der Folgerunde existierten → `while (true)` brach zu früh ab. → Abbruchbedingung auf `!advanced && remainingPlayable.isEmpty()` bzw. `isCompleted == true` geändert.
  3. Auto-Advance-Block in `buildActiveState` war vom `advanced`-Flag abhängig; das Button-Text „Nächste Runde“ blieb auch im Finale stehen. → Auto-Advance entkoppelt vom Rückgabewert, `isFinalRound` eingeführt, Button-Beschriftung entsprechend angepasst.

