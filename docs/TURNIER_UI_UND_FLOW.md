# 📱 Fachkonzept: Turnier-UI, Simulation & Ranglisten-Ansicht

Dieses Dokument definiert die Benutzeroberfläche und den Interaktions-Flow für den Saisonkalender, den Turnierverlauf (inkl. Test-Modi für User-Matches) und die Orders of Merit in der **Danny Darts App**.

---

## 🧭 1. Navigation & Einstieg aus der Karriere-Ansicht

* **Top-Bar & Breadcrumbs:** Jede Unterseite zeigt als Titel exakt den Namen des gewählten Bereichs (z. B. `Saisonkalender`, `Turnieransicht`, `Ranglisten`)(Bei Bestimmten auswahlen, wie z.B. einer Karriere, ein Turnier, wird dessen Name angezeigtund als Subtitel den vollständigen Breadcrumb-Pfad (`Karrieren > <Karrierename>`).
* **Tab-Navigation in der Karriere-Ansicht:** Statt einzelner dicker Buttons nutzt die `CareerDetailActivity` eine übersichtliche **Tab-Navigation** (bzw. einen integrierten Tab-Container) für die drei Kernbereiche:
  1. **Saisonkalender:** Chronologische Übersicht aller 74 Turniere der aktiven Saison.
  2. **Turnieransicht:** Rundenübersicht, Gruppenphase (beim Grand Slam of Darts) und Match-Steuerung des aktiven oder ausgewählten Turniers.
  3. **Ranglisten:** Wischbare Ansicht (`ViewPager2`) für die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

## 📅 2. Saisonkalender

* **Anzeige:** Liste aller Turniere der aktuellen Saison sortiert nach `order` (1 bis 74).
  * **Turniertypen & Bezeichnungen:** Alle Turniertypen und Labels kommen lokalisiert aus `strings.xml` (keine technischen Enum-Keys wie `PLAYERS_CHAMPIONSHIP` in der UI).
  * **Austragungsort:** Anzeige als `<Flagge> <Stadtname>` (ohne das Präfix „Austragungsland:“).
  * **Kennzeichnung:** Major-Turniere und Qualifier-Turniere (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) sind optisch durch Badges hervorgehoben.
* **Strikte Turnier-Sperre (Sequenzieller Ablauf):**
  * Es darf immer **nur ein Turnier gleichzeitig aktiv** sein.
  * Ein neues Turnier kann erst gestartet werden, wenn das vorherige Turnier vollständig abgeschlossen ist (`isCompleted == true`).
* **Status-Kennzeichnung:**
  * `Abgeschlossen` (`isCompleted == true`): Zeigt den Turniersieger (inkl. Flagge) und die erreichte Runde des Users an.
  * `Aktiv` (aktuell laufendes Turnier): Hervorgehoben mit **hellerem Rot** als Status-Hintergrund und Button **„Turnier öffnen“**.
  * `Geplant` (`isCompleted == false`, noch keine Matches generiert): Hervorgehoben mit **Dunkelrot** als Status-Hintergrund. Nur das jeweils nächste anstehende Turnier (sofern kein anderes Turnier aktiv ist) bietet den Button **„Turnier starten“** (ruft `GenerateBracketUseCase` auf und öffnet die Turnieransicht).
* **Debug-Funktion (`debugMode == true`):** Ist der Debug-Modus in der Konfiguration aktiviert, erscheint beim aktiven bzw. nächsten Turnier der Button **„Komplettes Turnier simulieren“** (simuliert alle Runden dieses einen Turniers durch). Ist `debugMode == false`, wird der Button ausgeblendet.

---

## 🎯 3. Turnieransicht & Match-Steuerung

### 3.0 Die 3 Zustände der Turnieransicht
Jedes Turnier im Saisonkalender ist anklickbar und öffnet die Turnieransicht in einem von drei Modi:
1. **Vorschau-Modus (`isCompleted == false` und noch keine Matches generiert):**
  - Zeigt Turnier-Metadaten (Name, Turniertyp, `<Flagge> <Stadtname>`, Gesamtpreisgeld) sowie die **Preisgeld-Tabelle pro Runde** (über `TournamentEvent.getRankingPrizeMoney(...)`) und die Spielformate (`getMatchDistance(...)`) an.
  - Falls es das nächste anstehende Turnier im Kalender ist und kein anderes Turnier aktiv läuft, wird der Button **„Turnier jetzt starten & auslosen“** angezeigt (`GenerateBracketUseCase`).
2. **Aktiver Turnier-Modus (`isCompleted == false` und Matches vorhanden):**
  - **Runden-Auswahl:** Horizontale **Runden-Pills / Material Chips** (Single-Selection) statt eines Dropdowns (plus Gruppen-Auswahl `A–P` beim Grand Slam of Darts).
  - **Vorab-Turnierbaum (Folgerunden):** Bei Turnieren mit festem Turnierbaum sind alle Runden-Pills bereits vorab anwählbar und zeigen Platzhalter-Paarungen an (z. B. `Sieger #1 vs. Sieger #2`). Bei Turnieren mit Live-Auslosung ab Runde 4 (`UK Open`, `European Open`) werden noch nicht ausgeloste Runden als `? vs. ?` dargestellt.
  - **Match-Karten:** Zeigen Spielernamen (beim User seinen hinterlegten `playerName` samt Landesflagge) und das Ergebnis an (ohne `Skill: x`-Anzeige).
  - **Zentrale Steuerung:** Die Schalter **„Automatische Simulation“** und **„Automatisch zur nächsten Runde“** liegen zentral in der **Konfiguration (`ConfigActivity`)** und werden über `SettingsManager` gesteuert. Die manuellen Simulations-Buttons in der Turnieransicht sind nur bei `debugMode == true` sichtbar.
3. **Rückblick-Modus (`isCompleted == true`):**
  - Nutzt dieselbe Runden-Pills-Ansicht wie der aktive Modus (read-only ohne Simulations-Buttons).
  - Zeigt oben eine Zusammenfassung: **Sieger** (inkl. Landesflagge), **Final-Ergebnis** (inkl. Name & Flagge des Finalgegners), **Deine erreichte Runde** (`reachedRound`) und **Gewonnenes Preisgeld** (`prizeMoney` aus `tournament_placements`).

### 3.1 Die 4 Optionen für User-Matches (`USER_ID = -1L`)
Ist ein offenes Match mit Beteiligung des Users an der Reihe, erhält der User beim Klick auf sein Match folgende Auswahlmöglichkeiten:
1. **„Match simulieren (Skill/Zufall)“** *(nur sichtbar bei `debugMode == true`)*: Simuliert das User-Match wie ein CPU-Match (nutzt `career.level` als Skill-Wert des Users).
2. **„Sieger festlegen (Sieg/Niederlage)“** *(nur sichtbar bei `debugMode == true`)*: Setzt direkt einen gültigen Sieg- oder Niederlagen-Spielstand passend zur `MatchDistance`.
3. **„Ergebnis manuell eingeben“**: Öffnet einen Dialog zur direkten Eingabe der gewonnenen Legs/Sets (z. B. `6 : 4`). Validiert gegen `TournamentEvent.getMatchDistance(round)` (inkl. `twoClearLegsRule`), speichert das Ergebnis und triggert den Rundenfortschritt.
4. **„Live spielen (GameEngine)“**: Übergibt Match-ID und `MatchDistance` (`bestOf`, `legsPerSet`, `twoClearLegsRule`, `SETS_DOUBLE_IN_OUT`) an die bestehende Match-/Scolia-Engine.

---

## 🏆 4. Ranglisten-Ansicht

* **Wisch-Navigation (`ViewPager2`):** Flüssiges Wischen nach links und rechts zwischen den 4 Ranglisten (`Order of Merit (OOM)`, `Pro Tour (PT OOM)`, `Players Champ. (PC OOM)`, `European Tour (ET OOM)`).
* **Datenquelle:** Ruft `GetOrderOfMeritUseCase(careerId, type)` auf (zeigt beim User seinen hinterlegten `playerName` und seine Landesflagge).
* **Darstellung:** Tabelle mit Platzierung (`rank`), **Landesflagge** (statt Text-Kürzel), Spielername (`name`), Preisgeld (`prizeMoney` in `£`) und optischer Hervorhebung der eigenen Zeile (`isUser == true`).
* **Sticky-User-Karte & Auto-Scroll:** Ist die Zeile des Users aktuell nicht im sichtbaren Scrollbereich der Liste, schwebt am unteren Bildschirmrand eine **Sticky-Karte mit dem User-Eintrag** über der Liste. Ein Klick auf diese Sticky-Karte scrollt die `RecyclerView` automatisch so, dass die Zeile des Users mittig im Bildschirm angezeigt wird.
