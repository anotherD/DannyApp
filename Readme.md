# 🎯 Danny Darts App (Profi-Darts-Karriere-Simulation)

## 📌 Über das Projekt
Die **Danny Darts App** ist eine umfassende Android-App zur Simulation einer eigenständigen, professionellen Dart-Karriere. Das Projekt verbindet realistische Bot-Gegner, ein ausgeklügeltes Saison- und Tour-Card-System mit einem Saison-Kalender mit Turnieren, Caller- und Sound-Engines sowie Statistiken mit einer nahtlosen **Scolia-Kamera-Integration** als Hardware-Sensor zur automatischen Treffererkennung.

---

## 🏗️ Kernmodule & Architektur

- 🏛️ **Gesamtarchitektur & Room-Datenbank:** MVVM-Pattern, Coroutines/Flow, Room DB (siehe [ARCHITEKTUR.md](docs/ARCHITEKTUR.md)).
- 🎯 **Saison- & Tour-Card-Logik:** 128er-Regel, User-Platzierung, Auffüll- & Kürzungslogik, Order of Merit (siehe [SAISON_LOGIK.md](docs/SAISON_LOGIK.md)).
- 🔌 **Scolia-Integration:** Nutzung des Scolia-Kamerasystems als reiner Hardware-Sensor über WebView & WebSocket Proxy (siehe [Scolia Architektur & Kernkonzept](docs/SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md)).

---

## 📖 Projektdokumentation (`docs/`)

Ausführliche Fachkonzepte, Forschungsberichte und Architekturbeschreibungen befinden sich im [`docs/`](docs/) Ordner:

- 🏛️ **[System-Architektur & Datenmodell](docs/ARCHITEKTUR.md)**: MVVM-Struktur, Room DB Schema.
- 🎯 **[Scolia Architektur & Kernkonzept](docs/SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md)**: Hardware-Sensor-Prinzip, WebSocket Proxy, WebView-Debugging & Logging.
- 🔬 **[Scolia Integration & Research](docs/SCOLIA_INTEGRATION_RESEARCH.md)**: Technische API-Analyse und WebSocket-Protokolle.
- 📊 **[Scolia Match 501 Guest Analysis](docs/scolia_match_501_guest_analysis.md)**: Detaillierte Auswertung von Test-Matches und Event-Mustern.
- 🎯 **[Saisons & Tour Cards](docs/SAISON_LOGIK.md)**: Turnierlogik und Saisonsystem.
- 📅 **[Saisonkalender, Turniere & Preisgeld](docs/SAISON_KALENDER_UND_TURNIERE.md)**: Detailliertes Fachkonzept der Pro Tour und Major-Turniere, K.o.- & Gruppen-Systeme, Setzlisten-Logik, Use-Case-Architektur und PDC-Preisgeldsonderregeln.
- 📱 **[Turnier-UI, Simulation & Ranglisten-Ansicht](docs/TURNIER_UI_UND_FLOW.md)**: Benutzeroberfläche, Interaktions-Flow, Test-Modi und Order of Merit Ansicht.
- 🔍 **[PDC-Regel- & Kalender-Update-Checkliste](docs/PDC_REGEL_UPDATE_CHECKLISTE.md)**: Offene 2027er-Reformen und Prompt-Vorlage für zukünftige PDC-Regelprüfungen.

---

## 🛠️ Aktueller Status & Kern-TODOs (MVP)

### ✅ Bereits umgesetzt
- [x] **Scolia Hardware-Sensor:** WebView mit WebSocket-Proxy, Tracking-Blocker, JSON-Parser & Event-Logger (`scolia_events.log`).
- [x] **Stammdaten & Profile:** User-Profile anlegen/bearbeiten/löschen, CSV-Spielerimport (`players.csv`), Spieler-Übersicht.
- [x] **Karriere & Saison-Basis:** Karrieren anlegen, 128er-Tour-Card-Vergabe für Saison 1 (`USER_ID = -1L`), Room DB.
- [x] **Turnier-Engine & Kalender (2027):** Zentrale JSON-Konfiguration (`season_calendar_2027.json`) für alle Saisonturniere (PC, ET, Majors & TCH-Qualifier), Auslosung, Runden-Progression, Preisgeld-Ausnahmen & 4 dynamische Orders of Merit.
- [x] **Turnier- & Ranglisten-UI:** Saisonkalender, Ranglisten-Ansicht mit 4 dynamischen Ranglisten, Turnieransicht (Vorschau, Aktiv, Rückblick) inkl. Test-Modi.

