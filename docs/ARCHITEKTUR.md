# 🏛️ System-Architektur & Datenmodell

Dieses Dokument bietet einen Gesamtüberblick über die Architektur, die Datenbank-Struktur und die Datenflüsse der **Danny Darts App**.

---

## 📐 1. Gesamtarchitektur (MVVM + Repository Pattern)

Die App folgt den empfohlenen Android-Architekturprinzipien mit **Model-View-ViewModel (MVVM)** und **Kotlin Coroutines / Flow**:

```
[ UI Layer: Activities / ViewModels ]
                 │
                 ▼
[ Repository Layer: CareerRepository, UserProfileRepository, SeasonRepository ]
                 │
                 ▼
[ Local Data Layer: Room DB (AppDatabase v12) ]
   ├── UserProfileDao
   ├── CareerDao
   ├── SeasonDao
   ├── TourCardDao
   └── PlayerDao
```

---

## 🗄️ 2. Room Datenbank Schema (`AppDatabase` v12)

```mermaid
erDiagram
    USER_PROFILES ||--o{ CAREERS : "besitzt (1:N)"
    CAREERS ||--o{ SEASONS : "beinhaltet (1:N)"
    SEASONS ||--o{ TOUR_CARDS : "enthaelt (exakt 128)"
    PLAYERS ||--o{ TOUR_CARDS : "CPU Spieler (null fuer User)"

    USER_PROFILES {
        long id PK
        string name UK
        string description
        long createdAt
    }

    CAREERS {
        long id PK
        string name
        int level
        int season
        long profileId FK
        long createdAt
    }

    SEASONS {
        long id PK
        long careerId FK
        int seasonNumber
        boolean isCompleted
        long createdAt
    }

    TOUR_CARDS {
        long id PK
        long seasonId FK
        long playerId "-1L (USER_ID) fuer User"
        int tourCardYear "1 oder 2"
    }

    PLAYERS {
        long id PK
        string name UK
        int skill "1-10"
        string countryCode
        double prizeMoney
        int tourCardYear "0-2"
        long updatedAt
    }
```

### 2.1 Tabellen-Beschreibung

1. **`user_profiles`**: Speichert menschliche Nutzerprofile (z. B. "Danny").
2. **`careers`**: Speichert Karrieren. Verweist per Fremdschlüssel `profile_id` auf `user_profiles.id` (`CASCADE`).
3. **`seasons`**: Speichert Saisons einer Karriere. Verweist per `career_id` auf `careers.id` (`CASCADE`).
4. **`tour_cards`**: Speichert die exakt 128 Tourcard-Inhaber pro Saison.
   - `player_id = USER_ID (-1L)` -> Der menschliche User der Karriere.
   - `player_id != USER_ID` -> CPU-Spieler aus `players`.
   - Property `isUser`: `playerId == USER_ID`.
5. **`players`**: Stammdatenbank aller Dart-Profis (importiert aus `players.csv`).

---

## 🔄 3. CSV-Spielerimport & Synchronisation

- Stammdaten der CPU-Spieler liegen in `app/src/main/assets/players.csv`.
- Beim ersten Start oder bei Erhöhung von `CURRENT_CSV_VERSION` in `AppDatabase.kt` führt `syncPlayersFromCsv` folgende Schritte aus:
  1. `csvInsertNewPlayers`: Fügt neue Spieler hinzu (sofern der Name nicht existiert).
  2. `csvUpdatePlayerStats`: Aktualisiert Skill, Land, Preisgeld und Stammdaten bestehender Spieler ohne deren Datenbank-ID zu verändern.

### 3.1 Anleitung: So führst du ein Update der Spielerdaten durch
Wenn du die Datei `players.csv` in den Assets bearbeitest (z. B. neue Spieler hinzufügst, Spielstärken anpasst oder Preisgelder aktualisierst):

1. Öffne `AppDatabase.kt` (`app/src/main/java/com/danny/dannyapp/data/db/AppDatabase.kt`).
2. Erhöhe die Konstante `CURRENT_CSV_VERSION` um `1`:
   ```kotlin
   companion object {
       private const val PREFS_NAME = "app_db_prefs"
       private const val KEY_CSV_DATA_VERSION = "csv_data_version"
       private const val CURRENT_CSV_VERSION = 2 // 👈 Hier um 1 erhöhen (z. B. 1 -> 2)
   }
   ```
3. Beim nächsten App-Start erkennt die App automatisch, dass eine neuere Version der Spielerdaten vorliegt (`lastSyncedVersion < CURRENT_CSV_VERSION`), führt den Daten-Import einmalig aus und speichert die neue Versionsnummer ab.

---

## 📝 5. Allgemeine Dokumentationspflicht

> **WICHTIG FÜR ALLE ENTWICKLER & KI-ASSISTENTEN:**
> Bei **jeder** Änderung am Projekt (egal ob Code, neue Features, UI-Komponenten, Fachlogiken, Datenmodelle oder Architektur) **MUSS** die entsprechende Dokumentation im `docs/`-Ordner (`ARCHITEKTUR.md`, `SAISON_LOGIK.md`, `SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md` etc.) bzw. die [Readme.md](../Readme.md) unmittelbar angepasst oder erstellt werden. 
> 
> Vor dem Abschluss jeder Aufgabe ist sicherzustellen, dass die Doku den aktuellen Ist-Zustand des Codes widerspiegelt.

---

## 📚 4. Weiterführende Dokumentation

- [🎯 Fachkonzept & Saison-Logik](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/SAISON_LOGIK.md)
- [🎯 Scolia Architektur & Kernkonzept](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/SCOLIA_ARCHITEKTUR_UND_KERNKONZEPT.md)
- [🔬 Scolia Integration & Research](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/SCOLIA_INTEGRATION_RESEARCH.md)
- [📊 Scolia Match 501 Guest Analysis](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/scolia_match_501_guest_analysis.md)
