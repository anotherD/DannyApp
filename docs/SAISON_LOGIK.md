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
* **Teilnehmerpools & Qualifikation:** Die exakten Teilnehmerzahlen und Qualifikationswege aller Turniertypen (128er PC, 48er ET, 160er UK/European Open usw.) sind in [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md) definiert.
* **Preisgelder:** Werden pro Turnier in `tournament_placements` erfasst und verweisen direkt auf `players.id` bzw. `USER_ID = -1L`.

---

## 🌳 5. Persistente Turnierbaum-Speicherung & Fortschrittslogik

* **Vollständige Baum-Generierung (`GenerateBracketUseCase`):**
  * Für alle Turniere mit festem Turnierbaum werden beim Turnierstart bereits **alle Runden** (von Runde 1 bis Finale bzw. Gruppenphase bis Finale) in `tournament_matches` angelegt.
  * Noch nicht feststehende Paarungen nutzen Platzhalter-IDs (`PLACEHOLDER_PREVIOUS_WINNER_ID = -999L`, Gruppen-Sieger-IDs oder Vorrunden-Platzhalter).
  * `UK_OPEN` und `EUROPEAN_OPEN` nutzen ab Runde 2 eine offene Runden-für-Runden-Auslosung.
* **Sofortige Sieger-Weiterleitung (`propagateWinnerOnMatchCompleted`):**
  * Sobald ein Match abgeschlossen ist, wird der Sieger sofort in den entsprechenden Slot der Folgerunde eingetragen (ausgenommen Turniere mit offener Auslosung).
  * Beim *Grand Slam of Darts* werden nach Tag 1 der Gruppenphase Verlierer und Sieger automatisch in die Partien von Tag 2 und Tag 3 eingetragen.
* **Rundenfortschritt & Abschluss (`AdvanceTournamentRoundUseCase`):**
  * Sind alle Runden eines Turniers abgeschlossen, werden die Preisgelder der Verlierer (ohne Duplikate) sowie das Sieger-Preisgeld (`WINNER`) in `tournament_placements` gebucht und das Turnier als abgeschlossen markiert (`isCompleted = true`).
  * Bei Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) wird das Turnier nach Abschluss von `QUAL_FINAL` ohne Preisgeldbuchung beendet.
  * Ist `autoAdvanceRound == false`, erscheint nach Abschluss aller Matches einer Runde der Button zum Weiterschalten; ist `autoAdvanceRound == true`, erfolgt der Wechsel automatisch.
