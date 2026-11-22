# 🏛️ System-Architektur & Datenmodell

Dieses Dokument bietet einen Gesamtüberblick über die Schichtenarchitektur, zentrale Architektur-Entscheidungen, technische Abläufe sowie die Daten-Pipelines der **Danny Darts App**.

---

## 📐 1. Gesamtarchitektur (MVVM + Repository Pattern)

Die App folgt dem **Model-View-ViewModel (MVVM)** Muster mit **Kotlin Coroutines / Flow** und strikter Schichten-Trennung:

```
[ UI Layer: ui/ (Activities, Fragments, ViewModels, Adapter in feature sub-packages: career/, profile/, oom/, tournament/, settings/, model/, enums/) ]
                              │
                              ▼
[ Domain Layer: domain/ (UseCases, Models, Enums, Config, Strategy) ]
                              │
                              ▼
[ Data Layer: data/ (Repositories, Room DB in data/db/) ]
```

* **Schichtenregel:** `ui` greift ausschließlich auf `domain` (und lesende Repositories) zu; `domain` enthält keinerlei Android-UI-Imports (`R.drawable`, `R.color`, `Context`).
* **Reaktive UI:** Änderungen in der Room-Datenbank werden über `Flow` beobachtet, sodass sich Kalender, Turnieransicht und Ranglisten automatisch aktualisieren.
* **Repository-Pflicht (Kein direkter DAO-Zugriff in UI & Domain):** Neue Datenbank-Zugriffe werden ausschließlich über Repositories (`data/db/*Repository.kt`) gekapselt, die garantierte non-null Verträge (`requireNotNull`) und zusammengesetzte Transaktionen bereitstellen – `ui/` und `domain/` greifen niemals direkt auf rohe DAOs zu.

---

## 🗄️ 2. Zentrale Datenquellen & Konventionen

* **Lokale Datenbank (`data/db/AppDatabase.kt`):** Alle Room-Entitäten, DAOs, Repositories und Migrationen liegen zentral im Paket `data/db/`.
* **Saison-Konfiguration (`assets/season_calendar_2027.json` & `TournamentConfigLoader`):** Single Source of Truth für alle Saisonturniere, Austragungsorte, Runden-Preisgelder und Match-Distanzen (inkl. Fail-Fast-Validierung beim Laden).
* **Eingefrorene Turnier-Setzlisten (`tournament_participants`):** Beim Turnierstart (`GenerateBracketUseCase`) – bzw. bei vorgeschalteten Qualifiern bereits beim Qualifier-Start (`PreQualifyMainTournamentUseCase`) – werden qualifizierte Teilnehmer und ihre Setzlistenposition gespeichert, damit sich der Turnierbaum während eines laufenden Turniers nicht mehr verschiebt.
* **Globale Konventionen:**
    * `USER_ID = -1L`: Feste ID für den menschlichen Spieler in Tour Cards, Matches und Preisgeld-Platzierungen.
    * `SkillLevel`: Spielstärken von `1` bis `10` (Default: `5`).
    * `Country`: ISO-3-Ländercodes (inkl. Home Nations `ENG`, `SCO`, `WAL`, `NIR`; Fallback `XYZ`, User-Default `DEU`).
* **Fail-Fast-Prinzip:** Interne Pflichtfelder (z. B. `Career.level`, `Career.matchDistancePreset`, Turnier-Runden, Setzlisten) und Domain-Übergänge verwenden keine stillen Fallbacks, sondern lösen bei Inkonsistenzen sofort eine `IllegalStateException` aus.
* **Karriere-Level vs. Skill-Level (Schwierigkeits- & Simulationsmodell):**
    * `Career.level` (`1..25`): Globaler Schwierigkeitsgrad der Karriere, der das Ziel-Average-Niveau aller CPU-Gegner festlegt (Basis-Durchschnitt: `22 + level * 3`, d. h. Level 1 ≈ 25 Avg bis Level 25 ≈ 97 Avg). Der menschliche User selbst spielt live ohne festen Skill-Wert (für Schnell-Simulationen wird der User bis zur finalen Game-Engine als neutraler Durchschnitt `SkillLevel.DEFAULT` gewertet).
    * `SkillLevel` (`1..10`, Default: `5`): Relatives Leistungsgefälle der CPU-Spieler untereinander innerhalb der durch das Karriere-Level vorgegebenen Average-Spanne (z. B. `Skill 10` wie Luke Littler am oberen Rand, `Skill 5` wie Rob Cross im Zentrum).
