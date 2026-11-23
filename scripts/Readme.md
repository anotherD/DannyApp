
# Requirements
In PowerShell
pip install requests beautifulsoup4
pip install pycountry

# Datenbeschaffung starten
In Powershell
python .\downloadOOMasCSV.py

# Spieler, zu denen keine Nation gefunden werden konnte, werden im script als Manuelles Mapping ergänzt:
````
MANUAL_COUNTRY_OVERRIDES = {
    "Michael Rosenauer": "DEU",
    "Levente Sarai": "HUN",
}
````

# Resultate in App bereitstellen
Die resultierenden csv darts_rankings.csv und initial_placements.csv sind für die App in app/src/main/assets/ zu kopieren. Von dort wird die app die csv importieren.

# Weitere Informationen:
Das file dartslytics_players_backup.csv dient als backup, falls die Seite einmal nicht mehr verfügbar ist, wollen wir die wertvollen Informationen nicht verlieren.
