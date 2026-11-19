# 🎯 Fachkonzept: Saisonkalender, Turniertypen & Ranglisten (Pro Tour & Majors)

Dieses Dokument definiert die Architektur, die Datenstrukturen und die Fachlogiken für den Turnier-, Kalender- und Match-Simulationsmodus der **Danny Darts App**.
Wichtig: Es sollen die Regeln, Namen und Pläne für die PDC Pro-Tour für das Jahr **2027** simuliert werden. Es ist darauf zu achten, dass keine veralteten Regeln genutzt werden.

---

## 📋 0. Grundlagen & Dokumentations-Standards (Verbindlich)

1. **Single Source of Truth:** Dieses Dokument bildet die verbindliche fachliche Grundlage für `season_calendar_2027.json` sowie die Domain-Logik (`TournamentQualificationUseCase`, `BracketGenerator`, `AdvanceTournamentRoundUseCase`, `CalculateGroupStandingsUseCase`). Im Code darf **nichts geraten** werden – fehlende Regeln werden zuerst hier geklärt, dokumentiert und erst danach implementiert.
2. **Strikte Quellenpflicht pro Turnier:** Jedes Turnier erhält am Ende seines Abschnitts die Unterrubrik **`Verifizierte Quellen`**. Zu jeder fachlichen Aussage (Preisgeld, Qualifikation, Setzliste, Tie-Breaker) muss eine konkrete, nachprüfbare Quelle hinterlegt sein.
3. **Verbindliche Pflichtfelder pro Turnier:** Jeder Turnier-Abschnitt muss in exakt dieser Reihenfolge aufgebaut sein:
   * **Rahmendaten:** Gastgeberland (`host_country`), Austragungsort (`host_city`), Teilnehmerzahl und Modus (K.o.-System oder Gruppen + K.o., Legs oder Sets).
   * **Teilnehmer & Qualifikation:** Exakte Zusammensetzung des Teilnehmerfeldes, Prioritätsreihenfolge der Qualifikationswege und Nachrücker-/Auffüll-Logik.
   * **Setzliste & Auslosung:** Nach welcher Rangliste wird gesetzt, wie werden Töpfe oder Seeds gebildet, wer erhält feste Baumpositionen (z. B. Standard-Setzbaum) und was wird frei gelost?
   * **Turnierverlauf & Paarungslogik:** Einstiegsrunden (falls gestaffelt), Spieltagsreihenfolge (bei Gruppen), Übergang in die Folgerunden (fester Turnierbaum vs. freie Neu-Auslosung) sowie vorgeschaltete Qualifier (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`).
   * **Tabellen-Sortierung (nur bei Gruppenphase):** Exakte Reihenfolge der Tie-Breaker bei Punktgleichheit.
   * **Preisgeld & Ranglisten-Ausnahmen:** Vollständige Runden-Preisgeldtabelle (rechnerisch geprüft gegen den Gesamttopf), explizite Zuordnung in welche Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`) das Preisgeld einfließt sowie Sonderregeln (z. B. £0 bei Auftaktniederlage, £0 in Runde 1).
   * **App-Vereinfachung vs. reale PDC-Regeln:** Klare Gegenüberstellung, wo die App bewusst vereinfacht, inklusive Verweis auf die reale PDC-Quelle.
   * **Verifizierte Quellen:** Konkrete Links zu genau diesem Turnier.

---

## 🏛️ 1. Architektur-Erweiterung & Datenmodell

### 1.1 Die User-ID Konvention (Globale Konstante)
Um Nullable-Fremdschlüssel-Probleme und komplexe Boolean-Flags zu vermeiden, erhält der menschliche User im gesamten System eine feste, eindeutige ID:
* **`USER_ID = -1L`**
  Diese ID wird in `tour_cards`, `tournament_matches` und `tournament_placements` verwendet. Bei der Spieler-Stammdatenbank (`players`) wird beim CSV-Import sichergestellt, dass kein CPU-Spieler die ID `-1` oder `0` erhalten kann (Auto-Increment startet bei 1).

### 1.2 Implementierte Entitäten

Die Tabellenstrukturen für den Turnierbetrieb wurden wie folgt implementiert:

* **`Tournament` (`tournaments`)**: Speichert Saisonturniere mit `host_country` (ISO-3-Code, z. B. `"ENG"`, `"DEU"`, `"AUT"`, `"NLD"`, `"BEL"`, `"HUN"`, `"CZE"`, `"CHE"`, `"FRA"`, `"POL"`, `"SVK"`) sowie `host_city` (z. B. "Milton Keynes", "Dortmund"). Jedes Turnier im Kalender verfügt zwingend über einen gültigen ISO-3 `host_country`.
* **`TournamentMatch` (`tournament_matches`)**: Verwaltet alle Einzelpartien.
* **`TournamentPlacement` (`tournament_placements`)**: Erfasst die errungenen Platzierungen und Preisgelder pro Spieler zur Berechnung der Ranglisten.

---

## 📅 2. Turniertypen, Qualifikation & Kalenderstruktur

Eine Saison umfasst folgende Turnierkategorien (konfiguriert in `season_calendar_2027.json`):
* PC: Players Championship
* ET: European Tour
* ET Tour Card Qualifier (`QUALIFIER_ET`)
* MAJOR: Major-Turniere, 8 pro Saison (`UK Open`, `European Open`, `World Matchplay`, `World Grand Prix`, `European Championship`, `Grand Slam of Darts`, `Players Championship Finals`, `World Championship`)
* Major Qualifiers: 2 pro Saison (`QUALIFIER_GRAND_SLAM` vor dem Grand Slam of Darts, `QUALIFIER_WM` vor der World Championship)
* **Zentrale Kalender- & Turnier-Konfiguration (`app/src/main/assets/season_calendar_2027.json`):** Die exakte chronologische Reihenfolge aller Saisonturniere (`order`), deren Austragungsstädte (`hostCity`) und Gastgeberländer (`hostCountry`), alle Runden-Preisgelder und Match-Distanzen (`events`) sowie die Dokumentationsfelder `date` und `date_source` zur Nachvollziehbarkeit sind zentral in `season_calendar_2027.json` gepflegt (Single Source of Truth).

Für die Turnier-Vorschau, Setzlisten-, Qualifikations- und Gruppenberechnung greifen das System sowie UI-Komponenten auf die zentralen `TournamentEvent`-Properties (`isQualifier`, `isMajor`, `qualifierBoardCount`) sowie auf dedizierte Use-Cases wie `TournamentQualificationUseCase`, `CalculateGroupStandingsUseCase` und `GetTournamentSummaryUseCase` zurück.

Natürlich kann kein Spieler mehrfach an einem Turnier teilnehmen.

---

### 2.0 Zentrale Regeln für nicht-simulierte Qualifier (Regional & International)

Da regionale Touren, Super Leagues, Sekundärtouren und Amateur-Qualifier nicht als eigene Turniere simuliert werden, bildet das System alle diese Qualifikationswege zentral über Nicht-Tourcard-Spieler der aktuellen Saison (`tour_card_year == 0`, die noch nicht für das jeweilige Turnier qualifiziert sind) ab.

#### A. Regionale & Nationale Qualifier (Sortierung nach `OOM DESC`, bei Gleichstand `skill DESC`)
Für geografisch gebundene Qualifier filtert das System zunächst nach den offiziellen ISO-3-Ländercodes der jeweiligen PDC-Region und sortiert nach der **PDC Order of Merit (`OOM` DESC)**. Reichen die Spieler in der Datenbank für eine Region nicht aus, erfolgt automatisch ein **Fallback auf den internationalen Pool** (`tour_card_year == 0`, sortiert nach `OOM` DESC):

* **Host Nation Qualifier (`host_country`):** Nicht-Tourcard-Spieler mit Nationalität `host_country` des Turniers (mit internationalem Fallback).
* **Nordic & Baltic Tour (`PDCNB`):** `SWE`, `FIN`, `DNK`, `NOR`, `ISL`, `EST`, `LVA`, `LTU`, `FRO`, `GRL`.
* **East / South-East Europe & Sub-Regional Qualifier:**
  * *East / South-East Europe:* `POL`, `HUN`, `CZE`, `SVK`, `HRV`, `SVN`, `ROU`, `BGR`, `UKR`, `SRB`, `BIH`, `MKD`, `MNE`, `ALB`.
  * *Nationale / Sub-Regionale Qualifier (WM):* Tschechien (`CZE`), Polen (`POL`), Ungarn / *Hungarian Super League* (`HUN`), Südeuropa / *Mediterranean* (`ITA`, `ESP`, `PRT`, `GRC`, `CYP`, `MLT`, `TUR`, `AND`, `SMR`, `MCO`) sowie *Netherlands & Belgium Qualifier* (`NLD`, `BEL`).
