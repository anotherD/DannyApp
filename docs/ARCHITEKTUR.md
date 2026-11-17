# 🏛️ System-Architektur & Datenmodell

Dieses Dokument bietet einen Gesamtüberblick über die Schichtenarchitektur, zentrale Architektur-Entscheidungen und die Daten-Pipelines der **Danny Darts App**.

---

## 📐 1. Gesamtarchitektur (MVVM + Repository Pattern)

Die App folgt dem **Model-View-ViewModel (MVVM)** Muster mit **Kotlin Coroutines / Flow** und strikter Schichten-Trennung:

```
[ UI Layer: ui/ (Activities, Fragments, ViewModels, Adapter) ]
                              │
                              ▼
[ Domain Layer: domain/ (UseCases, Models, Enums, Config) ]
                              │
                              ▼
[ Data Layer: data/ (Repositories, Room DB in data/db/) ]
```

* **Schichtenregel:** `ui` greift ausschließlich auf `domain` (und lesende Repositories) zu; `domain` enthält keinerlei Android-UI-Imports (`R.drawable`, `R.color`).
* **Reaktive UI:** Änderungen in der Room-Datenbank werden über `Flow` beobachtet, sodass sich Kalender, Turnieransicht und Ranglisten automatisch aktualisieren.

---

## 🗄️ 2. Zentrale Datenquellen & Konventionen

* **Lokale Datenbank (`data/db/AppDatabase.kt`):** Alle Room-Entitäten, DAOs, Repositories und Migrationen liegen zentral im Paket `data/db/`.
* **Saison-Konfiguration (`assets/season_calendar_2027.json` & `TournamentConfigLoader`):** Single Source of Truth für alle Saisonturniere, Austragungsorte, Runden-Preisgelder und Match-Distanzen (inkl. Fail-Fast-Validierung beim Laden).
* **Eingefrorene Turnier-Setzlisten (`tournament_participants`):** Beim Turnierstart (`GenerateBracketUseCase`) werden alle qualifizierten Teilnehmer und ihre Setzlistenposition einmalig gespeichert, damit sich der Turnierbaum während eines laufenden Turniers nicht mehr verschiebt.
* **Globale Konventionen:**
    * `USER_ID = -1L`: Feste ID für den menschlichen Spieler in Tour Cards, Matches und Preisgeld-Platzierungen.
    * `SkillLevel`: Spielstärken von `1` bis `10` (Default: `5`).
    * `Country`: ISO-3-Ländercodes (inkl. Home Nations `ENG`, `SCO`, `WAL`, `NIR`; Fallback `XYZ`, User-Default `DEU`).

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

## 📚 4. Weiterführende Dokumentation

* [🎯 Fachkonzept & Saison-Logik](SAISON_LOGIK.md)
* [📅 Saisonkalender, Turniere & Preisgeld](SAISON_KALENDER_UND_TURNIERE.md)
* [📱 Turnier-UI, Simulation & Ranglisten-Ansicht](TURNIER_UI_UND_FLOW.md)
* [🎯 Scolia Architektur & Kernkonzept](SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md)
* [🔬 Scolia Integration & Research](SCOLIA_INTEGRATION_RESEARCH.md)
* [📊 Scolia Match 501 Guest Analysis](scolia_match_501_guest_analysis.md)
