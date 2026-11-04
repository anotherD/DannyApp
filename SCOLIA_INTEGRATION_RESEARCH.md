# Scolia API & WebSocket Integration Research

Diese Dokumentation fasst alle Erkenntnisse über die Funktionsweise der Scolia-Anbindung aus der Analyse des *MyDartTraining*-Quellcodes sowie die Architektur-Blaupause für eine eigene Custom-App zusammen.

---

## 1. Überblick & Grundprinzip

* **Scolia ist spiel-agnostisch:** Das Scolia-System (Kameras + SBC-Einheit) hat **keine Ahnung**, welches Spiel (501, Cricket, Round the Clock) gespielt wird, und wird **nicht** darüber informiert, wenn ein Spiel gestartet oder beendet wird.
* **Passive Event-Streamer:** Das System fungiert rein als Event-Streamer über WebSockets. Sobald Pfeile geworfen oder herausgezogen werden, werden JSON-Events gesendet.
* **Spiellogik liegt zu 100 % im Client:** Punktezählung, Spielerwechsel, Legs, Sets, Checkout-Logik und Bust-Regeln werden vollständig in der eigenen App verarbeitet.

---

## 2. Technische Details aus MyDartTraining (Partner-API)

### Referenz-Dateien im Projekt:
* Class: `at.steirersoft.mydarttraining.scolia.ScoliaHelper`
* Interfaces: `IScoliaGameService`, `IScoliaNetwork`

### Endpunkte & URLs:
* **REST Base-URL:** `https://game.scoliadarts.com/api/social/boards`
* **WebSocket Base-URL:** `wss://game.scoliadarts.com/api/v1/social?serialNumber=<SERIAL>&accessToken=<TOKEN>`
* **MDT Partner Access Token:** `3cb7c2bb9ae07c97f0b64179b79b8130`

---

## 3. REST API Protokoll

Die REST-API dient ausschließlich der Board-Registrierung beim Service-Account.

| Methode | Endpunkt | Header / Parameter | Beschreibung |
| :--- | :--- | :--- | :--- |
| **`GET`** | `/api/social/boards` | `Authorization: <TOKEN>` | Fragt eine JSON-Liste aller aktuell verknüpften Boards ab. |
| **`PUT`** | `/api/social/boards` | `Authorization: <TOKEN>`<br>Body: `serialNumber=<SERIAL>` | Registriert ein Board beim Service-Account. |
| **`DELETE`** | `/api/social/boards/{serialNumber}` | `Authorization: <TOKEN>` | Meldet das Board vom Service-Account ab. |

---

## 4. WebSocket Event-Formate

### A. Empfangene Events (Scolia ➔ App):

#### 1. Wurf erkannt (`THROW_DETECTED`):
```json
{
  "type": "THROW_DETECTED",
  "payload": {
    "sector": "T20"
  }
}
```
*Mögliche Werte für `sector`:*
* `"T20"`, `"D20"`, `"S20"` (Triple, Double, Single Sektoren)
* `"25"` (Single Bull / Outer Bull)
* `"BULL"` (Double Bull / Inner Bull)
* `"None"` (Fehlwurf / Ausrutscher außerhalb der Segmente)

#### 2. Darts herausgezogen (`TAKEOUT_FINISHED`):
```json
{
  "type": "TAKEOUT_FINISHED"
}
```

#### 3. Board-Status geändert (`SBC_STATUS_CHANGED` / `HELLO_CLIENT`):
```json
{
  "type": "SBC_STATUS_CHANGED",
  "payload": {
    "boardStatus": "OK"
  }
}
```

### B. Gesendete Events (App ➔ Scolia):

#### Neu kalibrieren (`RECALIBRATE`):
```json
{
  "type": "RECALIBRATE"
}
```

---

## 5. Blaupause für eine eigene Custom App (Option 2: Eigenes Web-Session-Token)

Um eine eigene App ohne Abhängigkeit von MyDartTraining oder kostenpflichtige B2B-Lizenzen zu entwickeln:

### Schritte zur Umsetzung:
1. **Token beschaffen:** 
   * Logge dich auf `game.scoliadarts.com` im Browser ein.
   * Öffne `F12` (Entwicklertools) ➔ **Netzwerk (Network)** ➔ Filter **WS**.
   * Kopiere den `accessToken` aus der WebSocket-URL oder dem Auth-Header.
2. **WebSocket in eigener App verbinden:**
   * Verbinde dich per WebSocket mit der URL:
     `wss://game.scoliadarts.com/api/v1/social?serialNumber=<DEINE_SERIAL>&accessToken=<DEIN_USER_TOKEN>`
3. **Event-Loop implementieren:**
   * Verarbeite eingehende JSON-Nachrichten (`THROW_DETECTED`, `TAKEOUT_FINISHED`).
   * Sende alle 20–30 Sekunden einen WebSocket-Ping (Heartbeat), damit die Verbindung nicht abbricht.

---

## 6. Risiko-Analyse & Lösungsansätze

1. **Token Expiration:** Web-Session-Tokens verfallen nach einiger Zeit. 
   * *Lösung:* In der eigenen App einen Mechanismus einbauen, der den Token bei Bedarf erneuert oder aus liest.
2. **Parallele Verbindungen:** Der Scolia-Server erlaubt in der Regel nur 1 aktive WebSocket-Verbindung pro Board.
   * *Lösung:* Scolia-Webseite im Browser schließen, wenn die eigene App läuft.
3. **Server-Abhängigkeit:** Das Scolia-Board benötigt immer Internetkontakt zu `game.scoliadarts.com`, um Würfe auszuwerten.
