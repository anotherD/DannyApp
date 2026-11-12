# Generelle Anweisungen
Du wirst mir immer sagen, wenn deine Aussagen nur Vermutungen sind.
Erzähle mir immer 1 oder 2 Witze, wenn ich "Gute Nacht" wünsche oder mich verabschiede.

# Projekt-Anweisungen
Referenz-Codebase auf der Festplatte: C:\Users\Danny\Desktop\MDT\MDT-Gradle-export-AndroidApp
Schlage bei Fragen zu Spiellogiken, Scolia-Integration oder UI-Verhalten immer in dieser Referenz-Codebase nach.
Dieses Referenz-Projekt heißt "MDT" oder "My Dart Training".

# PDC-Regeln, Preisgelder & Kalender-Daten (Recherche-Pflicht)
- **Strikte Faktenbasis (Keine Vermutungen):** Übernimm bei Turnierformaten, Preisgeldern, Qualifikationskriterien und Spielplänen ausschließlich offiziell bestätigte und verifizierte PDC-Daten für die Ziel-Saison (aktuell 2027).
- **Quellen-Prüfung:** Prüfe Änderungen immer gegen offizielle PDC-Verlautbarungen (`pdc.tv` / *PDC Rankings Rules*) sowie aktuelle Turnierpläne und rechne Preisgeldsummen gegen den Gesamttopf (Prize Fund) gegen.
- **Unvollständige Ankündigungen:** Hat die PDC eine Reform angekündigt (z. B. Teilnehmer-Erweiterung), aber noch keine Runden-Preisgelder oder Setzlisten veröffentlicht, darf **nicht** geraten werden. Behalte das bisherige verifizierte Schema im Code bei und dokumentiere die angekündigte Reform ausschließlich als `TODO`-Kommentar im Code und als Hinweis in `docs/SAISON_KALENDER_UND_TURNIERE.md`.
- **Ausnahmen immer prüfen:** Erfasse neben Standard-Preisgeldern immer Ranglisten-Sonderregeln (z. B. £0 bei Auftaktniederlage gesetzter Spieler auf der European Tour, £0 in Runde 1 bei PC/UK Open, Freilos- und Nachrücker-Regeln).