* **DACH Super League (`PDCE Super League`):** `DEU`, `AUT`, `CHE`, `LIE`, `LUX`.
* **Asian Tour & Nationale Asien-Qualifier:**
  * *Asian Tour:* `PHL`, `JPN`, `CHN`, `HKG`, `TWN`, `SGP`, `MYS`, `THA`, `IND`, `KOR`, `IDN`, `VNM`, `ARE`, `BHR`, `KAZ`, `MNG` (sowie weitere PDC-Asia-Verbände).
  * *Nationale WM-Qualifier:* China (`CHN`), Japan (`JPN`), Indien (`IND`).
* **CDC Nordamerika & CDLC Lateinamerika/Karibik:**
  * *CDC (Nordamerika):* `USA`, `CAN`.
  * *CDLC (Lateinamerika & Karibik):* `BRA`, `ARG`, `CHL`, `COL`, `CRI`, `MEX`, `PAN`, `BHS`, `BRB`, `GUY`, `JAM`, `TTO`.
* **ANZ Premier League / DPA & DPNZ (Ozeanien):** `AUS`, `NZL`.
* **Afrika (`African Darts Group` – Northern & Southern Africa):** `ZAF`, `KEN`, `UGA`, `NGA`, `GHA`, `EGY`, `MAR`, `TUN`, `NAM`, `BWA`, `ZWE`, `ZMB`.

#### B. Nicht-regionale Sekundärtouren & Spezial-Qualifier (Aktueller Fallback: `.shuffled()`)
Für Qualifikationswege ohne Länderbindung, die über Geschlecht, Alter oder Q-School-Status definiert sind, nutzt die App aktuell **zufällige Nicht-Tourcard-Spieler (`tour_card_year == 0`, `.shuffled()`)**:

* **Women's Series & Women's World Matchplay:** Aktuell zufällige Nicht-Tourcard-Spieler (`.shuffled()`). *TODO:* Sobald `gender` in der `players`-Tabelle gepflegt ist, auf weibliche Nicht-Tourcard-Spieler eingrenzen.
* **Development Tour & World Youth Championship:** Aktuell zufällige Nicht-Tourcard-Spieler (`.shuffled()`). *TODO:* Sobald `birthYear` in der `players`-Tabelle gepflegt ist, auf Nicht-Tourcard-Spieler bis 23 Jahre eingrenzen.
* **Challenge Tour, UK/IRE Associate Member Qualifier & Amateur-Qualifier (Rileys, PDC START / Next Gen):** Aktuell zufällige Nicht-Tourcard-Spieler (`.shuffled()`).

