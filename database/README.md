# Ocean Explorer - Datenbank-Setup

## Voraussetzungen

- MySQL oder MariaDB Server auf Port 3306
- MySQL Connector/J (bereits in `libs/mysql-connector-j-8.3.0.jar` enthalten)

## Datenbank einrichten

1. **MySQL/MariaDB starten** (falls noch nicht laufend)

2. **Schema importieren:**
   ```bash
   mysql -u root -p < database/schema.sql
   ```
   
   Oder in der MySQL-Konsole:
   ```sql
   source database/schema.sql;
   ```

3. **Benutzer anpassen** (falls nicht root ohne Passwort):
   
   In `shipapp/SubmarineRepository.java` die Konstanten anpassen:
   ```java
   private static final String DB_USER = "root";
   private static final String DB_PASSWORD = ""; // Ihr Passwort hier
   ```

## Datenbank-Struktur

### Tabellen

| Tabelle | Beschreibung |
|---------|--------------|
| `submarines` | Stammdaten der Submarines (ID, Ship-ID, Status) |
| `submarine_positions` | Positionshistorie (x, y, z, Richtung, Tiefe) |
| `measurements` | Messpunkte (x, y, z Koordinaten) |
| `submarine_pictures` | Gespeicherte Bilder (Hex-String, Dateipfad) |
| `submarine_crashes` | Crash-Ereignisse |
| `submarine_arises` | Auftauch-Ereignisse |

### View

- `submarine_overview` - Übersicht aller Submarines mit letzter Position und Messanzahl

## API-Endpunkte für Datenbank-Abfragen

### Messpunkte abrufen

**Alle Submarines mit Messanzahl:**
```
GET http://localhost:8080/api/submarine/measurements
```

**Messpunkte eines bestimmten Submarines:**
```
GET http://localhost:8080/api/submarine/measurements?id=<submarineId>
```

## Beispiel-Abfragen

```sql
-- Alle aktiven Submarines
SELECT * FROM submarines WHERE status = 'active';

-- Alle Messpunkte eines Submarines
SELECT * FROM measurements WHERE submarine_id = 'sub_123' ORDER BY recorded_at;

-- Gesamtzahl der Messpunkte
SELECT COUNT(*) FROM measurements;

-- Submarine-Übersicht mit Statistiken
SELECT * FROM submarine_overview;
```

## Kompilieren mit MySQL-Unterstützung

```bash
# Windows
javac -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp/*.java ocean/*.java

# Linux/Mac
javac -cp ".:libs/json.jar:libs/mysql-connector-j-8.3.0.jar" shipapp/*.java ocean/*.java
```

## Server starten mit MySQL-Unterstützung

```bash
# Windows
java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer

# Linux/Mac
java -cp ".:libs/json.jar:libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer
```
