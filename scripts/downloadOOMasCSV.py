import csv
import requests
import pycountry
import re
import time
import random

from bs4 import BeautifulSoup
from collections import defaultdict

csv_filename = "darts_rankings.csv"
players_data = defaultdict(dict)
suffixList = []

# Nur die wenigen Darts-/Sport-Sonderfälle, die vom reinen ISO-3-Code abweichen
# Ich arbeite intern mit ISO-codes. damit die Landesflaggen-icons und convertierungen und Namensanzeogen korrekt sind !!
SPORT_OVERRIDES = {
    "gb-eng": "ENG",
    "gb-sct": "SCO",
    "gb-wls": "WAL",
    "gb-nir": "NIR",
    "ie": "IRL",   # WorldCup nutzt IRE statt IRL  IOC/FIFA/ISO nutzen alle IRL
    "ire": "IRL",   # WorldCup nutzt IRE statt IRL  IOC/FIFA/ISO nutzen alle IRL
    "DEU": "DEU",
    "ned": "NLD",
    "sui": "CHE",
    "den": "DNK",
    "por": "PRT",
    "cro": "HRV",
    "lat": "LVA",
    "rsa": "ZAF",
    "phi": "PHL",
    "sin": "SGP",
    "mas": "MYS",
    "mgl": "MNG",
    "uae": "ARE",
    "bah": "BHS",
    "crc": "CRI",

#    "de": "DEU",   # ISO wäre DEU
#    "nl": "NED",   # ISO wäre NLD
#    "ch": "SUI",   # ISO wäre CHE
#    "ie": "IRL",   # WorldCup nutzt IRE statt IRL  IOC/FIFA/ISO nutzen alle IRL
#    "dk": "DEN",   # ISO wäre DNK
#    "pt": "POR",   # ISO wäre PRT
#    "hr": "CRO",   # ISO wäre HRV
#    "lv": "LAT",   # ISO wäre LVA
#    "si": "SVN",   # ISO wäre SVN (gleich)
#    "za": "RSA",   # ISO wäre ZAF
#    "ph": "PHI",   # ISO wäre PHL
#    "sg": "SIN",   # ISO wäre SGP
#    "my": "MAS",   # ISO wäre MYS
#    "mn": "MGL",   # ISO wäre MNG
#    "ae": "UAE",   # ISO wäre ARE
#    "bs": "BAH",   # ISO wäre BHS
#    "cr": "CRC",   # ISO wäre CRI
#    "gi": "GIB",   # ISO wäre GIB (gleich)
}
def normalize_country_code(raw_code):
    code = raw_code.strip().lower()
    if not code:
        return ""
    
    # 1. Prüfen, ob ein Darts-Sonderkürzel vorliegt (z. B. GB-ENG -> ENG, DE -> DEU)
    if code in SPORT_OVERRIDES:
        return SPORT_OVERRIDES[code]
    
    # 2. Alle übrigen ~240 Länder automatisch über offiziellen ISO-Standard (Alpha-2 -> Alpha-3)
    country = pycountry.countries.get(alpha_2=code.upper())
    if country:
        return country.alpha_3
        
    return code.upper()

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}


# 1. Daten von den Webseiten abrufen und im Dictionary sammeln ---
print(f"\nSpieler aus Order of Merit laden. (Name, Ranking, Preisgeld)")
URLS = [
    "https://www.dartsrankings.com/",
    "https://www.dartsrankings.com/yeartodate2025",
    "https://www.dartsrankings.com/yeartodate2026"
]
for url in URLS:
    # Erzeugt dynamisch das Suffix ("price_money", "yeartodate2025", "yeartodate2026")
    suffix = url.split("/")[-1] if url.split("/")[-1] else "price_money"
    suffixList.append(suffix)
    
    print(f"  Lade Daten von {url}...")
    try:
        response = requests.get(url, headers=headers, timeout=10)
    except requests.exceptions.RequestException as e:
        print(f"  Netzwerkfehler bei {url}: {e}\n")
        continue
    
    if response.status_code == 200:
        soup = BeautifulSoup(response.text, "html.parser")
        table = soup.find("table")
        
        if table:
            rows = table.find_all("tr")
            
            for row in rows[1:]:
                cols = row.find_all("td")
                if not cols:
                    continue
                
                # Hauptseite: Hat meistens 4 oder mehr Spalten (Rk, +/-, Name, Prize Money...)
                if len(cols) >= 4:
                    name = cols[2].text.strip()
                    prize_money = cols[3].text.strip()
                
                # Year-to-Date Seite: Hat nur 3 Spalten (Rk, Name, Prize Money)
                elif len(cols) == 3:
                    name = cols[1].text.strip()
                    prize_money = cols[2].text.strip()
                else:
                    continue  # Überspringt Zeilen, die gar nicht passen
                
                # Validierung, damit keine Header oder reinen Zahlen-Zeilen reinrutschen
                if name and not name.replace('.', '', 1).isdigit():
                    try:
                        players_data[name][suffix] = float(prize_money)
                    except (ValueError, TypeError):
                        players_data[name][suffix] = 0.0

        else:
            print(f"  Fehler: Keine Tabelle auf {url} gefunden.\n")
    else:
        print(f"  Fehler beim Laden von {url}. Status-Code: {response.status_code}\n")