* **Modifizierbare Match-Distanzen (`MatchDistancePreset` & `Career.matchDistancePreset`):**
    * Beim Anlegen einer Karriere wird verbindlich eines der 4 Distanz-Presets (`ORIGINAL` = 100 %, `SHORTENED` = 75 %, `COMPACT` = 50 %, `RAPID` = 25 %) gewählt und dauerhaft an der `Career`-Entity gespeichert.
    * `TournamentEvent.getMatchDistance(round, preset)` liest die Basisdistanz ausschließlich aus `TournamentConfigLoader` (Single Source of Truth ohne hartcodierte Fallbacks) und skaliert sie zentral über `MatchDistancePreset.scaleDistance(...)` für Match-Simulation, Score-Validierung und Turnier-Info-Anzeige.

---

## 🔄 3. CSV-Spielerimport & Synchronisation

* Stammdaten der CPU-Spieler liegen in `app/src/main/assets/players.csv`.
* Beim ersten Start oder bei Erhöhung von `CURRENT_CSV_VERSION` in `AppDatabase.kt` führt `syncPlayersFromCsv` neue Spieler hinzu und aktualisiert bestehende Stammdaten, ohne deren Datenbank-ID zu verändern.

### 3.1 Anleitung: Update der Spielerdaten durchführen
Wenn du `players.csv` in den Assets bearbeitest:

1. Öffne `app/src/main/java/com/danny/dannyapp/data/db/AppDatabase.kt`.
2. Erhöhe die Konstante `CURRENT_CSV_VERSION` um `1`:
   ```kotlin
   companion object {
       private const val PREFS_NAME = "app_db_prefs"
       private const val KEY_CSV_DATA_VERSION = "csv_data_version"
       private const val CURRENT_CSV_VERSION = 2 // 👈 Hier jeweils um 1 erhöhen
   }
   ```
3. Beim nächsten App-Start erkennt die App automatisch die neuere CSV-Version und synchronisiert die Daten einmalig.

---

## 🌳 4. Persistente Turnierbaum-Speicherung & Fortschrittssteuerung

* **Vollständige Baum-Generierung (`GenerateBracketUseCase` & `TournamentRuleStrategyRegistry`):**
  * Turnierspezifische Match-Generierungs- und Qualifier-Übernahmeregeln sind als `TournamentRuleStrategy` unter `domain/strategy/` gekapselt.
  * Für alle Turniere mit festem Turnierbaum werden beim Turnierstart bereits **alle Runden** (von Runde 1 bis Finale bzw. Gruppenphase bis Finale) in `tournament_matches` angelegt.
  * Noch nicht feststehende Paarungen nutzen Platzhalter-IDs (`PLACEHOLDER_PREVIOUS_WINNER_ID = -999L`, `PLACEHOLDER_OPEN_DRAW_ID = -998L`, Gruppen-Sieger-IDs `-9000L..` oder Vorrunden-Platzhalter `-1000L..`).
  * `UK_OPEN` und `EUROPEAN_OPEN` nutzen ab Runde 2 eine offene Runden-für-Runden-Auslosung ohne Vorab-Generierung der Folgerunden.
  * **Persistente Startrunde:** Unmittelbar nach der Bracket-Erzeugung wird die erste tatsächlich angelegte Runde als aktive Startrunde direkt am Turnier gespeichert (`Tournament.currentRound`).
  * **Gewinner-basiertes Preisgeld-Modell:** Alle qualifizierten Teilnehmer erhalten ihr garantiertes Einstiegspreisgeld (inkl. gestaffelt einsteigender gesetzter Spieler bei `UK_OPEN` und `EUROPEAN_OPEN`, aber unter Beachtung der £0-Auftaktniederlagen-Regel für vorqualifizierte European-Tour-Seeds) **sofort beim Turnierstart** gutgeschrieben.
