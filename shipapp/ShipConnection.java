package shipapp;

import ocean.Course;
import ocean.Rudder;
import ocean.Vec;
import ocean.Vec2D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Kapselt die TCP-Verbindung zum Ocean-Server (Ship-Port)
 * und den aktuellen Schiffs-Zustand inkl. Scan/Radar-Ergebnisse.
 *
 * Die Klasse ist bewusst zustandsbehaftet und threadsicher,
 * damit sie direkt aus den HTTP-Handlern der ShipAppApiServer
 * Instanz verwendet werden kann.
 */
public class ShipConnection {

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

    public void connect(String host, int port) throws IOException {
        System.out.printf("Verbinde zu Ocean-Server %s:%d ...%n", host, port);
        shipSocket = new Socket(host, port);
        shipIn = new BufferedReader(new InputStreamReader(shipSocket.getInputStream(), StandardCharsets.UTF_8));
        shipOut = new PrintWriter(new OutputStreamWriter(shipSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        System.out.println("Verbindung zum Ocean-Server aufgebaut.");

        Thread t = new Thread(this::shipListenLoop, "ShipConnection-ShipListener");
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

    public synchronized void sendToShip(JSONObject cmd) {
        if (shipOut == null) {
            System.err.println("Keine Verbindung zum Ocean-Server.");
            return;
        }
        shipOut.println(cmd.toString());
    }

    // --- Scan/Radar API für HTTP-Handler ---

    public void resetScan() {
        synchronized (scanLock) {
            lastScanDepth = null;
            lastScanStddev = null;
        }
    }

    public ScanResult awaitScan(long timeoutMillis) {
        Integer depth;
        Double stddev;
        long timeoutAt = System.currentTimeMillis() + timeoutMillis;
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
        return new ScanResult(depth, stddev);
    }

    public void resetRadar() {
        synchronized (radarLock) {
            lastRadarEchos = null;
        }
    }

    public JSONArray awaitRadar(long timeoutMillis) {
        JSONArray echos;
        long timeoutAt = System.currentTimeMillis() + timeoutMillis;
        synchronized (radarLock) {
            while (lastRadarEchos == null && System.currentTimeMillis() < timeoutAt) {
                try {
                    radarLock.wait(200);
                } catch (InterruptedException ignored) {
                }
            }
            echos = lastRadarEchos;
        }
        return echos != null ? echos : new JSONArray();
    }

    // --- Getter für HTTP-API ---

    public String getShipId() {
        return shipId;
    }

    public Vec2D getCurrentSector() {
        return currentSector;
    }

    public Vec2D getCurrentDir() {
        return currentDir;
    }

    public Vec getCurrentAbsPos() {
        return currentAbsPos;
    }

    public record ScanResult(Integer depth, Double stddev) {
    }
}

