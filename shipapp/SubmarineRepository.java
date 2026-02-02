package shipapp;

import ocean.Vec;
import ocean.Vec2D;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository-Klasse für die Persistierung von Submarine-Daten in MySQL/MariaDB.
 * 
 * Speichert:
 * - Submarine-Stammdaten (submarines)
 * - Positionsdaten (submarine_positions)
 * - Messpunkte (measurements)
 * - Bilder (submarine_pictures)
 * - Crash-Ereignisse (submarine_crashes)
 * - Auftauchen-Ereignisse (submarine_arises)
 */
public class SubmarineRepository {

    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "ocean_explorer";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // Anpassen falls Passwort gesetzt

    private Connection connection;

    /**
     * Erstellt eine neue Repository-Instanz und verbindet zur Datenbank.
     */
    public SubmarineRepository() {
        connect();
    }

    /**
     * Stellt die Verbindung zur MySQL/MariaDB-Datenbank her.
     */
    private void connect() {
        try {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    DB_HOST, DB_PORT, DB_NAME);
            connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
            System.out.println("Datenbankverbindung hergestellt: " + url);
        } catch (SQLException e) {
            System.err.println("Fehler bei der Datenbankverbindung: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prüft ob die Verbindung aktiv ist und stellt sie ggf. wieder her.
     */
    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
    }

    /**
     * Schließt die Datenbankverbindung.
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("Datenbankverbindung geschlossen.");
            } catch (SQLException e) {
                System.err.println("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // Submarine-Stammdaten
    // ========================================================================

    /**
     * Speichert oder aktualisiert ein Submarine in der Datenbank.
     * 
     * @param submarineId ID des Submarines
     * @param shipId ID des zugehörigen Schiffs
     */
    public void saveSubmarine(String submarineId, String shipId) {
        ensureConnection();
        if (connection == null) return;

        String sql = """
            INSERT INTO submarines (id, ship_id, status)
            VALUES (?, ?, 'active')
            ON DUPLICATE KEY UPDATE 
                last_seen = CURRENT_TIMESTAMP,
                ship_id = VALUES(ship_id)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            stmt.setString(2, shipId != null ? shipId : "unknown");
            stmt.executeUpdate();
            System.out.println("Submarine gespeichert: " + submarineId);
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern des Submarines: " + e.getMessage());
        }
    }

    /**
     * Aktualisiert den Status eines Submarines.
     * 
     * @param submarineId ID des Submarines
     * @param status neuer Status (active, crashed, surfaced)
     */
    public void updateSubmarineStatus(String submarineId, String status) {
        ensureConnection();
        if (connection == null) return;

        String sql = "UPDATE submarines SET status = ?, last_seen = CURRENT_TIMESTAMP WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, submarineId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Aktualisieren des Submarine-Status: " + e.getMessage());
        }
    }

    // ========================================================================
    // Positionsdaten
    // ========================================================================

    /**
     * Speichert eine neue Position für ein Submarine (Ready-Event).
     * 
     * @param submarineId ID des Submarines
     * @param pos aktuelle Position
     * @param dir aktuelle Richtung (kann null sein)
     * @param depth aktuelle Tiefe
     * @param distance zurückgelegte Distanz
     */
    public void savePosition(String submarineId, Vec pos, Vec dir, int depth, int distance) {
        ensureConnection();
        if (connection == null || pos == null) return;

        String sql = """
            INSERT INTO submarine_positions 
            (submarine_id, pos_x, pos_y, pos_z, dir_x, dir_y, dir_z, depth, distance)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            stmt.setDouble(2, pos.getX());
            stmt.setDouble(3, pos.getY());
            stmt.setDouble(4, pos.getZ());
            
            if (dir != null) {
                stmt.setDouble(5, dir.getX());
                stmt.setDouble(6, dir.getY());
                stmt.setDouble(7, dir.getZ());
            } else {
                stmt.setNull(5, Types.DOUBLE);
                stmt.setNull(6, Types.DOUBLE);
                stmt.setNull(7, Types.DOUBLE);
            }
            
            stmt.setInt(8, depth);
            stmt.setInt(9, distance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern der Position: " + e.getMessage());
        }
    }

    // ========================================================================
    // Messpunkte (Measure)
    // ========================================================================

    /**
     * Speichert mehrere Messpunkte aus einem Measure-Event.
     * 
     * @param submarineId ID des Submarines
     * @param vecs JSONArray mit den Messpunkten
     */
    public void saveMeasurements(String submarineId, JSONArray vecs) {
        ensureConnection();
        if (connection == null || vecs == null || vecs.isEmpty()) return;

        String sql = "INSERT INTO measurements (submarine_id, vec_x, vec_y, vec_z) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);

            for (int i = 0; i < vecs.length(); i++) {
                JSONObject vecJson = vecs.optJSONObject(i);
                if (vecJson == null) continue;

                Vec vec = Vec.fromJson(vecJson);
                if (vec == null) continue;

                stmt.setString(1, submarineId);
                stmt.setDouble(2, vec.getX());
                stmt.setDouble(3, vec.getY());
                stmt.setDouble(4, vec.getZ());
                stmt.addBatch();
            }

            stmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
            System.out.printf("Submarine %s: %d Messpunkte gespeichert%n", submarineId, vecs.length());
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern der Messpunkte: " + e.getMessage());
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                // ignorieren
            }
        }
    }

    // ========================================================================
    // Bilder (Picture)
    // ========================================================================

    /**
     * Speichert ein empfangenes Bild.
     * 
     * @param submarineId ID des Submarines
     * @param pictureHex Hex-String des PNG-Bildes
     * @param filePath Pfad zur gespeicherten Datei (kann null sein)
     */
    public void savePicture(String submarineId, String pictureHex, String filePath) {
        ensureConnection();
        if (connection == null || pictureHex == null || pictureHex.isEmpty()) return;

        String sql = "INSERT INTO submarine_pictures (submarine_id, picture_hex, file_path) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            stmt.setString(2, pictureHex);
            stmt.setString(3, filePath);
            stmt.executeUpdate();
            System.out.printf("Submarine %s: Bild gespeichert (Länge=%d)%n", submarineId, pictureHex.length());
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern des Bildes: " + e.getMessage());
        }
    }

    // ========================================================================
    // Crash-Ereignisse
    // ========================================================================

    /**
     * Speichert ein Crash-Ereignis.
     * 
     * @param submarineId ID des Submarines
     * @param message Crash-Nachricht
     * @param sector Sektor des Crashs (kann null sein)
     * @param sunkPos Position des Absturzes (kann null sein)
     */
    public void saveCrash(String submarineId, String message, Vec2D sector, Vec sunkPos) {
        ensureConnection();
        if (connection == null) return;

        String sql = """
            INSERT INTO submarine_crashes 
            (submarine_id, message, sector_x, sector_y, sunk_pos_x, sunk_pos_y, sunk_pos_z)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            stmt.setString(2, message);
            
            if (sector != null) {
                stmt.setInt(3, sector.getX());
                stmt.setInt(4, sector.getY());
            } else {
                stmt.setNull(3, Types.INTEGER);
                stmt.setNull(4, Types.INTEGER);
            }
            
            if (sunkPos != null) {
                stmt.setDouble(5, sunkPos.getX());
                stmt.setDouble(6, sunkPos.getY());
                stmt.setDouble(7, sunkPos.getZ());
            } else {
                stmt.setNull(5, Types.DOUBLE);
                stmt.setNull(6, Types.DOUBLE);
                stmt.setNull(7, Types.DOUBLE);
            }
            
            stmt.executeUpdate();
            System.out.printf("Submarine %s: Crash gespeichert - %s%n", submarineId, message);
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern des Crashs: " + e.getMessage());
        }

        // Status aktualisieren
        updateSubmarineStatus(submarineId, "crashed");
    }

    // ========================================================================
    // Arise-Ereignisse (Auftauchen)
    // ========================================================================

    /**
     * Speichert ein Arise-Ereignis (Submarine taucht auf).
     * 
     * @param submarineId ID des Submarines
     * @param arisePos Position des Auftauchens
     */
    public void saveArise(String submarineId, Vec arisePos) {
        ensureConnection();
        if (connection == null) return;

        String sql = "INSERT INTO submarine_arises (submarine_id, arise_pos_x, arise_pos_y, arise_pos_z) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            
            if (arisePos != null) {
                stmt.setDouble(2, arisePos.getX());
                stmt.setDouble(3, arisePos.getY());
                stmt.setDouble(4, arisePos.getZ());
            } else {
                stmt.setNull(2, Types.DOUBLE);
                stmt.setNull(3, Types.DOUBLE);
                stmt.setNull(4, Types.DOUBLE);
            }
            
            stmt.executeUpdate();
            System.out.printf("Submarine %s: Arise gespeichert%n", submarineId);
        } catch (SQLException e) {
            System.err.println("Fehler beim Speichern des Arise-Events: " + e.getMessage());
        }

        // Status aktualisieren
        updateSubmarineStatus(submarineId, "surfaced");
    }

    // ========================================================================
    // Abfragen
    // ========================================================================

    /**
     * Gibt die Anzahl der Messpunkte für ein Submarine zurück.
     * 
     * @param submarineId ID des Submarines
     * @return Anzahl der Messpunkte
     */
    public int getMeasurementCount(String submarineId) {
        ensureConnection();
        if (connection == null) return 0;

        String sql = "SELECT COUNT(*) FROM measurements WHERE submarine_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Zählen der Messpunkte: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Gibt alle Messpunkte für ein Submarine als JSON zurück.
     * 
     * @param submarineId ID des Submarines
     * @return JSONArray mit allen Messpunkten
     */
    public JSONArray getMeasurements(String submarineId) {
        ensureConnection();
        JSONArray result = new JSONArray();
        if (connection == null) return result;

        String sql = "SELECT vec_x, vec_y, vec_z, recorded_at FROM measurements WHERE submarine_id = ? ORDER BY recorded_at";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, submarineId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject point = new JSONObject();
                    point.put("x", rs.getDouble("vec_x"));
                    point.put("y", rs.getDouble("vec_y"));
                    point.put("z", rs.getDouble("vec_z"));
                    point.put("recorded_at", rs.getTimestamp("recorded_at").toString());
                    result.put(point);
                }
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen der Messpunkte: " + e.getMessage());
        }
        return result;
    }

    /**
     * Gibt alle aktiven Submarines zurück.
     * 
     * @return Liste der Submarine-IDs
     */
    public List<String> getActiveSubmarines() {
        ensureConnection();
        List<String> result = new ArrayList<>();
        if (connection == null) return result;

        String sql = "SELECT id FROM submarines WHERE status = 'active'";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen aktiver Submarines: " + e.getMessage());
        }
        return result;
    }

    /**
     * Gibt die Gesamtzahl aller Messpunkte in der Datenbank zurück.
     * 
     * @return Gesamtzahl der Messpunkte
     */
    public int getTotalMeasurementCount() {
        ensureConnection();
        if (connection == null) return 0;

        String sql = "SELECT COUNT(*) FROM measurements";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Zählen aller Messpunkte: " + e.getMessage());
        }
        return 0;
    }
}
