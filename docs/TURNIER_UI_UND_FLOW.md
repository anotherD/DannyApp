# 📱 Fachkonzept: Turnier-UI, Simulation & Ranglisten-Ansicht

Dieses Dokument beschreibt die Benutzeroberfläche und den Interaktions-Flow für den Saisonkalender, den Turnierverlauf, die Ranglisten sowie die Spieler-Konfiguration in der **Danny Darts App**.

---

## 🧭 1. Navigation & Einstieg aus der Karriere-Ansicht

* **Top-Bar & Breadcrumbs:** Jede Unterseite zeigt als Titel den gewählten Bereich (z. B. `Saisonkalender`, `Turnieransicht`, `Ranglisten`) und als Subtitel den Pfad `Karrieren > <Karrierename> (Saison X)`.
* **Karrieren-Kacheln & Übersicht:** Zeigen Karrierename, aktuelle Saison, Level, Profilname sowie Spielername inklusive Landesflagge.
* **Tab-Navigation in der Karriere-Ansicht:**
    1. **Übersicht:** Kompakte Karriere-Infos sowie Direktzugriff auf das aktuelle oder nächste Turnier.
    2. **Saisonkalender:** Chronologische Übersicht aller Turniere der aktiven Saison.
    3. **Ranglisten:** Auswahl der 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

## 📅 2. Saisonkalender

* **Anzeige:** Chronologische Liste aller Saisonturniere mit Turniernummer, Name, lokalisiertem Turniertyp-Badge, Status-Badge und Austragungsort (Landesflagge + Stadtname).
* **Fokus & Hervorhebung:** Das aktive oder nächste geplante Turnier ist optisch hervorgehoben. Beim Öffnen scrollt die Liste automatisch zu diesem Turnier.
* **Strikte Turnier-Sperre (Sequenzieller Ablauf):** Es darf immer nur ein Turnier gleichzeitig aktiv sein. Ein neues Turnier kann erst gestartet werden, wenn das vorherige abgeschlossen ist.
* **Status-Zustände:**
    * **Abgeschlossen:** Zeigt bei Einzelsieger-Turnieren den Turniersieger an. Bei Multi-Sieger-Qualifiern (`QUALIFIER_ET`, `QUALIFIER_GRAND_SLAM`, `QUALIFIER_WM`) steht im Kalender nur *„Abgeschlossen“* ohne Einzel-Pokal und ohne Gewinnerliste (diese wird erst im Turnier-Rückblick gezeigt).
    * **Aktiv:** Zeigt die aktuell laufende Turnierrunde an.
    * **Geplant:** Das nächste anstehende Turnier bietet den Button **„Turnier starten“**.
* **Debug-Funktion:** Bei aktiviertem Debug-Modus steht am aktiven bzw. nächsten Turnier der Button **„Turnier simulieren“** zur Verfügung.

---

## 🎯 3. Turnieransicht & Match-Steuerung

### 3.1 Die 3 Zustände der Turnieransicht

1. **Vorschau-Modus (Geplantes Turnier):**
    * Zeigt Turnierkopfzeile, Gesamtpreisgeld sowie ein Info-Icon für die Runden-Preisgelder und Match-Distanzen.
    * Ist es das nächste anstehende Turnier im Kalender, kann es über **„Turnier jetzt starten & auslosen“** gestartet werden.
2. **Aktiver Turnier-Modus (Laufendes Turnier):**
    * **Farbliche Akzentuierung:** Die obere Steuerungskarte ist je nach Turniertyp (Major, Major-Qualifier, European Tour, ET-Qualifier, Players Championship) farblich abgesetzt.
    * **Runden-Navigation:** Horizontale Runden-Chips (farblich kodiert nach *Abgeschlossen*, *Aktiv* und *Ausstehend*) sowie horizontale Wischgesten (Swipe links/rechts) zum Wechseln zwischen den Runden. Beim Grand Slam of Darts gibt es zusätzlich eine Gruppen-Auswahl (`A–P`).
    * **Vorab-Turnierbaum vs. Freie Auslosung:**
        * Turniere mit festem Turnierbaum zeigen alle Runden vorab an und tragen feststehende Sieger sowie gesetzte Einsteiger (z. B. Top 16 OOM in Runde 2 der European Tour oder Freilos-Spieler in Runde 1 der Qualifier) direkt ein.
        * Bei Turnieren mit offener Runden-Auslosung (`UK Open` und `European Open`) zeigen noch nicht ausgeloste Folgerunden einen Hinweis auf den Auslosungszeitpunkt samt Liste der bereits qualifizierten Einsteiger.
    * **Match-Karten & Drei-Stufen-Namenskürzung:** Zeigen Setzlisten-Nummer, Landesflagge, Spielername und Spielstand. Passt ein Name nicht vollständig, wird zuerst der Vorname abgekürzt (`M.van Gerwen`) und bei weiterem Platzmangel nur der Namensteil gekürzt (`M.van Ger...`), damit Setzlisten-Nummern und Flaggen niemals abgeschnitten werden.
    * **Simulations-Sperre:** Während einer Batch-Simulation blockiert ein Lade-Overlay die Eingabe und die Zurück-Navigation.
    * **Steuerung:** Die Optionen *„Automatische Simulation“* und *„Automatisch zur nächsten Runde“* werden zentral in den Einstellungen verwaltet. Manuelle Simulations-Buttons sind nur im Debug-Modus sichtbar.
3. **Rückblick-Modus (Abgeschlossenes Turnier):**
    * Read-only-Ansicht aller Runden.
    * Zeigt oben den Turniersieger (bzw. bei Multi-Sieger-Qualifiern die zweispaltige Liste aller Qualifikanten), die vom User erreichte Runde sowie das gewonnene Preisgeld.

### 3.2 Optionen für User-Matches
Beim Klick auf ein eigenes offenes Match stehen folgende Optionen bereit:
1. **Sieger festlegen (Sieg/Niederlage)** *(nur im Debug-Modus)*: Setzt direkt ein gültiges Ergebnis passend zur Match-Distanz.
2. **Ergebnis manuell eingeben:** Dialog zur Eingabe von Legs/Sets mit Validierung gegen die Turnierregeln (inkl. *Two-Clear-Legs Rule* und *Double-In / Double-Out*).
3. **Live spielen (GameEngine):** Startet das Match in der Live-/Scolia-Engine.

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
