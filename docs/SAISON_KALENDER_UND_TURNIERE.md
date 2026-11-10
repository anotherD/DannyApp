# 🎯 Fachkonzept: Saisonkalender, Turniertypen & Ranglisten (Pro Tour & Majors)

Dieses Dokument definiert die Architektur, die Datenstrukturen und die Fachlogiken für den Turnier-, Kalender- und Match-Simulationsmodus der **Danny Darts App**.

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
*   `order` (Int, fortlaufende Nummer im Kalender, 1-basiert)
*   `is_completed` (Boolean, Default: `false`)

    private final String eventKey;
    private final int legsPerSet;
    private final CareerMatchDistances matchDistances;
    private final String name;
    private final int participants;
    private final CareerPrizeFund prizeFund;
    private final int qualifierBestOfLegs;
    private final int seriesNumber;
    private final boolean setPlay;
    private final CareerEventType type;

#### 2. `TournamentMatch` (Tabelle: `tournament_matches`)
*   `id` (PK, Long, Autoincrement)
*   `tournament_id` (FK -> `tournaments.id`, `ON DELETE CASCADE`)
*   `round` (String/Enum: `GROUP`, `L128`, `L64`, `L32`, `L16`, `QF`, `SF`, `F`)
*   `match_index` (Int, Sortierung innerhalb der Runde)
*   `player_1_id` (Long, Verweis direkt auf `players.id` oder `-1L` für den User – völlig unabhängig davon, ob der Spieler in `tour_cards` existiert oder nicht; Match-Zuweisung ist komplett entkoppelt)
*   `player_2_id` (Long, Verweis direkt auf `players.id` oder `-1L` für den User – völlig unabhängig davon, ob der Spieler in `tour_cards` existiert oder nicht; Match-Zuweisung ist komplett entkoppelt)
*   `player_1_score` (Int, Legs oder Sets)
*   `player_2_score` (Int)
*   `winner_id` (Long, Nullable)
*   `is_completed` (Boolean, Default: `false`)

#### 3. `TournamentPlacement` (Tabelle: `tournament_placements`)
*   `id` (PK, Long, Autoincrement)
*   `tournament_id` (FK -> `tournaments.id`, `ON DELETE CASCADE`)
*   `player_id` (Long, Verweis auf `players.id` oder `-1L` für User)
*   `reached_round` (String)
*   `prize_money` (Double, für die Ranglisten gewerteter Betrag)
    {
    "playerId": 1,
    "season": 1,
    "eventKey": "PT-01",
    "eventType": "proTour",
    "placement": 33,
    "amount": 1250
---

## 📅 2. Turniertypen, Qualifikation & Kalenderstruktur

Es gibt 3 Turniertypen:
* PC: Players Championship, 34 pro Saison
* ET: European Tour, 15 pro Saison
* MAJOR: Major-Turniere, 8 pro Saison

### 2.1 Players Championship (PC)
*   **Teilnehmer:** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1.   Alle 128 Tour-Card-Inhaber (inklusive User mit ID `-1L`).
*   **Auslosung:** Top 32 der *Pro Tour Order of Merit* sind gesetzt und werden im Bracket verteilt. Die restlichen 96 Spieler werden frei zugelost. Da beim Start einer karriere die *Pro Tour Order of Merit* noch leer ist, werden hierfür die Top 32 zufällig ermittelt.

### 2.2 European Tour (ET)
*   **Teilnehmer:** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
    3. 10 Gewinner des ET Tour Card Holder Qualifiers
    4. 4 Gewinner des Host Nation Qualifiers: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison. Es werden nur Spieler aus dem Gastgeberland (z. B. Deutschland bei ET-Events in Deutschland) berücksichtigt. Wird die nötige Anzahl nicht erreicht, so wird mit Spielern aufgefüllt, die nicht aus dem Gastgeberland stammen.
    5. 2 Gewinner anderer Qualifiers: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal.
*   **Ablauf:** Runde 1 spielen die 32 Qualifikanten (16 Matches). In Runde 2 steigen die 16 gesetzten Spieler ein. Die Zuordnung in Runde 2 folgt einem festen mathematischen Schlüssel (Kreuzvergleich), der die am höchsten gesetzten Spieler schützt:
    * Die Nummer 1 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 16 (vs. ungesetzter Qualifikant).
    * Die Nummer 2 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 15. (vs. ungesetzter Qualifikant).
    * ... und so weiter, bis:
    * Die Nummer 16 der Weltrangliste gegen den Sieger von Pro Tour Seed Nr. 1 spielt.

### 2.3 Die Major-Turniere

2.  **UK Open / European Open (MAJOR_LEG):** 160 Teilnehmer. Es nehmen alle 128 Tourcard-Inhaber der aktuellen Saison teil. Die 32 freien Plätze (Nachrücker/Qualifier) werden dynamisch aus der `players`-Tabelle ermittelt, indem alle Spieler geladen werden, die KEINE aktive Tourcard für die aktuelle `season_id` besitzen (`tour_card_year == 0`), sortiert nach `skill DESC`. K.o.-System ohne feste Setzliste. Jede Runde wird komplett frei neu ausgelost. Runden 1–3 gestaffelt nach Ranglistenplatzierung.
3.  **European Open (MAJOR_LEG - 160 Spieler):** Ein Major-Turnier, das exakt das spektakuläre Format der UK Open kopiert (160 Spieler, zeitloses Spielen auf 8 Bühnen parallel, keine feste Setzliste). Qualifiziert sind alle 128 Tourcard-Holder der aktuellen Saison sowie 32 Nachrücker/Qualifier ohne Tourcard (`tour_card_year == 0`), die dynamisch aus der `players`-Tabelle nach `skill DESC` ermittelt werden.
4.  **World Matchplay (MAJOR_LEG):** Top 16 Main OoM + Top 16 Pro Tour OoM. Reines K.o.-System (100% Legs, z. B. R1 Best of 19 Legs).
5.  **World Grand Prix (MAJOR_SET / Double-In):** Die Top-Spieler im K.o.-System, jedoch im **Set-Modus** (z. B. Best of 3 Sets, jedes Set Best of 5 Legs) und mit der Verschärfung **Double-In / Double-Out** (das Leg startet erst, wenn ein Doppel getroffen wird).
6.  **European Championship (MAJOR_LEG):** 32 Teilnehmer. Qualifiziert sind die Top 32 der European Tour Order of Merit der laufenden Saison. Setzung erfolgt strikt nach dieser Rangliste (Platz 1 gegen Platz 32). Reines K.o.-System im Leg-Format.
7.  **Grand Slam of Darts (MAJOR_GROUP):** 32 Spieler aufgeteilt in **8 Gruppen à 4 Spieler**. Gruppenphase im Round-Robin-System (Jeder gegen jeden, Best of 9 Legs). Die Top 2 jeder Gruppe rücken ins K.o.-Achtelfinale vor.
8**Players Championship Finals (MAJOR_LEG):** 64 Teilnehmer. Qualifiziert sind die Top 64 der Players Championship Order of Merit der laufenden Saison. Setzung erfolgt strikt nach dieser Rangliste (Platz 1 gegen Platz 64). Reines K.o.-System im Leg-Format.
9.  **World Championship (MAJOR_SET):** Das größte Event des Jahres mit 96 Spielern. Die Top 32 der Main OoM steigen direkt in Runde 2 ein. Runde 1 bestreiten die Top 32 der Pro Tour OoM gegen 32 internationale Qualifier (Nicht-Tourcard-Spieler). Set-Format (Best of 5 bis Best of 13 Sets im Finale).

---

## 💰 3. Preisgeldregeln & Ranglisten (Orders of Merit)

Das System führt getrennte, dynamische Ranglisten, basierend auf den kumulierten Daten der Karriere aus der Tabelle `tournament_placements`.

### 3.1 Die 4 Ranglisten

Alle Ranglisten errechnen sich aus den Preisgeldern (`TournamentPlacement`.`prize_money`), der in dieser Karriere gespielen Tourniere.
Der Stammdatenwert `players.prizeMoney` wird aktuell nicht verwendet (kann in einer späteren Ausbaustufe optional als historischer Startwert vor Karrierebeginn dienen).

1.  **Order of Merit:**  Kürzel: "OOM". Preisgelder aller Turniere der letzten zwei Jahre. Das heißt, die jüngsten zwei Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend). 
2.  **Pro Tour Order of Merit:** Kürzel: "PT OOM". Preisgeld aus `PLAYERS_CHAMPIONSHIP` und `EUROPEAN_TOUR` . Nur die jüngsten Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend).  
3.  **Players Championship Order of Merit:** Kürzel: "PC OOM". Ausschließlich Preisgelder aus `PLAYERS_CHAMPIONSHIP` der aktuellen saison.
4.  **European Tour Order of Merit:** Kürzel: "ET OOM". Ausschließlich Preisgelder aus `EUROPEAN_TOUR` der aktuellen saison.