### 🚧 Nächste Schritte (Kern-Funktionen)
- [ ] **Code-Refactoring & Turnierbaum-Stabilisierung:**
    - [ ] Turnier-UI und Turnierbaum-Ansicht (`TournamentBracketView`) nach Code-Bereinigung vollständig stabilisieren und Darstellungs-/Ausrichtungs-Bugs beheben.
- [ ] **Live-Match & Scolia-Anbindung:**
    - [ ] User-Match Live-Ansicht (Scoreboard, Restpunkte, Legs/Sets gemäß `MatchDistance` & Double-In/Out).
    - [ ] Spielsteuerung & Match-Engine (Wurf-Verarbeitung, Bot-Wurf-Timing, Caller/SoundPool-Engine).
    - [ ] Scolia-Steuerung (Match starten, beenden, neustarten, Reconnect & Fehlerbehandlung).
- [ ] **Saison-Abschluss & Folgesaisons (Basis):**
    - [ ] Berechnung der Tour Cards für Saison N+1 anhand der 2-Jahres-Order-of-Merit (Top 64 behalten Card, 2. Jahr verfällt).
    - [ ] Rudimentäre Q-School-Simulation (zufällige Auffüllung neuer Tour-Card-Inhaber).
- [ ] **Karriere-Verwaltung & Übersicht ausbauen:**
    - [x] Auf der Karrieren-Liste (`CareersActivity`) zusätzlich anzeigen, bei welchem Turnier und welcher Runde die Karriere aktuell steht.
    - [x] Karriere-Name nachträglich bearbeitbar machen.
    - [ ] Karriere-Level hochsetzen ermöglichen (z. B. gezielt nach Saisonende, damit die Karriere bei steigendem eigenem Können spannend bleibt).
    - [ ] Karriere-Übersicht (`CareerOverviewFragment`) um zentrale Infos ergänzen:
        - [ ] Direktanzeige „Nächstes Match“ zum schnellen Losspielen (über Einstellungen ein-/ausschaltbar, um das Turnier-Feeling bei Bedarf zu erhalten).
        - [x] Aktuelle Saison sowie eigene Positionen in den Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).
        - [ ] Turnier-Qualifikationsübersicht mit Ampelstatus (Grün = fix qualifiziert, Gelb = aktuell drin/unsicher, Rot = nicht qualifiziert) – mit Caching/Performance-Fokus.
        - [ ] Eigener 3-Dart-Average (sobald Spielstatistiken erfasst werden).
- [ ] **Turnier-Details, Infos & Rückblick:**
    - [x] Modifizierbare Match-Distanzen vor Karrierestart (z. B. 25% / 50 % / 75 % / 100 % Distanz für kürzere Spielzeiten).
    - [x] Turnier-Beschreibungen in der Vorschau um Qualifikations-Boni ergänzen (z. B. *„Sieger qualifiziert sich für Grand Slam of Darts“*).
    - [x] Turnier-Info-Dialog erweitern:
        - [x] Teilnehmerliste mit Qualifikationsweg und Setzposition pro Spieler anzeigen (live aktualisiert für bereits fix qualifizierte Spieler + temporärer Stand).
        - [x] Bei den Runden-Preisgeldern die Ranglisten-Ausnahmen anzeigen (z. B. £0 bei Auftaktniederlage gesetzter Spieler auf der European Tour).
    - [ ] Im Turnier-Rückblick (abgeschlossene Turniere) optional alle eigenen Spiele dieses Turniers gesammelt anzeigen.
- [ ] **UI-Polish, Navigation & Branding:**
    - [ ] Finaler App-Name, eigenes Launcher-Icon und überarbeitete, moderne Startseite (`MainActivity`).
    - [ ] Match-Nummer-Badge als echte integrierte Ausbeulung der Match-Kachel gestalten (statt darübergelagertem Badge).
    - [x] Eindeutige Match-Nummerierung einführen (mit Runden-Präfix wie `#L64-1` inkl. Platzhalter-Verweisen).
    - [x] Horizontales Ausfaden (`fadingEdge`) an den Bildschirmrändern links/rechts für horizontal scrollbare Elemente (Runden-Pills, Board-Pills, Gruppen-Pills, Ranglisten-Pills).
    - [ ] Einheitliches Chip-Auswahl-Verhalten angleichen: Runden-Chips (aktuell Rahmen = Auswahl, Hintergrund = Status) und Ranglisten-Chips (Hintergrund = Auswahl) auf einen konsistenten Android-Standard bringen.
    - [ ] Material 3 Expressive Motion: Taktilen Spring-/Jump-Effekt (`SpringAnimation` zentral in `UIHelper`) gezielt für primäre Buttons, FABs und Filter-Chips einbauen (nicht für scrollbare Listen-Kacheln).
    - [x] Toasts durch einheitliche UI-Hinweise (z. B. Material `Snackbar` oder eigene Status-Banner) ersetzen.
