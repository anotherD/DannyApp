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

Natürlich kann kein Spieler mehrfach an einem Turnier Teilnehmen.

### 2.1 Players Championship (PC)
*   **Teilnehmer:** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1. Alle 128 Tour-Card-Inhaber (inklusive User mit ID `-1L`).
*   **Auslosung:** Top 32 der *Pro Tour Order of Merit* sind gesetzt und werden im Bracket verteilt. Die restlichen 96 Spieler werden frei zugelost. Da beim Start einer karriere die *Pro Tour Order of Merit* noch leer ist, werden hierfür die Top 32 zufällig ermittelt.

### 2.2 European Tour (ET)
*   **Teilnehmer:** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
    3. 10 Gewinner des ET Tour Card Holder Qualifiers
    4. 4 Gewinner des Host Nation Qualifiers: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison. Es werden nur Spieler aus dem Gastgeberland (z. B. Deutschland bei ET-Events in Deutschland) berücksichtigt. Wird die nötige Anzahl nicht erreicht, so wird mit Spielern aufgefüllt, die nicht aus dem Gastgeberland stammen.
    5. 2 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal.
*   **Modus:**
    * K.O.-System
    * Leg
*   **Setzliste:** Runde 1 spielen die 32 Qualifikanten (16 Matches). In Runde 2 steigen die 16 gesetzten Spieler ein. Die Zuordnung in Runde 2 folgt einem festen mathematischen Schlüssel (Kreuzvergleich), der die am höchsten gesetzten Spieler schützt:
    * Die Nummer 1 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 16 (vs. ungesetzter Qualifikant).
    * Die Nummer 2 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 15. (vs. ungesetzter Qualifikant).
    * ... und so weiter, bis:
    * Die Nummer 16 der Weltrangliste gegen den Sieger von Pro Tour Seed Nr. 1 spielt.

### 2.3 Die Major-Turniere

**Major-Turniere folgen spezifischen Format- und Qualifikationsregeln:**

#### 2.1 UK Open / European Open
*   **Teilnehmer:** 160.
*   **Zusammensetzung & Qualifikation:**
    1. Alle 128 Tourcard-Inhaber der aktuellen Saison.
    2. 32 Qualifikanten ohne Tourcard (`tour_card_year == 0`), sortiert nach `skill DESC` aus der `players`-Tabelle.
*   **Modus:**
    * K.O.-System
    * Legs
*   **Setzliste:** ohne feste Setzliste. Runden 1–3 gestaffelt nach Ranglistenplatzierung:
    * Runde 1: Die Plätze 97–128 der Order of Merit + alle 32 Qualifikanten
    * Runde 2: Die 32 Sieger aus Runde 1 treffen auf die Plätze 65–96 der Order of Merit.
    * Runde 3: Die 32 Sieger aus Runde 2 treffen auf die Plätze 33–64 der Order of Merit.
    * Runde 4: Die 32 Sieger aus Runde 3 treffen auf die Top 32 der Order of Merit.

#### 2.2 European Open
*   alles genau wie bei UK Open

#### 2.3 World Matchplay
*   **Teilnehmer:** 32.
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
*   **Modus:** 
    * K.O.-System
    * Leg
    * „Two-Clear-Legs Rule“: ein Spiel muss mit 2 Legs Vorsprung gewonnen werden, ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs vorsprung hat. Hat ein Spieler 3 Legs mehr gewonnen, als nötig greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung. 
*   **Setzliste:**
    * OOM-Platz 1 spielt gegen Pro Tour Platz 16
    * OOM-Platz 2 spielt gegen Pro Tour Platz 15
    * OOM-Platz 3 spielt gegen Pro Tour Platz 14
    * ... und so weiter, bis:
    * OOM-Platz 16 gegen Pro Tour Platz 1 spielt.

#### 2.4 World Grand Prix
*   **Teilnehmer:** 32
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
*   **Modus:**
    * K.O.-System
    * Set (jedes Set: best of 3 Legs)
    * „Two-Clear-Legs Rule“: Greift erst, wenn beide Spieler nur noch einen Satz zum Sieg benötigen. Dieser Satz muss mit 2 Legs Vorsprung gewonnen werden, ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs vorsprung hat. Hat ein Spieler 6 Legs gewonnen, greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung (6:5). 
*   **Setzliste:**
    * Die Top 8 der Order of Merit sind gesetzt
    * Platz 9-16 der OOM: Diese acht Spieler sind zwar ebenfalls für die 1. Runde „gesetzt“ (sie können also in Runde 1 nicht gegen die Top 8 oder gegeneinander spielen), aber ihre genaue Position im Turnierbaum wird ausgelost.
    * Die 16 Spieler, die sich über die Pro Tour Order of Merit qualifiziert haben, sind völlig ungesetzt.
    
#### 2.5 European Championship (MAJOR_LEG)
*   **Teilnehmer:** 32.
*   **Zusammensetzung & Qualifikation:**
    1. Top 32 der European Tour Order of Merit der laufenden Saison.
*   **Modus:**
    * K.O.-System
    * Leg
*   **Setzliste:** 
    * Setzung strikt nach Rangliste (Platz 1 gegen Platz 32). Reines K.o.-System im Leg-Format.

#### 2.6 Grand Slam of Darts (MAJOR_GROUP)
*   **Teilnehmer:** 48
*   **Modus:**
    * Spieler in 16 Gruppen (A bis P) zu je 3 Spielern.
    * Leg
