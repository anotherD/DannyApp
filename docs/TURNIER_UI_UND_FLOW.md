# 📱 Fachkonzept: Turnier-UI, Simulation & Ranglisten-Ansicht

Dieses Dokument definiert die Benutzeroberfläche und den Interaktions-Flow für den Saisonkalender, den Turnierverlauf (inkl. Test-Modi für User-Matches) und die Orders of Merit in der **Danny Darts App**.

---

## 🧭 1. Navigation & Einstieg aus der Karriere-Ansicht

* **Top-Bar & Breadcrumbs:** Jede Unterseite zeigt als Titel exakt den Namen des gewählten Bereichs (z. B. `Saisonkalender`, `Turnieransicht`, `Ranglisten`). Bei Karrieren und Turnieren wird als Subtitel der vollständige Breadcrumb-Pfad im Format `Karrieren > <Karrierename> (Saison X)` (`breadcrumb_career_season_format`) angezeigt.
* **Karrieren-Kacheln & Übersicht (`CareerAdapter`, `CareerOverviewFragment`):** Jede Karriere-Kachel ist als kompaktes `ConstraintLayout` im 2x2-Raster aufgebaut (`item_career.xml`):
  - Links oben: `tvCareerName` (fett, 18sp)
  - Rechts oben: `tvCareerSeason` (`Saison X`, 14sp)
  - Links unten (unter `tvCareerName`): `tvCareerPlayer` (`<Flagge> <Spielername>`) und darunter `tvCareerProfile` (`Profil: <Name>`)
  - Rechts unten: `tvCareerLevel` (`Level X`, 14sp)
  - Zudem zeigen die Kacheln und die Übersicht (`tvCareerOverviewDetails`) konsistent Level, Profilnamen, aktuelle Saison sowie Spielernamen inklusive Landesflagge (über `TournamentUiHelper.formatFlagAndText`) an.
* **Tab-Navigation in der Karriere-Ansicht:** Statt einzelner dicker Buttons nutzt die `CareerDetailActivity` eine übersichtliche **Tab-Navigation** für die drei Kernbereiche:
  1. **Saisonkalender:** Chronologische Übersicht aller 74 Turniere der aktiven Saison.
  2. **Turnieransicht:** Rundenübersicht, Gruppenphase (beim Grand Slam of Darts) und Match-Steuerung des aktiven oder ausgewählten Turniers.
  3. **Ranglisten:** Wischbare Ansicht (`ViewPager2`) für die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

## 📅 2. Saisonkalender

* **Anzeige:** Liste aller Turniere der aktuellen Saison sortiert nach `order` (1 bis 74).
  * **Turniertypen & Bezeichnungen:** Alle Turniertypen und Labels kommen lokalisiert aus `strings.xml` (keine technischen Enum-Keys wie `PLAYERS_CHAMPIONSHIP` in der UI).
  * **Runden-Namen:** Alle Rundenbezeichnungen werden zentral über `TournamentUiHelper.getDisplayRoundName(roundKey, context)` dynamisch anhand von `strings.xml` lokalisiert.
  * **Austragungsort:** Anzeige als `<Flagge> <Stadtname>` (ohne das Präfix „Austragungsland:“).
  * **Kennzeichnung:** Major-Turniere und Qualifier-Turniere (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) sind optisch durch Badges hervorgehoben.
  * **Hervorhebung aktive / nächste Kachel:** Das active (`TournamentStatus.ACTIVE`) oder nächste geplante (`isNextPlanned`) Turnier wird optisch hervorgehoben mit `cardBackgroundColor` `@color/surface_karte_aktiv`, 2dp Rahmen (`strokeWidth`) und `strokeColor` `@color/status_active` bzw. `@color/status_planned`. Alle anderen Kacheln nutzen `@color/anthrazit_flaeche`, 1dp Rahmen und `@color/grau_rahmen_dunkel`.
  * **Initiales Scrollen:** Beim Laden scrollt der Kalender über `rvTournaments.post` mit einem Offset von `rvTournaments.height / 3` zum aktiven/nächsten Turnier, damit dieses nicht unter der Tab-Leiste verdeckt wird.
* **Strikte Turnier-Sperre (Sequenzieller Ablauf):**
  * Es darf immer **nur ein Turnier gleichzeitig aktiv** sein.
  * Ein neues Turnier kann erst gestartet werden, wenn das vorherige Turnier vollständig abgeschlossen ist (`isCompleted == true`).
