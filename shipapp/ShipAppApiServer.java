package shipapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import ocean.AppLauncher;
import ocean.Course;
import ocean.Rudder;
import ocean.Route;
import ocean.Vec;
import ocean.Vec2D;
import ocean.OceanPicture;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Optional;

/**
 * HTTP-API für die ShipApp, damit das React-Frontend die bestehende
 * Funktionalität steuern kann.
 *
 * Läuft als separater Prozess (main-Methode) und stellt Endpunkte unter
 * http://localhost:8080/api/... bereit.
 * Für mehrere Schiffe können verschiedene Ports über Kommandozeilenargumente
 * konfiguriert werden:
 *   java ShipAppApiServer [httpPort] [subServerPort] [oceanShipPort] [oceanSubPort] [oceanHost]
 *
 * Beispiel für zweites Schiff:
 *   java ShipAppApiServer 8081 6001 8150 8151 localhost
 */
public class ShipAppApiServer {

    // Standard-Konfiguration (kann über Kommandozeilenargumente überschrieben werden)
    private static final String DEFAULT_OCEAN_HOST = "localhost";
    private static final int DEFAULT_OCEAN_SHIP_PORT = 8150;
    private static final int DEFAULT_OCEAN_SUB_PORT = 8151;
    private static final int DEFAULT_SUB_SERVER_PORT = 6000;
    private static final int DEFAULT_HTTP_PORT = 8080;

    // Instanz-Konfiguration (pro Schiff unterschiedlich)
    private final String oceanHost;
    private final int oceanShipPort;
    private final int oceanSubPort;
    private final int subServerPort;
    private final int httpPort;

    // Verbindung Ocean-Server (Ship-Port)
    private Socket shipSocket;
    private BufferedReader shipIn;
    private PrintWriter shipOut;

    // Zustand Schiff
    private String shipId;
    private Vec2D currentSector;
    private Vec2D currentDir;
    private Vec currentAbsPos;

    // Letzte Scan/Radar-Ergebnisse
    private final Object scanLock = new Object();
    private Integer lastScanDepth = null;
    private Double lastScanStddev = null;

    private final Object radarLock = new Object();
    private JSONArray lastRadarEchos = null;

    // Submarine-Server
    private ServerSocket submarineServerSocket;
    private final Map<String, SubmarineSession> submarineSessions = new HashMap<>();

    // Datenbank-Repository für Submarine-Daten
    private SubmarineRepository submarineRepository;

    /**
     * Konstruktor mit Standard-Konfiguration.
     */
    public ShipAppApiServer() {
        this(DEFAULT_HTTP_PORT, DEFAULT_SUB_SERVER_PORT, DEFAULT_OCEAN_SHIP_PORT, 
             DEFAULT_OCEAN_SUB_PORT, DEFAULT_OCEAN_HOST);
    }

    /**
     * Konstruktor mit individueller Port-Konfiguration für mehrere Schiff-Instanzen.
     *
     * @param httpPort        HTTP-API-Port (z.B. 8080, 8081, 8082, ...)
     * @param subServerPort   Submarine-Server-Port (z.B. 6000, 6001, 6002, ...)
     * @param oceanShipPort   Ocean-Server Ship-Port
     * @param oceanSubPort    Ocean-Server Submarine-Port
     * @param oceanHost       Ocean-Server Hostname
     */
    public ShipAppApiServer(int httpPort, int subServerPort, int oceanShipPort, 
                            int oceanSubPort, String oceanHost) {
        this.httpPort = httpPort;
        this.subServerPort = subServerPort;
        this.oceanShipPort = oceanShipPort;
        this.oceanSubPort = oceanSubPort;
        this.oceanHost = oceanHost;
    }

