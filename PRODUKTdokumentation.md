# Ocean Explorer – Produktdokumentation

Diese Anleitung beschreibt, wie Sie **Ocean Explorer** anwenden und welche Funktionen (Features) zur Verfügung stehen.

---

## 1. Überblick

**Ocean Explorer** ist eine Steuerungsanwendung für ein virtuelles Forschungsschiff und Tauchroboter (Submarines) in einem simulierten Ozean. Sie besteht aus:

- **Backend:** Java-API-Server (ShipAppApiServer), der mit dem Ocean-Server und optional einer MySQL-Datenbank kommuniziert.
- **Frontend:** React-Weboberfläche (ShipApp Control), mit der Sie Schiff und Submarines steuern.
- **Datenbank (optional):** Speicherung von Submarine-Positionen, Messpunkten, Bildern und Ereignissen.

---

## 2. Voraussetzungen und Start

### 2.1 Voraussetzungen

- **Ocean-Server** muss laufen (z. B. über `oceanstarter.jar` oder `oceanserver.jar`).
- **Java** (z. B. Java 8+) mit den JARs in `libs/` (json.jar, mysql-connector-j-8.3.0.jar bei DB-Nutzung).
- **MySQL/MariaDB** (Port 3306), falls Sie die Datenbank-Funktionen nutzen wollen.
- **Node.js/npm** für das React-Frontend.

### 2.2 Backend starten

```bash
# Ohne Datenbank
java -cp ".;libs/json.jar" shipapp.ShipAppApiServer

# Mit MySQL (Windows)
java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer

# Mit anderen Ports (z. B. zweites Schiff)
java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer 8081 6001
```

Standard-API: **http://localhost:8080/api**

### 2.3 Frontend starten

```bash
cd shipapp-ui
npm install
npm run dev
```

Standard-URL: **http://localhost:5173**

### 2.4 Datenbank einrichten (optional)

Siehe `database/README.md`. Kurz:

1. MySQL starten.
2. Schema importieren: `mysql -u root -p < database/schema.sql`
3. In `shipapp/SubmarineRepository.java` ggf. Benutzer/Passwort anpassen.

---

## 3. Features und Anwendung

### 3.1 Schiff starten (Launch)

**Zweck:** Das Forschungsschiff mit dem Ocean-Server verbinden und in einem Sektor platzieren.

**Anwendung im Frontend:**

1. Im Bereich **„Schiff starten“** eintragen:
   - **Name** (z. B. Explorer1)
   - **Sektor X**, **Sektor Y** (Startposition im 10×10 km Ozean)
   - **Richtung dx**, **Richtung dy** (Fahrtrichtung)
2. Auf **„Schiff launchen“** klicken.

**Ergebnis:** Das Schiff erscheint im Ocean-Server, die aktuelle ShipID und Sektor/Richtung werden angezeigt. Ohne Launch sind Navigation und Submarine-Start nicht möglich.

---

### 3.2 Navigation (Schiff steuern)

**Zweck:** Das Schiff im Ozean bewegen (vorwärts/rückwärts, links/rechts).

**Anwendung:**

- **Buttons:** Im Bereich **„Navigation“** die Pfeil-Buttons nutzen (vorwärts, vorwärts links/rechts, rückwärts, rückwärts links/rechts).
- **Tastatur:**
  - **W** = Vorwärts  
  - **S** = Rückwärts  
  - **A** = Vorwärts links  
  - **D** = Vorwärts rechts  
  - **Q** = Rückwärts links  
  - **E** = Rückwärts rechts  

**Hinweis:** Das Schiff bewegt sich nur innerhalb des aktuellen Sektors; ein Sektorwechsel erfolgt über die Simulation.

---

### 3.3 Scan

**Zweck:** Am aktuellen Schiffstand die Wassertiefe und Streuung (Stddev) messen.

**Anwendung:** Button **„Scan“** in der Navigation klicken.