#### C. Verifizierte Quellen (Abschnitt 2.0)
* [PDC News – 2026/27 Paddy Power World Darts Championship International Qualifying structure confirmed](https://www.pdc.tv/news/2026/february/24/2026-27-paddy-power-world-championship-international-qualifying-structure-confirmed/)
* [PDC Europe – Turnierformate (START, Next Gen, Super League, Host Nation & East Europe Qualifier)](https://www.pdc-europe.tv/turnierformate)
* [PDC Nordic & Baltic – Eligible Countries & European Tour Qualification](https://pdc-nordic.tv/frequently-asked-questions/)
* [PDC Asia – Eligible Territories](https://www.pdc-asia.tv/index.php?a=Index&c=Index&m=category&mid=1&cid=288)
* [Wikipedia – PDC Global Affiliate Tours 2026](https://de.wikipedia.org/wiki/PDC_Global_Affiliate_Tours_2026)
* [Wikipedia – PDC Women's Series](https://de.wikipedia.org/wiki/PDC_Women%E2%80%99s_Series)

---

---

### 2.1 Players Championship (PC)
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** 128 Spieler.
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Alle 128 Tour-Card-Inhaber der aktuellen Saison (inklusive User mit ID `-1L`).
  2. **Auffüll-Logik bei unter 128 Tour-Card-Inhabern:** Sagen Tour-Card-Inhaber ab oder sind weniger als 128 Tour Cards vergeben, wird das Feld auf 128 Spieler aufgefüllt (siehe **App-Vereinfachung vs. reale PDC-Regeln**).
* **Setzliste & Auslosung:**
  * Die Top 32 der *Pro Tour Order of Merit* (`PT OOM`) sind nach dem offiziellen Standard-Setzbaum (`buildStandardSeedOrder(32)`) über alle 16 Boards (64 Erstrunden-Matches) verteilt (Seed `seedOrder32[i]` auf Match `2 * i`, dazwischen ungesetzte Partien auf Match `2 * i + 1`), sodass `(1)` und `(2)` frühestens im Finale aufeinandertreffen.
  * Die restlichen 96 Spieler werden frei zugelost.
* **Turnierverlauf & Paarungslogik:**
  * Fester K.o.-Turnierbaum ab Runde 1 (`L128` → `L64` → `L32` → `L16` → `QF` → `SF` → `F`).
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £150.000 pro Event):**

| Platzierung / Runde           | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler     |
|:------------------------------|:---------|:---------------|:--------------------------|
| **Sieger**                    | `WINNER` | 1              | £15.000                   |
| **Finale**                    | `F`      | 1              | £10.000                   |
| **Halbfinale (SF)**           | `SF`     | 2              | £6.500                    |
| **Viertelfinale (QF)**        | `QF`     | 4              | £4.000                    |
| **Achtelfinale (L16 / R4)**   | `L16`    | 8              | £3.000                    |
| **Letzte 32 (R3)**            | `L32`    | 16             | £2.000                    |
| **Letzte 64 (R2)**            | `L64`    | 32             | £1.250                    |
| **Letzte 128 (R1-Verlierer)** | `L128`   | 64             | **£0** *(kein Preisgeld)* |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt gleichzeitig in **drei Ranglisten** ein: **PDC Order of Merit (`OOM`)**, **Pro Tour Order of Merit (`PT OOM`)** sowie **Players Championship Order of Merit (`PC OOM`)** (nicht in `ET OOM`).
  * **Runde-1-Verlierer (`L128`):** Wer in der 1. Runde (`L128`) ausscheidet, erhält **£0** (siehe auch turnierübergreifende Freilos-Regel in Abschnitt 3.2).
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * *Reale PDC:* Sagen Tour-Card-Inhaber für ein PC-Event ab, rücken Spieler aus der *Challenge Tour Order of Merit* (und bei Bedarf *Development Tour Order of Merit*) nach, um das 128er-Feld aufzufüllen.
  * *App-Vereinfachung:* Da diese Secondary Tours nicht simuliert werden, wird das Feld bei unter 128 Tour-Card-Inhabern mit **zufälligen Nicht-Tourcard-Spielern** (`tour_card_year == 0`, `.shuffled()`, ohne Sortierung nach `skill` oder OOM) aufgefüllt; reichen auch diese nicht aus, greifen Erstrunden-Freilose (`Byes`).
* **Verifizierte Quellen:**
  * [PDC Tournaments – Players Championship](https://pdc.tv)
  * [PDC News – Biggest prize money increase in PDC history confirmed](https://pdc.tv)
  * [PDC Rankings Rules](https://pdc.tv)
  * [Wikipedia – 2026 PDC Players Championship series](https://wikipedia.org)

---

### 2.2 European Tour (ET)
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer (Hauptfeld):** 48 Spieler.
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Top 16 der **PDC Order of Merit (`OOM`)** (steigen in Runde 2 / `L32` ein).
  2. Top 16 der **Pro Tour Order of Merit (`PT OOM`)**, die nicht bereits über Punkt 1 qualifiziert sind (steigen in Runde 1 / `L48` als gesetzte Spieler ein).
  3. **10 Gewinner des ET Tour Card Holder Qualifiers (`QUALIFIER_ET`)** (siehe Abschnitt 2.3).
  4. **4 Gewinner des Host Nation Qualifiers:** Nicht-Tourcard-Spieler (`tour_card_year == 0`) aus `host_country` gemäß zentraler Qualifier-Regel (Abschnitt 2.0).
  5. **2 regionale Nicht-Tourcard-Qualifier:** **1 Nordic & Baltic Qualifier** + **1 East Europe Qualifier** gemäß zentraler Qualifier-Regel (Abschnitt 2.0).
* **Setzliste & Auslosung:**
  * **Runde 1 (`L48`):** Die 32 Qualifikanten treten gegeneinander an (16 Pro-Tour-Seeds gegen 16 zugeloste Qualifier = 16 Matches).
  * **Runde 2 (`L32`):** Die 16 gesetzten Top-OOM-Spieler steigen ein und werden nach dem offiziellen 16er-Standard-Setzbaum (`BRACKET_SEED_ORDER_16`) über die 16 Zweitrunden-Matches verteilt, sodass `(1)` und `(2)` frühestens im Finale aufeinandertreffen.
  * Die Zuordnung in `L32` folgt einem festen mathematischen Kreuzvergleich:
    * OOM Nr. 1 spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 16 (vs. ungesetzter Qualifikant).
    * OOM Nr. 2 spielt gegen den Sieger der Partie von Pro Tour Seed Nr. 15 (vs. ungesetzter Qualifikant).
    * ... bis OOM Nr. 16 gegen den Sieger von Pro Tour Seed Nr. 1.
* **Turnierverlauf & Paarungslogik:**
  * **Vorgeschalteter Qualifier:** Vor jedem ET-Event findet ein `QUALIFIER_ET` statt (`K = 10` Boards, 10 Sieger; Details siehe Abschnitt 2.3).
  * **Hauptfeld:** Fester K.o.-Baum ab `L48` → `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £230.000 pro Event):**

| Platzierung / Runde          | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------|:---------|:---------------|:----------------------|
| **Sieger**                   | `WINNER` | 1              | £35.000               |
| **Finale**                   | `F`      | 1              | £15.000               |
| **Halbfinale (SF)**          | `SF`     | 2              | £10.000               |
| **Viertelfinale (QF)**       | `QF`     | 4              | £8.000                |
| **Achtelfinale (L16 / R3)**  | `L16`    | 8              | £5.000                |
| **Letzte 32 (R2)**           | `L32`    | 16             | £3.500\*              |
| **Letzte 48 (R1-Verlierer)** | `L48`    | 16             | £2.000\*              |

  * **Ranglisten-Zuordnung:** Das gewertete Preisgeld fließt gleichzeitig in **drei Ranglisten** ein: **PDC Order of Merit (`OOM`)**, **Pro Tour Order of Merit (`PT OOM`)** sowie **European Tour Order of Merit (`ET OOM`)** (nicht in `PC OOM`).
  * **\*Auftaktniederlage gesetzter/vorqualifizierter Spieler (PDC Rankings Rules Section 4.17):** Alle 32 vorqualifizierten Spieler (Top 16 `OOM` in `L32` sowie Top 16 `PT OOM` in `L48`) erhalten ihr Preisgeld real ausgezahlt, aber **£0 für alle Ranglisten (`OOM`, `PT OOM`, `ET OOM`)**, wenn sie ihr **erstes Match** im Turnier verlieren (`TournamentPlacement.prize_money = 0.0`).
  * Qualifikanten (die 16 Spieler aus den Qualifiern) erhalten ihr Preisgeld auch bei einer Auftaktniederlage in Runde 1 (£2.000) voll für alle Ranglisten angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * Da die Host-Nation-, Nordic-&-Baltic- und East-Europe-Qualifier nicht als eigene Turniere ausgespielt werden, bestimmt die App die jeweiligen Nicht-Tourcard-Teilnehmer direkt über die Ländercodes und die aktuelle `OOM` (siehe Abschnitt 2.0).
* **Verifizierte Quellen:**
  * [PDC News: Major expansion announced for PDC European events in 2027](https://pdc.tv)
  * [PDC Rankings Rules (Section 4.17 – Seeded Losers Rule)](https://pdc.tv)
  * [European Darts Tour 2027 (Wikipedia)](https://wikipedia.org)
  * [Qualifikationsturniere für die European Darts Tour 2027 (Wikipedia)](https://wikipedia.org)

---

### 2.3 Tour Card Holder Qualifier (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`)
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** Alle nicht für das jeweilige Hauptturnier vorqualifizierten Tour-Card-Inhaber (inkl. `USER_ID = -1L`).
  * **Modus:** K.o.-System auf `K` parallelen Boards (Best-of 11 Legs, £0 Preisgeld), bis exakt `K` Qualifikanten feststehen:
    * `QUALIFIER_ET`: `K = 10` Boards → 10 Sieger
    * `QUALIFIER_GRAND_SLAM`: `K = 8` Boards (regulär mindestens 8 Sieger; erhöht sich dynamisch, falls über die vorherigen Qualifikationswege weniger als 40 Spieler feststehen)
    * `QUALIFIER_WM`: `K = 2` Boards (regulär mindestens 2 Sieger; erhöht sich dynamisch bei offenen internationalen Plätzen)
* **Teilnehmer & Qualifikation:**
  * Alle Tour-Card-Inhaber der aktuellen Saison (`tour_cards`), die nicht bereits über die Vorqualifikationslisten des jeweiligen Hauptturniers (z. B. Top 16 OOM + Top 16 PT OOM bei ET; Top 40 OOM + Top 40 PT OOM bei WM) qualifiziert sind.
* **Setzliste & Auslosung:**
  * **Offizielle Board-Setzliste:** Die Top `2 * K` teilnehmenden Tour-Card-Inhaber (gemäß PDC Order of Merit) sind als Seeds `(1)` bis `(2 * K)` über die `K` Boards verteilt (Board `b`: Seed `b + 1` in Match 1 und Seed `2 * K - b` in Match 4), sodass kein Seed in `QUAL_R1` auf einen anderen Seed trifft und die beiden Top-Seeds eines Boards erst im `QUAL_FINAL` aufeinandertreffen können.
  * **Vorrunde & Freilose (Byes):** Das Hauptfeld vor `QUAL_R1` ist als `K` getrennte 8er-Boards (insgesamt `8 * K` Startplätze) aufgebaut. Ist die Teilnehmerzahl `N > 8 * K`:
    * Die obersten `16 * K - N` gesetzten Spieler (inkl. `Seed 1..K`) erhalten ein Freilos in `QUAL_PRELIM` und stehen auf festen Board-Positionen in `QUAL_R1`.
    * Nur die untersten `2 * (N - 8 * K)` Spieler spielen in `QUAL_PRELIM` um die restlichen freien Plätze der `K` Boards.
  * **Freie Auslosung der restlichen Board-Slots:** Alle nicht zu den Top `2 * K` Seeds gehörenden Freilos-Spieler (`byePlayers.drop(2 * K)`) sowie die Plätze für die Vorrundensieger (`prelimWinners`) werden frei zugelost (`.shuffled()`) und auf die `6 * K` freien Erstrunden-Plätze der `K` Boards verteilt.
* **Turnierverlauf & Paarungslogik:**
  * Hauptrunden pro Board:
    * `QUALIFIER_ET` (`K = 10`): `QUAL_PRELIM` (falls `N > 80`) → `QUAL_R1` (40 Matches) → `QUAL_R2` (20 Matches) → `QUAL_FINAL` (10 Matches → 10 Sieger).
    * `QUALIFIER_GRAND_SLAM` (`K = 8`): `QUAL_PRELIM` (falls `N > 64`) → `QUAL_R1` (32 Matches) → `QUAL_R2` (16 Matches) → `QUAL_FINAL` (8 Matches → 8 Sieger).
    * `QUALIFIER_WM` (`K = 2`): `QUAL_PRELIM` (falls `N > 16`) → `QUAL_R1` (8 Matches) → `QUAL_R2` (4 Matches) → `QUAL_FINAL` (2 Matches → 2 Sieger).
  * Ab `QUAL_R1` läuft jedes Board als fester K.o.-Baum ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £0):**
  * **£0 Preisgeld:** Qualifier-Turniere schütten kein Preisgeld aus und fließen nicht in die Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`) ein.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * In der App nehmen alle nicht-vorqualifizierten Tour-Card-Inhaber geschlossen teil; freiwillige Nichtmeldungen einzelner Profis werden aktuell nicht simuliert.
* **Verifizierte Quellen:**
  * [PDC News – 2026 ET3-4 Seeds & Tour Card Holder qualifier entries confirmed](https://www.pdc.tv/news/2026/february/12/2026-et3-4-seeds---tour-card-holder-qualifier-entries-confirmed/)
  * [PDC News – Grand Slam of Darts Tour Card Holder Qualifier entries confirmed](https://www.pdc.tv/news/2026/november/05/grand-slam-of-darts-tour-card-holder-qualifier-entries-confirmed/)
  * [PDC News – 2026/27 Paddy Power World Championship International Qualifying structure confirmed](https://www.pdc.tv/news/2026/february/24/2026-27-paddy-power-world-championship-international-qualifying-structure-confirmed/)
  * [Qualifikationsturniere für die European Darts Tour 2027 (Wikipedia)](https://de.wikipedia.org/wiki/Qualifikationsturniere_f%C3%BCr_die_European_Darts_Tour_2027)

---

### 2.4 Die Major-Turniere

**Major-Turniere folgen spezifischen Format- und Qualifikationsregeln (Austragungsorte siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json)).**

#### 2.4.1 UK Open
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** 160 Spieler.
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Alle 128 Tour-Card-Inhaber der aktuellen Saison.
  2. 32 Nicht-Tourcard-Qualifikanten (`tour_card_year == 0`) gemäß zentraler Qualifier-Regel (Abschnitt 2.0).
* **Setzliste & Auslosung:**
  * Ohne Setzliste („FA Cup of Darts“). Runden 1–4 erfolgen als gestaffelter Einstieg nach der **PDC Order of Merit (`OOM`)**:
    * **Runde 1 (`R1_160`):** Plätze 97–128 der `OOM` + alle 32 Qualifikanten (32 Matches → 32 Verlierer scheiden aus).
    * **Runde 2 (`R2_128`):** Die 32 Sieger aus Runde 1 treffen auf die Plätze 65–96 der `OOM` (32 Matches).
    * **Runde 3 (`R3_96`):** Die 32 Sieger aus Runde 2 treffen auf die Plätze 33–64 der `OOM` (32 Matches).
    * **Runde 4 (`L64`):** Die 32 Sieger aus Runde 3 treffen auf die Top 32 der `OOM` (32 Matches).
  * **Offene Auslosung:** Die Runden 1–3 werden vorab frei gelost; **ab Runde 4 (`L64`) bis einschließlich zum Halbfinale (`SF`) wird nach jeder abgeschlossenen Runde komplett neu und ohne Setzliste gelost** (`isOpenDrawRound` bis `F`).
* **Turnierverlauf & Paarungslogik:**
  * Gestaffelte Einstiegsrunden (`R1_160` bis `L64`) mit freier Runden-Auslosung in jeder Runde (`R1_160` → `R2_128` → `R3_96` → `L64` → `L32` → `L16` → `QF` → `SF` → `F`).
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £750.000):**

| Platzierung / Runde           | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler     |
|:------------------------------|:---------|:---------------|:--------------------------|
| **Sieger**                    | `WINNER` | 1              | £120.000                  |
| **Finale**                    | `F`      | 1              | £60.000                   |
| **Halbfinale (SF)**           | `SF`     | 2              | £35.000                   |
| **Viertelfinale (QF)**        | `QF`     | 4              | £20.000                   |
| **Achtelfinale (L16 / R6)**   | `L16`    | 8              | £12.500                   |
| **Letzte 32 (R5)**            | `L32`    | 16             | £7.500                    |
| **Letzte 64 (R4)**            | `L64`    | 32             | £3.000                    |
| **Letzte 96 (R3)**            | `R3_96`  | 32             | £2.000                    |
| **Letzte 128 (R2)**           | `R2_128` | 32             | £1.250                    |
| **Letzte 160 (R1-Verlierer)** | `R1_160` | 32             | **£0** *(kein Preisgeld)* |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * **Runde-1-Verlierer (`R1_160`):** Alle Verlierer der 1. Runde (Qualifikanten sowie OOM-Plätze 97–128) erhalten **£0**.
  * **Einstieg in spätere Runden (Top 96):** Spieler, die erst in `R2_128`, `R3_96` oder `L64` einsteigen und dort sofort verlieren, erhalten regulär das Preisgeld dieser Runde (£1.250, £2.000 bzw. £3.000) voll für die `OOM` angerechnet (keine Nullrunde).
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * *Reale PDC:* Die 32 Nicht-Tourcard-Plätze setzen sich zusammen aus den **Top 8 der Challenge Tour OOM**, den **Top 8 der Development Tour OOM** (jeweils des Vorjahres, sofern keine Tour Card gewonnen wurde) sowie **16 Amateur-Qualifikanten** (8x Rileys Amateur Qualifiers + 8x PDC in the Community Qualifiers).
  * *App-Vereinfachung:* Da Secondary Tours und Amateur-Qualifier nicht simuliert werden, wählt die App 32 Nicht-Tourcard-Spieler (`tour_card_year == 0`) gemäß der zentralen Qualifier-Regel in Abschnitt 2.0 aus.
* **Verifizierte Quellen:**
  * [PDC Tournaments – Ladbrokes UK Open](https://www.pdc.tv/tournaments/ladbrokes-uk-open)
  * [PDC News – 2026 Ladbrokes UK Open preview](https://www.pdc.tv/news/2026/march/05/2026-ladbrokes-uk-open-preview/)
  * [PDC Rankings Rules (Section 2 & 4)](https://pdc.tv)
  * [Wikipedia – 2026 UK Open](https://en.wikipedia.org/wiki/2026_UK_Open)

---

#### 2.4.2 European Open (Neues PDC-Major ab 2027)
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** 160 Spieler.
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Alle 128 Tour-Card-Inhaber der aktuellen Saison.
  2. 32 Nicht-Tourcard-Qualifikanten (`tour_card_year == 0`) gemäß zentraler Qualifier-Regel (Abschnitt 2.0, sortiert nach `OOM` DESC, bei Gleichstand nach `skill` DESC).
* **Setzliste & Auslosung:**
  * Analog zu den UK Open ohne Setzliste mit gestaffeltem Einstieg nach der **PDC Order of Merit (`OOM`)** in Runden 1–4:
    * **Runde 1 (`R1_160`):** Plätze 97–128 der `OOM` + alle 32 Qualifikanten (32 Matches → 32 Verlierer scheiden aus).
    * **Runde 2 (`R2_128`):** Die 32 Sieger aus Runde 1 treffen auf die Plätze 65–96 der `OOM` (32 Matches).
    * **Runde 3 (`R3_96`):** Die 32 Sieger aus Runde 2 treffen auf die Plätze 33–64 der `OOM` (32 Matches).
    * **Runde 4 (`L64`):** Die 32 Sieger aus Runde 3 treffen auf die Top 32 der `OOM` (32 Matches).
  * **Offene Auslosung:** Offene Runden-Auslosung ab Runde 2 (`R2_128` bis `F`).
* **Turnierverlauf & Paarungslogik:**
  * Gestaffelte Einstiegsrunden (`R1_160` bis `L64`) mit freier Runden-Auslosung in jeder Runde (`R1_160` → `R2_128` → `R3_96` → `L64` → `L32` → `L16` → `QF` → `SF` → `F`).
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £750.000):**
  * Identische Runden-Verteilung wie bei den UK Open (`WINNER`: £120.000, `F`: £60.000, `SF`: £35.000, `QF`: £20.000, `L16`: £12.500, `L32`: £7.500, `L64`: £3.000, `R3_96`: £2.000, `R2_128`: £1.250, `R1_160`: **£0**).
  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * **Runde-1-Verlierer (`R1_160`):** Alle Verlierer der 1. Runde erhalten **£0**. Spieler mit Einstieg ab `R2_128`, `R3_96` oder `L64` erhalten das Preisgeld ihrer Einstiegsrunde bei sofortigem Ausscheiden regulär für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * *Reale PDC:* Die 32 Nicht-Tourcard-Plätze setzen sich zusammen aus den **Top 4 der Challenge Tour OOM**, den **Top 4 der Development Tour OOM**, **8 Qualifikanten aus PDC Europe Next Gen & PDCE Start** sowie **16 internationalen Qualifikanten**.
  * *App-Vereinfachung:* Da Secondary Tours und regionale Amateur-Qualifier nicht simuliert werden, wählt die App 32 Nicht-Tourcard-Spieler (`tour_card_year == 0`) gemäß der zentralen Qualifier-Regel in Abschnitt 2.0 aus.
* **Verifizierte Quellen:**
  * [PDC News – 160-player field confirmed for inaugural European Open at Salzburgarena](https://www.pdc.tv/news/2026/october/14/160-player-field-confirmed-for-inaugural-european-open-at-salzburgarena/)
  * [PDC Europe – PDC Europe präsentiert neues Major-Turnier: European Open feiert 2027 Premiere in Salzburg mit 160 Spielern](https://www.pdc-europe.tv/news/pdc-europe-prasentiert-neues-major-turnier-european-open-feiert-2027-premiere-in-salzburg-mit-160-spielern)
  * [PDC News – Major expansion announced for PDC European events in 2027](https://www.pdc.tv/news/2026/september/25/major-expansion-announced-for-pdc-european-events-in-2027)
  * [Wikipedia – European Open 2027 (Darts)](https://de.wikipedia.org/wiki/European_Open_2027_(Darts))

---

#### 2.4.3 World Matchplay
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** 32 Spieler.
  * **Modus:** K.o.-System, Legs, mit **„Two-Clear-Legs Rule“** (Details siehe Abschnitt 3.3).
* **Teilnehmer & Qualifikation:**
  1. Top 16 der **PDC Order of Merit (`OOM`)**.
  2. Top 16 der **Pro Tour Order of Merit (`PT OOM`)**, die nicht bereits über Punkt 1 qualifiziert sind.
* **Setzliste & Auslosung:**
  * Die **Top 16 der `OOM`** sind auf den Plätzen 1 bis 16 im offiziellen 16er-Standard-Setzbaum (`BRACKET_SEED_ORDER_16`) gesetzt.
  * Die **16 Qualifikanten der `PT OOM`** sind ungesetzt und werden den 16 gesetzten Spielern in Runde 1 (`L32`) **frei zugelost**.
* **Turnierverlauf & Paarungslogik:**
  * Fester K.o.-Turnierbaum ab `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £1.000.000):**

| Platzierung / Runde          | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------|:---------|:---------------|:----------------------|
| **Sieger**                   | `WINNER` | 1              | £225.000              |
| **Finale**                   | `F`      | 1              | £125.000              |
| **Halbfinale (SF)**          | `SF`     | 2              | £65.000               |
| **Viertelfinale (QF)**       | `QF`     | 4              | £35.000               |
| **Achtelfinale (L16 / R2)**  | `L16`    | 8              | £22.500               |
| **Letzte 32 (R1-Verlierer)** | `L32`    | 16             | £12.500               |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * Alle 32 Teilnehmer (auch gesetzte Erstrundenverlierer) erhalten bei regulärer Teilnahme das Preisgeld für die entsprechende Runde voll für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * Keine App-Vereinfachung nötig; Teilnehmerfeld, freie Auslosung der 16 `PT OOM`-Qualifikanten gegen die Top 16 `OOM`-Seeds und Preisgelder entsprechen 1:1 dem offiziellen PDC-Regelwerk.
* **Verifizierte Quellen:**
  * [PDC Tournaments – World Matchplay](https://pdc.tv)
  * [PDC News – 2026 Betfred World Matchplay preview](https://www.pdc.tv/news/2026/july/17/2026-betfred-world-matchplay-preview/)
  * [PDC Rankings Rules (Section 2 & 4)](https://pdc.tv)
  * [Wikipedia – 2026 World Matchplay](https://en.wikipedia.org/wiki/2026_World_Matchplay)

---

#### 2.4.4 World Grand Prix
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer:** 32 Spieler.
  * **Modus:** K.o.-System, Sets (jedes Set: **Best-of 5 Legs** / First to 3 Legs) mit **Double-In / Double-Out** und **„Two-Clear-Legs Rule“** im Entscheidungssatz (Details siehe Abschnitt 3.3).
* **Teilnehmer & Qualifikation:**
  1. Top 16 der **PDC Order of Merit (`OOM`)**.
  2. Top 16 der **Pro Tour Order of Merit (`PT OOM`)**, die nicht bereits über Punkt 1 qualifiziert sind.
* **Setzliste & Auslosung:**
  * Die **Top 16 der `OOM`** sind auf den Plätzen 1 bis 16 im offiziellen 16er-Standard-Setzbaum (`BRACKET_SEED_ORDER_16`) gesetzt.
  * Die **16 Qualifikanten der `PT OOM`** sind ungesetzt und werden den 16 gesetzten Spielern in Runde 1 (`L32`) **frei zugelost**.
* **Turnierverlauf & Paarungslogik:**
  * Fester K.o.-Turnierbaum ab `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £750.000):**

| Platzierung / Runde          | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------|:---------|:---------------|:----------------------|
| **Sieger**                   | `WINNER` | 1              | £150.000              |
| **Finale**                   | `F`      | 1              | £80.000               |
| **Halbfinale (SF)**          | `SF`     | 2              | £50.000               |
| **Viertelfinale (QF)**       | `QF`     | 4              | £35.000               |
| **Achtelfinale (L16 / R2)**  | `L16`    | 8              | £20.000               |
| **Letzte 32 (R1-Verlierer)** | `L32`    | 16             | £7.500                |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * Alle 32 Teilnehmer (auch gesetzte Erstrundenverlierer) erhalten bei regulärer Teilnahme mindestens £7.500 voll für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * Keine App-Vereinfachung nötig; Teilnehmerfeld, Setzliste, Double-In/Out-Format und Preisgelder entsprechen 1:1 dem offiziellen PDC-Regelwerk.
* **Verifizierte Quellen:**
  * [Sky Sports – World Grand Prix Darts 2026: Schedule, format, prize money](https://www.skysports.com/darts/news/13585772/world-grand-prix-darts-2026-schedule-format-prize-money-venue-dates-as-luke-littler-luke-humphries-seek-title)
  * [BBC Sport – Darts' World Grand Prix: Results and prize money](https://www.bbc.com/sport/darts/articles/ckge4vv3dplxo)
  * [DartsNews – World Grand Prix: All top 16 players seeded](https://dartsnews.com/pdc/world-grand-prix-2025-field-schedule-draw-results-predictions-and-format)
  * [Wikipedia – 2026 World Grand Prix (darts)](https://en.wikipedia.org/wiki/2026_World_Grand_Prix_(darts))

---

#### 2.4.5 European Championship
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json) (Westfalenhalle, Dortmund, Deutschland).
  * **Teilnehmer:** 32 Spieler (*TODO:* vorerst verifiziertes 32er-Schema; 2027er-Erweiterung auf 48 Spieler noch ohne offizielle Detailregeln).
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Top 32 der **European Tour Order of Merit (`ET OOM`)** der laufenden Saison.
* **Setzliste & Auslosung:**
  * Alle 32 Teilnehmer sind nach der `ET OOM` gesetzt (`(1)` bis `(32)`) und spielen nach dem 32er-Standard-Setzbaum (`seedOrder32[i]` vs. `31 - seedOrder32[i]`), sodass `(1)` und `(2)` frühestens im Finale aufeinandertreffen.
* **Turnierverlauf & Paarungslogik:**
  * Fester K.o.-Turnierbaum ab `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £750.000):**

| Platzierung / Runde          | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------|:---------|:---------------|:----------------------|
| **Sieger**                   | `WINNER` | 1              | £150.000              |
| **Finale**                   | `F`      | 1              | £80.000               |
| **Halbfinale (SF)**          | `SF`     | 2              | £50.000               |
| **Viertelfinale (QF)**       | `QF`     | 4              | £35.000               |
| **Achtelfinale (L16 / R2)**  | `L16`    | 8              | £20.000               |
| **Letzte 32 (R1-Verlierer)** | `L32`    | 16             | £7.500                |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * Alle 32 Teilnehmer (auch Erstrundenverlierer) erhalten bei regulärer Teilnahme mindestens £7.500 voll für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * **Hinweis zu 2027er-Reformen:** Die angekündigte Erweiterung auf 48 Spieler, 6 Turniertage und £1.000.000 Preisgeld wartet noch auf Detailregeln der PDC (siehe [PDC-Regel-Update-Checkliste](PDC_REGEL_UPDATE_CHECKLISTE.md)); bis dahin gilt das verifizierte 32er-Schema.
* **Verifizierte Quellen:**
  * [PDC News – Major expansion announced for PDC European events in 2027](https://www.pdc.tv/news/2026/september/25/major-expansion-announced-for-pdc-european-events-in-2027/)
  * [PDC Europe – PDC und PDC Europe bauen europäische Turnierserie 2027 aus](https://www.pdc-europe.tv/news/pdc-und-pdc-europe-bauen-europaische-turnierserie-2027-aus)
  * [Sporting News – 2026 European Darts Championship: Draw, schedule, prize money and format](https://www.sportingnews.com/us/darts/news/2026-european-darts-championship-draw-schedule-prize-money-format/54da7e3c71b21e04a355af17)
  * [Wikipedia – European Darts Championship 2026](https://de.wikipedia.org/wiki/European_Darts_Championship_2026)

  * [PDC News: Major expansion announced for PDC European events in 2027](https://www.pdc.tv/news/2026/september/25/major-expansion-announced-for-pdc-european-events-in-2027)
  * *TODO:* Turnierspezifische Links für Qualifikation, Setzbaum und Preisgeld-Split 2026/2027 ergänzen.

---

#### 2.4.6 Grand Slam of Darts
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json).
  * **Teilnehmer (Hauptfeld):** 48 Spieler (Offizielles PDC-Format ab 2026).
  * **Modus:** Gruppenphase (16 Gruppen A–P zu je 3 Spielern, Best-of 9 Legs) + K.o.-Phase ab Achtelfinale (`L16`, Legs).
* **Teilnehmer & Qualifikation:**
  Das 48-köpfige Teilnehmerfeld setzt sich in exakt dieser Prioritätsreihenfolge zusammen (bereits qualifizierte Spieler werden übersprungen):
  1. **PDC Televised Main Tournaments (bis zu 24 Plätze):** Sieger und Finalisten der letzten 12 Monate (zuerst alle Sieger, dann alle Finalisten) in der Reihenfolge: 
     * World Championship
     * Grand Slam of Darts
     * Premier League (Siehe **App-Vereinfachung vs. reale PDC-Regeln:**)
     * World Matchplay
     * World Grand Prix
     * World Masters (Siehe **App-Vereinfachung vs. reale PDC-Regeln:**)
     * UK Open
     * European Championship
     * Players Championship Finals
     * World Series of Darts Finals (Siehe **App-Vereinfachung vs. reale PDC-Regeln:**)
     * World Cup of Darts (Siehe **App-Vereinfachung vs. reale PDC-Regeln:**)
  2. **Additional Qualifiers (bis zu 12 Plätze):** 
     * World Youth Championship (Sieger & Finalist)
     * Development Tour OOM (#1)
     * Challenge Tour OOM (#1)
     * Women's World Matchplay (Siegerin)
     * Women's Series OOM (#1)
     * Asian Championship* (Sieger)
     * North American Championship (Sieger)
     * Nordic & Baltic Championship (Sieger)
     * ANZ Premier League* (Sieger)
  3. **Auffüllen auf 40 Spieler (Reserve List 1 & 2):**
     * Zuerst alle *European Tour*-Sieger der laufenden Saison (sortiert nach Anzahl ET-Siege, bei Gleichstand nach **PDC Order of Merit**).
     * Danach alle *Players Championship*-Sieger der laufenden Saison (sortiert nach Anzahl PC-Siege, bei Gleichstand nach **PDC Order of Merit**), bis 40 Spieler erreicht sind.
  4. **Tour Card Holder Qualifier (`QUALIFIER_GRAND_SLAM`, mindestens 8 Plätze):** Die restlichen Plätze bis 48 werden über den TCH-Qualifier ausgespielt (siehe Abschnitt 2.3; werden über Punkt 1–3 weniger als 40 Spieler erreicht, erhöht sich die Anzahl der Plätze im TCH-Qualifier entsprechend).
  5. **Sonder-Fallback:** Sollten selbst alle ET- und PC-Sieger nicht ausreichen, rücken die höchstplatzierten noch nicht qualifizierten Spieler der **PDC Order of Merit** nach.
* **Setzliste & Auslosung:**
  * Nach Abschluss des Qualifiers werden **alle 48 qualifizierten Spieler** nach der aktuellen **PDC Order of Merit** sortiert und in drei Lostöpfe zu je 16 Spielern aufgeteilt:
    * **Topf 1 (Seeds 1–16 der Qualifikanten):** Feste Gruppenköpfe für die Gruppen A bis P nach dem offiziellen 16er-Turnierbaum (`BRACKET_SEED_ORDER_16`).
    * **Topf 2 (Plätze 17–32 der Qualifikanten):** Werden frei den Gruppen A–P zugelost (je 1 Spieler pro Gruppe).
    * **Topf 3 (Plätze 33–48 der Qualifikanten):** Werden frei den Gruppen A–P zugelost (je 1 Spieler pro Gruppe).
* **Turnierverlauf & Paarungslogik:**
  * **Vorgeschalteter Qualifier:** Vor dem Grand Slam findet der `QUALIFIER_GRAND_SLAM` statt (`K = 8` Boards, 8 Sieger; Details siehe Abschnitt 2.3).
  * **Gruppenphase (Dreiergruppen):**
    * Tag 1: Topf 1 gegen Topf 3 (Topf 2 setzt aus).
    * Tag 2: Topf 2 gegen Verlierer von Tag 1 (Sieger von Tag 1 setzt aus).
    * Tag 3: Topf 2 gegen Sieger von Tag 1 (Verlierer von Tag 1 setzt aus).
    * **Aufstieg:** Nur der **Gruppenerste** jeder Gruppe (16 Spieler) zieht ins Achtelfinale (`L16`) ein.
  * **K.o.-Phase ab `L16`:** Fester Turnierbaum paarweise nach Gruppen: Sieger A vs. Sieger B, Sieger C vs. Sieger D, …, Sieger O vs. Sieger P (`L16` → `QF` → `SF` → `F`).
* **Tabellen-Sortierung (Tie-Breaker in der Gruppenphase):**
  Bei Punktgleichheit (2 Punkte pro Sieg, 0 Punkte bei Niederlage) gilt folgende Reihenfolge:
  1. **Punkte**
  2. **Leg-Differenz** (`Total Leg Difference`)
  3. **Gewonnene Legs** (`Total Legs Won`)
  4. **Legs gegen den Anwurf** (`Total Legs Won Against Throw`)
  5. **Höchster Turnier-Average** (auf 2 Nachkommastellen)
  6. **Höchster Sieg-Average** (auf 2 Nachkommastellen)
  7. **9-Dart-Shootout** (Stechen)
  * *TODO (App-Implementierung):* In `CalculateGroupStandingsUseCase` sind aktuell Kriterien **1 bis 3** (danach `playerId`) umgesetzt. Kriterien **4 bis 6** (Legs gegen Anwurf, Averages) ergänzen, sobald die Live-Match-Engine Wurf-Statistiken erfasst.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £1.000.000):**

| Platzierung / Runde                | Enum-Key    | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------------|:------------|:---------------|:----------------------|
| **Sieger**                         | `WINNER`    | 1              | £200.000              |
| **Finale**                         | `F`         | 1              | £100.000              |
| **Halbfinale (SF)**                | `SF`        | 2              | £60.000               |
| **Viertelfinale (QF)**             | `QF`        | 4              | £35.000               |
| **Achtelfinale (L16)**             | `L16`       | 8              | £20.000               |
| **Gruppenzweiter (Aus in Gruppe)** | `GROUP_2ND` | 16             | £12.500               |
| **Gruppendritter (Aus in Gruppe)** | `GROUP_3RD` | 16             | £5.000                |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * Da Non-Ranking-Events (*Premier League, World Masters, World Series, World Cup*) sowie Secondary/Regional Tours (*World Youth, Challenge Tour, Development Tour, Women's Series, Asian/North American/Nordic & Baltic/ANZ*) in der App nicht simuliert werden, ergeben sich für die App Ffolgende Änderungen:
    1. Sieger und Finalisten der Premier League, World Masters, World Series, World Cup werden nicht berücksichtigt (Turniere nicht in App Abgebildet)
    2. **12 zufällige Nicht-Tourcard-Holder (`tour_card_year == 0`, `.shuffled()`)** als Stellvertreter für die Additional Qualifiers aus Youth/Women's/Secondary/Affiliate Tours.
* **Verifizierte Quellen:**
  * [PDC News – 2026 Grand Slam of Darts qualification latest](https://www.pdc.tv/news/2026/october/07/2026-mr-vegas-grand-slam-of-darts-qualification-latest/)
  * [PDC News – Clayton & Ashton confirm Grand Slam qualification](https://www.pdc.tv/news/2026/october/12/clayton-ashton-confirm-grand-slam-qualification/)
  * [Sky Sports – Grand Slam of Darts 2026: Schedule, results, format, players, prize money](https://www.skysports.com/darts/news/13610214/grand-slam-of-darts-2026-schedule-new-format-players-prize-money-past-winners-as-luke-littler-looks-to-retain-title)
  * [Sky Sports – Grand Slam of Darts tables and permutations (Tie-Breakers & Bracket)](https://www.skysports.com/darts/news/13612459/grand-slam-of-darts-tables-and-permutations-what-luke-littler-luke-humphries-and-more-need-to-progress-to-last-16)
  * [BBC Sport – Grand Slam of Darts: Schedule, results & prize money](https://www.bbc.com/sport/darts/articles/cmwyqy57ejy6o)
  * [Wikipedia – 2026 Grand Slam of Darts](https://en.wikipedia.org/wiki/2026_Grand_Slam_of_Darts)

---

#### 2.4.7 Players Championship Finals
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json) (Butlin's Minehead Resort, Minehead, England).
  * **Teilnehmer:** 64 Spieler (*TODO:* vorerst verifiziertes 64er-Schema; 2027er-Erweiterung auf 128 Spieler noch ohne offizielle Detailregeln).
  * **Modus:** K.o.-System, Legs.
* **Teilnehmer & Qualifikation:**
  1. Top 64 der **Players Championship Order of Merit (`PC OOM`)** der laufenden Saison.
* **Setzliste & Auslosung:**
  * Alle 64 Teilnehmer sind nach der `PC OOM` gesetzt (`(1)` bis `(64)`) und spielen nach dem 64er-Standard-Setzbaum (`seedOrder64[i]` vs. `63 - seedOrder64[i]`), sodass `(1)` und `(2)` frühestens im Finale aufeinandertreffen.
* **Turnierverlauf & Paarungslogik:**
  * Fester K.o.-Turnierbaum ab `L64` → `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £750.000):**

| Platzierung / Runde          | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:-----------------------------|:---------|:---------------|:----------------------|
| **Sieger**                   | `WINNER` | 1              | £130.000              |
| **Finale**                   | `F`      | 1              | £70.000               |
| **Halbfinale (SF)**          | `SF`     | 2              | £40.000               |
| **Viertelfinale (QF)**       | `QF`     | 4              | £27.500               |
| **Achtelfinale (L16 / R3)**  | `L16`    | 8              | £15.000               |
| **Letzte 32 (R2)**           | `L32`    | 16             | £7.000                |
| **Letzte 64 (R1-Verlierer)** | `L64`    | 32             | £4.000                |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * Alle 64 Teilnehmer (auch Erstrundenverlierer) erhalten bei regulärer Teilnahme mindestens £4.000 voll für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * **Hinweis zu 2027er-Reformen:** Die angekündigte Erweiterung auf 128 Spieler (Top 128 `PT OOM`) im Mehrbrett-Modus wartet noch auf offizielle Detailregeln der PDC (siehe [PDC-Regel-Update-Checkliste](PDC_REGEL_UPDATE_CHECKLISTE.md)); bis dahin gilt das verifizierte 64er-Schema.
* **Verifizierte Quellen:**
  * [Dartsnews.de – Players Championship Finals stehen ab 2027 vor einer großen Veränderung](https://dartsnews.de/pdc/players-championship-finals-stehen-ab-2027-vor-einer-grossen-veranderung)
  * [PDC Tournaments – Players Championship](https://www.pdc.tv/tournaments/pdc-protour/players-championship)
  * [Wikipedia – 2026 Players Championship Finals](https://en.wikipedia.org/wiki/2026_Players_Championship_Finals)
  * [Mastercaller – Players Championship Finals 2026](https://mastercaller.com/tournaments/players-championship-finals/2026)

---

#### 2.4.8 World Championship
* **Rahmendaten:**
  * **Gastgeberland & Austragungsort:** Aktuelle und verbindliche Informationen siehe [season_calendar_2027.json](../app/src/main/assets/season_calendar_2027.json) (Alexandra Palace – Great Hall, London, England).
  * **Teilnehmer (Hauptfeld):** 128 Spieler.
  * **Modus:** K.o.-System, Sets (jedes Set: Best-of 5 Legs) mit **„Two-Clear-Legs Rule“** im Entscheidungssatz (Details siehe Abschnitt 3.3).
* **Teilnehmer & Qualifikation:**
  1. Top 40 der **PDC Order of Merit (`OOM`)** (80 vorqualifizierte Tour-Card-Spieler zusammen mit Punkt 2).
  2. Top 40 der **Pro Tour Order of Merit (`PT OOM`)**, die nicht bereits durch Punkt 1 qualifiziert sind.
  3. **48 Qualifier-Plätze:**
     * **2 Sieger des World Championship Tour Card Holder Qualifiers (`QUALIFIER_WM`, `K = 2` Boards; Details siehe Abschnitt 2.3)**.
     * **46 internationale Nicht-Tourcard-Qualifier** (`tour_card_year == 0`) gemäß zentraler Qualifier-Regel (Abschnitt 2.0, sortiert nach `OOM` DESC, bei Gleichstand nach `skill` DESC).
* **Setzliste & Auslosung:**
  * Alle 128 Spieler starten gemeinsam in **Runde 1 (`L128`)**:
    1. Die qualifizierten Spieler werden nach ihrer Position in der **PDC Order of Merit (`OOM`)** sortiert.
    2. **Plätze 1–32 der `OOM` (Top 32 Seeds):** Sind auf festen Positionen im offiziellen 32er-Standard-Setzbaum (`buildStandardSeedOrder(32)`) auf der linken Seite der Erstrunden-Paarungen gesetzt, sodass `(1)` und `(2)` frühestens im Finale aufeinandertreffen.
    3. **Plätze 33–64 der `OOM`:** Werden zufällig auf die übrigen 32 linken Baumpositionen (den Setzlistenplätzen 33–64) zugelost (treffen somit in Runde 1 nicht auf die Top 32 oder aufeinander).
    4. **Plätze 65–128 (inkl. aller 48 Qualifier):** Werden frei auf die 64 rechten Baumpositionen zugelost und treten in Runde 1 gegen die Top 64 der `OOM` an.
* **Turnierverlauf & Paarungslogik:**
  * **Vorgeschalteter Qualifier:** Unmittelbar vor der WM findet der `QUALIFIER_WM` statt (`K = 2` Boards, 2 Sieger; Details siehe Abschnitt 2.3).
  * **Hauptfeld:** Fester K.o.-Turnierbaum ab `L128` → `L64` → `L32` → `L16` → `QF` → `SF` → `F` ohne erneutes Mischen.
* **Preisgeld & Ranglisten-Ausnahmen (Gesamt: £5.000.000):**

| Platzierung / Runde           | Enum-Key | Anzahl Spieler | Preisgeld pro Spieler |
|:------------------------------|:---------|:---------------|:----------------------|
| **Sieger**                    | `WINNER` | 1              | £1.000.000            |
| **Finale**                    | `F`      | 1              | £400.000              |
| **Halbfinale (SF)**           | `SF`     | 2              | £200.000              |
| **Viertelfinale (QF)**        | `QF`     | 4              | £100.000              |
| **Achtelfinale (L16 / R4)**   | `L16`    | 8              | £60.000               |
| **Letzte 32 (R3)**            | `L32`    | 16             | £35.000               |
| **Letzte 64 (R2)**            | `L64`    | 32             | £25.000               |
| **Letzte 128 (R1-Verlierer)** | `L128`   | 64             | £15.000               |

  * **Ranglisten-Zuordnung:** Das Preisgeld fließt voll in die **PDC Order of Merit (`OOM`)** ein (nicht in `PT OOM`, `PC OOM` oder `ET OOM`).
  * Verliert ein gesetzter Spieler in Runde 1 (`L128`), erhält er die £15.000 dennoch voll für die `OOM` angerechnet.
* **App-Vereinfachung vs. reale PDC-Regeln:**
  * *Reale PDC:* Die 46 internationalen Nicht-Tourcard-Plätze setzen sich aus festen Kontingenten zusammen, darunter **Women's Series / Women's World Matchplay** (mindestens 4 Plätze), **Development Tour & World Youth Championship**, **Challenge Tour**, regionale Touren (**Asian Tour**, **Nordic & Baltic**, **CDC Nordamerika**, **ANZ/Australien**, **Afrika**) sowie regionale Super Leagues (z. B. **DACH Super League**).
  * *App-Vereinfachung:* Da diese Touren nicht simuliert werden, wählt die App 46 Nicht-Tourcard-Spieler (`tour_card_year == 0`) gemäß der zentralen Qualifier-Regel in Abschnitt 2.0 aus.
* **Verifizierte Quellen:**
  * [PDC News – 2025/26 Paddy Power World Darts Championship field confirmed](https://www.pdc.tv/news/2025/11/23/202526-paddy-power-world-darts-championship-field-confirmed)
  * [PDC News – 2026/27 Paddy Power World Championship International Qualifying structure confirmed](https://pdc.tv)
  * [Sky Sports – World Darts Championship: Schedule, format, previous winners and nine darters at Alexandra Palace](https://www.skysports.com/darts/news/13611967/world-darts-championship-schedule-format-previous-winners-and-nine-darters-at-alexandra-palace)
  * [Wikipedia – 2027 PDC World Darts Championship](https://en.wikipedia.org/wiki/2027_PDC_World_Darts_Championship)

---

## 💰 3. Preisgeldregeln & Ranglisten (Orders of Merit)

Das System führt getrennte, dynamische Ranglisten, basierend auf den kumulierten Daten der Karriere aus der Tabelle `tournament_placements`.

### 3.1 Die 4 Ranglisten

Alle Ranglisten errechnen sich aus den Preisgeldern (`TournamentPlacement.prize_money`) der in dieser Karriere gespielten Turniere.
Der Stammdatenwert `players.prizeMoney` wird aktuell nicht verwendet (kann in einer späteren Ausbaustufe optional als historischer Startwert vor Karrierebeginn dienen).

1. **Order of Merit:** Kürzel: "OOM". Preisgelder aller Ranking-Turniere der letzten zwei Jahre. Das heißt, die jüngsten zwei Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend).
2. **Pro Tour Order of Merit:** Kürzel: "PT OOM". Preisgeld aus `PLAYERS_CHAMPIONSHIP` und `EUROPEAN_TOUR`. Nur die jüngsten Austragungen jedes Turniers werden berücksichtigt (saisonübergreifend, 1-Jahres-Fenster).
3. **Players Championship Order of Merit:** Kürzel: "PC OOM". Ausschließlich Preisgelder aus `PLAYERS_CHAMPIONSHIP` der aktuellen Saison.
4. **European Tour Order of Merit:** Kürzel: "ET OOM". Ausschließlich Preisgelder aus `EUROPEAN_TOUR` der aktuellen Saison.

* **Verifizierte Quellen (Abschnitt 3.1):**
  * [PDC Rankings Rules (Section 2 – PDC Order of Merit & ProTour Order of Merit)](https://pdc.tv)
  * [PDC Rankings – Live Orders of Merit](https://pdc.tv)

### 3.2 Turnierübergreifende Ausnahmeregelungen (Wann gibt es £0 Preisgeld / Ranglistenpunkte?)

Ergänzend zu den jeweiligen Turnierregeln in Abschnitt 2 gelten für die Simulation der PDC-Ranglisten (gemäß *PDC Rankings Rules*, Abschnitte 3.7 & 4.17) folgende turnierübergreifende Sonderfälle:

1. **Nichtantritt / Absage vor Turnierstart (Withdrawal, Regel 4.17):**
   * Qualifiziert sich ein Spieler für ein Hauptfeld (Major oder European Tour), tritt aber vor seinem ersten Match nicht an, erhält er **£0** (weder Auszahlung noch Ranglistenpunkte), selbst wenn er erst in einer späteren Runde eingestiegen wäre.
2. **Freilos (Bye) durch unvollständiges Teilnehmerfeld (Regel 4.17):**
   * Erhält ein Spieler durch die Auslosung ein Freilos in Runde 2 und verliert dort sein erstes gespieltes Match, wird er ranglistentechnisch so behandelt, als hätte er in Runde 1 verloren (er erhält also nur das Preisgeld der 1. Runde – bei PC/UK Open somit **£0**).
   * *Gegenausnahme:* Kommt das Freilos zustande, weil der geplante Gegner kurzfristig zurückgezogen hat (Walkover), gilt das Match als gewonnen und das Preisgeld der erreichten Runde wird regulär gewertet.
3. **Nachrücker in spätere Runden (Late Replacement, Regel 3.7):**
   * Rückt ein Ersatzspieler für einen ausgefallenen Spieler direkt in eine spätere Runde nach (z. B. Runde 2 bei ET oder Runde 3 bei UK Open) und verliert dort sofort sein erstes Match, erhält er nur das Preisgeld der allerersten Turnierrunde (bei UK Open also **£0**, bei ET **£2.000**). Gewinnt er sein Auftaktmatch, greift ab der nächsten Runde das reguläre Preisgeld.

* **Verifizierte Quellen (Abschnitt 3.2):**
  * [PDC Rankings Rules (Section 3.7 – Late Replacements & Section 4.17 – Seeded Losers / Byes / Withdrawals)](https://pdc.tv)

### 3.3 Spielformate & Sonderregeln (Zentrale Definition)
Alle konkreten Runden-Distanzen (Best-of-Legs/Sets) aller Turniere sind zentral in `app/src/main/assets/season_calendar_2027.json` (`events.<EVENT>.distances`) definiert. Für die Spiel-Engine gelten folgende zentrale Formatregeln:

* **Set-Format:** Standardmäßig besteht ein Set aus *Best-of 5 Legs* (First to 3 Legs) – das gilt sowohl für die *World Championship* als auch für den *World Grand Prix*. (Ausnahme bei nicht-simulierten Non-Ranking-Events: *World Masters* mit Best-of 3 Legs pro Set).
* **Two-Clear-Legs Rule:**
  * *Leg-Format (World Matchplay):* Das Spiel muss mit mindestens 2 Legs Vorsprung gewonnen werden. Steht es nach 3 zusätzlichen Legs über der regulären Zieldistanz immer noch unentschieden (z. B. 12:12 bei First to 10), entscheidet das nächste Leg als Sudden-Death-Leg (13:12).
  * *Set-Format (World Grand Prix & World Championship):* Greift erst im entscheidenden letzten Satz, wenn beide Spieler nur noch ein Set zum Sieg benötigen. Dieser Satz muss mit 2 Legs Vorsprung gewonnen werden; bei 5:5 entscheidet das 11. Leg als Sudden-Death-Leg (6:5).
* **Double-In / Double-Out (World Grand Prix):** Das Leg wird mathematisch erst heruntergezählt, sobald der erste Pfeil des Spielers in einem äußeren Doppelring oder im Double-Bull landet, und muss auf einem Doppel beendet werden.

* **Verifizierte Quellen (Abschnitt 3.3):**
  * [Darts Regulation Authority (DRA) Rule Book](https://thedra.co.uk)
  * [PDC News – Two-clear-legs tie-break rule explained (World Matchplay & World Championship)](https://pdc.tv)
  * [Sky Sports – World Grand Prix Darts: Double-In / Double-Out & Two-Clear-Legs format](https://skysports.com)

---

## 🛠️ 4. Technische Umsetzung & Implementierungsübersicht

Um das MVVM-Muster sauber einzuhalten, ist die komplexe Geschäfts- und Berechnungslogik in dedizierte Use-Cases und Domain-Klassen ausgelagert:

* **`TournamentConfigLoader` & `season_calendar_2027.json`**: Zentraler Loader inkl. Fail-Fast-Validierung für Kalender, Preisgelder und Match-Distanzen.
* **`TournamentEvent`**: Enum aller Turniertypen inkl. Gesamtpreisgeld und Regel-Mapping für Match-Distanzen, Preisgeld-Auszahlungen pro Runde (inkl. `QUALIFIER_WM`) sowie zentralen Properties (`isQualifier`, `isMajor`, `qualifierBoardCount`).
* **`TournamentQualificationUseCase`**: Zentrale Logik für Qualifikationspools, Setzlisten, OOM-Sortierung, Qualifier-Board-Zuordnung und Vorschau-Paarungen.
* **`CalculateGroupStandingsUseCase`**: Single Source of Truth für Grand-Slam-Gruppentabellen und Tabellenberechnung.
* **`GetTournamentSummaryUseCase`**: Einheitliche Zustandsermittlung (`COMPLETED`/`ACTIVE`/`PLANNED`), Sieger- & User-Platzierungs-Zusammenfassung für den Saisonkalender und die Detailansicht.
* **`TournamentRound`**: Enum aller Turnierrunden von Gruppenphase bis Finale/Sieger.
* **`MatchDistance`**: Data Class zur Definition von Best-of-Legs/Sets, Legs pro Set und Two-Clear-Legs-Regeln.
* **`BracketGenerator`**: Algorithmische Generierung von Setzlisten und Erstrunden-Paarungen für alle Turniertypen inkl. dynamischer Qualifikanten-Brackets mit Vorrunden/Freilosen.
* **`GenerateSeasonCalendarUseCase`**: Erzeugt die chronologische Liste aller Saisonturniere aus `season_calendar_2027.json`.
* **`GenerateBracketUseCase`**: Generiert die Paarungen für anstehende Turniere, speichert Teilnehmer/Setzlisten (`tournament_participants`) und schreibt sofort beim Turnierstart für alle qualifizierten Teilnehmer das garantierte Einstiegspreisgeld (inkl. gestaffelt einsteigender gesetzter Spieler bei Major-Turnieren wie UK Open oder European Open) in die Platzierungstabelle.
* **`CompleteMatchUseCase`**: Einheitlicher UseCase für Match-Simulation, manuelle Ergebniseingabe und Debug-Abschluss basierend auf Skill und Wahrscheinlichkeiten.
* **`AdvanceTournamentRoundUseCase`**: Zentrale Zustandsmaschine für den Turnierfortschritt; führt nach jedem Matchgewinn eine sofortige Gewinner-Hochstufung auf das Preisgeld der erreichten Folgerunde durch (z. B. automatischer Aufstieg vorqualifizierter ET-Seeds nach ihrem ersten Sieg), steuert den Rundenfortschritt und beendet Qualifier-Turniere (`QUALIFIER_ET` = 10, `QUALIFIER_GRAND_SLAM` = 8, `QUALIFIER_WM` = 4), sobald die Zielanzahl an Siegern feststeht.
* **`TournamentProgressionUseCase`**: Verwaltet den Gesamtfortschritt eines Turniers und steuert über `simulateCompleteTournament(...)` die komplette Turniersimulation zentral von der Bracket-Erstellung über realistische CPU- und User-Matches bis zum Turnierabschluss.
* **`GetOrderOfMeritUseCase`**: Berechnet und liefert die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

### Übergreifende Quellen (Saisonkalender & allgemeines Regelwerk 2026/2027)

* **Offizielles PDC-Regelwerk & Preisgeldtabellen (2026/27):**
    - [PDC Rankings Rules (as of January 1, 2026 – Section 2, 3, 4 & 8)](https://www.pdc.tv/rankings/pdc-rankings-rules)
* **PDC-Ankündigung der Preisgelderhöhung ab 2026:**
    - [PDC News: Biggest prize money increase in PDC history confirmed](https://www.pdc.tv/news/2025/03/31/biggest-prize-money-increase-pdc-history-confirmed)
* **PDC-Erweiterung European Open & European Tour 2027:**
    - [PDC News: Major expansion announced for PDC European events in 2027](https://www.pdc.tv/news/2026/september/25/major-expansion-announced-for-pdc-european-events-in-2027)
* **Offizieller PDC-Saisonkalender 2027 (Termine, Reihenfolge & Austragungsstädte):**
    - [PDC News: European Open & historic French event headline 2027 PDC calendar](https://www.pdc.tv/news/2026/september/25/european-open-historic-french-event-headline-2027-pdc-calendar/)
    - [PDC Pro Tour 2027 (Wikipedia)](https://de.wikipedia.org/wiki/PDC_Pro_Tour_2027)
    - [Qualifikationsturniere für die European Darts Tour 2027 (Wikipedia)](https://de.wikipedia.org/wiki/Qualifikationsturniere_f%C3%BCr_die_European_Darts_Tour_2027)
    - [European Darts Tour 2027 (Wikipedia)](https://de.wikipedia.org/wiki/European_Darts_Tour_2027)
    - [Darts-Oche PDC Calendar](https://darts-oche.com/pdc-calendar.html)
    - [Darts1 Termine](https://www.darts1.de/termine/termine.php)