    public static void main(String[] args) throws Exception {
        // Kommandozeilenargumente parsen
        int httpPort = DEFAULT_HTTP_PORT;
        int subServerPort = DEFAULT_SUB_SERVER_PORT;
        int oceanShipPort = DEFAULT_OCEAN_SHIP_PORT;
        int oceanSubPort = DEFAULT_OCEAN_SUB_PORT;
        String oceanHost = DEFAULT_OCEAN_HOST;

        if (args.length >= 1) {
            httpPort = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            subServerPort = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            oceanShipPort = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            oceanSubPort = Integer.parseInt(args[3]);
        }
        if (args.length >= 5) {
            oceanHost = args[4];
        }

        System.out.println("=== ShipAppApiServer Konfiguration ===");
        System.out.printf("  HTTP-Port:           %d%n", httpPort);
        System.out.printf("  Submarine-Server:    %d%n", subServerPort);
        System.out.printf("  Ocean-Ship-Port:     %d%n", oceanShipPort);
        System.out.printf("  Ocean-Sub-Port:      %d%n", oceanSubPort);
        System.out.printf("  Ocean-Host:          %s%n", oceanHost);
        System.out.println("======================================");

        ShipAppApiServer server = new ShipAppApiServer(httpPort, subServerPort, 
                                                        oceanShipPort, oceanSubPort, oceanHost);
        server.start();
    }

    public void start() throws Exception {
        // 1. Datenbank-Repository initialisieren
        submarineRepository = new SubmarineRepository();

        // 2. Verbindung zum Ocean-Server
        connectToOceanServer(oceanHost, oceanShipPort);

        // 3. Submarine-Server starten
        startSubmarineServer(subServerPort, oceanHost, oceanSubPort);

        // 4. HTTP-Server starten
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.createContext("/api/state", new StateHandler());
        httpServer.createContext("/api/launch", new LaunchHandler());
        httpServer.createContext("/api/navigate", new NavigateHandler());
        httpServer.createContext("/api/scan", new ScanHandler());
        httpServer.createContext("/api/radar", new RadarHandler());
        httpServer.createContext("/api/submarine/start", new SubStartHandler());
        httpServer.createContext("/api/submarine/pilot", new SubPilotHandler());
        httpServer.createContext("/api/submarine/kill", new SubKillHandler());
        httpServer.createContext("/api/submarine/picture/latest", new SubPictureLatestFileHandler());
        httpServer.createContext("/api/submarine/picture", new SubPictureHandler());
        httpServer.createContext("/api/submarine/measurements", new MeasurementsHandler());
        httpServer.createContext("/api/reset", new ResetHandler());
        httpServer.createContext("/api", this::handleRoot);
        httpServer.setExecutor(null);
        httpServer.start();

        System.out.println("ShipAppApiServer läuft auf http://localhost:" + httpPort + "/api");
    }

    // ------------------------------------------------------------
    // HTTP Hilfsfunktionen
    // ------------------------------------------------------------

    private void handleRoot(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, new JSONObject().put("status", "ok"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    // ------------------------------------------------------------
    // HTTP-Handler
    // ------------------------------------------------------------

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            JSONObject root = new JSONObject();
            if (shipId != null) {
                JSONObject ship = new JSONObject();
                ship.put("id", shipId);
                if (currentSector != null) {
                    ship.put("sector", new JSONObject()
                            .put("x", currentSector.getX())
                            .put("y", currentSector.getY()));
                }
                if (currentDir != null) {
                    ship.put("dir", new JSONObject()
                            .put("x", currentDir.getX())
                            .put("y", currentDir.getY()));
                }
                root.put("ship", ship);
            } else {
                root.put("ship", JSONObject.NULL);
            }

            JSONArray subs = new JSONArray();
            synchronized (submarineSessions) {
                for (SubmarineSession s : submarineSessions.values()) {
                    subs.put(s.toJson());
                }
            }
            root.put("submarines", subs);

            sendJson(exchange, 200, root);
        }
    }

    private class LaunchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            String body = readBody(exchange);
            JSONObject jo = body.isEmpty() ? new JSONObject() : new JSONObject(body);

            String name = jo.optString("name", "Explorer1");
            int x = jo.optInt("x", 0);
            int y = jo.optInt("y", 0);
            int dx = jo.optInt("dx", 0);
            int dy = jo.optInt("dy", 1);

