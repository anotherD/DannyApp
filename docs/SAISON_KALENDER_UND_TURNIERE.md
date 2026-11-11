# 🎯 Fachkonzept: Saisonkalender, Turniertypen & Ranglisten (Pro Tour & Majors)

Dieses Dokument definiert die Architektur, die Datenstrukturen und die Fachlogiken für den Turnier-, Kalender- und Match-Simulationsmodus der **Danny Darts App**.

---

## 🏛️ 1. Architektur-Erweiterung & Datenmodell (Room v13)

### 1.1 Die User-ID Konvention (Globale Konstante)
Um Nullable-Fremdschlüssel-Probleme und komplexe Boolean-Flags zu vermeiden, erhält der menschliche User im gesamten System eine feste, eindeutige ID:
* **`USER_ID = -1L`**
  Diese ID wird in `tour_cards`, `tournament_matches` und `tournament_placements` verwendet. Bei der Spieler-Stammdatenbank (`players`) wird beim CSV-Import sichergestellt, dass kein CPU-Spieler die ID `-1` oder `0` erhalten kann (Auto-Increment startet bei 1).

### 1.2 Implementierte Entitäten (Room v13)

Die Tabellenstrukturen für den Turnierbetrieb wurden wie folgt implementiert:

*   **`Tournament` (`tournaments`)**: Speichert Saisonturniere mit `id`, `season_id`, `name`, `type` (`PLAYERS_CHAMPIONSHIP`, `EUROPEAN_TOUR`, `MAJOR_LEG`, `MAJOR_SET`, `MAJOR_GROUP`, `QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`), `order` (1–73), `is_completed` und `host_country` (`host_country`, z. B. `"GER"`, `"AUT"`, `"ENG"`).
*   **`TournamentMatch` (`tournament_matches`)**: Verwaltet alle Einzelpartien mit `id`, `tournament_id`, `round`, `match_index`, `player_1_id`, `player_2_id` (jeweils Spieler-ID oder `USER_ID = -1L`), Scores, `winner_id` und `is_completed`.
*   **`TournamentPlacement` (`tournament_placements`)**: Erfasst die errungenen Platzierungen und Preisgelder pro Spieler zur Berechnung der Ranglisten (`id`, `tournament_id`, `player_id`, `reached_round`, `prize_money`).

---

## 📅 2. Turniertypen, Qualifikation & Kalenderstruktur

Es gibt 3 Turniertypen:
* PC: Players Championship, 34 pro Saison
* ET: European Tour, 15 pro Saison
* MAJOR: Major-Turniere, 8 pro Saison

Natürlich kann kein Spieler mehrfach an einem Turnier teilnehmen.

### 2.1 Players Championship (PC)
*   **Teilnehmer:** 128 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1. Alle 128 Tour-Card-Inhaber (inklusive User mit ID `-1L`).
*   **Auslosung:** Top 32 der *Pro Tour Order of Merit* sind gesetzt und werden im Bracket verteilt. Die restlichen 96 Spieler werden frei zugelost. Da beim Start einer Karriere die *Pro Tour Order of Merit* noch leer ist, werden hierfür die Top 32 zufällig ermittelt.
*   **Preisgeldaufschlüsselung (Gesamt: £150.000 pro Event):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £15.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £10.000 |
| **Halbfinale (SF)** | `SF` | 2 | £6.500 |
| **Viertelfinale (QF)** | `QF` | 4 | £4.000 |
| **Achtelfinale (L16 / R4)** | `L16` | 8 | £3.000 |
| **Letzte 32 (R3)** | `L32` | 16 | £2.000 |
| **Letzte 64 (R2)** | `L64` | 32 | £1.250 |
| **Letzte 128 (R1-Verlierer)** | `L128` | 64 | **£0** *(kein Preisgeld)* |

*   **Ausnahmen & Besonderheiten (PC):**
    *   **Runde-1-Verlierer:** Wer in der 1. Runde (Last 128) ausscheidet, erhält **£0**.
    *   **Freilos-Regel (Bye):** Erhält ein Spieler durch ein nicht volles Teilnehmerfeld ein Freilos in Runde 2 und verliert dort, erhält er das Preisgeld von Runde 1 (**£0**).
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* Sagen Tour-Card-Inhaber für ein PC-Event ab, rücken Spieler aus der *Challenge Tour Order of Merit* (und bei Bedarf *Development Tour*) nach, um das 128er-Feld aufzufüllen. Da diese Secondary Tours aktuell nicht simuliert werden, wird das Feld ggf. aus Nicht-Tourcard-Spielern (`tour_card_year == 0`) aufgefüllt oder mit Byes gearbeitet.