* **Status-Kennzeichnung & Multi-Sieger-Qualifier:**
  * `Abgeschlossen` (`isCompleted == true`):
    * **Einzelsieger-Turniere:** Zeigt den Turniersieger (inkl. Landesflagge) im Status-Badge und die erreichte Runde des Users an.
    * **Multi-Sieger-Qualifier (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`):** Zeigt im Status-Badge nur `Abgeschlossen` an (ohne Einzel-Pokal). Auf den **Kalender-Kacheln** (`item_tournament.xml`) sind die Qualifier-Gewinner zur Platzersparnis ausgeblendet (`tvQualifierWinnersHeader` / `layoutQualifierWinnersList` auf `GONE`). Im **Turnier-Rückblick** (`TournamentDetailActivity`) bleibt die 2-spaltige Gewinnerliste erhalten, wobei jede Zeile über `TournamentUiHelper.formatFlagAndText(...)` mit `<Flagge> <Spielername>` formatiert wird.
  * `Aktiv` (aktuell laufendes Turnier): Hervorgehoben mit Status-Hintergrund und Button **„Turnier öffnen“**.
  * `Geplant` (`isCompleted == false`, noch keine Matches generiert): Hervorgehoben mit Status-Hintergrund. Nur das jeweils nächste anstehende Turnier (sofern kein anderes Turnier aktiv ist) bietet den Button **„Turnier starten“** (ruft `GenerateBracketUseCase` auf und öffnet die Turnieransicht).
* **Debug-Funktion (`debugMode == true`):** Ist der Debug-Modus in der Konfiguration aktiviert, erscheint beim aktiven bzw. nächsten Turnier der Button **„Komplettes Turnier simulieren“** (simuliert alle Runden dieses einen Turniers durch). Ist `debugMode == false`, wird der Button ausgeblendet.

---

## 🎯 3. Turnieransicht & Match-Steuerung

### 3.0 Die 3 Zustände der Turnieransicht
Jedes Turnier im Saisonkalender ist anklickbar und öffnet die Turnieransicht in einem von drei Modi:
1. **Vorschau-Modus (`isCompleted == false` und noch keine Matches generiert):**
  - Zeigt dieselbe einheitliche Turnier-Kopfzeile wie der aktive Modus (Turniername, Status-Badge „Geplant“, Turniertyp-Badge, `<Flagge> <Stadtname>` sowie das **Info-Icon** zum Öffnen des Preisgeld- & Distanz-Dialogs inkl. Gesamtpreisgeld und Sieger-Preisgeld `WINNER`).
  - Falls es das nächste anstehende Turnier im Kalender ist und kein anderes Turnier aktiv läuft, wird unten der Button **„Turnier jetzt starten & auslosen“** angezeigt (`GenerateBracketUseCase`).
2. **Aktiver Turnier-Modus (`isCompleted == false` und Matches vorhanden):**
  - **Runden-Auswahl:** Horizontale **Runden-Pills / Material Chips** (Single-Selection) statt eines Dropdowns (plus Gruppen-Auswahl `A–P` beim Grand Slam of Darts).
  - **Vorab-Turnierbaum & Freie Auslosung:**
    - Bei Turnieren mit festem Turnierbaum sind alle Runden-Pills bereits vorab anwählbar und zeigen Platzhalter-Paarungen bzw. sofort feststehende Spieler an (z. B. ET L32 zeigt die Top 16 OOM-Seeds als echte `player1Id`, und abgeschlossene Vorgängermatches tragen ihre Sieger sofort in Vorschau-Paarungen ein).
    - Bei Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) werden in `QUAL_R1` während der Vorrunde bereits feststehende Freilos-Spieler (`Seed 1..K` und Byes) auf ihren echten Board-Plätzen angezeigt.
    - Bei `UK_OPEN` und `EUROPEAN_OPEN` gibt es keine feste Setzliste (`getSeededPlayerMap` liefert `emptyMap()`). In noch nicht ausgelosten Folgerunden (`R2_128` bis `F`) wird statt leerer Matchkarten ein Info-Container (`layoutOpenDrawInfo`) mit `@string/draw_after_round_info` sowie darunter (bei Runden mit Neuzugängen oder abgeschlossenen Vorrundensiegern) `@string/qualified_round_entrants_header` samt zentrierten Gruppen-Überschriften und **2-spaltigen** Spielerlisten (inkl. Flagge und Seed über `TournamentUiHelper.formatFlagAndText`) angezeigt.
  - **Seed-Nummern & Dynamische Namens-Kürzung bei Spielernamen:** Ist ein Spieler im jeweiligen Turnier gesetzt, wird sein Name im Match-Adapter für Spieler 1 (links) und Spieler 2 (rechts) angezeigt. Dank Breitenprüfung (`doOnPreDraw` mit `paint.measureText` und `TextUtils.ellipsize`) werden lange Namen bei Bedarf dynamisch auf Initialen verkürzt (`C. Menzies`), sodass Seed-Nummern rechts (`(2)`) niemals abgeschnitten werden.
  - **Match-Karten:** Zeigen Spielernamen (beim User seinen hinterlegten `playerName` samt Landesflagge) und das Ergebnis an (ohne `Skill: x`-Anzeige).
  - **Simulations-Sperre & Lade-Overlay (`layoutSimulatingOverlay`, `isSimulatingBatch`):** Während der Ausführung von „Komplettes Turnier simulieren“ (`simulateCompleteTournament()`) oder „CPU simulieren“ (`simulateCpuMatches()`) wird ein halbtransparentes Overlay (`layoutSimulatingOverlay`) mit Lade-Indikator und dem Text `@string/simulating_tournament` angezeigt. Gleichzeitig werden Zwischen-Updates in `loadTournament` über `filter { !isSimulatingBatch.value }` pausiert und der Zurück-Button wird per `OnBackPressedCallback` blockiert.
  - **Zentrale Steuerung:** Die Schalter **„Automatische Simulation“** und **„Automatisch zur nächsten Runde“** liegen zentral in der **Konfiguration (`ConfigActivity`)** und werden über `SettingsManager` gesteuert. Die manuellen Simulations-Buttons in der Turnieransicht sind nur bei `debugMode == true` sichtbar.
3. **Rückblick-Modus (`isCompleted == true`):**
  - Nutzt dieselbe Runden-Pills-Ansicht wie der aktive Modus (read-only ohne Simulations-Buttons).
  - **Einzelsieger-Turniere:** Zeigt oben die Zusammenfassung **Sieger** (inkl. Landesflagge), **Final-Ergebnis** (inkl. Name & Flagge des Finalgegners), **Deine erreichte Runde** (`reachedRound`) und **Gewonnenes Preisgeld** (`prizeMoney` aus `tournament_placements`).
  - **Multi-Sieger-Qualifier:** Zeigt die formatierte Gewinner-Liste als **2-spaltige** vertikale Liste unter `@string/qualifier_winners_header` (`Qualifier-Gewinner:`) an und blendet das Final-Ergebnis (`tvReviewFinalScore`) aus.

### 3.1 Die Optionen für User-Matches (`USER_ID = -1L`)
Ist ein offenes Match mit Beteiligung des Users an der Reihe, erhält der User beim Klick auf sein Match folgende Auswahlmöglichkeiten:
1. **„Sieger festlegen (Sieg/Niederlage)“** *(nur sichtbar bei `debugMode == true`)*: Setzt direkt einen gültigen Sieg- oder Niederlagen-Spielstand passend zur `MatchDistance`.
2. **„Ergebnis manuell eingeben“**: Öffnet einen Dialog zur direkten Eingabe der gewonnenen Legs/Sets (z. B. `6 : 4`). Validiert gegen `TournamentEvent.getMatchDistance(round)` (inkl. `twoClearLegsRule`), speichert das Ergebnis und triggert den Rundenfortschritt.
3. **„Live spielen (GameEngine)`**: Übergibt Match-ID und `MatchDistance` (`bestOf`, `legsPerSet`, `twoClearLegsRule`, `SETS_DOUBLE_IN_OUT`) an die bestehende Match-/Scolia-Engine.

---

## 🏆 4. Ranglisten-Ansicht

* **Wisch-Navigation (`ViewPager2`):** Flüssiges Wischen nach links und rechts zwischen den 4 Ranglisten (`Order of Merit (OOM)`, `Pro Tour (PT OOM)`, `Players Champ. (PC OOM)`, `European Tour (ET OOM)`).
* **Datenquelle:** Ruft `GetOrderOfMeritUseCase(careerId, type)` auf (zeigt beim User seinen hinterlegten `playerName` und seine Landesflagge).
* **Darstellung:** Tabelle mit Platzierung (`rank`), **Landesflagge** (statt Text-Kürzel), Spielername (`name`), Preisgeld (`prizeMoney` in `£`) und optischer Hervorhebung der eigenen Zeile (`isUser == true`).
  - Um dreistellige Ränge wie `#100` oder `#128` einzeilig und ohne Umbruch darzustellen, haben `tvRank` in `item_order_of_merit.xml` sowie `tvStickyRankTop` und `tvStickyRankBottom` in `fragment_order_of_merit_container.xml` eine feste Breite von `52dp` und `android:maxLines="1"`.
* **Dynamische Sticky-User-Karten & Auto-Scroll:**
  * Ist die Zeile des Users außerhalb des sichtbaren Bereichs platziert, wird dynamisch eine Sticky-User-Karte eingeblendet:
    * Steht der User weiter oben in der Tabelle (`userIndex < firstVisible`), erscheint `cardStickyUserTop` direkt unter der Tab-Leiste (im XML im Vordergrund über dem `ViewPager2` platziert).
    * Steht der User weiter unten in der Tabelle (`userIndex > lastVisible`), erscheint `cardStickyUserBottom` am unteren Bildschirmrand.
    * Befindet sich der User im sichtbaren Bereich (`userIndex in firstVisible..lastVisible`), werden beide Karten ausgeblendet (`GONE`).
  * Ein Klick auf eine der Sticky-Karten scrollt die `RecyclerView` automatisch so zur User-Position, dass die Zeile des Users optimal im Sichtbereich platziert wird.