*   **Zusammensetzung & Qualifikation:**
    * Die Top 16 der Order of Merit sind qualifiziert und als Gruppenköpfe gesetzt.
    * Die nächsten 16 Plätze werden wie folgt vergeben, bis 16 Spieler ausgewählt wurden (ist ein Spieler bereits qualifiert, wird dem nächsten Punkt fortgefahren). Sind die Spieler ermittelt, wird jeder Zufällig einer Gruppe zugelost.
       1. Sieger letzes World Championship
       2. Sieger letzes Grand Slam of Darts
       3. Sieger letzes World Matchplay
       4. Sieger letzes World Grand Prix
       5. Sieger letzes UK Open
       6. Sieger letzes European
       7. Finalist letztes World Championship
       8. Finalist letztes Grand Slam of Darts
       9. Finalist letztes World Matchplay Finalist
       10. Finalist letztes World Grand Prix
       11. Finalist letztes UK Open
       12. Finalist letztes European Championship
       13. Gewinner der diesjährigen European Tour Turniere (gereiht nach Anzahl der Siege, bei Gleichstand nach Position in Order of Merit). 
       14. Gewinner der diesjährigen Players Championship Turniere (gereiht nach Anzahl der Siege, bei Gleichstand nach Position in Order of Merit).
      15. Gibt es dann immer noch freie Plätze, rücken die am höchsten platzierten, noch nicht qualifizierten Spieler aus der Pro Tour Order of Merit nach
    * 8 Plätze: Die 8 Sieger des Tour Card Holder Qualifiers. Diese Spieler werden zufällig je einer Gruppe zugelost.
    * 8 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal. Diese Spieler werden zufällig je einer Gruppe zugelost.
*   **Gruppenphase:**
    Die Auslosung erfolgt über drei exakte Lostöpfe:
    * Topf 1 (Die gesetzten Köpfe): Die Weltranglistenplätze 1 bis 16. Platz 1 geht in Gruppe A, Platz 2 in Gruppe B... bis Platz 16 in Gruppe P.
    * Topf 2 (16 Spieler): Die 16 Major-Qualifikanten. Sie werden den Gruppen A–P frei zugelost.
    * Topf 3 (16 Spieler): Die 16 Qualifierspieler. Sie werden ebenfalls den Gruppen A–P frei zugelost.
    Der exakte Ablauf der Dreiergruppe (Best of 9 Legs / First to 5):
    Da es eine ungerade Spieleranzahl pro Gruppe gibt, setzt ein Spieler pro Spieltag aus:
    * Tag 1: Der gesetzte Spieler (Topf 1) spielt gegen den Qualifier-Spieler (Topf 3). Der Major-Qualifikant (Topf 2) setzt aus.
    * Tag 2: Der Major-Qualifikant (Topf 2) steigt ein und spielt gegen den Verlierer von Tag 1. Der Sieger von Tag 1 setzt aus.
    * Tag 3: Der Major-Qualifikant (Topf 2) spielt gegen den Sieger von Tag 1. Der Verlierer von Tag 1 setzt aus.
    Die Top 2 jeder Gruppe qualifizieren sich für die K.-o.-Runde (insgesamt 32 Spieler).
*   **K.O.-Phase (Das 32er-Turnierbaum-System)**
    *  Die 16 Gruppensieger werden strikt gegen die 16 Gruppenzweiten gekreuzt:
        * Sieger Gruppe A gegen Zweiter Gruppe B
        * Sieger Gruppe B gegen Zweiter Gruppe A
        * Sieger Gruppe C gegen Zweiter Gruppe D
        * Sieger Gruppe D gegen Zweiter Gruppe C
        * ... das System läuft exakt so weiter paarweise durch bis: Sieger Gruppe O gegen Zweiter Gruppe P / Sieger Gruppe P gegen Zweiter Gruppe O.

#### 2.7 Players Championship Finals (MAJOR_LEG)
*   **Teilnehmer:** 64.
*   **Zusammensetzung & Qualifikation:**
    1. Top 64 der Players Championship Order of Merit der laufenden Saison.
*   **Modus:**
    * Leg
*   **Setzliste:**
    * Setzung strikt nach Rangliste der Players Championship Order of Merit (Platz 1 gegen Platz 64). Reines K.O.-System im Leg-Format.

#### 2.8 World Championship (MAJOR_SET)
*   **Teilnehmer:** 128.
*   **Zusammensetzung & Qualifikation:**
    1. Top 40 der Order of Merit
    2. Top 40 der Pro Tour Order of Merit, die nicht bereits durch 1. qualifiziert sind
    3. 48 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal.
*   **Modus:**
    * Set (jedes Set: best of 3 Legs)
    * „Two-Clear-Legs Rule“: Greift erst, wenn beide Spieler nur noch einen Satz zum Sieg benötigen. Dieser Satz muss mit 2 Legs Vorsprung gewonnen werden, ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs vorsprung hat. Hat ein Spieler 6 Legs gewonnen, greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung (6:5).
*   **Setzliste:**
    1. Die Qualifizierten Spier, werden nach ihrer Position in der Order of Merit sortiert.  
    2. Platz 1-32 aus dieser Liste sind auf Platz 1-32 gesetzt
    3. Platz 33-64 aus dieser Liste werden zufällig den Plätzen 33-64 zugelost.
    4. Die restlichen 64 Spieler werden zufällig zugelost.

---

## 💰 3. Preisgeldregeln & Ranglisten (Orders of Merit)

Das System führt getrennte, dynamische Ranglisten, basierend auf den kumulierten Daten der Karriere aus der Tabelle `tournament_placements`.

### 3.1 Die 4 Ranglisten

Alle Ranglisten errechnen sich aus den Preisgeldern (`TournamentPlacement`.`prize_money`), der in dieser Karriere gespielten Tourniere.
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