### 2.2 European Tour (ET) & ET Tour Card Holder Qualifier (`QUALIFIER_ET`)
*   **ET Qualifier (`QUALIFIER_ET`):** Vor jedem der 15 European Tour Events findet ein Tour Card Holder Qualifier statt. Daran nehmen alle Tour-Card-Inhaber (inkl. `USER_ID = -1L`) teil, die nicht bereits über die Top 16 OOM oder Top 16 PT OOM vorqualifiziert sind. Das Turnier läuft im K.o.-Modus, bis exakt 10 Sieger (Qualifikanten) feststehen (£0 Preisgeld).
*   **Teilnehmer (European Tour):** 48 Spieler.
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
    3. 10 Gewinner des ET Tour Card Holder Qualifiers (`QUALIFIER_ET`)
    4. 4 Gewinner des Host Nation Qualifiers: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen Saison. Es werden primär Spieler aus dem Gastgeberland (`host_country`) berücksichtigt und bei Bedarf international aufgefüllt.
    5. 2 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen Saison, die nicht bereits qualifiziert sind.
*   **Modus:**
    * K.O.-System
    * Leg
*   **Setzliste:** Runde 1 spielen die 32 Qualifikanten (16 Matches). In Runde 2 steigen die 16 gesetzten Spieler ein. Die Zuordnung in Runde 2 folgt einem festen mathematischen Schlüssel (Kreuzvergleich), der die am höchsten gesetzten Spieler schützt:
    * Die Nummer 1 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 16 (vs. ungesetzter Qualifikant).
    * Die Nummer 2 der Weltrangliste spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 15 (vs. ungesetzter Qualifikant).
    * ... und so weiter, bis:
    * Die Nummer 16 der Weltrangliste gegen den Sieger von Pro Tour Seed Nr. 1 spielt.
*   **Preisgeldaufschlüsselung (Gesamt: £230.000 pro Event):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £35.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £15.000 |
| **Halbfinale (SF)** | `SF` | 2 | £10.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £8.000 |
| **Achtelfinale (L16 / R3)** | `L16` | 8 | £5.000 |
| **Letzte 32 (R2)** | `L32` | 16 | £3.500\* |
| **Letzte 48 (R1-Verlierer)** | `L48` | 16 | £2.000\* |

*   **\*Ausnahmen & Besonderheiten (ET – Extrem wichtig für die Ranglisten-Logik!):**
    *   **Auftaktniederlage gesetzter/vorqualifizierter Spieler:** Alle 32 vorqualifizierten Spieler (Top 16 OOM, die in R2 einsteigen, sowie Top 16 Pro Tour, die in R1 starten) erhalten ihr Preisgeld zwar real ausgezahlt, aber **£0 für die Ranglisten (OOM, PT OOM, ET OOM)**, wenn sie ihr **erstes Match** im Turnier verlieren! Für `TournamentPlacement.prize_money` (Ranglistenwert) muss in diesem Fall `0.0` gespeichert werden.
    *   Qualifikanten (die 16 Spieler aus den Qualifiern) erhalten ihr Preisgeld auch bei einer Auftaktniederlage in Runde 1 (£2.000) voll für alle Ranglisten angerechnet.
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* Die 6 Nicht-Tourcard-Plätze (Punkte 4 & 5) teilen sich in der Realität auf in **4 Host Nation Qualifier**, **1 Nordic & Baltic Qualifier** sowie **1 East Europe Qualifier**. Im Konzept sind die beiden regionalen Tour-Qualifier (Nordic & Baltic / East Europe) aktuell zu „2 Gewinner anderer Qualifier“ ohne Nationsbindung zusammengefasst, da diese regionalen Toursysteme noch nicht abgebildet sind.

### 2.3 Die Major-Turniere

**Major-Turniere folgen spezifischen Format- und Qualifikationsregeln:**

#### 2.1 UK Open
*   **Teilnehmer:** 160.
*   **Zusammensetzung & Qualifikation:**
    1. Alle 128 Tourcard-Inhaber der aktuellen Saison.
    2. 32 Qualifikanten ohne Tourcard (`tour_card_year == 0`), sortiert nach `skill DESC` aus der `players`-Tabelle.
*   **Modus:**
    * K.O.-System
    * Legs
*   **Setzliste:** ohne feste Setzliste. Runden 1–3 gestaffelt nach Ranglistenplatzierung:
    * Runde 1 (`R1_160`): Die Plätze 97–128 der Order of Merit + alle 32 Qualifikanten (32 Matches -> 32 Verlierer scheiden aus).
    * Runde 2 (`R2_128`): Die 32 Sieger aus Runde 1 treffen auf die Plätze 65–96 der Order of Merit (32 Matches -> 32 Verlierer scheiden aus).
    * Runde 3 (`R3_96`): Die 32 Sieger aus Runde 2 treffen auf die Plätze 33–64 der Order of Merit (32 Matches -> 32 Verlierer scheiden aus).
    * Runde 4 (`L64`): Die 32 Sieger aus Runde 3 treffen auf die Top 32 der Order of Merit (32 Matches -> 32 Verlierer scheiden aus).