**Ergebnis:** Im Log erscheinen `depth` und `stddev`. Die API liefert diese Werte (GET-äquivalent über Scan-POST).

---

### 3.4 Submarine starten

**Zweck:** Einen Tauchroboter im aktuellen Sektor starten, um den Sektor zu erkunden.

**Anwendung:** Button **„Submarine starten“** im Bereich **„Submarines“** klicken.

**Bedingung:** Schiff muss gelauched sein und im Sektor bleiben; während ein Submarine aktiv ist, darf das Schiff den Sektor nicht wechseln.

**Ergebnis:** Ein neues Submarine erscheint in der Liste mit ID (z. B. `sub_#sub03#0#Explorer1_…`), Position (x, y, z), Tiefe (depth) und Distanz (distance).

---

### 3.5 Submarine steuern (Pilot)

**Zweck:** Submarine bewegen, drehen, auf-/abtauchen, Foto aufnehmen, lokalisieren.

**Anwendung:**

- **Auswahl:** In der Submarine-Liste eine Submarine auswählen (Klick auf das Pfeil-Icon „Mit Pfeiltasten steuern“). Die ausgewählte Submarine wird mit den Pfeiltasten gesteuert.
- **Tastatur (Pfeiltasten):**
  - **↑** = Geradeaus (Route C)
  - **↓** = Abtauchen (DOWN)
  - **←** = Nach links drehen (W)
  - **→** = Nach rechts drehen (E)
- **Buttons pro Submarine:**
  - **Geradeaus** (Play) – Route C
  - **Aufsteigen** (↑) – UP
  - **Abtauchen** (↓) – DOWN
  - **Foto** (Kamera) – Foto aufnehmen und in der Live-View anzeigen
  - **Locate** – Position/Rückmeldung
  - **Rotate left** (W) / **Rotate right** (E)
  - **Kill** – Submarine beenden

**Routen (Route):** C (geradeaus), N, E, S, W, NE, NW, SE, SW, UP, DOWN, None (für Aktionen wie Foto).

**Aktionen (action):** `take_photo` (Foto aufnehmen), `locate` (lokalisieren).

---

### 3.6 Kamerabild (Live-View)

**Zweck:** Ein von der Submarine aufgenommenes Bild anzeigen.

**Anwendung:**

1. Submarine auswählen.
2. Bei einer Submarine auf **Kamera-Icon** klicken oder in der Sektion **„Kamerabild“** auf das Kamera-Icon klicken (nimmt Foto der ausgewählten Submarine auf).
3. Das Bild erscheint im Bereich **„Kamerabild“** (aus API oder Fallback über neueste Datei).

Bilder können serverseitig in der Datenbank und im Ordner `pictures/` gespeichert werden.

---

### 3.7 Log

**Zweck:** Chronologische Anzeige von Aktionen und Fehlern (Launch, Navigate, Scan, Pilot, Fehler).

**Anwendung:** Log wird automatisch gefüllt. **„Clear All“** leert die Anzeige (nur lokal, keine Server-Änderung).

---

### 3.8 Reset Session

**Zweck:** Alles zurücksetzen: Schiff abmelden, alle Submarines beenden, Verbindung zum Ocean-Server neu aufbauen.

**Anwendung:** Button **„Reset Session“** im Bereich **„Schiff starten“** klicken.

**Ergebnis:** Zustand im Frontend wird geleert; Sie können danach erneut **„Schiff launchen“** ausführen.

---

### 3.9 Submarine beenden (Kill)

**Zweck:** Eine Submarine gezielt beenden.

**Anwendung:** Bei der gewünschten Submarine auf das **Kill-Icon** (Mülleimer) klicken.

**Ergebnis:** Die Submarine wird getrennt und verschwindet aus der Liste.

---

## 4. API-Features (für Entwickler/Integration)

Die folgenden Endpunkte stehen zusätzlich zur UI-Funktionalität zur Verfügung:

