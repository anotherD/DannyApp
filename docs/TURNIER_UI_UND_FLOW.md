# 📱 Fachkonzept: Turnier-UI, Simulation & Ranglisten-Ansicht

Dieses Dokument beschreibt die Benutzeroberfläche und den Interaktions-Flow für den Saisonkalender, den Turnierverlauf, die Ranglisten sowie die Spieler-Konfiguration in der **Danny Darts App**.

---

## 🧭 1. Navigation & Einstieg aus der Karriere-Ansicht

* **Top-Bar & Breadcrumbs:** Jede Unterseite zeigt als Titel den gewählten Bereich (z. B. `Saisonkalender`, `Turnieransicht`, `Ranglisten`) und als Subtitel den Pfad `Karrieren > <Karrierename> (Saison X)`.
* **Karrieren-Kacheln & Übersicht:** Zeigen Karrierename, aktuelle Saison, Level, Profilname sowie Spielername inklusive Landesflagge. Über das Kontextmenü (Long-Press) lassen sich bestehende Karrieren umbenennen oder löschen.
* **Eigenständige Seiten aus der Karriere-Ansicht (`CareerDetailActivity`):**
    1. **Karriere-Übersicht (`CareerDetailActivity`):** Kompakte Karriere-Infos, klickbare Anzeige der eigenen Positionen und Preisgelder in allen 4 Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`), Schnellzugriffs-Kacheln rechts daneben (oben: Saisonkalender, unten: Statistiken) sowie Direktzugriff auf das aktuelle oder nächste Turnier.
    2. **Saisonkalender (`SeasonCalendarActivity`):** Chronologische Übersicht aller Turniere der aktiven Saison.
    3. **Ranglisten (`OrderOfMeritActivity`):** Auswahl der 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`) mit direkter Vorauswahl beim Klick aus der Karriere-Übersicht.

---

## 📅 2. Saisonkalender

