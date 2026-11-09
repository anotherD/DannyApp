# Analyse des Referenz-Matches: 501 gegen lokalen Gast

## 📋 Kontext
* **Referenz-Logdatei:** [scolia_match_501_guest.log](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/scolia_match_501_guest.log)
* **Spielmodus:** X01 (501 Double Out)
* **Teilnehmer:** `Stellarvore` (Mensch) vs. `DannyGast` (Gast)
* **Erfasste WebSocket-Events:** 276 Events

---

## 📊 Event-Typen Übersicht & Häufigkeit

| Event-Typ                             | Anzahl | Kategorie         | Beschreibung                                                    |
|:--------------------------------------|:-------|:------------------|:----------------------------------------------------------------|
| `API::GAME::GAME_STATE_CHANGED`       | 114    | Spiel-Loop        | Haupt-Event: Wurf-Daten, Restpunkte, Averages, Wurf-Koordinaten |
| `API::COMMON::PONG`                   | 53     | System            | Heartbeat / Verbindungstest                                     |
| `API::COMMON::TAKEOUT_STARTED`        | 29     | Board-Phase       | Spieler berührt / zieht Darts aus dem Board                     |
| `API::COMMON::TAKEOUT_FINISHED`       | 29     | Board-Phase       | Darts wurden vollständig aus dem Board gezogen                  |
| `API::COMMON::SCORE_CORRECTION_STATE` | 23     | Spiel-Loop        | Status der Punkte-Korrekturen                                   |
| `API::COMMON::PUBLISH`                | 9      | Board-Status      | Board-Zustand (Calibrating, Online, Throw, Phase)               |
| `API::BULL_THROW::GAME_STATE_CHANGED` | 8      | Vor-Spiel         | Wurf-Events beim Ausbullen (Wer beginnt das Leg)                |
| `API::COMMON::REFRESH_CLIENT_TOKEN`   | 2      | Authentifizierung | Erneuerung des JWT-Web-Tokens                                   |
| `API::COMMON::HELLO_CLIENT`           | 1      | Verbindung        | Initialer WebSocket-Handshake                                   |
| `API::COMMON::PLAYER_JOINED`          | 1      | Lobby             | Spieler-Informationen (`Stellarvore`)                           |
| `API::COMMON::SBC_FOUND`              | 1      | Board-Erkennung   | Verknüpftes Board & verfügbare Bot-Konfigurationen              |
| `API::BULL_THROW::GAME_CURRENT_STATE` | 1      | Vor-Spiel         | Initialer Status beim Ausbullen                                 |
| `API::X01::GAME_STARTED`              | 1      | Spiel-Start       | Offizieller Beginn des 501-Matches                              |
| `API::GAME::GAME_CURRENT_STATE`       | 1      | Spiel-Start       | Initialer Spielzustand zu Beginn des Legs                       |
| `API::GAME::GAME_ENDED_STATISTICS`    | 1      | Spiel-Ende        | Gesamtstatistiken nach Match-Ende                               |
| `API::X01::GAME_STATS`                | 1      | Spiel-Ende        | 2D-Koordinaten-Map aller geworfenen Darts im Leg                |
| `API::X01::GAME_DELETED`              | 1      | Spiel-Ende        | Leg wurde beendet und aus dem Speicher gelöscht                 |

---

## 🔍 Detail-Analyse der Schlüssel-Events

### 1. Wurf-Erkennung (`API::GAME::GAME_STATE_CHANGED`)
Tritt bei jedem geworfenen Dart auf. Die Daten liegen in `payload.state.game.lastTriplets`:

```json
{
  "type": "API::GAME::GAME_STATE_CHANGED",
  "payload": {
    "state": {
      "game": {
        "lastTriplets": {
          "6a8a03d632f03ebcdae32eb9": {
            "0": [
              {
                "sector": "T20",
                "score": 60,
                "coordinates": [3, 99],
                "bounceout": false,
                "remainingScore": 164,
                "detectionTime": "2026-11-04T18:16:12.949Z",
                "isSuccessfulHit": true
              }
            ]
          }
        }
      }
    }
  }
}
```

* **Extrahierte Felder für unsere App:**
  * `sector`: `"T20"`, `"D16"`, `"s5"`, `"Bull"`, `"25"`, `"None"`
  * `score`: Punkte des Darts (z. B. 60, 32, 5, 0)
  * `coordinates`: X/Y-Koordinaten in mm auf dem Board
  * `remainingScore`: verbleibende Restpunkte

---

### 2. Darts herausziehen (`API::COMMON::TAKEOUT_FINISHED`)
Sobald der Spieler seine 3 Darts geworfen hat und aus der Scheibe zieht:

```json
{
  "type": "API::COMMON::TAKEOUT_FINISHED",
  "payload": {
    "falseTakeout": false
  }
}
```
* **Bedeutung für die App:** Das Board ist wieder frei. Nächster Spieler ist an der Reihe.

---

### 3. Spielende & Statistiken (`API::GAME::GAME_ENDED_STATISTICS`)
Wird gesendet, wenn der entscheidende Doppel-Dart zum Leg-Gewinn getroffen wurde:

```json
{
  "type": "API::GAME::GAME_ENDED_STATISTICS",
  "payload": {
    "game": {
      "status": "Finished",
      "type": "X01",
      "winnerIds": ["6a8a03d632f03ebcdae32eb9"],
      "scoreboard": { "...":"..."  }
    }
  }
}
```

---

## ✅ Verifizierung der Kotlin-Implementierung

Anhand dieser Analyse wurden folgende Klassen in der Android-App angepasst und verifiziert:

1. [ScoliaModels.kt](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/app/src/main/java/com/danny/dannyapp/ScoliaModels.kt): Enthält Datenklassen für `DartThrow` und das neue `ScoliaEvent.GameEnded`.
2. [ScoliaEventParser.kt](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/app/src/main/java/com/danny/dannyapp/ScoliaEventParser.kt): Parst alle oben aufgelisteten Event-Typen lückenlos in typsichere Kotlin-Objekte.
3. [MainActivity.kt](file:///C:/Users/Danny/AndroidStudioProjects/DannyViewsApp/app/src/main/java/com/danny/dannyapp/MainActivity.kt): Protokolliert alle Events ungefiltert in der Logdatei und zeigt strukturierte Treffer-Logs in Logcat.
