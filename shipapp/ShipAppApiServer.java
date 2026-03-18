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

import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

    private final String oceanHost;
    private final int oceanShipPort;
    private final int oceanSubPort;
    private final int subServerPort;
    private final int httpPort;

    private final ShipConnection shipConnection = new ShipConnection();

    private ServerSocket submarineServerSocket;
    private final Map<String, SubmarineSession> submarineSessions = new HashMap<>();

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
        submarineRepository = new SubmarineRepository();

        shipConnection.connect(oceanHost, oceanShipPort);

        startSubmarineServer(subServerPort, oceanHost, oceanSubPort);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.createContext("/api/state", new StateHandler());
        httpServer.createContext("/api/launch", new LaunchHandler());
        httpServer.createContext("/api/navigate", new NavigateHandler());
        httpServer.createContext("/api/scan", new ScanHandler());
        httpServer.createContext("/api/radar", new RadarHandler());
        httpServer.createContext("/api/submarine/start", new SubStartHandler());
        httpServer.createContext("/api/submarine/pilot", new SubPilotHandler());
        httpServer.createContext("/api/submarine/kill", new SubKillHandler());
        httpServer.createContext("/api/submarine/history", new SubHistoryHandler());
        httpServer.createContext("/api/submarine/positions", new SubPositionsHandler());
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
            String shipId = shipConnection.getShipId();
            if (shipId != null) {
                JSONObject ship = new JSONObject();
                ship.put("id", shipId);
                Vec2D currentSector = shipConnection.getCurrentSector();
                if (currentSector != null) {
                    ship.put("sector", new JSONObject()
                            .put("x", currentSector.getX())
                            .put("y", currentSector.getY()));
                }
                Vec2D currentDir = shipConnection.getCurrentDir();
                if (currentDir != null) {
                    ship.put("dir", new JSONObject()
                            .put("x", currentDir.getX())
                            .put("y", currentDir.getY()));
                }

                ship.put("status", "active");

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

            shipConnection.sendToShip(cmd);

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
            shipConnection.sendToShip(cmd);

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
            shipConnection.resetScan();

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "scan");
            shipConnection.sendToShip(cmd);

            ShipConnection.ScanResult result = shipConnection.awaitScan(2000);
            Integer depth = result.depth();
            Double stddev = result.stddev();
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
            shipConnection.resetRadar();

            JSONObject cmd = new JSONObject();
            cmd.put("cmd", "radar");
            shipConnection.sendToShip(cmd);

            JSONArray echos = shipConnection.awaitRadar(2000);
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
            if (submarineRepository != null) {
                String subId = session.getSubmarineId();
                if (subId == null || subId.isBlank()) {
                    subId = (id != null && !id.isBlank()) ? id : session.getIdSafe();
                }
                submarineRepository.updateSubmarineStatus(subId, "terminated");
            }

            sendJson(exchange, 200, new JSONObject().put("status", "killed"));
        }
    }

    /**
     * Liefert eine Übersicht aller jemals gespeicherten Submarines inkl. Status und letzter Position/Zeit.
     * GET /api/submarine/history
     */
    private class SubHistoryHandler implements HttpHandler {
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

            var subs = submarineRepository.getSubmarineOverview();
            JSONObject resp = new JSONObject();
            resp.put("submarines", subs);
            sendJson(exchange, 200, resp);
        }
    }

    /**
     * Liefert die letzten N Positionen eines Submarines aus der Datenbank.
     * GET /api/submarine/positions?id=<submarineId>&limit=30
     *
     * id ist optional: wenn nicht gesetzt, wird ein aktives Submarine aus der aktuellen Session genommen (falls vorhanden).
     */
    private class SubPositionsHandler implements HttpHandler {
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

            if (submarineRepository == null) {
                sendJson(exchange, 500, new JSONObject().put("error", "Datenbank nicht verfügbar"));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String submarineId = null;
            Integer limit = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "id".equals(pair[0])) {
                        submarineId = pair[1];
                    } else if (pair.length == 2 && "limit".equals(pair[0])) {
                        try {
                            limit = Integer.parseInt(pair[1]);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (submarineId == null || submarineId.isEmpty()) {
                synchronized (submarineSessions) {
                    submarineId = submarineSessions.values().stream()
                            .map(SubmarineSession::getIdSafe)
                            .findFirst()
                            .orElse(null);
                }
            }

            if (submarineId == null || submarineId.isEmpty()) {
                sendJson(exchange, 400, new JSONObject().put("error", "no submarine id provided and none active"));
                return;
            }

            int safeLimit = limit != null ? limit : 30;
            JSONArray positions = submarineRepository.getLatestPositions(submarineId, safeLimit);
            JSONObject resp = new JSONObject();
            resp.put("submarine_id", submarineId);
            resp.put("limit", safeLimit);
            resp.put("count", positions.length());
            resp.put("positions", positions);
            sendJson(exchange, 200, resp);
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

            SubmarineSession session;
            synchronized (submarineSessions) {
                if (submarineId == null || submarineId.isEmpty()) {
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
            String shipId = shipConnection.getShipId();
            if (shipId != null) {
                try {
                    JSONObject cmd = new JSONObject();
                    cmd.put("cmd", "exit");
                    shipConnection.sendToShip(cmd);
                } catch (Exception e) {
                    System.err.println("Fehler beim Senden von exit: " + e.getMessage());
                }
            }

            shipConnection.resetScan();
            shipConnection.resetRadar();

            // alle Submarines trennen
            synchronized (submarineSessions) {
                for (SubmarineSession s : submarineSessions.values()) {
                    s.kill();
                }
                submarineSessions.clear();
            }

            // bestehende Verbindung zum Ocean-Server neu aufbauen
            try {
                shipConnection.connect(oceanHost, oceanShipPort);
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
                JSONArray measurements = submarineRepository.getMeasurements(submarineId);
                resp.put("submarine_id", submarineId);
                resp.put("count", measurements.length());
                resp.put("measurements", measurements);
            } else {
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
        String shipId = shipConnection.getShipId();
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
        private Vec lastSavedPos;
        private int depth;
        private int distance;
        private String lastMessageText;
        private String lastMessageType;
        private Vec lastMessagePos;
        
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
            if (lastMessageText != null) {
                jo.put("lastMessageText", lastMessageText);
            } else {
                jo.put("lastMessageText", JSONObject.NULL);
            }
            if (lastMessageType != null) {
                jo.put("lastMessageType", lastMessageType);
            } else {
                jo.put("lastMessageType", JSONObject.NULL);
            }
            if (lastMessagePos != null) {
                jo.put("lastMessagePos", new JSONObject()
                        .put("x", lastMessagePos.getX())
                        .put("y", lastMessagePos.getY())
                        .put("z", lastMessagePos.getZ()));
            }
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
        
                if (submarineRepository != null && submarineId != null) {
                    submarineRepository.updateSubmarineStatus(submarineId, "terminated");
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

            if (submarineRepository != null && submarineId != null) {
                submarineRepository.saveSubmarine(submarineId, shipConnection.getShipId());
                // Nur echte Bewegung persistieren (Rotation/Dir-Änderung ohne Positionswechsel ignorieren)
                if (shouldPersistPosition(lastPos)) {
                    submarineRepository.savePosition(submarineId, lastPos, lastDir, depth, distance);
                    lastSavedPos = lastPos;
                }
            }
        }

        private boolean shouldPersistPosition(Vec pos) {
            if (pos == null) return false;
            if (lastSavedPos == null) return true;

            // Jede echte Bewegung tracken; Rotationen liefern i.d.R. gleiche Position.
            // Wir ignorieren nur exakt gleiche/nahezu gleiche Positionen (Double-Rauschen).
            final double eps = 1e-6;
            double dx = pos.getX() - lastSavedPos.getX();
            double dy = pos.getY() - lastSavedPos.getY();
            double dz = pos.getZ() - lastSavedPos.getZ();
            return (Math.abs(dx) > eps) || (Math.abs(dy) > eps) || (Math.abs(dz) > eps);
        }

        private void handleSubMessage(JSONObject msg) {
            String type = msg.optString("type", "info");
            String text = msg.optString("text", "");
            JSONObject posJson = msg.optJSONObject("pos");
            Vec pos = posJson != null ? Vec.fromJson(posJson) : null;
            this.lastMessageType = type;
            this.lastMessageText = text;
            this.lastMessagePos = pos;
            System.out.printf("Submarine-Message (id=%s, type=%s): %s, pos=%s%n",
                    submarineId, type, text, pos);

            // Viele Submarines schicken Positions-Updates als "message" mit pos.
            // Für die Track-Map persistieren wir deshalb auch hier (aber nur bei echter Bewegung).
            if (submarineRepository != null && submarineId != null && pos != null) {
                this.lastPos = pos;
                if (shouldPersistPosition(pos)) {
                    submarineRepository.savePosition(submarineId, pos, lastDir, depth, distance);
                    lastSavedPos = pos;
                }
            }
        }

        private void handleMeasure(JSONObject msg) {
            JSONArray vecs = msg.optJSONArray("vecs");
            int count = vecs != null ? vecs.length() : 0;
            System.out.printf("Submarine MEASURE (id=%s): %d neue Messpunkte%n", submarineId, count);

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

            this.lastPictureHex = hex;
            this.lastPictureTimestamp = System.currentTimeMillis();

            String savedFilePath = null;

            try {
                var img = OceanPicture.convertHexString2Image(hex);
                if (img == null) {
                    System.err.println("Submarine PICTURE: Konnte Bild aus Hex-String nicht dekodieren.");
                    return;
                }

                File dir = new File("pictures");
                if (!dir.exists() && !dir.mkdirs()) {
                    System.err.println("Submarine PICTURE: Konnte Verzeichnis 'pictures' nicht anlegen.");
                    return;
                }

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

        String getSubmarineId() {
            return submarineId;
        }
    }
}
