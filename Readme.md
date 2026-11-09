# Danny Darts App (Scolia Integration & Karrieremodus)

## 📌 Motivation & Zielsetzung
Scolia bietet eine hervorragende Hardware zur automatischen Treffererkennung per Kamera, jedoch sind die integrierten Bots auf der Webseite limitiert. Ein offizieller API-Zugriff für eigene Apps wird von Scolia nicht oder nur zu sehr hohen monatlichen Kosten angeboten.

Diese App ermöglicht eine eigenständige, professionelle Dart-Karriere-Simulation mit realistischen Bots, Callern und Statistiken, indem das Scolia-Kamerasystem als rein lokaler **Hardware-Sensor zur Wurferkennung** genutzt wird.

---

## 🏗️ Architektur & Kernkonzept

### 1. Scolia als reiner Hardware-Sensor (Einweg-Kommunikation)
- Ein unsichtbares Android `WebView` lädt im Hintergrund ein **9999-Punkte Solo-Spiel** auf `game.scoliadarts.com`.
- Das Scolia-Kamerasystem erfasst kontinuierlich alle Würfe des menschlichen Spielers.
- Über einen JavaScript-Hook (`WebSocket Proxy`) werden die Wurf-Events in Echtzeit an die Android-App übermittelt.
- **Vorteil:** Es müssen **keine** Bot-Würfe oder Steuerbefehle an Scolia zurückgesendet werden. Die App ist dadurch 100 % unabhängig, extrem stabil und zukunftssicher gegen Scolia-Webseiten-Updates.

### 2. Datenfluss
```
[ Scolia Kameras ] ──> [ Scolia WebApp (WebView Hintergrund) ]
                                    │
                                    │ (WebSocket Proxy Hook)
                                    ▼
                          [ Android App Core ]
                             ├── Menschlicher Wurf (Event-Empfang)
                             ├── Bot-Wurf (Lokale KI-Simulation)
                             ├── Caller & Sound-Engine (SoundPool)
                             └── Karrieremodus & UI-Anzeige
```

---

## 🛠️ Technische Umsetzung (Proof of Concept - PoC)

### 1. WebSocket Proxy Hook (`MainActivity.kt`)
Um die Datenpakete des Scolia-WebSockets abzufangen, ohne die Funktionalität des WebSockets zu stören, wird ein JavaScript `Proxy` injiziert:
- Erhält die statischen `WebSocket`-Eigenschaften (`WebSocket.OPEN` etc.) aufrecht.
- Leitet eintreffende JSON-Datenpakete (`API::GAME::GAME_STATE_CHANGED`, Wurf-Koordinaten, Sektoren) über das `@JavascriptInterface` an Kotlin weiter.

### 2. WebView & AdBlocker Konfiguration
- **JavaScript & DOM Storage:** Aktiviert für Session-Verwaltung.
- **Desktop User-Agent:** Erzwingt die vollständige Chrome-Desktop-Ansicht.
- **Tracking-Blocker:** Fängt Netzwerkanfragen an `posthog.com` in `shouldInterceptRequest` ab und gibt eine leere JSON-Antwort `{}` zurück.

---

## 🔍 WebView Debugging (Chrome DevTools)

Das Chrome-Entwicklerwerkzeug (DevTools) für die `WebView` ist so konfiguriert, dass es **automatisch nur in Debug-Builds** aktiv ist:

### Wann ist Debug-Mode aktiv?
- **Aktiv (Debug-Build):** Wenn die App direkt aus Android Studio gestartet wird („Run“ / „Debug“) oder ein Debug-APK gebaut wurde (`./gradlew assembleDebug`).
  - Das Android-Buildsystem setzt dabei automatisch `FLAG_DEBUGGABLE` (`android:debuggable="true"`).
  - In Google Chrome auf dem PC kann unter `chrome://inspect` das verbundene Android-Gerät ausgewählt werden, um die WebView, DOM, Konsolen-Logs und WebSockets live zu analysieren.
- **Inaktiv (Release-Build):** Wenn die App für die Produktion oder den Play Store gebaut wird (`./gradlew assembleRelease`).
  - `FLAG_DEBUGGABLE` ist deaktiviert (`false`). Das schützt die WebView in der fertigen App vor unbefugtem Zugriff oder Inspektion per USB/ADB.

---

## 📊 CSV-Spielerdatenbank & Updates (`players.csv`)

Die App importiert beim ersten Start die Spielerdaten aus der Datei `app/src/main/assets/players.csv` in die lokale Room-Datenbank.

### Performance-Caching
Damit beim normalen Start der App nicht jedes Mal die gesamte CSV-Datei eingelesen und verarbeitet werden muss, ist in `AppDatabase.kt` ein Versionierungs-Caching über `SharedPreferences` verbaut (`KEY_CSV_DATA_VERSION`).

### 🔄 Anleitung: So führst du ein Update der Spielerdaten durch
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

## 📂 Zugriff auf die Logdatei (`scolia_events.log`)

So öffnest du die Event-Logdatei direkt in Android Studio:
1. Klicke ganz rechts unten in Android Studio auf den Reiter **Device File Explorer** (oder oben im Menü: `View` ➡️ `Tool Windows` ➡️ `Device File Explorer`).
2. Navigiere in der Ordnerstruktur zu:  
   `data` ➡️ `data` ➡️ `com.danny.dannyapp` ➡️ `files`
3. Doppelklicke auf **`scolia_events.log`**, um sie direkt im Editor anzuzeigen.

---

## 🚀 Roadmap & Ausblick

- [x] **PoC:** WebSocket Proxy zur zuverlässigen Wurferkennung im WebView.
- [x] **PoC:** Tracking-Blocker & Stabile WebView-Konfiguration.
- [ ] **JSON-Parser:** Detaillierte Auswertung aller Wurf-Events (Sektor, Ring, Koordinaten).
- [ ] **Caller Engine:** Latenzfreie Sound-Wiedergabe ("180!", "Twenty", "Game Shot") via Android `SoundPool`.
- [ ] **Bot-Simulation:** KI-Gegner basierend auf echten Spielerdatenbanken mit unterschiedlichen Stärkeprofilen (Averages von 40 bis 100+).
- [ ] **Karrieremodus:** Simulation von Profi-Turnieren, Ranglisten, Saisons und umfangreichen Statistiken.
