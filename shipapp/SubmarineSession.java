package shipapp;

import ocean.Vec;
import ocean.Vec2D;
import ocean.OceanPicture;
import ocean.Route;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Repräsentiert eine einzelne TCP-Verbindung zu einem Submarine.
 * Kapselt die Kommunikation, den letzten bekannten Zustand und das
 * Speichern in der Datenbank.
 */
public class SubmarineSession extends Thread {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final Map<String, SubmarineSession> submarineSessions;
    private final SubmarineRepository submarineRepository;
    private final Supplier<String> shipIdSupplier;

    private String submarineId;
    private Vec lastPos;
    private Vec lastDir;
    private int depth;
    private int distance;

    String lastPictureHex;
    long lastPictureTimestamp;

    public SubmarineSession(Socket socket,
                            Map<String, SubmarineSession> submarineSessions,
                            SubmarineRepository submarineRepository,
                            Supplier<String> shipIdSupplier) throws IOException {
        super("ShipAppApi-SubmarineSession");
        this.socket = socket;
        this.submarineSessions = submarineSessions;
        this.submarineRepository = submarineRepository;
        this.shipIdSupplier = shipIdSupplier;
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
            String shipId = shipIdSupplier != null ? shipIdSupplier.get() : null;
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
}