# 2. Die Daten nach dem 'price_money'-Preisgeld absteigend (reverse=True) sortieren
players_data = dict(sorted(players_data.items(), key=lambda item: item[1].get("price_money", 0.0), reverse=True))


# 3. Skill-level hinzufügen
# (Maximaler Rang, Skill-Level)
print(f"\nFüge Skill Level hinzu ...")
SKILL_THRESHOLDS = [
    (1, 10),
    (3, 9),
    (6, 8),
    (12, 7),
    (18, 6),
    (28, 5),
    (46, 4),
    (72, 3),
    (104, 2)
]

for index, (name, data) in enumerate(players_data.items(), start=1):
    # Findet das erste passende Level; falls index > 104, wird 1 genommen
    data["skillLevel"] = next(
        (level for limit, level in SKILL_THRESHOLDS if index <= limit), 
        1
    )


# 4. Tour Card und Two-Year Status von /postwch ermitteln ---
tc_url = "https://www.dartsrankings.com/postwch"
print(f"\nErmittle Tour Card Status aus {tc_url}")

found_count = 0;
found_count_first = 0;
try:
    tc_resp = requests.get(tc_url, headers=headers, timeout=10)
    if tc_resp.status_code == 200:
        tc_soup = BeautifulSoup(tc_resp.text, "html.parser")
        tc_table = tc_soup.find("table")
        if tc_table:
            for row in tc_table.find_all("tr")[1:]:
                cols = row.find_all("td")
                if len(cols) >= 2:
                    player_name = cols[1].text.strip()
                    row_classes = row.get("class") or []
                    
                    if player_name:
                        # Jeder Spieler OHNE 'notourcard' besitzt aktuell eine Tour Card
                        if "notourcard" not in row_classes:
                            found_count+=1

                            # Spieler im ersten Jahr der 2-Jahres-Tourkarte merken
                            if "twoyear" in row_classes:
                                players_data[player_name]["tour_card_year"] = 1
                                found_count_first+=1
                            else:
                                players_data[player_name]["tour_card_year"] = 2
except requests.exceptions.RequestException as e:
    print(f"  Fehler beim Laden der Tour-Card-Daten: {e}")

print(f"  Tour-Card-Inhaber: {found_count} (davon 1. Jahr: {found_count_first})")


# 5. Nationen von /worldcup ermitteln
wc_url = "https://dartsrankings.com/worldcup"
print(f"\nErmittle Spieler Nationen von : {wc_url} ...")
found_count = 0
try:
    wc_resp = requests.get(wc_url, headers=headers, timeout=10)
    if wc_resp.status_code == 200:
        wc_soup = BeautifulSoup(wc_resp.text, "html.parser")
        for row in wc_soup.find_all("tr"):
            name_span = row.find("span", class_="playername")
            flag_img = row.find("img", class_="flag")
            
            if name_span and flag_img:
                p_name = name_span.text.strip()
                p_nation = flag_img.get("alt", "").strip()
                if p_name and p_nation and p_name in players_data:
                    players_data[p_name]["country"] = normalize_country_code(p_nation)
                    found_count+=1;
except requests.exceptions.RequestException as e:
    print(f"  Fehler beim Laden der Nationen: {e}")

print(f"  Nationen gefunden für: {found_count} Spieler")

# 5b. Fehlende Nationen über Dartslytics-API holen
print("Ermittle fehlende Nationen von https://dartslytics.com ...")

api_headers = {
    "User-Agent": headers["User-Agent"],
    "Origin": "https://dartslytics.com",
    "Referer": "https://dartslytics.com"
}

def make_slug(text):
    # Entfernt Sonderzeichen/Apostrophe und vereinheitlicht Leerzeichen/Bindestriche
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")

found_count = 0
players_mapping = {}          # Exakter Key: "Luke Littler"
players_mapping_lower = {}    # Kleingeschrieben: "luke littler"
players_mapping_slug = {}     # Slug-Key: "william-o-connor"

