package shipapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ocean.AppLauncher;
import ocean.Course;
import ocean.Rudder;
import ocean.Route;
import ocean.Vec;
import ocean.Vec2D;

/**
 * Einfache Konsolen-Implementierung der ShipApp.
 *
 * Aufgaben:
 * - Verbindung zum Ocean-Server aufbauen
 * - Schiff "launchen" und steuern (navigate, scan, radar, exit)
 * - Submarine-Server-Socket bereitstellen (Variante "submarine")
 * - Submarines mit {@link AppLauncher} starten und eingehende Daten anzeigen
 *
 * Diese Variante konzentriert sich auf die Kernfunktionalität laut Aufgabenblatt,
 * ohne Datenbank- oder Web-Integration.
 */
public class ShipApp {

    public static void main(String[] args) {
        new ShipApp().run();
    }

    private final Scanner scanner = new Scanner(System.in);

    // Verbindung zum Ocean-Server (Ship-Port)
    private Socket shipSocket;
    private BufferedReader shipIn;
    private PrintWriter shipOut;

    // Aktueller Schiffszustand
    private String shipId;
    private Vec2D currentSector;
    private Vec2D currentDir;
    private Vec currentAbsPos;

    // Submarine-Server (ShipApp als Server, Submarines als Client)
    private ServerSocket submarineServerSocket;
    private final Map<String, SubmarineSession> submarineSessions = new HashMap<>();

    private void run() {
        System.out.println("=== ShipApp (Variante submarine) ===");
        try {
            // 1. Verbindungsparameter erfragen
            String oceanHost = askString("Ocean-Server Host", "localhost");
            int oceanShipPort = askInt("Ocean-Server Ship-Port", 0);
            int oceanSubPort = askInt("Ocean-Server Submarine-Port", 0);
            int shipSubServerPort = askInt("ShipApp-Server-Port für Submarines", 0);

            if (oceanShipPort <= 0 || oceanSubPort <= 0 || shipSubServerPort <= 0) {
                System.err.println("Alle Ports müssen > 0 sein. Programm wird beendet.");
                return;
            }

            // 2. Verbindung zum Ocean-Server (Ship-Port) herstellen
            connectToOceanServer(oceanHost, oceanShipPort);

            // 3. Schiff launchen
            launchShip();

            // Auf Antwort des Servers warten (asynchrone launched-Message)
            // max. ~5 Sekunden warten, bis shipId gesetzt wurde
            if (shipId == null) {
                for (int i = 0; i < 50 && shipId == null; i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        // ignorieren, wir prüfen shipId trotzdem
                    }
                }
            }

            if (shipId == null) {
                System.err.println("Ship wurde nicht erfolgreich gelauncht (keine 'launched'-Antwort vom Server empfangen).");
                System.err.println("Bitte prüfe im Ocean-Server-Admin-Fenster, ob ein Fehler zum Launch angezeigt wird.");
                // Wir beenden NICHT sofort, damit Navigation/Scan/Radar trotzdem testbar sind.
            }

            // 4. Submarine-Server starten (ShipApp als Server)
            startSubmarineServer(shipSubServerPort, oceanHost, oceanSubPort);

            // 5. Konsolenmenü für manuelle Steuerung
            mainMenu(oceanHost, oceanSubPort);

        } catch (IOException e) {
            System.err.println("I/O-Fehler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeAll();
        }

        System.out.println("ShipApp beendet.");
    }

    private String askString(String label, String defaultVal) {
        System.out.print(label + " [" + defaultVal + "]: ");
        String line = scanner.nextLine().trim();
        return line.isEmpty() ? defaultVal : line;
    }