- [ ] **Spieler-Detailansicht:** Klick auf einen Spielernamen (in Turnieren, Ranglisten oder Spielerliste) öffnet die Detailseite mit Karriere-Stats und Erfolgen.
- [ ] **Match-Detailansicht:** Klick auf ein abgeschlossenes oder laufendes Match öffnet die Match-Statistiken (Leg-/Set-Verlauf, Averages, Check-outs).
- [ ] Entwicklungs-Hinweis zu DB-Fallbacks aus INSTRUCTIONS.md entfernen, sobald Produktiv-Migrationen greifen

---

## 🚀 Geplante Ausbaustufen (Roadmap)

### 1. Realismus, Simulation & Karrieretiefe
- [ ] **Initiale Order of Merit (Karrierestart):** Checkbox beim Anlegen einer Karriere (Standard: aktiv), um mit realen `players.csv`-Preisgeldern zu starten und Vorjahrespreisgelder (`yeartodate2025`) über die Saison hinweg anteilig (mit Major-Gewichtung) abzuschmelzen.
- [ ] **Erweiterte Simulations-Anzeige (Konfigurierbar):**
    - [ ] Im Simulations-Overlay anzeigen, welche Runde gerade simuliert wird (in den Einstellungen ein-/ausschaltbar).
    - [ ] Optionale(Settings) Live-Simulation der CPU-Matches: Match wird markiert, Ergebnis poppt auf, konfigurierbare Wartezeit (z. B. 1–3 Sekunden oder deaktivierbar), weiter zum nächsten Match.
- [ ] **Turnier-Absagen & Antritts-Logik:**
    - [ ] Option für den User, einzelne Turniere abzusagen (inkl. Nachrücker-Regelung).
    - [ ] Dynamische CPU-Absagen (Top-Stars lassen öfter Players Championships aus; Spieler spielen häufiger, wenn ihre Major-Qualifikation oder Tour Card wackelt).
- [ ] **Erweiterte Q-School & Saison-Warnungen:**
    - [ ] Spielbare/simulierte Q-School am Saisonende für User (bei Tour-Card-Verlust) und CPU-Spieler.
    - [ ] Saison-Warnung im 2. Tour-Card-Jahr: Anzeige, wie viel Preisgeld zum sicheren Erhalt der Tour Card (Top 64) fehlt.
- [ ] **Saison-Historie & Erfolge:** Übersicht abgeschlossener Saisons mit Titeln, besten Major-Resultaten und Karriere-Meilensteinen des Users.
- [ ] **Optionale Non-Ranking-Events:** Integration von *Premier League*, *World Masters* und *World Series of Darts (inkl. Finals)*.
- [ ] **Detaillierte Match- & Spieler-Statistiken:** 3-Dart-Average, Checkout-Quote, 180er-Zähler für User und detaillierte CPU-Simulation.

### 2. Spielerdatenbank & CSV-Pipeline (`scripts/`)
- [ ] **Python-Generator-Script ins Repo aufnehmen (`scripts/`):**
    - [ ] Gewichtete Skill-Berechnung aus den Preisgeldern der letzten Jahre statt reiner Gesamtsumme.
    - [ ] Manuelle Skill-Overrides im Script oder bessere Logik der Skillberechnung (z. B. für Top-Spieler mit wenig Turnierteilnahmen wie Gary Anderson).
- [ ] **Spieler-Verwaltung in der App & Import-Logik:**
    - [ ] Spieler in der App bearbeitbar machen & eigene Custom-Spieler anlegen (`is_custom` / `manual_skill_override` schützen vor CSV-Überschreibung).
    - [ ] Spieler-Profil erweitern (Geburtsdatum, Spielerbild/Avatar).
    - [ ] Soft-Delete (`is_active = false`) beim CSV-Import für ausgeschiedene Spieler (Tour Card in Stammdaten entziehen, laufende Karrieren sauber auslaufen lassen).
    - [ ] Erweiterte regionale Qualifier-Pools für die WM und weitere Turniere (Women's Series, Asian Tour, African Qualifier, Challenge/Development Tour).

---

## 📊 CSV-Spielerdatenbank & Updates (`players.csv`)

Die App importiert beim ersten Start die Spielerdaten aus `app/src/main/assets/players.csv` in die lokale Room-Datenbank.
- 💡 **Anleitung für Updates:** Wie du `players.csv` aktualisiert und die `CURRENT_CSV_VERSION` in `AppDatabase.kt` anpasst, findest du direkt in der [System-Architektur & Datenmodell Dokumentation (Abschnitt 3.1)](docs/ARCHITEKTUR.md#31-anleitung-so-führst-du-ein-update-der-spielerdaten-durch).
