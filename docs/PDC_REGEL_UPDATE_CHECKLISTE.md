# 🔍 Checkliste & Prompt-Vorlage: PDC-Regel- & Kalender-Updates

Dieses Dokument dient als Wiederholungs-Anweisung, um neue PDC-Ankündigungen (z. B. fehlende 2027er Detailregeln oder spätere Saisons) gründlich, faktenbasiert und ohne Vermutungen in die App einzupflegen.

## 📋 Aktuell bekannte offene Punkte (Stand: November 2026)
Folgende 2027er Reformen wurden von der PDC bereits angekündigt, warten aber noch auf offizielle Detailregeln (*2027 PDC Rankings Rules*):
1. **European Championship 2027 (Dortmund):** Erweiterung auf **48 Spieler** und **£1.000.000 Gesamtpreisgeld** -> Offen: Runden-Preisgeld-Split, Qualifikationsschlüssel für 48 Plätze, Setzlisten-Modus.
2. **Players Championship Finals 2027 (Minehead):** Erweiterung auf **128 Spieler** (Mehrbrett-Modus) -> Offen: Gesamtpreisgeld, Runden-Preisgeld-Split (inkl. eventueller £0-Runde 1), Qualifikations-/Setzlisten-Regeln.

---

## 🤖 Kopierbarer Prüf-Prompt für KI-Assistenten

> **Prompt-Vorlage:**
> „Bitte prüfe gründlich und faktenbasiert den aktuellen offiziellen PDC-Stand für die Saison **[JAHR EINFÜGEN]** (insbesondere *PDC Rankings Rules*, offizielle `pdc.tv`-News und Turnierpläne) für alle Turniere in `docs/SAISON_KALENDER_UND_TURNIERE.md`, `TournamentEvent.kt` und `GenerateSeasonCalendarUseCase.kt`.
>
> **Wichtige Regeln für deine Prüfung:**
> 1. **Keine Vermutungen:** Trenne strikt zwischen 100 % verifizierten Fakten (mit Quellenangabe) und noch unveröffentlichten Details.
> 2. **Doppelte Gegenprüfung:** Prüfe Teilnehmerzahlen, Qualifikationswege, Setzlisten, Spielformate (Legs/Sets/Two-Clear-Legs), Austragungsländer (`hostCountry`), Turniernamen/Reihenfolge, Runden-Preisgelder (inkl. rechnerischer Summe gegen den Gesamttopf) sowie Ranglisten-Ausnahmen (£0-Regeln).
> 3. **Umgang mit offenen Details:** Falls die PDC eine Änderung zwar angekündigt hat, aber Runden-Preisgelder oder Setzlisten noch nicht veröffentlicht sind, rate nicht, sondern halte sie ausschließlich als `TODO`-Kommentar im Code und Hinweis in der Doku fest.“
