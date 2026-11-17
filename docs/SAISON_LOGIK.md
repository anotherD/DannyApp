# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons, Tour Cards sowie der Turnierbaum- und Fortschrittssteuerung im Karrieremodus.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder**:
* **1 Tour Card für den menschlichen User:**
  * `playerId = USER_ID (-1L)` (repräsentiert den User der Karriere)
  * `tourCardYear = 2` (fest vorgegeben)
* **127 Tour Cards für CPU-Spieler:**
  * `playerId` verweist auf die ID aus der `players`-Tabelle.
  * `tourCardYear` ist `1` oder `2`.

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert. Die 127 CPU-Tourcards werden in folgenden Schritten ermittelt:

### Schritt 1: CPU-Kandidaten filtern & sortieren
Alle CPU-Spieler aus der `players`-Tabelle mit `tour_card_year > 0` werden geladen und sortiert nach:
1. `skill DESC` (Spielstärke 1–10)
2. `prizeMoney DESC`
3. `name ASC`

### Schritt 2: Vergabe & Ausgleich auf exakt 127 CPU-Plätze
* **Mehr als 127 Kandidaten (`> 127`):** Es werden nur die besten 127 Kandidaten übernommen. Schwächere Tourcard-Spieler erhalten für diese Saison keine Tourcard.
* **Exakt 127 Kandidaten (`= 127`):** Alle 127 Kandidaten erhalten eine Tourcard.
* **Weniger als 127 Kandidaten (`< 127`):** Alle vorhandenen Kandidaten übernehmen ihre Tourcard. Die fehlende Differenz wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) nach Skill aufgefüllt (mit `tourCardYear = 2`).

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Beim Saisonwechsel:
1. Die bisherige Saison wird als abgeschlossen markiert (`isCompleted = true`).
2. Eine neue Saison mit `seasonNumber = currentSeason.seasonNumber + 1` wird erstellt.
3. **Geplante Order-of-Merit-Logik:**
  * Spieler im 1. Jahr steigen ins 2. Jahr auf (`tourCardYear = 2`).
  * Spieler im 2. Jahr verlieren ihre Card, sofern sie nicht in den Top 64 der 2-Jahres-Order-of-Merit stehen.
  * Freie Plätze werden über die Q-School aufgefüllt (`tourCardYear = 1`).
4. **Aktuelle Übergangslogik:** Nutzt dieselbe Übernahme- und Auffülllogik wie Saison 1.

---

## 🏆 4. Turnierteilnahmen & Entkopplung der Match-Zuweisung

* **Tabelle `tour_cards`:** Speichert ausschließlich die 128 aktiven Tourcard-Inhaber der jeweiligen Saison (inklusive `USER_ID = -1L`).
* **Entkopplung in `tournament_matches`:** In `player_1_id` und `player_2_id` werden direkt die Stamm-IDs aus `players` (bzw. `USER_ID = -1L`) gespeichert. Turnierteilnehmer müssen nicht zwingend in `tour_cards` existieren.
* **128-Spieler Tour Card Turniere (z. B. Players Championship):** Setzen sich zu 100 % aus den aktiven Tourcard-Holdern der aktuellen Saison zusammen.
* **160-Spieler Turniere (`UK Open` & `European Open`):**
  * **128 Tourcard-Holder:** Werden aus `tour_cards` geladen.
  * **32 freie Plätze (Nachrücker/Qualifier):** Werden dynamisch aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) der `players`-Tabelle nach `skill DESC, prize_money DESC` aufgefüllt.
* **Preisgelder:** Werden pro Turnier in `tournament_placements` erfasst und verweisen direkt auf `players.id` bzw. `USER_ID = -1L`.

---

## 🌳 5. Persistente Turnierbaum-Speicherung & Fortschrittslogik

* **Vollständige Baum-Generierung (`GenerateBracketUseCase`):**
  * Für alle Turniere mit festem Turnierbaum werden beim Turnierstart bereits **alle Runden** (von Runde 1 bis Finale bzw. Gruppenphase bis Finale) in `tournament_matches` angelegt.
  * Noch nicht feststehende Paarungen nutzen Platzhalter-IDs (`PLACEHOLDER_PREVIOUS_WINNER_ID = -999L`, Gruppen-Sieger-IDs oder Vorrunden-Platzhalter).
  * `UK_OPEN` und `EUROPEAN_OPEN` behalten ihre offene Runden-für-Runden-Auslosung bei.
* **Sofortige Sieger-Weiterleitung (`propagateWinnerOnMatchCompleted`):**
  * Sobald ein Match abgeschlossen ist (durch Simulation oder User-Eingabe), wird der Sieger sofort in den entsprechenden Slot der Folgerunde eingetragen (ausgenommen Turniere mit offener Auslosung).
  * Beim *Grand Slam of Darts* werden nach Tag 1 der Gruppenphase Verlierer und Sieger automatisch in die Partien von Tag 2 und Tag 3 eingetragen.
* **Rundenfortschritt & Abschluss (`AdvanceTournamentRoundUseCase`):**
  * Sind alle Runden eines Turniers abgeschlossen, werden die Preisgelder der Verlierer (ohne Duplikate) sowie das Sieger-Preisgeld (`WINNER`) in `tournament_placements` gebucht und das Turnier als abgeschlossen markiert (`isCompleted = true`).
  * Bei Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) wird das Turnier nach Abschluss von `QUAL_FINAL` ohne Preisgeldbuchung beendet.
  * Eine Runde gilt erst als bereit zum Weiterschalten, wenn alle Matches dieser Runde abgeschlossen sind (`isCurrentRoundCompleted == true`).
  * Ist `autoAdvanceRound == false`, erscheint nach Rundenabschluss der Button zum Weiterschalten in die nächste Runde (bzw. im Finale zum Abschließen des Turniers). Ist `autoAdvanceRound == true`, erfolgt der Wechsel automatisch.
