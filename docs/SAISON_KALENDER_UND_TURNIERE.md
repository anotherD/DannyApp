# 🎯 Fachkonzept: Saisonkalender, Turniertypen & Ranglisten (Pro Tour & Majors)

Dieses Dokument definiert die vollständige Architektur, die Datenstrukturen und die Fachlogiken für den Turnier-, Kalender- und Match-Simulationsmodus der **Danny Darts App**.

---

## 🏛️ 1. Architektur-Erweiterung & Datenmodell (Room v13)

### 1.1 Die User-ID Konvention (Globale Konstante)
Um Nullable-Fremdschlüssel-Probleme und komplexe Boolean-Flags zu vermeiden, erhält der menschliche User im gesamten System eine feste, eindeutige ID:
* **`USER_ID = -1L`**
  Diese ID wird in `tour_cards`, `tournament_matches` und `tournament_placements` verwendet. Bei der Spieler-Stammdatenbank (`players`) wird beim CSV-Import sichergestellt, dass kein CPU-Spieler die ID `-1` oder `0` erhalten kann (Auto-Increment startet bei 1).

### 1.2 Neue Entitäten (Entities)

#### 1. `Tournament` (Tabelle: `tournaments`)
*   `id` (PK, Long, Autoincrement)
*   `season_id` (FK -> `seasons.id`, `ON DELETE CASCADE`)
*   `name` (String, z. B. *"Players Championship 1"*)
*   `type` (String/Enum: `PLAYERS_CHAMPIONSHIP`, `EUROPEAN_TOUR`, `MAJOR_LEG`, `MAJOR_SET`, `MAJOR_GROUP`)
*   `chronological_order` (Int, fortlaufende Nummer im Kalender, 1-basiert)
*   `is_completed` (Boolean, Default: `false`)

#### 2. `TournamentMatch` (Tabelle: `tournament_matches`)
*   `id` (PK, Long, Autoincrement)
*   `tournament_id` (FK -> `tournaments.id`, `ON DELETE CASCADE`)
*   `round` (String: `GROUP`, `L128`, `L64`, `L32`, `L16`, `QF`, `SF`, `F`)
*   `match_index` (Int, Sortierung innerhalb der Runde)
*   `player_1_id` (Long, Verweis auf `players.id` oder `-1L` für User)
*   `player_2_id` (Long, Verweis auf `players.id` oder `-1L` für User)
*   `player_1_score` (Int, Legs oder Sets)
*   `player_2_score` (Int)
*   `winner_id` (Long, Nullable)
*   `is_completed` (Boolean, Default: `false`)

#### 3. `TournamentPlacement` (Tabelle: `tournament_placements`)
*   `id` (PK, Long, Autoincrement)
*   `tournament_id` (FK -> `tournaments.id`, `ON DELETE CASCADE`)
*   `player_id` (Long, Verweis auf `players.id` oder `-1L` für User)
*   `reached_round` (String)
*   `prize_money_earned` (Double, tatsächliche Auszahlung)
*   `prize_money_ranking` (Double, für die Ranglisten gewerteter Betrag – wichtig für Ausnahmen!)

---

## 📅 2. Turniertypen, Qualifikation & Kalenderstruktur

Der Saisonkalender umfasst 30 Players Championship Turniere, 13 European Tour Turniere und ausgewählte Major-Formate.

### 2.1 Players Championship (PC)
*   **Teilnehmer:** Alle 128 Tour-Card-Inhaber (inklusive User mit ID `-1L`).
*   **Auslosung:** Top 32 der *Pro Tour Order of Merit* sind gesetzt und werden im Bracket verteilt. Die restlichen 96 Spieler werden frei zugelost. At Saisonbeginn (Turnier 1 & 2) gilt die Sortierung nach `skill DESC` aus den Stammdaten.