* **Anzeige:** Chronologische Liste aller Saisonturniere mit Turniernummer, Name, lokalisiertem Turniertyp-Badge, Status-Badge und Austragungsort (Landesflagge + Stadtname).
* **Fokus & Hervorhebung:** Das aktive oder nächste geplante Turnier ist optisch hervorgehoben. Beim Öffnen scrollt die Liste automatisch zu diesem Turnier.
* **Strikte Turnier-Sperre (Sequenzieller Ablauf):** Es darf immer nur ein Turnier gleichzeitig aktiv sein. Ein neues Turnier kann erst gestartet werden, wenn das vorherige abgeschlossen ist.
* **Status-Zustände:**
    * **Abgeschlossen:** Zeigt bei Einzelsieger-Turnieren den Turniersieger an. Bei Multi-Sieger-Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`, `QUALIFIER_WORLD_MASTERS`)) steht im Kalender nur *„Abgeschlossen“* ohne Einzel-Pokal und ohne Gewinnerliste (diese wird erst im Turnier-Rückblick gezeigt).
    * **Aktiv:** Zeigt die aktuell laufende Turnierrunde an.
    * **Geplant:** Das nächste anstehende Turnier bietet den Button **„Turnier starten“**.
* **Debug-Funktion:** Bei aktiviertem Debug-Modus (`debugMode == true`) steht an jedem nicht abgeschlossenen Turnier ein kompakter Fast-Forward-Button (`⏩`) zur Verfügung, der alle offenen Turniere der Saison chronologisch bis einschließlich zum gewählten Turnier mit Fortschritts-Overlay durchsimuliert.

---

## 🎯 3. Turnieransicht & Match-Steuerung

### 3.1 Die 3 Zustände der Turnieransicht

1. **Vorschau-Modus (Geplantes Turnier):**
    * Zeigt Turnierkopfzeile, Gesamtpreisgeld sowie ein Info-Icon für den Turnier-Info-Dialog.
    * Ist es das nächste anstehende Turnier im Kalender, kann es über **„Turnier starten“** gestartet werden.
2. **Aktiver Turnier-Modus (Laufendes Turnier):**
    * **Farbliche Akzentuierung:** Die obere Steuerungskarte ist je nach Turniertyp (Major, Major-Qualifier, European Tour, ET-Qualifier, Players Championship) farblich abgesetzt.
    * **Runden-Navigation:** Horizontale Runden-Chips (farblich kodiert nach *Abgeschlossen*, *Aktiv* und *Ausstehend*) sowie horizontale Wischgesten (Swipe links/rechts) zum Wechseln zwischen den Runden. Beim Grand Slam of Darts gibt es zusätzlich eine Gruppen-Auswahl (`A–P`).
    * **Vorab-Turnierbaum vs. Freie Auslosung:**
        * Turniere mit festem Turnierbaum zeigen alle Runden vorab an und tragen feststehende Sieger sowie gesetzte Einsteiger (z. B. Top 16 OOM in Runde 2 der European Tour oder Freilos-Spieler in Runde 1 der Qualifier) direkt ein.
        * Bei Turnieren mit offener Runden-Auslosung (`UK Open` und `European Open`) zeigen noch nicht ausgeloste Folgerunden einen Hinweis auf den Auslosungszeitpunkt samt Liste der bereits qualifizierten Einsteiger.
    * **Match-Karten, Match-Nummerierung & Drei-Stufen-Namenskürzung:** Jedes Match trägt eine eindeutige Kennung mit Runden-Präfix (z. B. `#L64-1`, in Gruppen z. B. `Gruppe A • #GRP-1`). Zeigen Setzlisten-Nummer, Landesflagge, Spielername und Spielstand. Passt ein Name nicht vollständig, wird zuerst der Vorname abgekürzt (`M.van Gerwen`) und bei weiterem Platzmangel nur der Namensteil gekürzt (`M.van Ger...`), damit Setzlisten-Nummern und Flaggen niemals abgeschnitten werden.
    * **Turnier-Info-Dialog (Über das Info-Icon in allen 3 Modi erreichbar):**
        * **Tab 1 – Preisgelder & Distanzen:** Zeigt Gesamtpreisgeld, Runden-Preisgelder, Match-Distanzen sowie turnierspezifische Ranglisten-Ausnahmen (z. B. £0 bei Auftaktniederlage gesetzter Spieler auf der European Tour bzw. £0 in Runde 1 bei PC/UK Open/European Open).
        * **Tab 2 – Teilnehmer:** Zeigt Qualifikationsregeln, alle qualifizierten Teilnehmer gruppiert nach Qualifikationsweg samt Setzposition sowie einen Hinweis, ob das Teilnehmerfeld noch vorläufig oder bereits eingefroren ist (inkl. offener Qualifier-Plätze).
    * **Simulations-Sperre:** Während einer Batch-Simulation blockiert ein Lade-Overlay die Eingabe und die Zurück-Navigation.

### 3.2 Simulations-Steuerung, Settings & Button-Matrix (Single Source of Truth)

Jeder Simulations-Button hängt an genau einer Einstellung in `SettingsManager`:

| Button / Aktion | Ort | Abhängiges Setting | Sichtbarkeits- & Aktivierungsbedingung |
| :--- | :--- | :--- | :--- |
| **`CPU simulieren`** | Turnieransicht (Aktiv) | `autoSimulateCpu` | Sichtbar, wenn `autoSimulateCpu == false` und offene CPU-Matches in der aktiven Runde existieren (unabhängig von `debugMode`). |
| **`Nächste Runde` / `Turnier abschließen`** | Turnieransicht (Aktiv) | `autoAdvanceRound` | Sichtbar, wenn `autoAdvanceRound == false` und alle Matches der aktiven Runde beendet sind (unabhängig von `debugMode`). |
| **`Turnier simulieren`** | Turnieransicht (Aktiv) | `debugMode` | Sichtbar nur im Debug-Modus (`debugMode == true`). Simuliert das komplette Turnier inkl. User-Matches bis zum Ende. |
| **`⏩` (Bis hier simulieren)** | Saisonkalender & Übersicht | `debugMode` | Sichtbar nur im Debug-Modus (`debugMode == true`) bei noch nicht abgeschlossenen Turnieren (`status != COMPLETED`). |
| **`Sieger festlegen (Sieg/Niederlage)`** | User-Match-Dialog | `debugMode` | Sichtbar nur im Debug-Modus (`debugMode == true`). |
| **`Turnier starten`** | Saisonkalender, Übersicht & Vorschau | — | Sichtbar beim nächsten geplanten Turnier (`isNextPlanned == true`). |