| Methode | Endpunkt | Beschreibung |
|--------|----------|--------------|
| GET | `/api/state` | Aktueller Zustand (Schiff, alle Submarines mit Position/Tiefe/Distanz) |
| POST | `/api/launch` | Schiff starten (Body: name, x, y, dx, dy) |
| POST | `/api/navigate` | Schiff steuern (Body: rudder, course) |
| POST | `/api/scan` | Scan auslösen (Response: depth, stddev) |
| POST | `/api/radar` | Radar abfragen (Response: echos) |
| POST | `/api/submarine/start` | Submarine starten |
| POST | `/api/submarine/pilot` | Submarine steuern (Body: id, route, action) |
| POST | `/api/submarine/kill` | Submarine beenden (Body: id) |
| GET | `/api/submarine/picture?id=<id>` | Letztes Bild einer Submarine (Base64) |
| GET | `/api/submarine/picture/latest?id=<id>` | Neuestes Bild als PNG (Datei-Fallback) |
| GET | `/api/submarine/measurements` | Übersicht: alle Submarines mit Messanzahl |
| GET | `/api/submarine/measurements?id=<id>` | Messpunkte einer Submarine (aus DB) |
| POST | `/api/reset` | Session zurücksetzen |

**Hinweis:** Radar und Messpunkte werden von der API bereitgestellt; die aktuelle UI zeigt Scan-Ergebnisse im Log und nutzt die Picture- und State-Endpunkte. Messpunkte können z. B. per API oder eigener UI ausgewertet werden.

---

## 5. Datenbank-Features (optional)

Wenn MySQL eingerichtet ist, speichert das Backend automatisch:

- **Submarines:** ID, ship_id, Status (active/crashed/surfaced)
- **Positionshistorie:** submarine_positions (x, y, z, Richtung, Tiefe, Distanz)
- **Messpunkte:** measurements (x, y, z pro Submarine)
- **Bilder:** submarine_pictures (Hex-Daten, Dateipfad)
- **Ereignisse:** submarine_crashes, submarine_arises

**Abfragen:**

- Alle Submarines mit Messanzahl: `GET /api/submarine/measurements`
- Messpunkte einer Submarine: `GET /api/submarine/measurements?id=<submarineId>`

Details und SQL-Beispiele: `database/README.md`.

---

## 6. Mehrere Schiffe betreiben

Für mehrere Schiffe nacheinander verschiedene Ports verwenden:

```bash
# Schiff 1 (Standard)
java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer

# Schiff 2
java -cp ".;libs/json.jar;libs/mysql-connector-j-8.3.0.jar" shipapp.ShipAppApiServer 8081 6001
```

Im Frontend die API-URL anpassen (`shipapp-ui/src/App.jsx`):

```javascript
const API_BASE = 'http://localhost:8081/api';  // für Schiff 2
```

Oder mehrere Frontend-Instanzen mit verschiedenen Ports starten (z. B. `npm run dev -- --port 5174`).

---

## 7. Feature-Übersicht (Kurz)

| Feature | Beschreibung |
|--------|----------------|
| **Schiff starten** | Schiff mit Ocean-Server verbinden, Sektor und Richtung setzen |
| **Schiff navigieren** | Schiff mit WASD + Q/E oder Buttons bewegen |
| **Scan** | Tiefe und Stddev am aktuellen Stand messen |
| **Submarine starten** | Tauchroboter im Sektor starten |
| **Submarine steuern** | Bewegen (C, N, E, S, W, …), UP/DOWN, Foto, Locate |
| **Kamerabild** | Foto der Submarine in der Live-View anzeigen |
| **Submarine beenden** | Einzelne Submarine killen |
| **Reset Session** | Schiff und alle Submarines zurücksetzen |
| **Log** | Aktionen und Fehler protokollieren |
| **API** | state, launch, navigate, scan, radar, submarine/*, reset, measurements |
| **Datenbank** | Positionen, Messpunkte, Bilder, Crashes/Arises speichern und abfragen |

---

*Stand: Projekt Ocean Explorer v1.0*
