# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons und Tour Cards im Karrieremodus. Es dient als zentrale Referenz für Entwickler und KI-Assistenten.

> 📝 **Pflicht zur Doku-Pflege:** Bei allen künftigen Regelanpassungen oder Erweiterungen (z. B. Order of Merit, Saisonkalender) **muss** dieses Dokument umgehend vom Entwickler/KI-Assistenten aktualisiert werden.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder**:
* **1 Tour Card für den menschlichen User:**
  - `playerId = USER_ID (-1L)` (repräsentiert den User der Karriere)
  - `tourCardYear = 2` (fest vorgegeben)
  - Computed Kotlin Property: `val isUser: Boolean get() = playerId == USER_ID`
* **127 Tour Cards für CPU-Spieler:**
  - `playerId` entspricht der ID aus der `players`-Tabelle.
  - `tourCardYear` ist `1` oder `2`.

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert (`SeasonRepository.createSeason(careerId)`). 

Die 127 CPU-Tourcards werden in folgenden Schritten ermittelt:

### Schritt 1: CPU-Kandidaten filtern & sortieren
Alle CPU-Spieler aus der `players`-Tabelle mit `tour_card_year > 0` werden geladen und sortiert nach:
1. `skill DESC` (Spielstärke 1–10)
2. `prizeMoney DESC`
3. `name ASC`

### Schritt 2: Vergabe & Ausgleich auf exakt 127 CPU-Plätze

```mermaid
flowchart TD
    A[Saison 1 erstellen] --> B[1 Tour Card fuer User anlegen\nplayerId=USER_ID (-1L), tourCardYear=2]
    B --> C[CPU-Spieler mit tour_card_year > 0 laden]
    C --> D{Anzahl CPU-Kandidaten?}

    D -- > 127 -- > E[Kuerzungslogik:\nTop 127 nehmen. Schwächste Kandidaten ab Platz 128 verfallen für diese Saison]
    D -- = 127 -- > F[Exakt 127 Kandidaten uebernehmen]
    D -- < 127 -- > G[Auffuelllogik:\nAlle Kandidaten nehmen + Differenz aus besten Nicht-Tourcard-Spielern nachrücken lassen (tourCardYear = 2)]

    E --> H[Exakt 128 TourCards in DB speichern]
    F --> H
    G --> H
```

* **Fall A: Mehr als 127 Kandidaten (> 127)**
  - Es werden nur die besten 127 Kandidaten übernommen.
  - Schwächere Tourcard-Spieler erhalten für diese Saison keine Tourcard.
* **Fall B: Exakt 127 Kandidaten (= 127)**
  - Alle 127 Kandidaten erhalten eine Tourcard.
* **Fall C: Weniger als 127 Kandidaten (< 127)**
  - Alle vorhandenen Kandidaten übernehmen ihre Tourcard.
  - Die fehlende Differenz ($127 - \text{Anzahl}$) wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) der `players`-Tabelle nach Skill aufgefüllt.
  - Diese nachgerückten Spieler erhalten für die Saison `tourCardYear = 2`.

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Beim Saisonwechsel (`createSeason(careerId)`):
1. Die bisherige Saison wird als abgeschlossen markiert (`isCompleted = true`).
2. Eine neue Saison mit `seasonNumber = currentSeason.seasonNumber + 1` wird erstellt.
3. **Zukunft (Order of Merit):** Die Tourcards werden anhand der 2-Jahres-Geldrangliste ermittelt:
   - Spieler im 1. Jahr steigen ins 2. Jahr auf (`tourCardYear = 2`).
   - Spieler im 2. Jahr verlieren ihre Card, außer sie stehen in den Top X der Order of Merit.
   - Nachrücker aus Q-School / Development Tour erhalten `tourCardYear = 1`.
4. **Vorübergehende Logik:** Gleiche Übernahme- & Auffülllogik wie in Saison 1.

---

## 🏆 4. Turnierteilnahmen & Nicht-Tourcard-Spieler

* **Tabelle `tour_cards`:** Speichert **ausschließlich** die 128 aktiven Tourcard-Inhaber der jeweiligen Saison.
* **Tour Card Turniere:** Teilnehmer werden direkt aus `tour_cards` geladen.
* **Open / Qualifier Turniere:** Kombination aus `tour_cards` + dynamische Abfrage von Nicht-Tourcard-Spielern aus der `players`-Tabelle:
  ```sql
  SELECT * FROM players 
  WHERE id NOT IN (SELECT player_id FROM tour_cards WHERE season_id = :seasonId)
  ORDER BY skill DESC
  ```
* **Preisgelder:** Werden nicht in `TourCard` gespeichert, sondern später pro Event in einer eigenen Tabelle (`event_placements`).
