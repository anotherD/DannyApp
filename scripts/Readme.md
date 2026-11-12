
# Requirements
In Powershell
pip install requests beautifulsoup4
pip install pycountry

# Datenbeschaffung starten
In Powershell
python .\downloadOOMasCSV.py

# Resultate in App bereitstellen
Das resultierende csv darts_rankings.csv ist für die App in app/src/main/assets/players.csv zu kopieren. Von dort wird die app die csv importieren.

# Weitere Informationen:
das file dartslytics_players_backup.csv dient als backup, falls die seite einmal nicht mehr verfügbar ist, wollen wir die wertvollen Informationen nicht verlieren.