*   **Preisgeldaufschlüsselung (Gesamt: £750.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £120.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £60.000 |
| **Halbfinale (SF)** | `SF` | 2 | £35.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £20.000 |
| **Achtelfinale (L16 / R6)** | `L16` | 8 | £12.500 |
| **Letzte 32 (R5)** | `L32` | 16 | £7.500 |
| **Letzte 64 (R4)** | `L64` | 32 | £3.000 |
| **Letzte 96 (R3)** | `R3_96` | 32 | £2.000 |
| **Letzte 128 (R2)** | `R2_128` | 32 | £1.250 |
| **Letzte 160 (R1-Verlierer)** | `R1_160` | 32 | **£0** *(kein Preisgeld)* |

*   **Ausnahmen & Besonderheiten (UK Open):**
    *   **Runde-1-Verlierer (`R1_160`):** Alle Spieler, die in Runde 1 (Last 160) verlieren – sowohl die 32 Qualifikanten als auch die Tour-Card-Inhaber der Plätze 97–128 –, gehen komplett leer aus (**£0**).
    *   **Einstieg in spätere Runden (Top 96):** Spieler, die aufgrund ihrer Weltranglistenposition erst in Runde 2 (`R2_128`, Plätze 65–96), Runde 3 (`R3_96`, Plätze 33–64) oder Runde 4 (`L64`, Top 32) einsteigen und dort sofort ihr erstes Match verlieren, erhalten regulär das Preisgeld dieser Runde (£1.250, £2.000 bzw. £3.000) voll für die Order of Merit angerechnet. Hier gibt es *keine* Nullrunde wie auf der European Tour.
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* Die 32 Nicht-Tourcard-Plätze setzen sich in der Realität zusammen aus den **Top 8 der Challenge Tour Order of Merit**, den **Top 8 der Development Tour Order of Merit** (jeweils des Vorjahres, sofern keine Tour Card gewonnen wurde) sowie **16 Amateur-Qualifikanten** (8x Rileys Amateur Qualifiers + 8x PDC in the Community Qualifiers). In der App wird dies aktuell vereinfacht über die Top 32 Nicht-Tourcard-Spieler nach `skill DESC` abgebildet.

#### 2.2 European Open (Neues PDC-Major ab 2027)
*   Alles genau wie bei den UK Open (160 Teilnehmer, gestaffelter Rundeneinstieg und identische Preisgeldverteilung mit **£750.000** Gesamtpreisgeld).
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* Bei der Premiere ab 2027 in Salzburg (160 Spieler) teilen sich die 32 Nicht-Tourcard-Plätze auf in **Top 4 Challenge Tour**, **Top 4 Development Tour**, **8 PDC Europe Next Gen / START Qualifier** sowie **16 internationale Qualifikanten**. In der App wird dies wie bei den UK Open über die Top 32 Nicht-Tourcard-Spieler nach `skill DESC` abgebildet.

#### 2.3 World Matchplay
*   **Teilnehmer:** 32.
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
*   **Modus:** 
    * K.O.-System
    * Leg
    * „Two-Clear-Legs Rule“: Ein Spiel muss mit 2 Legs Vorsprung gewonnen werden; ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs Vorsprung hat. Hat ein Spieler 3 Legs mehr gewonnen, als nötig, greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung (Sudden-Death-Leg).
*   **Setzliste / Auslosung (1:1 wie offizielle PDC-Regel):**
    * Die **Top 16 der Order of Merit** sind auf den Plätzen 1 bis 16 im Turnierbaum gesetzt (damit sie nicht in Runde 1 aufeinandertreffen).
    * Die **16 Qualifikanten der Pro Tour Order of Merit** sind ungesetzt und werden den 16 gesetzten Spielern in Runde 1 **frei zugelost** (anstatt starr 1 vs. 16 zu kreuzen).
*   **Preisgeldaufschlüsselung (Gesamt: £1.000.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £225.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £125.000 |
| **Halbfinale (SF)** | `SF` | 2 | £65.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £35.000 |
| **Achtelfinale (L16 / R2)** | `L16` | 8 | £22.500 |
| **Letzte 32 (R1-Verlierer)** | `L32` | 16 | £12.500 |

*   **Ausnahmen & Besonderheiten:** Alle 32 Teilnehmer erhalten bei regulärer Teilnahme mindestens £12.500 voll für die Order of Merit angerechnet.
*   *(Keine App-Vereinfachung nötig: Teilnehmerfeld, freie Auslosung der 16 Pro-Tour-Qualifikanten gegen die Top 16 Seeds und Preisgelder entsprechen 1:1 dem offiziellen PDC-Regelwerk).*

#### 2.4 World Grand Prix
*   **Teilnehmer:** 32
*   **Zusammensetzung & Qualifikation:**
    1. Top 16 der Order of Merit
    2. Top 16 der Pro Tour Order of Merit, die nicht bereits über Punkt 1 qualifiziert sind.
*   **Modus:**
    * K.O.-System
    * Set (jedes Set: best of 3 Legs)
    * „Two-Clear-Legs Rule“: Greift erst, wenn beide Spieler nur noch einen Satz zum Sieg benötigen. Dieser Satz muss mit 2 Legs Vorsprung gewonnen werden; ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs Vorsprung hat. Hat ein Spieler 6 Legs gewonnen, greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung (6:5). 
*   **Setzliste:**
    * Die Top 8 der Order of Merit sind gesetzt.
    * Platz 9–16 der OOM: Diese acht Spieler sind zwar ebenfalls für die 1. Runde „gesetzt“ (sie können also in Runde 1 nicht gegen die Top 8 oder gegeneinander spielen), aber ihre genaue Position im Turnierbaum wird ausgelost.
    * Die 16 Spieler, die sich über die Pro Tour Order of Merit qualifiziert haben, sind völlig ungesetzt.
*   **Preisgeldaufschlüsselung (Gesamt: £750.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £150.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £80.000 |
| **Halbfinale (SF)** | `SF` | 2 | £50.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £35.000 |
| **Achtelfinale (L16 / R2)** | `L16` | 8 | £20.000 |
| **Letzte 32 (R1-Verlierer)** | `L32` | 16 | £7.500 |

*   **Ausnahmen & Besonderheiten:** Alle 32 Teilnehmer erhalten bei regulärer Teilnahme mindestens £7.500 voll für die Order of Merit angerechnet.
*   *(Keine App-Vereinfachung nötig: Teilnehmerfeld und Preisgelder entsprechen 1:1 dem offiziellen PDC-Regelwerk).*

#### 2.5 European Championship (MAJOR_LEG)
*   **Teilnehmer:** 32.
*   **Zusammensetzung & Qualifikation:**
    1. Top 32 der European Tour Order of Merit der laufenden Saison.
*   **Modus:**
    * K.O.-System
    * Leg
*   **Setzliste:** 
    * Setzung strikt nach Rangliste (Platz 1 gegen Platz 32). Reines K.o.-System im Leg-Format.
*   **Preisgeldaufschlüsselung (Gesamt: £750.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £150.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £80.000 |
| **Halbfinale (SF)** | `SF` | 2 | £50.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £35.000 |
| **Achtelfinale (L16 / R2)** | `L16` | 8 | £20.000 |
| **Letzte 32 (R1-Verlierer)** | `L32` | 16 | £7.500 |

*   **Ausnahmen & Besonderheiten:** Alle 32 Teilnehmer erhalten bei regulärer Teilnahme mindestens £7.500 voll für die Order of Merit angerechnet.
*   *(Hinweis für zukünftige Saison-Updates: Für 2026 gilt das obige 32er-Feld mit £750.000; ab Herbst 2027 erweitert die PDC die European Championship auf 48 Spieler und £1.000.000 Preisgeld).*

#### 2.6 Grand Slam of Darts (MAJOR_GROUP)
*   **Teilnehmer:** 48 (Offizielles PDC-Format ab 2026)
*   **Modus:**
    * 16 Gruppen (A bis P) zu je 3 Spielern (Leg-Format: Best of 9 Legs).
    * **Aufstieg in die K.-o.-Runde:** Nur der **Gruppenerste** jeder Gruppe (insgesamt 16 Spieler) zieht direkt in die K.-o.-Runde (`L16` / Achtelfinale) ein. Gruppenzweite und Gruppendritte scheiden nach der Gruppenphase aus.
*   **Zusammensetzung & Qualifikation:**
    * Die Top 16 der Order of Merit sind qualifiziert und als Gruppenköpfe gesetzt.
    * Die nächsten 16 Plätze werden wie folgt vergeben, bis 16 Spieler ausgewählt wurden (ist ein Spieler bereits qualifiziert, wird mit dem nächsten Punkt fortgefahren). Sind die Spieler ermittelt, wird jeder zufällig einer Gruppe zugelost:
       1. Sieger letztes World Championship
       2. Sieger letztes Grand Slam of Darts
       3. Sieger letztes World Matchplay
       4. Sieger letztes World Grand Prix
       5. Sieger letztes UK Open
       6. Sieger letztes European Championship
       7. Finalist letztes World Championship
       8. Finalist letztes Grand Slam of Darts
       9. Finalist letztes World Matchplay
       10. Finalist letztes World Grand Prix
       11. Finalist letztes UK Open
       12. Finalist letztes European Championship
       13. Gewinner der diesjährigen European Tour Turniere (gereiht nach Anzahl der Siege, bei Gleichstand nach Position in Order of Merit). 
       14. Gewinner der diesjährigen Players Championship Turniere (gereiht nach Anzahl der Siege, bei Gleichstand nach Position in Order of Merit).
       15. Gibt es dann immer noch freie Plätze, rücken die am höchsten platzierten, noch nicht qualifizierten Spieler aus der Pro Tour Order of Merit nach.
    * 8 Plätze: Die 8 Sieger des Tour Card Holder Qualifiers. Diese Spieler werden zufällig je einer Gruppe zugelost.
    * 8 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen Saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal. Diese Spieler werden zufällig je einer Gruppe zugelost.
*   **Gruppenphase:**
    Die Auslosung erfolgt über drei exakte Lostöpfe:
    * Topf 1 (Die gesetzten Köpfe): Die Weltranglistenplätze 1 bis 16. Platz 1 geht in Gruppe A, Platz 2 in Gruppe B... bis Platz 16 in Gruppe P.
    * Topf 2 (16 Spieler): Die 16 Major-/ProTour-Qualifikanten. Sie werden den Gruppen A–P frei zugelost.
    * Topf 3 (16 Spieler): Die 16 Qualifierspieler. Sie werden ebenfalls den Gruppen A–P frei zugelost.
    Der exakte Ablauf der Dreiergruppe (Best of 9 Legs / First to 5):
    Da es eine ungerade Spieleranzahl pro Gruppe gibt, setzt ein Spieler pro Spieltag aus:
    * Tag 1: Der gesetzte Spieler (Topf 1) spielt gegen den Qualifier-Spieler (Topf 3). Der Spieler aus Topf 2 setzt aus.
    * Tag 2: Der Spieler aus Topf 2 steigt ein und spielt gegen den Verlierer von Tag 1. Der Sieger von Tag 1 setzt aus.
    * Tag 3: Der Spieler aus Topf 2 spielt gegen den Sieger von Tag 1. Der Verlierer von Tag 1 setzt aus.
*   **K.O.-Phase (Das 16er-Turnierbaum-System ab Achtelfinale `L16`):**
    * Die 16 Gruppensieger treten direkt im Achtelfinale (`L16`) paarweise gegeneinander an:
        * Sieger Gruppe A gegen Sieger Gruppe B
        * Sieger Gruppe C gegen Sieger Gruppe D
        * Sieger Gruppe E gegen Sieger Gruppe F
        * ... das System läuft exakt so weiter paarweise durch bis: Sieger Gruppe O gegen Sieger Gruppe P.
*   **Preisgeldaufschlüsselung (Gesamt: £1.000.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £200.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £100.000 |
| **Halbfinale (SF)** | `SF` | 2 | £60.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £35.000 |
| **Achtelfinale (L16)** | `L16` | 8 | £20.000 |
| **Gruppenzweiter (Aus in Gruppe)** | `GROUP_2ND` | 16 | £12.500 |
| **Gruppendritter (Aus in Gruppe)** | `GROUP_3RD` | 16 | £5.000 |

*   **Ausnahmen & Besonderheiten:** Der früher übliche Gruppensieger-Bonus von £3.500 wurde mit der Formatumstellung 2026 abgeschafft. Alle Preisgelder zählen voll für die Order of Merit.
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* In der Realität erhalten bis zu 12 automatische Startplätze nicht nur Major-Finalisten, sondern auch Sieger/Finalisten von **World Series of Darts Finals**, **World Cup of Darts**, **World Youth Championship** (Sieger & Finalist), **Women's World Matchplay** (Siegerin), **Women's Series** (#1), **Challenge Tour** (#1), **Development Tour** (#1) sowie regionale Champions (CDC Continental Cup, Asian Championship). Außerdem sind die Top 16 der OOM nicht pauschal über ihre Weltrangliste gesetzt, sondern nur die 16 höchstplatzierten Qualifikanten werden als Gruppenköpfe gesetzt. Im Konzept werden diese Secondary-/Women's-/World-Series-Plätze aktuell durch das Nachrücken über die *Pro Tour Order of Merit* sowie die *8 Nicht-Tourcard-Qualifier* kompensiert.

#### 2.7 Players Championship Finals (MAJOR_LEG)
*   **Teilnehmer:** 64.
*   **Zusammensetzung & Qualifikation:**
    1. Top 64 der Players Championship Order of Merit der laufenden Saison.
*   **Modus:**
    * Leg
*   **Setzliste:**
    * Setzung strikt nach Rangliste der Players Championship Order of Merit (Platz 1 gegen Platz 64). Reines K.O.-System im Leg-Format.
*   **Preisgeldaufschlüsselung (Gesamt: £750.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £130.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £70.000 |
| **Halbfinale (SF)** | `SF` | 2 | £40.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £27.500 |
| **Achtelfinale (L16 / R3)** | `L16` | 8 | £15.000 |
| **Letzte 32 (R2)** | `L32` | 16 | £7.000 |
| **Letzte 64 (R1-Verlierer)** | `L64` | 32 | £4.000 |

*   **Ausnahmen & Besonderheiten:** Alle 64 Teilnehmer erhalten bei regulärer Teilnahme mindestens £4.000 voll für die Order of Merit angerechnet.
*   *(Hinweis für zukünftige Saison-Updates: Für 2026 gilt das 64er-Feld; ab 2027 plant die PDC eine Erweiterung der Players Championship Finals auf 128 Spieler).*

#### 2.8 World Championship (MAJOR_SET)
*   **Teilnehmer:** 128.
*   **Zusammensetzung & Qualifikation:**
    1. Top 40 der Order of Merit
    2. Top 40 der Pro Tour Order of Merit, die nicht bereits durch 1. qualifiziert sind
    3. 48 Gewinner anderer Qualifier: Plätze für Nicht-Tourcard-Spieler (`tour_card_year == 0`) der aktuellen Saison, die nicht bereits qualifiziert sind. Die Nation ist hier egal.
*   **Modus:**
    * Set (jedes Set: best of 5 Legs)
    * „Two-Clear-Legs Rule“: Greift erst, wenn beide Spieler nur noch einen Satz zum Sieg benötigen. Dieser Satz muss mit 2 Legs Vorsprung gewonnen werden; ist dies nicht der Fall, wird weiter gespielt, bis ein Spieler 2 Legs Vorsprung hat. Hat ein Spieler 6 Legs gewonnen, greift diese Regel nicht mehr, und es reicht ein Leg Vorsprung (6:5).
*   **Setzliste:**
    1. Die qualifizierten Spieler werden nach ihrer Position in der Order of Merit sortiert.  
    2. Platz 1–32 aus dieser Liste sind auf Platz 1–32 gesetzt.
    3. Platz 33–64 aus dieser Liste werden zufällig den Plätzen 33–64 zugelost.
    4. Die restlichen 64 Spieler werden zufällig zugelost.
*   **Preisgeldaufschlüsselung (Gesamt: £5.000.000):**

| Platzierung / Runde | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
| :--- | :--- | :--- | :--- |
| **Sieger** | `WINNER` | 1 | £1.000.000 |
| **Finalist (Runner-Up)** | `F` | 1 | £400.000 |
| **Halbfinale (SF)** | `SF` | 2 | £200.000 |
| **Viertelfinale (QF)** | `QF` | 4 | £100.000 |
| **Achtelfinale (L16 / R4)** | `L16` | 8 | £60.000 |
| **Letzte 32 (R3)** | `L32` | 16 | £35.000 |
| **Letzte 64 (R2)** | `L64` | 32 | £25.000 |
| **Letzte 128 (R1-Verlierer)** | `L128` | 64 | £15.000 |

*   **Ausnahmen & Besonderheiten:** Seit der Erweiterung auf 128 Spieler (Saison 2025/26 bzw. 2026/27) starten auch die Top 32 in Runde 1 (Last 128). Verliert ein gesetzter Spieler in Runde 1, erhält er die £15.000 dennoch voll für die Order of Merit angerechnet. *(Optionaler Sonderbonus: Ein 9-Darter bei der WM wird oft mit £60.000 gesponsert, zählt aber **nicht** zur Order of Merit).*
*   **Hinweis zur App-Vereinfachung vs. reale PDC-Regeln:**
    *   *Reale PDC:* Die 48 internationalen Plätze (Punkt 3) setzen sich in der Realität aus festen Kontingenten zusammen, darunter **Women's Series** (mindestens 4 Plätze inkl. Women's World Matchplay), **Development Tour** (Top 3 + World Youth Champion), **Challenge Tour** (Top 3), regionale Touren (**Asian Tour**, **Nordic & Baltic**, **CDC Nordamerika**, **ANZ/Australien**, **Afrika**) sowie regionale Super Leagues (z. B. **DACH Super League**) und ein Tour Card Holder Last-Chance Qualifier. Im Konzept sind diese 48 Plätze aktuell als einheitlicher Pool freier Nicht-Tourcard-Qualifier zusammengefasst.

---

## 💰 3. Preisgeldregeln & Ranglisten (Orders of Merit)

Das System führt getrennte, dynamische Ranglisten, basierend auf den kumulierten Daten der Karriere aus der Tabelle `tournament_placements`.

### 3.1 Die 4 Ranglisten

Alle Ranglisten errechnen sich aus den Preisgeldern (`TournamentPlacement.prize_money`), der in dieser Karriere gespielten Turniere.
Der Stammdatenwert `players.prizeMoney` wird aktuell nicht verwendet (kann in einer späteren Ausbaustufe optional als historischer Startwert vor Karrierebeginn dienen).

1.  **Order of Merit:**  Kürzel: "OOM". Preisgelder aller Turniere der letzten zwei Jahre. Das heißt, die jüngsten zwei Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend). 
2.  **Pro Tour Order of Merit:** Kürzel: "PT OOM". Preisgeld aus `PLAYERS_CHAMPIONSHIP` und `EUROPEAN_TOUR` . Nur die jüngsten Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend).  
3.  **Players Championship Order of Merit:** Kürzel: "PC OOM". Ausschließlich Preisgelder aus `PLAYERS_CHAMPIONSHIP` der aktuellen Saison.
4.  **European Tour Order of Merit:** Kürzel: "ET OOM". Ausschließlich Preisgelder aus `EUROPEAN_TOUR` der aktuellen Saison.

### 3.2 Turnierübergreifende Ausnahmeregelungen (Wann gibt es £0 Preisgeld / Ranglistenpunkte?)

Ergänzend zu den jeweiligen Turnierregeln in Abschnitt 2 gelten für die Simulation der PDC-Ranglisten (gemäß *PDC Rankings Rules*, Abschnitte 3.7 & 4.17) folgende turnierübergreifende Sonderfälle:

1.  **Nichtantritt / Absage vor Turnierstart (Withdrawal, Regel 4.17):**
    *   Qualifiziert sich ein Spieler für ein Hauptfeld (Major oder European Tour), tritt aber vor seinem ersten Match nicht an, erhält er **£0** (weder Auszahlung noch Ranglistenpunkte), selbst wenn er erst in einer späteren Runde eingestiegen wäre.
2.  **Freilos (Bye) durch unvollständiges Teilnehmerfeld (Regel 4.17):**
    *   Erhält ein Spieler durch die Auslosung ein Freilos in Runde 2 und verliert dort sein erstes gespieltes Match, wird er ranglistentechnisch so behandelt, als hätte er in Runde 1 verloren (er erhält also nur das Preisgeld der 1. Runde – bei PC/UK Open somit **£0**).
    *   *Gegenausnahme:* Kommt das Freilos zustande, weil der geplante Gegner kurzfristig zurückgezogen hat (Walkover), gilt das Match als gewonnen und das Preisgeld der erreichten Runde wird regulär gewertet.
3.  **Nachrücker in spätere Runden (Late Replacement, Regel 3.7):**
    *   Rückt ein Ersatzspieler für einen ausgefallenen Spieler direkt in eine spätere Runde nach (z. B. Runde 2 bei ET oder Runde 3 bei UK Open) und verliert dort sofort sein erstes Match, erhält er nur das Preisgeld der allerersten Turnierrunde (bei UK Open also **£0**, bei ET **£2.000**). Gewinnt er sein Auftaktmatch, greift ab der nächsten Runde das reguläre Preisgeld.

### 3.3 Format-Spezifikationen für Sets und Sonderregeln
*   **Set-Modus (World Championship):** Ein Set besteht immer aus *Best of 5 Legs* (First to 3 Legs gewinnt das Set).
*   **Double-In / Double-Out (World Grand Prix):** Das Leg wird mathematisch erst heruntergezählt, sobald der erste Pfeil des Spielers in einem äußeren Doppelring oder im Double-Bull landet.

---

## 4. Spielformate (Legs & Sets)

### 4.1 Leg-Format (Pro Tour & ausgewählte Majors)
*   **Players Championship & European Tour:** Bis Viertelfinale: Best of 11 Legs. Halbfinale: Best of 13 Legs. Finale: Best of 15 Legs.
*   **UK Open & European Open:** Runden 1–3: Best of 11 Legs. Runde 4 bis Viertelfinale: Best of 19 Legs. Halbfinale und Finale: Best of 21 Legs.
*   **Grand Slam of Darts:** Gruppenphase: Best of 9 Legs. Achtelfinale: Best of 19 Legs. Viertelfinale, Halbfinale und Finale: Best of 31 Legs.
*   **World Matchplay:** Runde 1: Best of 19 Legs. Runde 2: Best of 21 Legs. Viertelfinale: Best of 31 Legs. Halbfinale: Best of 33 Legs. Finale: Best of 35 Legs.
*   **Players Championship Finals & European Championship:** Runde 1: Best of 11 Legs. Runde 2 und Viertelfinale: Best of 19 Legs. Halbfinale und Finale: Best of 21 Legs.

### 4.2 Set-Format (World Championship & World Masters)
Ein Set besteht aus *Best of 5 Legs* (First to 3 Legs gewinnt das Set).
*   **World Championship:** Runde 1 & 2: Best of 5 Sets (First to 3). Runde 3 & 4: Best of 7 Sets (First to 4). Viertelfinale: Best of 9 Sets (First to 5). Halbfinale: Best of 11 Sets (First to 6). Finale: Best of 13 Sets (First to 7).
*   **World Masters (Set-Sonderformat):** Ein Set besteht hier verkürzt aus *Best of 3 Legs* (First to 2).

### 4.3 Sonderformate & Modifikationen
*   **World Grand Prix (MAJOR_SET):** Wird im Set-Format gespielt (Runde 1: Best of 3 Sets, Runde 2 & Viertelfinale: Best of 5 Sets, Halbfinale: Best of 9 Sets, Finale: Best of 11 Sets). Es gilt die Verschärfung **Double-In / Double-Out** (das Leg startet erst, wenn ein Doppel getroffen wird, und endet auch auf einem Doppel).

---

## 🛠️ 5. Technische Umsetzung & Implementierungsübersicht

Um das MVVM-Muster sauber einzuhalten, wird die komplexe Geschäfts- und Berechnungslogik in dedizierte Use-Cases und Domain-Klassen ausgelagert.

### 5.1 Umgesetzte Klassen & Use Cases (Architektur-Übersicht)

Die folgenden zentralen Komponenten sind vollständig implementiert:

*   **`TournamentEvent`**: Enum aller Turniertypen inkl. Gesamtpreisgeld und Regel-Mapping für Match-Distanzen und Preisgeld-Auszahlungen pro Runde.
*   **`TournamentRound`**: Enum aller Turnierrunden von Gruppenphase bis Finale/Sieger.
*   **`MatchDistance`**: Data Class zur Definition von Best-of-Legs/Sets, Legs pro Set und Two-Clear-Legs-Regeln.
*   **`BracketGenerator`**: Algorithmische Generierung von Setzlisten und Erstrunden-Paarungen für alle 10 Turniertypen.
*   **`GenerateSeasonCalendarUseCase`**: Erzeugt die chronologische Liste aller Saisonturniere.
*   **`GenerateBracketUseCase`**: Generiert die Paarungen für anstehende Turniere.
*   **`SimulateCpuMatchUseCase`**: Simuliert CPU-Matches basierend auf Skill und Wahrscheinlichkeiten.
*   **`AdvanceTournamentRoundUseCase`**: Steuert den Rundenfortschritt nach Abschluss aller Partien einer Runde.
*   **`TournamentProgressionUseCase`**: Verwaltet den Gesamtfortschritt eines Turniers.
*   **`GetOrderOfMeritUseCase`**: Berechnet und liefert die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

### Verwendete Quellen zur Doppelprüfung (Stand: Saison 2026/2027)
*   **Offizielles PDC-Regelwerk & Preisgeldtabellen (2026/27):** [PDC Rankings Rules (as of January 1, 2026 – Section 2, 3, 4 & 8)](https://www.pdc.tv/rankings/pdc-rankings-rules)
*   **PDC-Ankündigung der Preisgelderhöhung ab 2026:** [PDC News: Biggest prize money increase in PDC history confirmed](https://www.pdc.tv/news/2025/03/31/biggest-prize-money-increase-pdc-history-confirmed)
*   **PDC-Erweiterung European Open & European Tour 2027:** [PDC News: Major expansion announced for PDC European events in 2027](https://www.pdc.tv/news/2026/september/25/major-expansion-announced-for-pdc-european-events-in-2027)
*   **Turnierspezifische Verifizierungen 2026/2027:** [Wikipedia – 2026 PDC Players Championship series](https://en.wikipedia.org/wiki/2026_PDC_Players_Championship_series), [Wikipedia – 2026 World Matchplay](https://en.wikipedia.org/wiki/2026_World_Matchplay), [Wikipedia – 2026 UK Open](https://en.wikipedia.org/wiki/2026_UK_Open), [Sky Sports – Grand Slam of Darts 2026](https://www.skysports.com/darts/news/13610214/grand-slam-of-darts-2026-schedule-new-format-players-prize-money-past-winners-as-luke-littler-looks-to-retain-title), [Wikipedia – 2027 PDC World Darts Championship](https://en.wikipedia.org/wiki/2027_PDC_World_Darts_Championship).