### 3.2 PDC-Sonderregeln für Preisgelder (vereinfacht für die App)
*   Es wird nur ein einziges Feld `prize_money` gespeichert, das den für die Ranglisten relevanten Betrag repräsentiert.
*   Sonderregeln, die früher zwischen `prize_money_earned` und `TournamentPlacement`.`prize_money` unterschieden (z. B. European Tour: Setzliste-Verlierer in Runde 2 erhalten finanzielles Preisgeld, aber keinen Ranking-Beitrag), wurden entfernt, da die App nur die für die Rankings relevanten Werte verwaltet.
*   Für die Rangliste relevanz: `prize_money` wird ausschließlich aus Turniertypen gespeist, die für die Orders of Merit gewertet werden (Players Championship, European Tour, Majors mit entsprechender Formatierung).

### 3.3 Format-Spezifikationen für Sets und Sonderregeln
*   **Set-Modus (World Championship):** Ein Set besteht immer aus *Best of 5 Legs* (First to 3 Legs gewinnt das Set).
*   **Double-In / Double-Out (World Grand Prix):** Das Leg wird mathematisch erst heruntergezählt, sobald der erste Pfeil des Spielers in einem äußeren Doppelring oder im Double-Bull landet.

---

## 4. Spielformate (Legs & Sets)

### 4.1 Leg-Format (Pro Tour & ausgewählte Majors)
*   **UK Open & European Open:** Runden 1–3: Best of 11 Legs. Runde 4 bis Viertelfinale: Best of 19 Legs. Halbfinale und Finale: Best of 21 Legs.
*   **Grand Slam of Darts:** Gruppenphase: Best of 9 Legs. Achtelfinale: Best of 19 Legs. Viertelfinale, Halbfinale und Finale: Best of 31 Legs.
*   **World Matchplay:** Runde 1: Best of 19 Legs. Runde 2: Best of 21 Legs. Viertelfinale: Best of 31 Legs. Halbfinale: Best of 33 Legs. Finale: Best of 35 Legs.
*   **Players Championship Finals & European Championship:** Runde 1: Best of 11 Legs. Runde 2 und Viertelfinale: Best of 19 Legs. Halbfinale und Finale: Best of 21 Legs.

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

