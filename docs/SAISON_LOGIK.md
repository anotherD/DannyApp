# 🎯 Fachkonzept & Logik: Saisons & Tour Cards

Dieses Dokument beschreibt die Fachlogik zur Erstellung und Verwaltung von Saisons, Tour Cards sowie der Entkopplung von Turnierteilnahmen im Karrieremodus. Technische Details zur Turnierbaum-Speicherung und Zustandsmaschine sind in [ARCHITEKTUR.md](ARCHITEKTUR.md#4-persistente-turnierbaum-speicherung--fortschrittssteuerung) dokumentiert.

---

## 📌 1. Das Tour Card Reglement (128er-Regel)

In jeder Saison gibt es **exakt 128 Tour Card Holder**:
* **1 Tour Card für den menschlichen User:** `USER_ID (-1L)` startet im 1. Tour-Card-Jahr (`tourCardYear = 2`, fest vorgegeben).
* **127 Tour Cards für CPU-Spieler:** Jeweils im 1. oder 2. Tour-Card-Jahr (`tourCardYear = 1` oder `2`).

---

## ⚙️ 2. Vergabe- & Auffüll-Logik (Saison 1)

Beim Erstellen einer neuen Karriere wird automatisch **Saison 1** initialisiert. Die 127 CPU-Tourcards werden wie folgt ermittelt:
1. Alle CPU-Spieler aus den Stammdaten mit aktiver Tour Card (`tour_card_year > 0`) werden geladen und absteigend nach Spielstärke (`skill`) und historischem Preisgeld (`prizeMoney`).
2. **Mehr als 127 Kandidaten (`> 127`):** Nur die besten 127 Kandidaten erhalten eine Tour Card; schwächere fallen heraus.
3. **Weniger als 127 Kandidaten (`< 127`):** Alle Kandidaten übernehmen ihre Tour Card; die Differenz wird aus den besten Nicht-Tourcard-Spielern (`tour_card_year == 0`) nach Spielstärke aufgefüllt (mit `tourCardYear = 2`).

---

## 🔄 3. Vergabe in Folgesaisons (Saison N+1)

Beim Saisonwechsel wird die bisherige Saison als abgeschlossen markiert und die nächste Saison (`seasonNumber + 1`) angelegt:
* **Geplante Order-of-Merit-Logik:** Spieler im 1. Jahr steigen ins 2. Jahr auf (`tourCardYear = 2`). Spieler im 2. Jahr verlieren ihre Card, sofern sie nicht in den Top 64 der 2-Jahres-Order-of-Merit (`OOM`) stehen. Freie Plätze werden über die Q-School aufgefüllt (`tourCardYear = 1`).
* **Aktuelle Übergangslogik:** Nutzt dieselbe Übernahme- und Auffülllogik wie Saison 1.

---

## 🏆 4. Turnierteilnahmen & Entkopplung der Match-Zuweisung

* **Entkopplung der Teilnehmer von Tour Cards:** In Turnier-Matches werden direkt die Spieler-IDs aus den Stammdaten (bzw. `USER_ID = -1L`) gespeichert. Turnierteilnehmer müssen nicht zwingend eine Tour Card besitzen (z. B. regionale oder internationale Nicht-Tourcard-Qualifier bei Majors oder der European Tour).
* **Teilnehmerpools & Qualifikation:** Die exakten Teilnehmerzahlen und Qualifikationswege aller Turniertypen (128er-PC, 48er-ET, 160er-UK/European Open usw.) sind in [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md) definiert.
* **Preisgelder:** Werden pro Turnier und Spieler erfasst und bilden die Grundlage für die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`). Details siehe [SAISON_KALENDER_UND_TURNIERE.md](SAISON_KALENDER_UND_TURNIERE.md)