* **Sofortige Gewinner-Hochstufung (`AdvanceTournamentRoundUseCase.propagateWinnerOnMatchCompleted`):**
  * Sobald ein Match abgeschlossen ist, wird der Gewinner sofort in den entsprechenden Slot der Folgerunde eingetragen und im Preisgeld-Placement unmittelbar auf die erreichte Folgerunde hochgestuft (wodurch z. B. vorqualifizierte ET-Seeds nach ihrem Auftaktsieg automatisch von £0 auf das reguläre Rundenpreisgeld steigen).
  * Beim *Grand Slam of Darts* werden nach Abschluss der Gruppenphase Gruppenzweite und Gruppensieger automatisch auf ihre entsprechenden Platzierungs-Preisgelder hochgestuft.
* **Zentrale Zustandsmaschine & Rundenfortschritt (`AdvanceTournamentRoundUseCase`):**
  * Ermittelt über `evaluateTournamentState(...)` als *Single Source of Truth* den Rundenstatus: Die **aktive Runde wird direkt vom Turnier selbst gelesen** (`tournament.currentRound`).
  * Alle Runden vor der aktiven Runde gelten als abgeschlossen, alle danach als ausstehend – einheitlich für alle Turnierarten inkl. Qualifier, ohne gesonderte Sonderbehandlung.
  * Die Preisgelder jeder abgeschlossenen Runde werden **sofort** beim Rundenwechsel verbucht; nach dem Finale wird das Sieger-Preisgeld eingetragen, das Turnier als abgeschlossen markiert und die aktive Runde am Turnier gelöscht (`currentRound = null`).
  * Bei Qualifiern (`QUALIFIER_ET` = 10, `QUALIFIER_GRAND_SLAM` = 8, `QUALIFIER_WM` = 2) wird das Turnier nach Abschluss des Qualifikationsfinales ohne Preisgeldbuchung beendet.
  * Das Weiterschalten erfolgt entweder manuell nach Abschluss aller Matches einer Runde oder bei aktivem Schnellfortschritt automatisch.

---

## 📚 5. Domain-Komponenten & UI-Helper