**Trigger-Zeitpunkte für automatische Abläufe (Domain-Layer `TournamentProgressionUseCase`):**
* **`autoSimulateCpu == true`:** Löst die Simulation aller offenen CPU-Matches der aktuellen Runde automatisch aus:
  1. Unmittelbar nach dem **Start eines Turniers** (egal ob aus Kalender, Übersicht oder Vorschau gestartet).
  2. Unmittelbar nach jedem **Rundenwechsel** (egal ob manuell per Button oder über `autoAdvanceRound`).
  3. Unmittelbar beim **Zurückkehren aus den Einstellungen** (`onResume`), falls `autoSimulateCpu` nachträglich eingeschaltet wurde.
* **`autoAdvanceRound == true`:** Schaltet automatisch in die nächste Runde weiter (bzw. schließt das Turnier nach dem Finale ab), sobald alle Matches einer Runde abgeschlossen sind – ebenfalls auch beim Zurückkehren aus den Einstellungen (`onResume`), falls das Setting nachträglich aktiviert wurde.

### 3.3 Turnierbaum-Ansicht (Bracket View) & Schwebe-Buttons
* **Schwebe-Buttons unten:** Im aktiven Modus und im Rückblick ermöglichen zwei runde Buttons das Zentrieren auf das eigene Match in der aktuellen Runde (deaktiviert/ausgegraut, falls der User in dieser Runde nicht spielt) sowie das Umschalten zwischen **Rundenliste** und **Turnierbaum**.
* **Turnierbaum & Layout-Regeln:**
    * Zeigt alle Runden nebeneinander mit synchronem vertikalem Scrollen, Verbindungslinien (mit Hervorhebung eigener Siege) und kompakten Matchkacheln.
    * **1:1-Vorrunden-Ausrichtung (`QUAL_PRELIM` → `QUAL_R1`, European Tour `L48` → `L32`):** Vorrunden-Kacheln stehen unabhängig von der fokussierten Runde immer exakt auf derselben vertikalen Höhe links vor ihrem zugehörigen Ziel-Match in der Folgerunde (gerade 1:1-Verbindungslinie, Lücken zwischen nicht belegten Runde-1-Matches bleiben erhalten, kein vertikales Springen beim Rundenwechsel).
    * **Grand Slam of Darts (Gruppenphase → K.o.-Runde):** In der Gruppenphase werden die 16 Gruppentabellen (`A–P`) angezeigt; je zwei benachbarte Gruppen (`A+B`, `C+D`, …, `O+P`) führen mit 2:1-Verbindungslinien zu ihrem Achtelfinal-Match (`L16`).
    * **Offene Auslosungen (`UK Open` & `European Open`):** Zwischen Runden mit freier Neu-Auslosung (`R1_160`, `R2_128`, `R3_96`) werden keine Verbindungslinien gezeichnet; erst ab dem festen 64er-Turnierbaum (`L64` bis `F`) werden reguläre 2:1-Baumlinien dargestellt.
* **Sonderformate:** Im Grand Slam of Darts werden in der Gruppenphase kompakte Gruppentabellen angezeigt; bei Qualifiern lassen sich einzelne Boards (`Board 1..K` oder `Alle`) filtern.

---

## 🏆 4. Ranglisten-Ansicht

* **Auswahl:** 4 Filter-Chips für *Order of Merit (OOM)*, *Pro Tour (PT OOM)*, *Players Championship (PC OOM)* und *European Tour (ET OOM)*.
* **Darstellung:** Liste mit Rang, Landesflagge, Spielername und Preisgeld. Die Zeile des Users ist farblich hervorgehoben.
* **Sticky-User-Karte:** Scrollt der User aus dem sichtbaren Bereich heraus, erscheint oben bzw. unten eine schwebende Karte mit seiner aktuellen Platzierung. Ein Klick darauf scrollt direkt zur eigenen Position.

---

## ⚙️ 5. Einstellungen & Spieler-Verwaltung

* **Einstellungen:** Zentrale Schalter für Gameplay (*Automatische Simulation*, *Automatisch zur nächsten Runde*), Design (*Modernes Match-Kachel-Design*) und Entwickler (*Debug-Modus*).
* **Spieler-Übersicht:** Liste aller hinterlegten Profis mit Landesflagge, Tour-Card-Symbolen und farbkodierter Spielstärke (`L1–L10`). Umschaltbare Sortierung nach Spielstärke oder Name (per Button oder horizontaler Wischgeste).
