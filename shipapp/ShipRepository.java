package shipapp;

import ocean.Vec2D;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Repository-Klasse für Ship-Metadaten (aktive Schiffe).
 *
 * Hält den Zustand des Schiffs in der Tabelle "ships" synchron:
 * - id, optional name
 * - Status (active/inactive)
 * - aktueller Sektor und Richtung
 * - Zeitstempel (created_at, last_seen)
 */
public class ShipRepository {

    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "ocean_explorer";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private Connection connection;

    public ShipRepository() {
        connect();
    }

    private void connect() {
        try {
            String url = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    DB_HOST, DB_PORT, DB_NAME);
            connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
            System.out.println("ShipRepository: Datenbankverbindung hergestellt: " + url);
        } catch (SQLException e) {
            System.err.println("ShipRepository: Fehler bei der Datenbankverbindung: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
     * Upsert für das aktive Schiff.
     *
     * @param shipId  ID des Schiffs (vom Ocean-Server)
     * @param name    Optionaler Anzeigename (kann null sein)
     * @param sector  aktueller Sektor (kann null sein)
     * @param dir     aktuelle Richtung (kann null sein)
     */
    public void upsertActiveShip(String shipId, String name, Vec2D sector, Vec2D dir) {
        ensureConnection();
        if (connection == null || shipId == null) {
            return;
        }

        String sql = """
                INSERT INTO ships (id, name, status, current_sector_x, current_sector_y, dir_x, dir_y)
                VALUES (?, ?, 'active', ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    status = 'active',
                    current_sector_x = VALUES(current_sector_x),
                    current_sector_y = VALUES(current_sector_y),
                    dir_x = VALUES(dir_x),
                    dir_y = VALUES(dir_y),
                    last_seen = CURRENT_TIMESTAMP
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, shipId);
            stmt.setString(2, name);

            if (sector != null) {
                stmt.setInt(3, sector.getX());
                stmt.setInt(4, sector.getY());
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
                stmt.setNull(4, java.sql.Types.INTEGER);
            }

            if (dir != null) {
                stmt.setInt(5, dir.getX());
                stmt.setInt(6, dir.getY());
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
                stmt.setNull(6, java.sql.Types.INTEGER);
            }

            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ShipRepository: Fehler beim Upsert des Schiffs: " + e.getMessage());
        }
    }

    /**
     * Liest einen Ship-Datensatz als JSON.
     *
     * @param shipId Ship-ID
     * @return JSONObject mit Status und Zeitstempeln oder null
     */
    public JSONObject getShip(String shipId) {
        ensureConnection();
        if (connection == null || shipId == null) {
            return null;
        }

        String sql = """
                SELECT id, name, status, current_sector_x, current_sector_y,
                       dir_x, dir_y, created_at, last_seen
                FROM ships
                WHERE id = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, shipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    JSONObject jo = new JSONObject();
                    jo.put("id", rs.getString("id"));
                    jo.put("name", rs.getString("name"));
                    jo.put("status", rs.getString("status"));
                    jo.put("current_sector_x", rs.getObject("current_sector_x"));
                    jo.put("current_sector_y", rs.getObject("current_sector_y"));
                    jo.put("dir_x", rs.getObject("dir_x"));
                    jo.put("dir_y", rs.getObject("dir_y"));

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp lastSeen = rs.getTimestamp("last_seen");
                    jo.put("created_at", createdAt != null ? createdAt.toString() : null);
                    jo.put("last_seen", lastSeen != null ? lastSeen.toString() : null);
                    return jo;
                }
            }
        } catch (SQLException e) {
            System.err.println("ShipRepository: Fehler beim Lesen des Schiffs: " + e.getMessage());
        }
        return null;
    }
}