            Vec2D sector = new Vec2D(x, y);
            Vec2D dir = new Vec2D(dx, dy);

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "launch");
            cmd.put("name", name);
            cmd.put("typ", "ship");
            cmd.put("sector", sector.toJson());
            cmd.put("dir", dir.toJson());

            sendToShip(cmd);

            sendJson(exchange, 200, new JSONObject().put("status", "sent"));
        }
    }

    private class NavigateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            String body = readBody(exchange);
            JSONObject jo = body.isEmpty() ? new JSONObject() : new JSONObject(body);

            String rudderStr = jo.optString("rudder", Rudder.Center.name());
            String courseStr = jo.optString("course", Course.Forward.name());
            Rudder rudder = Rudder.valueOf(rudderStr);
            Course course = Course.valueOf(courseStr);

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "navigate");
            cmd.put("rudder", rudder.name());
            cmd.put("course", course.name());
            sendToShip(cmd);

            sendJson(exchange, 200, new JSONObject().put("status", "sent"));
        }
    }

    private class ScanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            synchronized (scanLock) {
                lastScanDepth = null;
                lastScanStddev = null;
            }

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "scan");
            sendToShip(cmd);

            Integer depth;
            Double stddev;
            long timeoutAt = System.currentTimeMillis() + 2000;
            synchronized (scanLock) {
                while (lastScanDepth == null && System.currentTimeMillis() < timeoutAt) {
                    try {
                        scanLock.wait(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                depth = lastScanDepth;
                stddev = lastScanStddev;
            }
            JSONObject resp = new JSONObject();
            resp.put("depth", depth != null ? depth : JSONObject.NULL);
            resp.put("stddev", stddev != null ? stddev : JSONObject.NULL);
            sendJson(exchange, 200, resp);
        }
    }

    private class RadarHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            synchronized (radarLock) {
                lastRadarEchos = null;
            }

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "radar");
            sendToShip(cmd);

            JSONArray echos;
            long timeoutAt = System.currentTimeMillis() + 2000;
            synchronized (radarLock) {
                while (lastRadarEchos == null && System.currentTimeMillis() < timeoutAt) {
                    try {
                        radarLock.wait(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                echos = lastRadarEchos;
            }
            JSONObject resp = new JSONObject();
            resp.put("echos", echos != null ? echos : new JSONArray());
            sendJson(exchange, 200, resp);
        }
    }

    private class SubStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            startSubmarineProcess(oceanHost, oceanSubPort);
            sendJson(exchange, 200, new JSONObject().put("status", "sent"));
        }
    }

    private class SubPilotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            String body = readBody(exchange);
            JSONObject jo = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            String id = jo.optString("id", null);
            String routeStr = jo.optString("route", Route.C.name());
            String action = jo.optString("action", "");

            SubmarineSession session;
            synchronized (submarineSessions) {
                if (id == null || id.isEmpty()) {
                    session = submarineSessions.values().stream().findFirst().orElse(null);
                } else {
                    session = submarineSessions.get(id);
                }
            }
            if (session == null) {
                sendJson(exchange, 400, new JSONObject().put("error", "no such submarine"));
                return;
            }

            Route route = Route.valueOf(routeStr);
            session.sendPilot(route, action);
            sendJson(exchange, 200, new JSONObject().put("status", "sent"));
        }
    }

    private class SubKillHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            String body = readBody(exchange);
            JSONObject jo = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            String id = jo.optString("id", null);

            SubmarineSession session;
            synchronized (submarineSessions) {
                if (id == null || id.isEmpty()) {
                    session = submarineSessions.values().stream().findFirst().orElse(null);
                } else {
                    session = submarineSessions.get(id);
                }
            }
            if (session == null) {
                sendJson(exchange, 400, new JSONObject().put("error", "no such submarine"));
                return;
            }

            session.kill();
            synchronized (submarineSessions) {
                submarineSessions.remove(session.getIdSafe());
            }
            sendJson(exchange, 200, new JSONObject().put("status", "killed"));
        }
    }

    /**
     * Serves the latest picture file from disk (pictures/sub_*_*.png).
     * GET /api/submarine/picture/latest?id=<submarineId> - optional filter by submarine id.
     * Returns raw PNG so the UI can use it as img src when the JSON picture API returns nothing.
     */
    private class SubPictureLatestFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            String submarineId = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "id".equals(pair[0])) {
                        submarineId = pair[1];
                    }
                }
            }
            Path dir = Paths.get("pictures");
            if (!Files.isDirectory(dir)) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String prefix = submarineId != null && !submarineId.isEmpty()
                    ? "sub_" + submarineId + "_"
                    : "sub_";
            Optional<Path> latest;
            try (var stream = Files.list(dir)) {
                latest = stream
                        .filter(p -> p.getFileName().toString().endsWith(".png")
                                && p.getFileName().toString().startsWith(prefix))
                        .max(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p).toMillis();
                            } catch (IOException e) {
                                return 0L;
                            }
                        }));
            } catch (IOException e) {
                System.err.println("SubPictureLatestFile: " + e.getMessage());
                latest = Optional.empty();
            }
            if (latest.isEmpty()) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            byte[] body = Files.readAllBytes(latest.get());
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /**
     * Handler zum Abrufen des letzten Bildes eines Submarines für die Live-View.
     * GET /api/submarine/picture?id=<submarineId> - Letztes Bild als Base64
     *
     * Sucht zuerst im Memory (aktive Session), dann in der Datenbank.
     */
    private class SubPictureHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            // Query-Parameter auslesen
            String query = exchange.getRequestURI().getQuery();
            String submarineId = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "id".equals(pair[0])) {
                        submarineId = pair[1];
                    }
                }
            }

            JSONObject resp = new JSONObject();
            String base64 = null;
            String foundId = submarineId;
            long timestamp = 0;

            // 1. Zuerst im Memory (aktive Session) suchen
            SubmarineSession session;
            synchronized (submarineSessions) {
                if (submarineId == null || submarineId.isEmpty()) {
                    // Erstes Submarine mit Bild nehmen
                    session = submarineSessions.values().stream()
                            .filter(s -> s.lastPictureHex != null && !s.lastPictureHex.isEmpty())
                            .findFirst()
                            .orElse(submarineSessions.values().stream().findFirst().orElse(null));
                } else {
                    session = submarineSessions.get(submarineId);
                }
            }

            if (session != null) {
                foundId = session.getIdSafe();
                base64 = session.getLastPictureBase64();
                timestamp = session.lastPictureTimestamp;
            }

            // 2. Falls kein Memory-Bild, aus Datenbank laden
            if (base64 == null && submarineRepository != null) {
                JSONObject dbPicture;
                if (foundId != null && !foundId.isEmpty()) {
                    dbPicture = submarineRepository.getLatestPicture(foundId);
                } else {
                    dbPicture = submarineRepository.getLatestPictureAny();
                    if (dbPicture != null) {
                        foundId = dbPicture.optString("submarine_id", null);
                    }
                }

                if (dbPicture != null) {
                    String hexFromDb = dbPicture.optString("picture_hex", null);
                    timestamp = dbPicture.optLong("captured_at", 0);
                    if (hexFromDb != null && !hexFromDb.isEmpty()) {
                        base64 = hexToBase64(hexFromDb);
                    }
                }
            }

            // Response zusammenbauen
            if (base64 != null) {
                resp.put("id", foundId != null ? foundId : JSONObject.NULL);
                resp.put("picture", base64);
                resp.put("timestamp", timestamp);
                resp.put("hasPicture", true);
            } else {
                resp.put("id", foundId != null ? foundId : JSONObject.NULL);
                resp.put("picture", JSONObject.NULL);
                resp.put("hasPicture", false);
            }

            sendJson(exchange, 200, resp);
        }

        private String hexToBase64(String hex) {
            try {
                int len = hex.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                         + Character.digit(hex.charAt(i + 1), 16));
                }
                return java.util.Base64.getEncoder().encodeToString(data);
            } catch (Exception e) {
                System.err.println("Fehler bei Hex->Base64 Konvertierung: " + e.getMessage());
                return null;
            }
        }
    }

    private class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            // Schiff abmelden
            if (shipId != null) {
                try {
                    JSONObject cmd = new JSONObject();
                    cmd.put("cmd", "exit");
                    sendToShip(cmd);
                } catch (Exception e) {
                    System.err.println("Fehler beim Senden von exit: " + e.getMessage());
                }
            }

            shipId = null;
            currentSector = null;
            currentDir = null;
            currentAbsPos = null;

            synchronized (scanLock) {
                lastScanDepth = null;
                lastScanStddev = null;
            }
            synchronized (radarLock) {
                lastRadarEchos = null;
            }

            // alle Submarines trennen
            synchronized (submarineSessions) {
                for (SubmarineSession s : submarineSessions.values()) {
                    s.kill();
                }
                submarineSessions.clear();
            }

            // bestehende Verbindung zum Ocean-Server schließen und neu aufbauen,
            // damit ein wirklich frisches Spiel möglich ist
            try {
                if (shipSocket != null && !shipSocket.isClosed()) {
                    shipSocket.close();
                }
            } catch (IOException ignored) {
            }
            shipSocket = null;
            shipIn = null;
            shipOut = null;

            try {
                connectToOceanServer(oceanHost, oceanShipPort);
            } catch (IOException e) {
                System.err.println("Fehler beim Reconnect zum Ocean-Server nach Reset: " + e.getMessage());
            }

            JSONObject resp = new JSONObject().put("status", "reset");
            sendJson(exchange, 200, resp);
        }
    }

    /**
     * Handler zum Abrufen der gespeicherten Messpunkte aus der Datenbank.
     * GET /api/submarine/measurements?id=<submarineId> - Messpunkte eines Submarines
     * GET /api/submarine/measurements - Übersicht aller Submarines mit Zählungen
     */
    private class MeasurementsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            if (submarineRepository == null) {
                sendJson(exchange, 500, new JSONObject().put("error", "Datenbank nicht verfügbar"));
                return;
            }

            // Query-Parameter auslesen
            String query = exchange.getRequestURI().getQuery();
            String submarineId = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "id".equals(pair[0])) {
                        submarineId = pair[1];
                    }
                }
            }

            JSONObject resp = new JSONObject();
            if (submarineId != null && !submarineId.isEmpty()) {
                // Messpunkte eines bestimmten Submarines
                JSONArray measurements = submarineRepository.getMeasurements(submarineId);
                resp.put("submarine_id", submarineId);
                resp.put("count", measurements.length());
                resp.put("measurements", measurements);
            } else {
                // Übersicht aller Submarines
                var activeSubmarines = submarineRepository.getActiveSubmarines();
                JSONArray subsArray = new JSONArray();
                for (String id : activeSubmarines) {
                    JSONObject sub = new JSONObject();
                    sub.put("id", id);
                    sub.put("measurement_count", submarineRepository.getMeasurementCount(id));
                    subsArray.put(sub);
                }
                resp.put("submarines", subsArray);
                resp.put("total_measurements", submarineRepository.getTotalMeasurementCount());
            }

            sendJson(exchange, 200, resp);
        }
    }

    // ------------------------------------------------------------
    // Verbindung Ocean-Server (Ship-Client)
    // ------------------------------------------------------------

    private void connectToOceanServer(String host, int port) throws IOException {
        System.out.printf("Verbinde zu Ocean-Server %s:%d ...%n", host, port);
        shipSocket = new Socket(host, port);
        shipIn = new BufferedReader(new InputStreamReader(shipSocket.getInputStream(), StandardCharsets.UTF_8));
        shipOut = new PrintWriter(new OutputStreamWriter(shipSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        System.out.println("Verbindung zum Ocean-Server aufgebaut.");

        Thread t = new Thread(this::shipListenLoop, "ShipAppApi-ShipListener");
        t.setDaemon(true);
        t.start();
    }

    private void shipListenLoop() {
        try {
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
    }

    private void handleLaunched(JSONObject msg) {
        this.shipId = msg.optString("id", null);
        JSONObject sectorJson = msg.optJSONObject("sector");
        if (sectorJson != null) {
            this.currentSector = Vec2D.fromJson(sectorJson);
        }
        JSONObject absposJson = msg.optJSONObject("abspos");
        if (absposJson != null) {
            // abspos kommt als 2D-Vektor ("vec2") vom Ocean-Server.
            // Erst in Vec2D parsen und dann nach Vec (z = 0) umwandeln.
            Vec2D abs2d = Vec2D.fromJson(absposJson);
            this.currentAbsPos = abs2d != null ? abs2d.asVec() : null;
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
            Vec2D abs2d = Vec2D.fromJson(absposJson);
            currentAbsPos = abs2d != null ? abs2d.asVec() : null;
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
        synchronized (scanLock) {
            lastScanDepth = depth;
            lastScanStddev = stddev;
            scanLock.notifyAll();
        }
        System.out.printf("Scan-Ergebnis (ShipID=%s): depth=%d m, stddev=%.2f%n",
                msg.optString("id", "?"), depth, stddev);
    }

    private void handleRadarResponse(JSONObject msg) {
        JSONArray echos = msg.optJSONArray("echos");
        synchronized (radarLock) {
            lastRadarEchos = echos != null ? echos : new JSONArray();
            radarLock.notifyAll();
        }
        System.out.println("Radar-Antwort mit " + (echos != null ? echos.length() : 0) + " Echos");
    }

    private synchronized void sendToShip(JSONObject cmd) {
        if (shipOut == null) {
            System.err.println("Keine Verbindung zum Ocean-Server.");
            return;
        }
        shipOut.println(cmd.toString());
    }

    // ------------------------------------------------------------
    // Submarine-Server
    // ------------------------------------------------------------

    private void startSubmarineServer(int serverPort, String oceanHost, int oceanSubPort) throws IOException {
        submarineServerSocket = new ServerSocket(serverPort);
        String localHostName = InetAddress.getLocalHost().getHostName();

        System.out.printf("Submarine-Server gestartet auf Port %d (Host=%s)%n", serverPort, localHostName);
        System.out.printf("Bereit zum Starten von Submarines (OceanSubPort=%d)%n", oceanSubPort);

        Thread t = new Thread(() -> submarineAcceptLoop(localHostName, serverPort, oceanHost, oceanSubPort),
                "ShipAppApi-SubmarineAccept");
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
    // Innere Klasse: SubmarineSession
    // ------------------------------------------------------------

    private class SubmarineSession extends Thread {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private String submarineId;
        private Vec lastPos;
        private Vec lastDir;
        private int depth;
        private int distance;
        
        // Letztes empfangenes Bild für Live-View
        private String lastPictureHex;
        private long lastPictureTimestamp;

        SubmarineSession(Socket socket) throws IOException {
            super("ShipAppApi-SubmarineSession");
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        String getIdSafe() {
            return submarineId != null ? submarineId : "sub@" + socket.getPort();
        }

        JSONObject toJson() {
            JSONObject jo = new JSONObject();
            jo.put("id", submarineId != null ? submarineId : JSONObject.NULL);
            if (lastPos != null) {
                jo.put("pos", new JSONObject()
                        .put("x", lastPos.getX())
                        .put("y", lastPos.getY())
                        .put("z", lastPos.getZ()));
            }
            jo.put("depth", depth);
            jo.put("distance", distance);
            jo.put("hasPicture", lastPictureHex != null && !lastPictureHex.isEmpty());
            jo.put("pictureTimestamp", lastPictureTimestamp);
            return jo;
        }
        
        /**
         * Gibt das letzte empfangene Bild als Base64 zurück.
         */
        String getLastPictureBase64() {
            if (lastPictureHex == null || lastPictureHex.isEmpty()) {
                return null;
            }
            // Hex-String in Bytes konvertieren, dann Base64
            try {
                byte[] bytes = hexStringToByteArray(lastPictureHex);
                return java.util.Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                System.err.println("Fehler bei Hex->Base64 Konvertierung: " + e.getMessage());
                return null;
            }
        }
        
        private byte[] hexStringToByteArray(String hex) {
            int len = hex.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                     + Character.digit(hex.charAt(i + 1), 16));
            }
            return data;
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
                synchronized (submarineSessions) {
                    if (submarineId != null) {
                        submarineSessions.remove(submarineId);
                    }
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleSubmarineMessage(String jsonLine) {
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
        }

        private void handleReady(JSONObject msg) {
            this.submarineId = msg.optString("id", this.submarineId);
            JSONObject posJson = msg.optJSONObject("pos");
            JSONObject dirJson = msg.optJSONObject("dir");
            depth = msg.optInt("depth", -1);
            distance = msg.optInt("distance", -1);
            lastPos = posJson != null ? Vec.fromJson(posJson) : null;
            lastDir = dirJson != null ? Vec.fromJson(dirJson) : null;
            synchronized (submarineSessions) {
                submarineSessions.put(getIdSafe(), this);
            }
            System.out.printf("Submarine READY (id=%s): pos=%s, depth=%d, distance=%d%n",
                    submarineId, lastPos, depth, distance);

            // In Datenbank speichern
            if (submarineRepository != null && submarineId != null) {
                submarineRepository.saveSubmarine(submarineId, shipId);
                submarineRepository.savePosition(submarineId, lastPos, lastDir, depth, distance);
            }
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

            // Messpunkte in Datenbank speichern
            if (submarineRepository != null && submarineId != null && vecs != null) {
                submarineRepository.saveMeasurements(submarineId, vecs);
            }
        }

        private void handlePicture(JSONObject msg) {
            String hex = msg.optString("picture", "");
            int len = hex != null ? hex.length() : 0;
            System.out.printf("Submarine PICTURE (id=%s): Bild empfangen (PNG-Hex-String, Länge=%d)%n",
                    submarineId, len);

            if (hex == null || hex.isEmpty()) {
                return;
            }

            // Letztes Bild für Live-View speichern
            this.lastPictureHex = hex;
            this.lastPictureTimestamp = System.currentTimeMillis();

            String savedFilePath = null;

            try {
                // Hex-String in Bild umwandeln
                var img = OceanPicture.convertHexString2Image(hex);
                if (img == null) {
                    System.err.println("Submarine PICTURE: Konnte Bild aus Hex-String nicht dekodieren.");
                    return;
                }

                // Zielverzeichnis vorbereiten (relativ zum Working-Directory)
                File dir = new File("pictures");
                if (!dir.exists() && !dir.mkdirs()) {
                    System.err.println("Submarine PICTURE: Konnte Verzeichnis 'pictures' nicht anlegen.");
                    return;
                }

                // Dateiname: pictures/sub_<id>_<timestamp>.png
                String idSafe = submarineId != null ? submarineId : "unknown";
                long ts = System.currentTimeMillis();
                String filename = new File(dir, "sub_" + idSafe + "_" + ts + ".png").getPath();

                boolean ok = OceanPicture.saveAsPNG(img, filename);
                if (ok) {
                    System.out.println("Submarine PICTURE: Bild gespeichert unter: " + filename);
                    savedFilePath = filename;
                } else {
                    System.err.println("Submarine PICTURE: Speichern unter '" + filename + "' fehlgeschlagen.");
                }
            } catch (Exception e) {
                System.err.println("Submarine PICTURE: Fehler beim Speichern des Bildes: " + e.getMessage());
            }

            // Bild in Datenbank speichern
            if (submarineRepository != null && submarineId != null) {
                submarineRepository.savePicture(submarineId, hex, savedFilePath);
            }
        }

        private void handleSubCrash(JSONObject msg) {
            String message = msg.optString("message", "Crash");
            JSONObject sectorJson = msg.optJSONObject("sector");
            JSONObject sunkPosJson = msg.optJSONObject("sunkPos");
            Vec2D sector = sectorJson != null ? Vec2D.fromJson(sectorJson) : null;
            Vec sunkPos = sunkPosJson != null ? Vec.fromJson(sunkPosJson) : null;
            System.out.printf("!!! Submarine-Crash (id=%s): %s, Sektor=%s, SinkPos=%s%n",
                    submarineId, message, sector, sunkPos);

            // Crash in Datenbank speichern
            if (submarineRepository != null && submarineId != null) {
                submarineRepository.saveCrash(submarineId, message, sector, sunkPos);
            }
        }

        private void handleArise(JSONObject msg) {
            JSONObject arisePosJson = msg.optJSONObject("arisePos");
            Vec arisePos = arisePosJson != null ? Vec.fromJson(arisePosJson) : null;
            System.out.printf("Submarine ARISE (id=%s): arisePos=%s%n", submarineId, arisePos);

            // Arise-Event in Datenbank speichern
            if (submarineRepository != null && submarineId != null) {
                submarineRepository.saveArise(submarineId, arisePos);
            }
        }

        void kill() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
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