### 2.2 European Tour (ET)
*   **Teilnehmer:** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1.  **Top 16 Pro Tour Order of Merit:** Fest qualifiziert und für Runde 2 gesetzt.
    2.  **Top 16 Main Order of Merit:** Die 16 nächstbesten Spieler der Gesamtrangliste.
    3.  **10 Tour Card Holder Qualifier:** Werden über ein separates K.o.-Turnier (Best of 11 Legs) ausgespielt, an dem alle verbleibenden Tourcard-Inhaber teilnehmen können.
    4.  **6 Host Nation / Regional Qualifier:** Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`). Hier greift der **Nationen-Filter**: Bevorzugt werden Spieler aus dem Gastgeberland (z. B. Deutschland bei ET-Events in Deutschland) nach `skill DESC` selektiert. Gibt es zu wenige Spieler dieser Nation, wird das Feld per Zufallsprinzip mit spielfähigen CPU-Spielern anderer Nationen aufgefüllt.
*   **Ablauf:** Runde 1 spielen die 32 Qualifikanten (16 Matches). In Runde 2 steigen die 16 gesetzten Spieler ein.

### 2.3 Die Major-Turniere
Zur Simulation einer echten Karriere werden drei Major-Typen integriert:
1.  **World Matchplay (MAJOR_LEG):** Top 16 Main OoM + Top 16 Pro Tour OoM. Reines K.o.-System (100% Legs, z. B. R1 Best of 19 Legs).
2.  **World Grand Prix (MAJOR_SET / Double-In):** Die Top-Spieler im K.o.-System, jedoch im **Set-Modus** (z. B. Best of 3 Sets, jedes Set Best of 5 Legs) und mit der Verschärfung **Double-In / Double-Out** (das Leg startet erst, wenn ein Doppel getroffen wird).
3.  **Grand Slam of Darts (MAJOR_GROUP):** 32 Spieler aufgeteilt in **8 Gruppen à 4 Spieler**. Gruppenphase im Round-Robin-System (Jeder gegen jeden, Best of 9 Legs). Die Top 2 jeder Gruppe rücken ins K.o.-Achtelfinale vor.
4.  **World Championship (MAJOR_SET):** Das größte Event des Jahres mit 96 Spielern. Die Top 32 der Main OoM steigen direkt in Runde 2 ein. Runde 1 bestreiten die Top 32 der Pro Tour OoM gegen 32 internationale Qualifier (Nicht-Tourcard-Spieler). Set-Format (Best of 5 bis Best of 13 Sets im Finale).
5.  **Players Championship Finals (MAJOR_LEG):** Die Top 64 Spieler der reinen *Players Championship Order of Merit* qualifizieren sich hierfür. Reines K.o.-System im Leg-Format (z. B. Runde 1 Best of 11 Legs, Finale Best of 21 Legs). Feste Setzung strikt nach der PC-Rangliste (Platz 1 gegen 64, Platz 2 gegen 63 usw.).
6.  **European Championship (MAJOR_LEG):** Die Top 32 Spieler der reinen *European Tour Order of Merit* qualifizieren sich. Reines K.o.-System im Leg-Format (Runde 1 Best of 11 Legs, Finale Best of 21 Legs). Setzung strikt nach der ET-Rangliste (Platz 1 gegen 32).
7.  **UK Open (MAJOR_LEG - 160 Spieler):** Das "FA Cup des Darts". Es nehmen alle 128 Tourcard-Inhaber teil, ergänzt um 32 Amateure/Challenge-Tour-Nachrücker. Das Alleinstellungsmerkmal: **Es gibt keine Setzliste** – ab Runde 4 kann in jeder Runde jede ID (auch die des Users) gegen jeden Top-Spieler gelost werden. Die Runden 1–3 filtern nach Rangliste (schwächere Spieler steigen zuerst ein). Das Turnier wird parallel auf bis zu 8 Bühnen ausgetragen.
    (Die fehlenden 32 Plätze zur Auffüllung auf 160 Spieler werden dynamisch aus Nicht-Tourcard-Spielern mit `tour_card_year == 0` nach `skill DESC` aufgefüllt).
8.  **PDC European Open (MAJOR_LEG - 160 Spieler):** Ein brandneues Major-Turnier, das exakt das spektakuläre Format der UK Open kopiert (160 Spieler, zeitloses Spielen auf 8 Bühnen parallel, keine feste Setzliste). Qualifiziert sind alle 128 Tourcard-Holder sowie 32 regionale europäische Qualifier (z. B. über die *PDC Europe Start*-Serie).
9.  **PDC World Masters (MAJOR_SET / ehemals "The Masters" - 32 Spieler):** Dieses Turnier wurde reformiert und wird im schnellen Set-Modus gespielt (2 Legs reichen für einen Satzgewinn). Die Top 24 der Weltrangliste sind fest qualifiziert. Die verbleibenden 8 Plätze werden über eine vorgelagerte, harte Gruppen-Vorrunde ausgespielt, an der auch die restlichen Tourcard-Inhaber teilnehmen können.

---

## 💰 3. Preisgeldregeln & Ranglisten (Orders of Merit)

Das System führt getrennte, dynamische Ranglisten, basierend auf den kumulierten Daten der aktuellen Saison aus der Tabelle `tournament_placements`.

### 3.1 Die 4 Kern-Ranglisten
1.  **Main Order of Merit (Gesamtrangliste):** Basis-Wert aus `players.prizeMoney` plus alle Erträge (`prize_money_ranking`) aller Turniertypen der aktuellen Saison.
2.  **Pro Tour Order of Merit:** Kumuliertes Ranglisten-Preisgeld aus `PLAYERS_CHAMPIONSHIP` und `EUROPEAN_TOUR`.
3.  **Players Championship Order of Merit:** Ausschließlich Preisgelder aus `PLAYERS_CHAMPIONSHIP`.
4.  **European Tour Order of Merit:** Ausschließlich Preisgelder aus `EUROPEAN_TOUR`.

### 3.2 PDC-Sonderregeln für Preisgelder (Wichtig für die Logik!)
*   **European Tour (Gesetzte Verlierer):** Wenn ein gesetzter Spieler (Top 16) in der 2. Runde der European Tour sein erstes Spiel verliert, erhält er zwar das finanzielle Preisgeld (`prize_money_earned = £3.500`), dieses wird jedoch **nicht** für die Ranglisten gewertet (`prize_money_ranking = £0`).
*   **Players Championship (Runde 1):** Verlierer der ersten Runde (Last 128) erhalten £0.
*   **Gesetzte Erstrunden-Verlierer (ET, European Championship, PC Finals):** Wenn ein gesetzter Spieler bei der European Tour (in Runde 2) oder bei den PC Finals / der European Championship (in Runde 1) sein jeweils erstes Turnierspiel verliert, erhält er das Preisgeld zwar finanziell ausgezahlt (`prize_money_earned`), es wird jedoch **nicht** für die Ranglisten gewertet (`prize_money_ranking = £0`).

### 3.3 Format-Spezifikationen für Sets und Sonderregeln
*   **Set-Modus (World Championship):** Ein Set besteht immer aus *Best of 5 Legs* (First to 3 Legs gewinnt das Set).
*   **Double-In / Double-Out (World Grand Prix):** Das Leg wird mathematisch erst heruntergezählt, sobald der erste Pfeil des Spielers in einem äußeren Doppelring oder im Double-Bull landet.

---

## 4. Spielformate (Legs & Sets)

### 4.1 Leg-Format (Pro Tour & ausgewählte Majors)
*   **Players Championship / European Tour:** Best of 11 Legs (First to 6). Halbfinale: Best of 13. Finale: Best of 15.
*   **UK Open & European Open:** Runden 1–3: Best of 11 Legs. Ab Runde 4 bis Viertelfinale: Best of 19 Legs. Halbfinale und Finale: Best of 21 Legs.
*   **Grand Slam:** Gruppenphase: Best of 9 Legs (First to 5). K.o.-Phasen bis zu Best of 31 Legs im Finale.
*   **World Matchplay & Players Championship Finals & European Championship:** Reines K.o.-System im Leg-Format (Langen Distanzen je nach Runde, z. B. Finale bis zu Best of 21/31 Legs).

### 4.2 Set-Format (World Championship & World Masters)
Ein Set besteht aus *Best of 5 Legs* (First to 3 Legs gewinnt das Set).
*   **World Championship:** Runde 1 & 2: Best of 5 Sets (First to 3). Finale: Best of 13 Sets (First to 7).
*   **World Masters (Set-Sonderformat):** Ein Set besteht hier verkürzt aus *Best of 3 Legs* (First to 2).

### 4.3 Sonderformate & Modifikationen
*   **World Grand Prix (MAJOR_SET):** Wird im Set-Format gespielt (Runde 1: Best of 3 Sets). Es gilt die Verschärfung **Double-In / Double-Out** (das Leg startet erst, wenn ein Doppel getroffen wird, und endet auch auf einem Doppel).


## 🛠️ 5. Technische Umsetzung (Domain-, Use-Case- & Engine-Layer)

Um das MVVM-Muster sauber einzuhalten, wird die komplexe Geschäfts- und Berechnungslogik **nicht in den Repositories** implementiert. Repositories (wie z. B. `SeasonRepository` oder `PlayerRepository` in `data/db/`) agieren analog zu Doctrine-Repositories im PHP/Symfony-Umfeld: Sie verwalten rein den Datenfluss, Datenbank-I/O und Transaktionen.

Die Ablaufsteuerung, Match-Generierung und algorithmischen Berechnungen werden in dedizierte **Use-Case-Klassen** ausgelagert (entspricht Services oder Command-Handlern in Symfony).

### 5.1 Use Cases & Verantwortlichkeiten
*   **`GenerateBracketUseCase`:** Berechnet nach Abschluss eines Turniers (oder bei Saisonstart) die Setzlisten anhand der spezifischen Orders of Merit aus `tournament_placements` und generiert die Paarungen (Runde 1) für das nächste anstehende Turnier in `tournament_matches`.
*   **`SimulateCpuMatchUseCase`:** Simuliert CPU-vs-CPU-Matches. *Ausbaustufe 1:* Gewichteter Zufall basierend auf dem `skill`-Faktor. *Ausbaustufe 2:* Schritt-für-Schritt-Darts-Simulation, die pro Match virtuelle 3-Dart-Averages und Doppelquoten erzeugt, um tiefe Statistiken für die CPU-Gegner zu erfassen.
*   **`GameEngine / SpielLogik`:** Eine eigenständige Engine steuert die Live-Partien des Users. Sie verarbeitet die Wurfdaten – entweder manuell per Tastatur-Eingabe oder automatisiert als Event-Stream über den WebSocket Proxy der *Scolia-Kamera-Integration*.