try:
    dl_url = "https://api.dartslytics.com/get_players_mapping"
    r = requests.get(dl_url, headers=api_headers, timeout=10)
    if r.status_code == 200:
        raw_mapping = r.json()
        # --- Backup der Dartslytics-Daten als CSV speichern ---
        backup_filename = "dartslytics_players_backup.csv"
        with open(backup_filename, mode="w", newline="", encoding="utf-8") as backup_file:
            backup_writer = csv.writer(backup_file, delimiter=";")
            backup_writer.writerow(["id", "first_name", "last_name", "full_name", "slug", "country_raw", "country_iso"])
            
            for player_id, info in raw_mapping.items():
                first = (info.get("first_name") or "").strip()
                last = (info.get("last_name") or "").strip()
                full_name = f"{first} {last}".strip()
                slug = (info.get("slug") or "").strip()
                raw_code = (info.get("country") or "").strip()
                iso_code = normalize_country_code(raw_code) if raw_code else ""
                
                backup_writer.writerow([player_id, first, last, full_name, slug, raw_code, iso_code])
                
        print(f"  Backup gespeichert in '{backup_filename}' ({len(raw_mapping)} Einträge).")

        # Dictionary aufbauen: {"Luke Littler": {"first_name": ..., "country": "GB-ENG"}, ...}
        for player_id, info in raw_mapping.items():
            first = (info.get("first_name") or "").strip()
            last = (info.get("last_name") or "").strip()
            full_name = f"{first} {last}".strip()
            slug = (info.get("slug") or make_slug(full_name)).strip().lower()

           
            if full_name:
                players_mapping[full_name] = info
                players_mapping_lower[full_name.lower()] = info
            if slug:
                players_mapping_slug[slug] = info


        # Fehlende Nationen in 3 Stufen zuordnen
        for name, data in players_data.items():
            if data.get("country"):
                continue
            # Stufe 1: Exakter Treffer ("Vorname Nachname")
            info = players_mapping.get(name)
            
            # Stufe 2: Groß-/Kleinschreibung ignorieren ("McDonald" vs "Mcdonald")
            if not info:
                info = players_mapping_lower.get(name.lower())
                
            # Stufe 3: Abgleich über normalisierten Slug ("O'Connor" vs "O Connor")
            if not info:
                info = players_mapping_slug.get(make_slug(name))
            
            if info:
                raw_code = (info.get("country") or "").strip()
                if raw_code:
                    players_data[name]["country"] = normalize_country_code(raw_code)
                    found_count += 1
    else:
        print(f"  Fehler beim Laden des Mappings: HTTP-Status {r.status_code}")

except requests.exceptions.RequestException as e:
    print(f"  Netzwerkfehler beim Laden des Mappings: {e}")

print(f"Fehlende Nationen zugeordnet: {found_count}")
# Spieler ausgeben, die trotzdem noch keine Nation haben
missing = [name for name, data in players_data.items() if not data.get("country")]
if missing:
    print(f"Noch ohne Nation ({len(missing)}): {', '.join(missing)}")



###########################
#found_count = 0
# Eine Session hält die Verbindung offen (schont den Server)
#with requests.Session() as session:
#    session.headers.update(api_headers)
#  
#    for name, data in players_data.items():
#        if data.get("country"):
#            continue
#
#        # Macht aus "William O'Connor" -> "william-o-connor"
#        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
#        url = "https://api.dartslytics.com/get_player_data?slug=" + slug
#        print(f"Lade {url} ...")
#        try:
#            r = session.get(url, timeout=5)
#            if r.status_code == 200:
#                j = r.json()
#                player_obj = j.get("player")
#                if isinstance(player_obj, dict) and player_obj.get("country"):
#                    raw_code = str(player_obj["country"]).strip()
#                    players_data[name]["country"] = normalize_country_code(raw_code)
#                    found_count += 1
#                else:
#                    print(f"Warnung für {name} ({slug}): JSON-Key 'player.country' fehlt oder ist leer.")
#                    
#            elif r.status_code in (429, 403):
#                print(f"ABBRUCH: Rate-Limit oder Blockierung (Status {r.status_code}) bei {name}! Breche weitere API-Abfragen ab.")
#                break
#            else:
#                print(f"Fehler beim Laden der Nation für {name} (url: {url}): HTTP-Status {r.status_code}")
#
#        except requests.exceptions.RequestException as e:
#            print(f"Netzwerkfehler beim Laden der Nation für {name}: {e}")
#        # Kleine zufällige Pause (zwischen 0,3 und 0,7 Sekunden) nach jedem Request
#        time.sleep(random.uniform(0.3, 0.7))
#print(f"Fehlende Nationen zugeordnet: {found_count}")

# 10. CSV schreiben
print(f"\nSpeichere alle Daten in '{csv_filename}'...")

with open(csv_filename, mode="w", newline="", encoding="utf-8") as file:
    writer = csv.writer(file, delimiter=";")
    
    # Header schreiben
    header_row = ["rank", "name", "country", "tour_card_year"] + suffixList + ["skill_level"]
    writer.writerow(header_row)
    
    # Zeilen für jeden Spieler schreiben
    for index, (name, data) in enumerate(players_data.items(), start=1):
        row = [
            index,
            name,
            data.get("country", ""),
            data.get("tour_card_year", ""),
            *[data.get(suffix, "0") for suffix in suffixList],
            data.get("skillLevel", 1),
        ]    
        writer.writerow(row)

print("Fertig!")