    private int askInt(String label, int defaultVal) {
        while (true) {
            System.out.print(label + " [" + defaultVal + "]: ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                return defaultVal;
            }
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Bitte eine gültige Ganzzahl eingeben.");
            }
        }
    }

    // ------------------------------------------------------------
    // Verbindung zum Ocean-Server (Ship-Client)
    // ------------------------------------------------------------

    private void connectToOceanServer(String host, int port) throws IOException {
        System.out.printf("Verbinde zu Ocean-Server %s:%d ...%n", host, port);
        shipSocket = new Socket(host, port);
        shipIn = new BufferedReader(new InputStreamReader(shipSocket.getInputStream(), StandardCharsets.UTF_8));
        shipOut = new PrintWriter(new OutputStreamWriter(shipSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        System.out.println("Verbindung zum Ocean-Server aufgebaut.");

        // Hintergrund-Thread liest alle eingehenden Nachrichten
        Thread t = new Thread(this::shipListenLoop, "ShipApp-ShipListener");
        t.setDaemon(true);
        t.start();
    }

    private void shipListenLoop() {
        try {
            System.out.println("Ship-Listener-Thread gestartet, warte auf Server-Nachrichten ...");
            String line;
            while ((line = shipIn.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                System.out.println("Vom Ocean-Server empfangen: " + line);
                handleShipMessage(line);
            }
        } catch (IOException e) {
            System.err.println("Verbindung zum Ocean-Server wurde beendet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private synchronized void handleShipMessage(String jsonLine) {
        try {
            JSONObject msg = new JSONObject(jsonLine);
            String cmd = msg.optString("cmd", "");
            switch (cmd) {
                case "launched" -> handleLaunched(msg);
                case "message" -> handleShipInfoMessage(msg);
                case "move2d" -> handleMove2d(msg);
                case "crash" -> handleShipCrash(msg);
                case "scanned" -> handleScanned(msg);
                case "radarresponse" -> handleRadarResponse(msg);
                default -> System.out.println("Unbekannte Ship-Server-Nachricht: " + msg.toString());
            }
        } catch (JSONException e) {
            System.err.println("Fehler beim Parsen der Ship-Server-Nachricht: " + jsonLine);
            e.printStackTrace();
        }
    }

    private void handleLaunched(JSONObject msg) {
        this.shipId = msg.optString("id", null);
        JSONObject sectorJson = msg.optJSONObject("sector");
        if (sectorJson != null) {
            this.currentSector = Vec2D.fromJson(sectorJson);
        }
        JSONObject absposJson = msg.optJSONObject("abspos");
        if (absposJson != null) {
            this.currentAbsPos = Vec.fromJson(absposJson);
        }
        System.out.printf("Ship erfolgreich gelauncht. ID=%s, Sektor=%s, Pos=%s%n",
                shipId, currentSector, currentAbsPos);
    }

    private void handleShipInfoMessage(JSONObject msg) {
        String type = msg.optString("type", "info");
        String text = msg.optString("text", "");
        System.out.printf("Ship-Server-Message (%s): %s%n", type, text);
    }

    private void handleMove2d(JSONObject msg) {
        JSONObject sectorJson = msg.optJSONObject("sector");
        JSONObject dirJson = msg.optJSONObject("dir");
        JSONObject absposJson = msg.optJSONObject("abspos");
        if (sectorJson != null) {
            currentSector = Vec2D.fromJson(sectorJson);
        }
        if (dirJson != null) {
            currentDir = Vec2D.fromJson(dirJson);
        }
        if (absposJson != null) {
            currentAbsPos = Vec.fromJson(absposJson);
        }
        System.out.printf("Neue Schiffsposition: Sektor=%s, Richtung=%s, Pos=%s%n",
                currentSector, currentDir, currentAbsPos);
    }

    private void handleShipCrash(JSONObject msg) {
        String message = msg.optString("message", "Crash");
        JSONObject sectorJson = msg.optJSONObject("sector");
        JSONObject sunkPosJson = msg.optJSONObject("sunkPos");
        Vec2D sector = sectorJson != null ? Vec2D.fromJson(sectorJson) : null;
        Vec sunkPos = sunkPosJson != null ? Vec.fromJson(sunkPosJson) : null;
        System.out.printf("!!! Ship-Crash: %s, Sektor=%s, Sink-Pos=%s%n", message, sector, sunkPos);
    }

    private void handleScanned(JSONObject msg) {
        int depth = msg.optInt("depth", -1);
        double stddev = msg.optDouble("stddev", 0.0);
        System.out.printf("Scan-Ergebnis (ShipID=%s): depth=%d m, stddev=%.2f%n",
                msg.optString("id", "?"), depth, stddev);
    }

    private void handleRadarResponse(JSONObject msg) {
        System.out.println("Radar-Echos:");
        JSONArray echos = msg.optJSONArray("echos");
        if (echos == null) {
            System.out.println("  (keine Echos)");
            return;
        }
        for (int i = 0; i < echos.length(); i++) {
            JSONObject echo = echos.getJSONObject(i);
            Vec2D sector = Vec2D.fromJson(echo.getJSONObject("sector"));
            int height = echo.optInt("height", 0);
            String ground = echo.optString("ground", "?");
            System.out.printf("  Sektor=%s, ground=%s, height=%d%n", sector, ground, height);
        }
    }

    private synchronized void sendToShip(JSONObject cmd) {
        if (shipOut == null) {
            System.err.println("Keine Verbindung zum Ocean-Server.");
            return;
        }
        shipOut.println(cmd.toString());
    }

    // ------------------------------------------------------------
    // Schiff starten und steuern
    // ------------------------------------------------------------

    private void launchShip() {
        System.out.println("=== Schiff launchen ===");
        String name = askString("Schiffsname", "Explorer1");
        int x = askInt("Startsektor X (0-99)", 0);
        int y = askInt("Startsektor Y (0-99)", 0);
        int dx = askInt("Start-Richtung dx (-1,0,1)", 0);
        int dy = askInt("Start-Richtung dy (-1,0,1)", 1);

        Vec2D sector = new Vec2D(x, y);
        Vec2D dir = new Vec2D(dx, dy);

        JSONObject cmd = new JSONObject();
        cmd.put("cmd", "launch");
        cmd.put("name", name);
        cmd.put("typ", "ship");
        cmd.put("sector", sector.toJson());
        cmd.put("dir", dir.toJson());

        sendToShip(cmd);
        System.out.println("Launch-Befehl gesendet, warte auf Antwort des Servers...");
    }

    private void navigate() {
        System.out.println("=== Navigate ===");
        Rudder rudder = askRudder();
        Course course = askCourse();

        JSONObject cmd = new JSONObject();
        cmd.put("cmd", "navigate");
        cmd.put("rudder", rudder.name());
        cmd.put("course", course.name());
        sendToShip(cmd);
    }

    private Rudder askRudder() {
        while (true) {
            System.out.print("Ruder (L=Left, C=Center, R=Right) [C]: ");
            String s = scanner.nextLine().trim().toUpperCase();
            if (s.isEmpty() || s.equals("C")) {
                return Rudder.Center;
            } else if (s.equals("L")) {
                return Rudder.Left;
            } else if (s.equals("R")) {
                return Rudder.Right;
            }
            System.out.println("Ungültige Eingabe.");
        }
    }

    private Course askCourse() {
        while (true) {
            System.out.print("Kurs (F=Forward, B=Backward) [F]: ");
            String s = scanner.nextLine().trim().toUpperCase();
            if (s.isEmpty() || s.equals("F")) {
                return Course.Forward;
            } else if (s.equals("B")) {
                return Course.Backward;
            }
            System.out.println("Ungültige Eingabe.");
        }
    }

    private void sendScan() {
        JSONObject cmd = new JSONObject();
        cmd.put("cmd", "scan");
        sendToShip(cmd);
    }

    private void sendRadar() {
        JSONObject cmd = new JSONObject();
        cmd.put("cmd", "radar");
        sendToShip(cmd);
    }

    private void sendExit() {
        JSONObject cmd = new JSONObject();
        cmd.put("cmd", "exit");
        sendToShip(cmd);
    }

    // ------------------------------------------------------------
    // Submarine-Server (ShipApp als Server)
    // ------------------------------------------------------------

    private void startSubmarineServer(int serverPort, String oceanHost, int oceanSubPort) throws IOException {
        submarineServerSocket = new ServerSocket(serverPort);
        String localHostName = InetAddress.getLocalHost().getHostName();

        System.out.printf("Submarine-Server gestartet auf Port %d (Host=%s)%n", serverPort, localHostName);
        System.out.printf("Bereit zum Starten von Submarines (OceanSubPort=%d)%n", oceanSubPort);

        // Thread für accept-Loop
        Thread t = new Thread(() -> submarineAcceptLoop(localHostName, serverPort, oceanHost, oceanSubPort),
                "ShipApp-SubmarineAccept");
        t.setDaemon(true);
        t.start();
    }

    private void submarineAcceptLoop(String shipHost, int shipPort, String oceanHost, int oceanSubPort) {
        while (!submarineServerSocket.isClosed()) {
            try {
                Socket s = submarineServerSocket.accept();
                SubmarineSession session = new SubmarineSession(s);
                session.start();
                System.out.println("Neue Submarine-Verbindung angenommen.");
            } catch (IOException e) {
                if (!submarineServerSocket.isClosed()) {
                    System.err.println("Fehler im Submarine-Accept-Loop: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void startSubmarineProcess(String oceanHost, int oceanSubPort) {
        if (shipId == null) {
            System.err.println("Kein ShipID bekannt. Schiff muss zuerst gelauncht sein.");
            return;
        }
        if (submarineServerSocket == null || submarineServerSocket.isClosed()) {
            System.err.println("Submarine-Server läuft nicht.");
            return;
        }
        try {
            String shipHost = InetAddress.getLocalHost().getHostName();
            int shipPort = submarineServerSocket.getLocalPort();
            System.out.printf("Starte Submarine (shipId=%s, shipHost=%s, shipPort=%d, oceanHost=%s, oceanSubPort=%d)%n",
                    shipId, shipHost, shipPort, oceanHost, oceanSubPort);
            boolean ok = AppLauncher.startSubmarine(shipId, shipHost, shipPort, oceanHost, oceanSubPort);
            if (!ok) {
                System.err.println("Submarine-Prozess konnte nicht gestartet werden.");
            } else {
                System.out.println("Submarine-Prozess gestartet (siehe Submarine-GUI).");
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Ermitteln des lokalen Hostnamens: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Submarine-Steuerung (Pilot)
    // ------------------------------------------------------------

    private void pilotSubmarine() {
        if (submarineSessions.isEmpty()) {
            System.out.println("Keine aktiven Submarines verbunden.");
            return;
        }
        System.out.println("Verfügbare Submarines:");
        for (String id : submarineSessions.keySet()) {
            System.out.println(" - " + id);
        }
        System.out.print("Submarine-ID (leer = erstes nehmen): ");
        String id = scanner.nextLine().trim();
        SubmarineSession session;
        if (id.isEmpty()) {
            session = submarineSessions.values().iterator().next();
        } else {
            session = submarineSessions.get(id);
            if (session == null) {
                System.out.println("Unbekannte Submarine-ID.");
                return;
            }
        }

        Route route = askRoute();
        String action = askAction(route);
        session.sendPilot(route, action);
    }

    private Route askRoute() {
        System.out.println("Mögliche Routes: C,N,NE,E,SE,S,SW,W,NW,UP,DOWN,None");
        while (true) {
            System.out.print("Route [C]: ");
            String s = scanner.nextLine().trim().toUpperCase();
            if (s.isEmpty()) {
                return Route.C;
            }
            try {
                return Route.valueOf(s);
            } catch (IllegalArgumentException e) {
                System.out.println("Ungültige Route.");
            }
        }
    }

    private String askAction(Route route) {
        // Nur bei Route.None spielt action eine Rolle
        if (route != Route.None) {
            return "";
        }
        System.out.println("Aktionen bei Route=None: take_photo, locate oder leer.");
        System.out.print("Action: ");
        return scanner.nextLine().trim();
    }

    // ------------------------------------------------------------
    // Hauptmenü
    // ------------------------------------------------------------

    private void mainMenu(String oceanHost, int oceanSubPort) {
        while (true) {
            System.out.println();
            System.out.println("=== Hauptmenü ===");
            System.out.println("1) Navigate");
            System.out.println("2) Scan");
            System.out.println("3) Radar");
            System.out.println("4) Submarine starten");
            System.out.println("5) Submarine pilotieren");
            System.out.println("6) WASD-Steuerung (WASD bewegen, Q/E drehen)");
            System.out.println("9) Exit (Ship verlassen)");
            System.out.print("Auswahl: ");
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> navigate();
                case "2" -> sendScan();
                case "3" -> sendRadar();
                case "4" -> startSubmarineProcess(oceanHost, oceanSubPort);
                case "5" -> pilotSubmarine();
                case "6" -> wasdControlLoop();
                case "9" -> {
                    sendExit();
                    return;
                }
                default -> System.out.println("Ungültige Auswahl.");
            }
        }
    }

    /**
     * Einfache direkte Tastatursteuerung:
     * W/S: vor/zurück, A/D: vorwärts links/rechts,
     * Q/E: rückwärts links/rechts (mehr Rotationseffekt),
     * X: zurück ins Hauptmenü.
     */
    private void wasdControlLoop() {
        System.out.println("=== WASD-Steuerung aktiviert ===");
        System.out.println("W: vorwärts, S: rückwärts, A: vorwärts links, D: vorwärts rechts");
        System.out.println("Q: rückwärts links (stärkere Drehung), E: rückwärts rechts");
        System.out.println("X: zurück zum Hauptmenü");

        while (true) {
            System.out.print("[W/A/S/D/Q/E/X]: ");
            String line = scanner.nextLine().trim().toUpperCase();
            if (line.isEmpty()) {
                continue;
            }
            char c = line.charAt(0);
            if (c == 'X') {
                System.out.println("WASD-Steuerung beendet.");
                return;
            }

            Rudder rudder;
            Course course;

            switch (c) {
                case 'W' -> {
                    rudder = Rudder.Center;
                    course = Course.Forward;
                }
                case 'S' -> {
                    rudder = Rudder.Center;
                    course = Course.Backward;
                }
                case 'A' -> {
                    rudder = Rudder.Left;
                    course = Course.Forward;
                }
                case 'D' -> {
                    rudder = Rudder.Right;
                    course = Course.Forward;
                }
                case 'Q' -> {
                    rudder = Rudder.Left;
                    course = Course.Backward;
                }
                case 'E' -> {
                    rudder = Rudder.Right;
                    course = Course.Backward;
                }
                default -> {
                    System.out.println("Unbekannte Eingabe.");
                    continue;
                }
            }

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "navigate");
            cmd.put("rudder", rudder.name());
            cmd.put("course", course.name());
            sendToShip(cmd);
        }
    }

    // ------------------------------------------------------------
    // Aufräumen
    // ------------------------------------------------------------

    private void closeAll() {
        try {
            if (shipSocket != null && !shipSocket.isClosed()) {
                shipSocket.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (submarineServerSocket != null && !submarineServerSocket.isClosed()) {
                submarineServerSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------
    // Innere Klasse: SubmarineSession
    // ------------------------------------------------------------

    private class SubmarineSession extends Thread {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private String submarineId;

        SubmarineSession(Socket socket) throws IOException {
            super("ShipApp-SubmarineSession");
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    handleSubmarineMessage(line);
                }
            } catch (IOException e) {
                System.err.println("Submarine-Verbindung beendet: " + e.getMessage());
            } finally {
                if (submarineId != null) {
                    submarineSessions.remove(submarineId);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleSubmarineMessage(String jsonLine) {
            try {
                JSONObject msg = new JSONObject(jsonLine);
                String cmd = msg.optString("cmd", "");
                switch (cmd) {
                    case "ready" -> handleReady(msg);
                    case "message" -> handleSubMessage(msg);
                    case "measure" -> handleMeasure(msg);
                    case "picture" -> handlePicture(msg);
                    case "crash" -> handleSubCrash(msg);
                    case "arise" -> handleArise(msg);
                    default -> System.out.println("Unbekannte Submarine-Nachricht: " + msg.toString());
                }
            } catch (JSONException e) {
                System.err.println("Fehler beim Parsen der Submarine-Nachricht: " + jsonLine);
                e.printStackTrace();
            }
        }

        private void handleReady(JSONObject msg) {
            this.submarineId = msg.optString("id", this.submarineId);
            if (submarineId != null) {
                submarineSessions.put(submarineId, this);
            }
            JSONObject posJson = msg.optJSONObject("pos");
            JSONObject dirJson = msg.optJSONObject("dir");
            int depth = msg.optInt("depth", -1);
            int distance = msg.optInt("distance", -1);
            Vec pos = posJson != null ? Vec.fromJson(posJson) : null;
            Vec dir = dirJson != null ? Vec.fromJson(dirJson) : null;
            System.out.printf("Submarine READY (id=%s): pos=%s, dir=%s, depth=%d, distance=%d%n",
                    submarineId, pos, dir, depth, distance);
        }

        private void handleSubMessage(JSONObject msg) {
            String type = msg.optString("type", "info");
            String text = msg.optString("text", "");
            JSONObject posJson = msg.optJSONObject("pos");
            Vec pos = posJson != null ? Vec.fromJson(posJson) : null;
            System.out.printf("Submarine-Message (id=%s, type=%s): %s, pos=%s%n",
                    submarineId, type, text, pos);
        }

        private void handleMeasure(JSONObject msg) {
            JSONArray vecs = msg.optJSONArray("vecs");
            int count = vecs != null ? vecs.length() : 0;
            System.out.printf("Submarine MEASURE (id=%s): %d neue Messpunkte%n", submarineId, count);
        }

        private void handlePicture(JSONObject msg) {
            System.out.printf("Submarine PICTURE (id=%s): Bild empfangen (PNG-Hex-String, Länge=%d)%n",
                    submarineId, msg.optString("picture", "").length());
        }

        private void handleSubCrash(JSONObject msg) {
            String message = msg.optString("message", "Crash");
            JSONObject sectorJson = msg.optJSONObject("sector");
            JSONObject sunkPosJson = msg.optJSONObject("sunkPos");
            Vec2D sector = sectorJson != null ? Vec2D.fromJson(sectorJson) : null;
            Vec sunkPos = sunkPosJson != null ? Vec.fromJson(sunkPosJson) : null;
            System.out.printf("!!! Submarine-Crash (id=%s): %s, Sektor=%s, SinkPos=%s%n",
                    submarineId, message, sector, sunkPos);
        }

        private void handleArise(JSONObject msg) {
            JSONObject arisePosJson = msg.optJSONObject("arisePos");
            Vec arisePos = arisePosJson != null ? Vec.fromJson(arisePosJson) : null;
            System.out.printf("Submarine ARISE (id=%s): arisePos=%s%n", submarineId, arisePos);
        }

        void sendPilot(Route route, String action) {
            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "pilot");
            cmd.put("route", route.name());
            if (action != null && !action.isEmpty()) {
                cmd.put("action", action);
            } else {
                cmd.put("action", JSONObject.NULL);
            }
            out.println(cmd.toString());
        }
    }
}

