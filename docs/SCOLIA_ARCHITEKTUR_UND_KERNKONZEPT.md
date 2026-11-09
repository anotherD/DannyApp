# 🎯 Scolia-Architektur & Kernkonzept

Dieses Dokument beschreibt die Architektur und das Kernkonzept der Scolia-Hardware-Anbindung in der **Danny Darts App**.

---

## 📌 Motivation & Zielsetzung

Das Scolia-Kamerasystem bietet eine hervorragende Hardware zur automatischen Treffererkennung per Kamera. Allerdings sind die integrierten Software-Funktionen und Bots auf der offiziellen Scolia-Plattform limitiert, und ein offizieller API-Zugriff für eigene Apps wird von Scolia nicht oder nur zu sehr hohen monatlichen B2B-Kosten angeboten.

**Ziel der Scolia-Integration:** 
Nutzung des Scolia-Kamerasystems als reiner, lokaler **Hardware-Sensor zur Wurferkennung** für unsere eigenständige Dart-App.

---

## 🏗️ Architektur & Funktionsweise

### 1. Scolia als reiner Hardware-Sensor (Einweg-Kommunikation)
- Ein unsichtbares Android `WebView` lädt im Hintergrund ein **9999-Punkte Solo-Spiel** auf `game.scoliadarts.com`.
- Das Scolia-Kamerasystem erfasst kontinuierlich alle Würfe am Board.
- Über einen JavaScript-Hook (`WebSocket Proxy`) werden die Wurf-Events in Echtzeit an die Android-App übermittelt.
- **Vorteil:** Es müssen **keine** Bot-Würfe oder Steuerbefehle an Scolia zurückgesendet werden. Die App verarbeitet die gesamte Spiellogik lokal und ist dadurch 100 % unabhängig, extrem stabil und zukunftssicher gegen Scolia-Webseiten-Updates.

### 2. Datenfluss
```
[ Scolia Kameras ] ──> [ Scolia WebApp (WebView Hintergrund) ]
                                    │
                                    │ (WebSocket Proxy Hook)
                                    ▼
                          [ Android App Core ]
                             ├── Menschlicher Wurf (Event-Empfang)
                             ├── Bot-Wurf (Lokale Simulation)
                             ├── Caller & Sound-Engine (SoundPool)
                             └── Karrieremodus & UI-Anzeige
```

---

## 🛠️ Technische Umsetzung

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

## 📂 Zugriff auf die Logdatei (`scolia_events.log`)

So öffnest du die Event-Logdatei direkt in Android Studio:
1. Klicke ganz rechts unten in Android Studio auf den Reiter **Device File Explorer** (oder oben im Menü: `View` ➡️ `Tool Windows` ➡️ `Device File Explorer`).
2. Navigiere in der Ordnerstruktur zu:  
   `data` ➡️ `data` ➡️ `com.danny.dannyapp` ➡️ `files`
3. Doppelklicke auf **`scolia_events.log`**, um sie direkt im Editor anzuzeigen.

---

## 📚 Weiterführende Dokumentation

- [🔬 Scolia Integration & Research](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/SCOLIA_INTEGRATION_RESEARCH.md)
- [📊 Scolia Match 501 Guest Analysis](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/docs/scolia_match_501_guest_analysis.md)
