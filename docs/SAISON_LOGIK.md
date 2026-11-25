# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons, Tour Cards sowie der Entkopplung von Turnierteilnahmen im Karrieremodus. Technische Details zur Turnierbaum-Speicherung und Zustandsmaschine sind in [ARCHITEKTUR.md](ARCHITEKTUR.md#4-persistente-turnierbaum-speicherung--fortschrittssteuerung) dokumentiert.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder**:
* **1 Tour Card für den menschlichen User:** `USER_ID (-1L)` startet im 1. Tour-Card-Jahr (`tourCardYear = 2`, fest vorgegeben).
* **127 Tour Cards für CPU-Spieler:** Jeweils im 1. oder 2. Tour-Card-Jahr (`tourCardYear = 1` oder `2`).

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert. Die 127 CPU-Tourcards werden wie folgt ermittelt:
1. Alle CPU-Spieler aus den Stammdaten mit aktiver Tour Card (`tour_card_year > 0`) werden geladen und absteigend nach Spielstärke (`skill`) und historischem Preisgeld (`prizeMoney`).
2. **Mehr als 127 Kandidaten (`> 127`):** Nur die besten 127 Kandidaten erhalten eine Tour Card; schwächere fallen heraus.
3. **Weniger als 127 Kandidaten (`< 127`):** Alle Kandidaten übernehmen ihre Tour Card; die Differenz wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) nach Spielstärke aufgefüllt (mit `tourCardYear = 2`).

---

## 📊 2.1 Optionale initiale Order of Merit (Karrierestart)

Beim Anlegen einer neuen Karriere entscheidet der User über eine Option (**„Order of Merit vorbefüllen“**, Standard: **aktiv / `true`**), ob die Ranglisten mit realen historischen Preisgeldern starten oder bei **£0**:

* **Deaktiviert (`false`):** Es wird direkt **Saison 1** angelegt; alle Spieler (inkl. User) starten in allen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`) mit **£0**.
* **Aktiviert (`true`, Standard):** Vor `Saison 1` werden zwei abgeschlossene historische Vor-Saisons (**`Saison -1`** für das ältere Jahr und **`Saison 0`** für die jüngsten 12 Monate des 24-Monats-Fensters, jeweils `isCompleted = true`) mit dem vollständigen Turnierkalender angelegt und aus `assets/initial_placements.csv` befüllt:
  * **Echte Einzelergebnisse aus `initial_placements.csv`:** Die Rohdaten pro Spieler (`player_name;date;raw_tournament_name;prize_money`) werden beim Import in der App chronologisch pro Turnier-Slot auf `Saison -1` (ältere Austragung) und `Saison 0` (jüngere Austragung) verteilt.
  * **Nicht im Kalender enthaltene Ranking-Majors (z. B. *World Masters*):** Solange ein historisches Ranking-Turnier noch nicht als eigenes `TournamentEvent` im Saisonkalender abgebildet ist, schlägt der Importer dessen Preisgeld im selben Vorjahr einem bestehenden Major-Turnier (z. B. *UK Open*) zu, damit die 2-Jahres-Gesamtsumme exakt erhalten bleibt. Neue 2027er-Events (*European Open*) starten in beiden Vor-Saisons mit £0.
  * **Plausibilitäts-Fallback:** Fehlt ein Spieler in `.csv` (z. B. ohne Detailseite auf `dartsrankings.com`), verteilt die App sein in `darts_rankings.csv` hinterlegtes Gesamtpreisgeld (`prizeMoney`) anteilig nach Turnier-Preisgeldtöpfen auf `Saison -1` und `Saison 0`.
  * **Saisonübergreifendes Abschmelzen:** Da `GetOrderOfMeritUseCase` stets die jüngsten zwei Austragungen (`OOM`) bzw. die jüngste Austragung (`PT OOM`) auswertet, starten `OOM` und `PT OOM` exakt mit den realen Vorjahreswerten (`PC OOM` und `ET OOM` starten auf £0) und schmelzen während `Saison 1` Turnier für Turnier realistisch ab.

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Nach Abschluss aller Turniere einer Saison wird der Saisonwechsel nicht automatisch ausgelöst, sondern explizit vom User über die **Saison-Zusammenfassung** gestartet (`AdvanceSeasonUseCase`). Dabei wird die bisherige Saison abgeschlossen, optional das Karriere-Level angepasst, `Career.season` auf `seasonNumber + 1` erhöht und der neue Saisonkalender ohne erneute OOM-Vorbefüllung angelegt:
* **Top-64- & Tour-Card-Jahr-Regel:** Alle 128 Tour-Card-Inhaber der Vorsaison (inklusive `USER_ID = -1L`) werden gegen die Top 64 der aktuellen 2-Jahres-Order-of-Merit (`OOM`) geprüft:
  * Spieler in den **Top 64** behalten ihre Tour Card (Jahr 1 steigt auf `tourCardYear = 2` auf; Jahr 2 bleibt bei `tourCardYear = 2`).
  * Spieler außerhalb der Top 64 im **1. Jahr** (`tourCardYear == 1`) behalten ihre Tour Card und steigen ins 2. Jahr auf (`tourCardYear = 2`).
  * Spieler außerhalb der Top 64 im **2. Jahr** (`tourCardYear == 2`) verlieren ihre Tour Card (gilt auch für `USER_ID`).
* **Q-School-Simulation (Auffüllung auf 128 Cards mit `tourCardYear = 1`):** Alle Spieler ohne Tour Card (inklusive soeben ausgeschiedener Spieler) nehmen an der Q-School teil. Hat `USER_ID` seine Tour Card verloren, gewinnt er die Q-School vorläufig garantiert (`tourCardYear = 1`); die verbleibenden freien Plätze werden absteigend nach `skill` (bei gleicher Spielstärke zufällig gemischt) aus den CPU-Kandidaten besetzt.

## 🏆 4. Turnierteilnahmen & Entkopplung der Match-Zuweisung

* **Entkopplung der Teilnehmer von Tour Cards:** In Turnier-Matches werden direkt die Spieler-IDs aus den Stammdaten (bzw. `USER_ID = -1L`) gespeichert. Turnierteilnehmer müssen nicht zwingend eine Tour Card besitzen (z. B. regionale oder internationale Nicht-Tourcard-Qualifier bei Majors oder der European Tour).
* **Teilnehmerpools & Qualifikation:** Die exakten Teilnehmerzahlen und Qualifikationswege aller Turniertypen (128er-PC, 48er-ET, 160er-UK/European Open usw.) sind in [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md) definiert.
* **Preisgelder:** Werden pro Turnier und Spieler erfasst und bilden die Grundlage für die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`). Details siehe [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md)