### 5.1 Domain Config, Models & UseCases (`domain/`)
* **`TournamentConfigLoader` & `season_calendar_2027.json`**: Zentraler Loader inkl. Fail-Fast-Validierung für Kalender, Preisgelder und Match-Distanzen.
* **`TournamentEvent` & `TournamentRound` & `MatchDistance` & `MatchDistancePreset`**: Enums und Datenmodelle für Turniertypen, Rundenfolgen, Preisgeld-Auszahlungen pro Runde, Best-of-Legs/Sets, Two-Clear-Legs-Regeln sowie karrierespezifische Distanz-Presets.
* **`TournamentRuleStrategy` & `TournamentRuleStrategyRegistry`**: Kapselt turnierspezifische Match-Generierungs- und Qualifier-Übernahmeregeln unter `domain/strategy/`.
* **`GenerateSeasonCalendarUseCase`**: Erzeugt die chronologische Liste aller Saisonturniere aus `season_calendar_2027.json`.
* **`GenerateBracketUseCase` & `BracketGenerator`**: Erzeugt Setzlisten, Erstrunden-Paarungen (inkl. dynamischer Qualifikanten-Brackets mit Vorrunden/Freilosen), speichert Teilnehmer/Setzlisten (`tournament_participants`) und verbucht das garantierte Einstiegspreisgeld.
* **`CompleteMatchUseCase` & `MatchScoreValidator`**: Einheitlicher UseCase für Match-Simulation, manuelle Ergebniseingabe, Score-Validierung und Debug-Abschluss basierend auf Skill und Wahrscheinlichkeiten.
* **`AdvanceTournamentRoundUseCase`**: Zentrale Zustandsmaschine für Gewinner-Hochstufung, Rundenfortschritt und Turnierabschluss.
* **`TournamentProgressionUseCase`**: Verwaltet den Gesamtfortschritt eines Turniers und steuert über `simulateCompleteTournament(...)` die komplette Turniersimulation zentral von der Bracket-Erstellung über realistische CPU- und User-Matches bis zum Turnierabschluss.
* **`TournamentQualificationUseCase` & `QualificationHelper`**: Zentrale Logik für Qualifikationspools, Setzlisten, OOM-Sortierung, Qualifier-Board-Zuordnung und Vorschau-Paarungen.
* **`PreQualifyMainTournamentUseCase`**: Ermittelt die 1:1-Zuordnung zwischen vorgeschaltetem Qualifier und Hauptturnier (`QUALIFIER_ET` ↔ `EUROPEAN_TOUR`, `QUALIFIER_GRAND_SLAM` ↔ `GRAND_SLAM_OF_DARTS`, `QUALIFIER_WM` ↔ `WORLD_CHAMPIONSHIP`) und friert alle vorqualifizierten Hauptfeld-Teilnehmer bereits beim Qualifier-Start idempotent in `tournament_participants` ein.
* **`GrandSlamQualificationResolver`**: Kapselt die Prioritäts-Qualifikation des Grand Slam of Darts (Majors, Additional Qualifiers, ET-/PC-Saisonsieger vor dem Qualifier sowie Auffüllen mit Qualifier-Siegern und Topf-1–3-Setzliste beim Hauptturnier-Start).
* **`ResolveNonSimulatedQualifiersUseCase`**: Zentrale Ermittlung nicht-simulierter regionaler und internationaler Qualifier-Pools (ISO-3-Regionen nach OOM sowie Sekundärtouren/Damen/Youth).
* **`CalculateGroupStandingsUseCase`**: Single Source of Truth für Grand-Slam-Gruppentabellen und Tabellenberechnung.
* **`GetTournamentSummaryUseCase`**: Einheitliche Zustandsermittlung (`COMPLETED`/`ACTIVE`/`PLANNED`), Sieger- & User-Platzierungs-Zusammenfassung für den Saisonkalender und die Detailansicht.
* **`GetOrderOfMeritUseCase`**: Berechnet und liefert die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).
* **`GetPlayersUseCase`**: Reaktiver Zugriff auf sortierte Spielerlisten für die Spielerkonfiguration (`SettingsPlayersActivity`).

### 5.2 UI-Paketstruktur & Helper (`ui/`)
* **`ui/UIHelper.kt`**: Zentrale Helper für Flaggen-Rendering (`formatFlagAndText`), dynamische Namenskürzungen (`bindSmartPlayerName`) und Match-Kachel-Stile (`applyMatchCardStyle`).
* **`ui/career/`**: Karrieren-Übersicht, Detailansicht und Pager.
* **`ui/profile/`**: Profilverwaltung und -auswahl.
* **`ui/oom/`**: Order of Merit Container, Fragmente und Adapter.
* **`ui/tournament/`**: Turnierkalender, Turnierdetails, ViewModel, Mapper, Bracket-View und UI-Helper (`TournamentUiHelper`, `TournamentDetailUiHelper`, `TournamentBracketCardBuilder`, `TournamentDialogs`).
* **`ui/settings/`**: App-Einstellungen und Spielerkonfiguration.

---

## 📚 6. Weiterführende Dokumentation

* [🎯 Fachkonzept & Saison-Logik](SAISON_LOGIK.md)
* [📅 Saisonkalender, Turniere & Preisgeld](SAISON_KALENDER_UND_TURNIERE.md)
* [📱 Turnier-UI, Simulation & Ranglisten-Ansicht](TURNIER_UI_UND_FLOW.md)
* [🎯 Scolia Architektur & Kernkonzept](SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md)
* [🔬 Scolia Integration & Research](SCOLIA_INTEGRATION_RESEARCH.md)
* [📊 Scolia Match 501 Guest Analysis](scolia_match_501_guest_analysis.md)
