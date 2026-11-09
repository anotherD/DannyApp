# 🎯 Danny Darts App (Profi-Darts-Karriere-Simulation)

## 📌 Über das Projekt
Die **Danny Darts App** ist eine umfassende Android-App zur Simulation einer eigenständigen, professionellen Dart-Karriere. Das Projekt verbindet realistische Bot-Gegner, ein ausgeklügeltes Saison- und Tour-Card-System, Caller- und Sound-Engines sowie Statistiken mit einer nahtlosen **Scolia-Kamera-Integration** als Hardware-Sensor zur automatischen Treffererkennung.

---

## 🏗️ Kernmodule & Architektur

- 🏛️ **Gesamtarchitektur & Room-Datenbank:** MVVM-Pattern, Coroutines/Flow, Room DB v12 (siehe [ARCHITEKTUR.md](docs/ARCHITEKTUR.md)).
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

---

## 🛠️ Aktueller Status (In Umsetzung)

Die Scolia-Anbindung ist erfolgreich konzipiert und befindet sich in der aktiven Implementierung:
- [x] WebSocket Proxy zur zuverlässigen Wurferkennung im WebView.
- [x] Tracking-Blocker & Stabile WebView-Konfiguration.
- [ ] JSON-Parser & Event-Auswertung (Sektor, Ring, Koordinaten).
- [ ] Caller Engine (SoundPool Latenzfreie Sprachausgabe).
- [ ] Bot-Simulation & Karrieremodus-UI.

---

## 📊 CSV-Spielerdatenbank & Updates (`players.csv`)

Die App importiert beim ersten Start die Spielerdaten aus `app/src/main/assets/players.csv` in die lokale Room-Datenbank.
- 💡 **Anleitung für Updates:** Wie du `players.csv` aktualisierst und die `CURRENT_CSV_VERSION` in `AppDatabase.kt` anpasst, findest du direkt in der [System-Architektur & Datenmodell Dokumentation (Abschnitt 3.1)](docs/ARCHITEKTUR.md#31-anleitung-so-führst-du-ein-update-der-spielerdaten-durch).
