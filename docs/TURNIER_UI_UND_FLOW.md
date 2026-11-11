# 📱 Fachkonzept: Turnier-UI, Simulation & Ranglisten-Ansicht

Dieses Dokument definiert die Benutzeroberfläche und den Interaktions-Flow für den Saisonkalender, den Turnierverlauf (inkl. Test-Modi für User-Matches) und die Orders of Merit in der **Danny Darts App**.

---

## 🧭 1. Navigation & Einstieg aus der Karriere-Ansicht

Aus der bestehenden **Karriere-Detailansicht** heraus erhält der User Zugriff auf drei zentrale Bereiche (als Tabs):
1. **Saisonkalender (`TournamentCalendarScreen`):** Chronologische Übersicht aller Turniere der aktiven Saison.
2. **Turnieransicht (`TournamentDetailScreen`):** Rundenübersicht, Gruppenphase (beim Grand Slam of Darts) und Match-Steuerung.
3. **Ranglisten (`OrderOfMeritScreen`):** Tab-Ansicht für die 4 dynamischen Ranglisten (`OOM`, `PT OOM`, `PC OOM`, `ET OOM`).

---

## 📅 2. Saisonkalender (`TournamentCalendarScreen`)

* **Anzeige:** Liste aller Turniere der aktuellen Saison sortiert nach `order` .
* **Status-Kennzeichnung:**
  * `Abgeschlossen` (`isCompleted == true`): Zeigt den Turniersieger und die erreichte Runde des Users an. Beim Anklicken gelangt man zum `TournamentDetailScreen` des Turniers.
  * `Aktiv` (erstes ungespieltes Turnier mit bereits generierten Matches): Hervorgehoben. Beim Anklicken gelangt man zum `TournamentDetailScreen` des Turniers.
  * `Geplant` (`isCompleted == false`, noch keine Matches generiert): Das jeweils nächste anstehende Turnier bietet den Button **„Turnier starten“** (ruft `GenerateBracketUseCase` auf und wechselt in den `TournamentDetailScreen`).
* **Kennzeichnung:** Major Turniere sind besonders gekennzeichnet. Qualifier Turniere sind ebenfalls (anders) gekennzeichnet.
* **Schnelltest-Option (Dev/Test):** Ein optionaler Button **„Komplettes Turnier simulieren“** (simuliert alle Runden inkl. User-Matches automatisch durch, um Saisons schnell zu testen).

---

## 🎯 3. Turnieransicht & Match-Steuerung (`TournamentDetailScreen`)

### 3.1 Runden- & Gruppenansicht
* Zeigt die Matches der aktuellen Runde (oder bei `MAJOR_GROUP` die 16 Gruppen A–P mit Tabelle und Spieltagen).
* Matches mit Beteiligung des Users (`player1Id == USER_ID` oder `player2Id == USER_ID`, `USER_ID = -1L`) sind optisch hervorgehoben.

### 3.2 Steuerung für CPU-Matches
* **„CPU-Matches dieser Runde simulieren“:** Simuliert alle offenen Matches der aktuellen Runde, an denen der User **nicht** beteiligt ist (`TournamentProgressionUseCase` / `SimulateCpuMatchUseCase`). Ist in der Konfiguration "Automatische Simulation" aktiviert (default=`true`, gehalten im ViewModel-State / SharedPreferences), wird diese Funktion direkt ausgeführt.
* **„Nächste Runde generieren“** : Sobald alle Matches einer Runde abgeschlossen sind, wird `AdvanceTournamentRoundUseCase` ausgeführt (vergibt Preisgelder in `tournament_placements` und erzeugt die Matches der Folgerunde). Ist in der Konfiguration "Automatisch zur nächsten Runde" aktiviert (default=`true`, gehalten im ViewModel-State / SharedPreferences), wird die nächste Runde direkt generiert.

### 3.3 Die 3 Optionen für User-Matches (`USER_ID = -1L`)
Ist ein offenes Match mit Beteiligung des Users an der Reihe, erhält der User drei Auswahlmöglichkeiten:
1. **„Match simulieren (Skill/Zufall)“:** Simuliert das User-Match wie ein CPU-Match (nutzt `career.level` als Skill-Wert des Users). Ideal für schnelle Gesamttests.
2. **„Sieger festlegen“:** Öffnet einen Dialog in dem man "Sieg" oder "Niederlage" auswählen kann. Das genaue Ergebnis wird dann simuliert.
3. **„Ergebnis manuell eingeben“:** Öffnet einen Dialog zur direkten Eingabe der gewonnenen Legs/Sets (z. B. `6 : 4`). Validiert gegen `TournamentEvent.getMatchDistance(round)` (dass ein gültiger Sieger feststeht), speichert das Ergebnis in `tournament_matches` und triggert den Rundenfortschritt.
4. **„Live spielen (GameEngine)“:** Übergibt Match-ID und `MatchDistance` (`bestOf`, `legsPerSet`, `twoClearLegsRule`, `SETS_DOUBLE_IN_OUT`) an die bestehende Match-/Scolia-Engine (vorerst als Schnittstellen-Callback vorbereitet, bis die automatische Scolia-Anbindung verknüpft wird).

---

## 🏆 4. Ranglisten-Ansicht (`OrderOfMeritScreen`)

* **4 Tabs:** `Order of Merit (OOM)`, `Pro Tour (PT OOM)`, `Players Champ. (PC OOM)`, `European Tour (ET OOM)`. Die mussen nicht explizit als Tabs/Buttons angezeogt werden. Es reicht, wenn man durch wischen nach links oder rechts die vier Ansichten wechselt.
* **Datenquelle:** Ruft `GetOrderOfMeritUseCase(careerId, type)` auf.
* **Darstellung:** Tabelle mit Platzierung (`rank`), Länderflagge (`countryCode`), Spielername (`name`), Preisgeld (`prizeMoney` in `£`) und optischer Hervorhebung der eigenen Zeile (`isUser == true`).
