# Generelle Anweisungen
- Du wirst mir immer sagen, wenn deine Aussagen nur Vermutungen sind.
- Erzähle mir immer 1 oder 2 Witze, wenn ich "Gute Nacht" wünsche oder mich verabschiede.
- **Token-sparsame Antworten:** Schreibe keine langen Erklärungen, Zwischenberichte oder Zusammenfassungen im Chat. Führe die Code-Änderungen direkt aus und antworte am Ende maximal mit einer kurzen Stichpunktliste der geänderten Dateien.

# Projekt-Anweisungen
- Referenz-Codebase auf der Festplatte: C:\Users\Danny\Desktop\MDT\MDT-Gradle-export-AndroidApp .Schlage bei Fragen zu Spiellogiken, Scolia-Integration oder UI-Verhalten immer in dieser Referenz-Codebase nach. Dieses Referenz-Projekt heißt "MDT" oder "My Dart Training".
- Erstelle Unit-Tests nur, wenn explizit danach gefragt wird
- **Strings-Pflicht:** Alle sichtbaren UI-Texte, Labels, Turniertypen und Status-Bezeichnungen müssen zwingend in `app/src/main/res/values/strings.xml` gepflegt werden (keine hartcodierten deutschen Texte im Kotlin-Code).

# PDC-Regeln, Preisgelder & Kalender-Daten (Nur bei explizitem Regel-/Kalender-Update)
- **Strikte Faktenbasis (Keine Vermutungen):** Übernimm bei Turnierformaten, Preisgeldern, Qualifikationskriterien und Spielplänen ausschließlich offiziell bestätigte und verifizierte PDC-Daten für die Ziel-Saison (aktuell 2027).
- **Quellen-Prüfung:** Prüfe Änderungen immer gegen offizielle PDC-Verlautbarungen (`pdc.tv` / *PDC Rankings Rules*) sowie aktuelle Turnierpläne und rechne Preisgeldsummen gegen den Gesamttopf (Prize Fund) gegen.
- **Unvollständige Ankündigungen:** Hat die PDC eine Reform angekündigt (z. B. Teilnehmer-Erweiterung), aber noch keine Runden-Preisgelder oder Setzlisten veröffentlicht, darf **nicht** geraten werden. Behalte das bisherige verifizierte Schema im Code bei und dokumentiere die angekündigte Reform ausschließlich als `TODO`-Kommentar im Code und als Hinweis in `docs/SAISON_KALENDER_UND_TURNIERE.md`.
- **Ausnahmen immer prüfen:** Erfasse neben Standard-Preisgeldern immer Ranglisten-Sonderregeln (z. B. £0 bei Auftaktniederlage gesetzter Spieler auf der European Tour, £0 in Runde 1 bei PC/UK Open, Freilos- und Nachrücker-Regeln).

# UI-, Navigation- & Code-Standards (Verbindlich)
- **Top-Bar Breadcrumb-Standard:** Jede Unterseite zeigt als `titleText` exakt den Namen des aufrufenden Buttons/Bereichs (z. B. `Saisonkalender`, `Ranglisten`, `Turnieransicht`) und als `subtitleText` den vollständigen Breadcrumb-Pfad (z. B. `Karrieren > <Karrierename>`).
- **Debug-Modus:** Alle Schnell-Simulations-Buttons (`Komplettes Turnier simulieren`, `CPU simulieren`, `Match simulieren`) dürfen nur sichtbar sein, wenn in den Einstellungen (`SettingsManager`) `debugMode == true` ist (Default: `true`).
- **Reaktive Datenströme (Room `Flow` & `DiffUtil`):** Beobachte Datenbankänderungen konsequent über reaktive Room-`Flow`s und `ListAdapter` (`DiffUtil`), statt Listen manuell neu zu laden – so aktualisiert sich die UI bei Rücknavigation automatisch und effizient.
- **Zentrales Farbschema (`colors.xml`):** Alle UI- und Badge-Farben müssen zentral in `app/src/main/res/values/colors.xml` definiert werden (keine hartcodierten Hex-Codes im Kotlin-Code).
- **Farbkodierung der Turniertyp-Badges (über `colors.xml`):**
  - `MAJOR`: Gold-Ton
  - `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM` (Major-Qualifier): Dunkelgold
  - `QUALIFIER_ET` (ET-Qualifier): Dunkelrot
  - `EUROPEAN_TOUR`: Rubinrot
  - `PLAYERS_CHAMPIONSHIP`: Neutrales Dunkelgrau
- **Flaggen & Spielernamen (Zentrale Helper-Pflicht):** Für das Rendern von Landesflaggen, Spielernamen und dynamischen Namenskürzungen müssen zwingend die zentralen Methoden in `TournamentUiHelper` (`formatFlagAndText`, `bindSmartPlayerName`) genutzt werden.

