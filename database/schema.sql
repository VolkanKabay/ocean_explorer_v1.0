-- Ocean Explorer Database Schema
-- MySQL/MariaDB on port 3306

-- Datenbank erstellen (falls noch nicht vorhanden)
CREATE DATABASE IF NOT EXISTS ocean_explorer
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ocean_explorer;

CREATE TABLE IF NOT EXISTS submarines (
    id VARCHAR(100) PRIMARY KEY,
    ship_id VARCHAR(100) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status ENUM('active', 'terminated') DEFAULT 'active',
    INDEX idx_status (status),
    INDEX idx_ship_id (ship_id)
);

-- Tabelle für Submarine-Positionen (Tracking)
CREATE TABLE IF NOT EXISTS submarine_positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id VARCHAR(100) NOT NULL,
    pos_x DOUBLE NOT NULL,
    pos_y DOUBLE NOT NULL,
    pos_z DOUBLE NOT NULL,
    dir_x DOUBLE,
    dir_y DOUBLE,
    dir_z DOUBLE,
    depth INT,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submarine_id) REFERENCES submarines(id) ON DELETE CASCADE,
    INDEX idx_submarine_id (submarine_id),
    INDEX idx_recorded_at (recorded_at)
);

-- Tabelle für Messpunkte (Measure-Events)
CREATE TABLE IF NOT EXISTS measurements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id VARCHAR(100) NOT NULL,
    vec_x DOUBLE NOT NULL,
    vec_y DOUBLE NOT NULL,
    vec_z DOUBLE NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submarine_id) REFERENCES submarines(id) ON DELETE CASCADE,
    INDEX idx_submarine_id (submarine_id),
    INDEX idx_recorded_at (recorded_at)
);

-- Tabelle für Bilder (Picture-Daten)
CREATE TABLE IF NOT EXISTS submarine_pictures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id VARCHAR(100) NOT NULL,
    picture_hex LONGTEXT NOT NULL,
    file_path VARCHAR(500),
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submarine_id) REFERENCES submarines(id) ON DELETE CASCADE,
    INDEX idx_submarine_id (submarine_id),
    INDEX idx_captured_at (captured_at)
);

-- Tabelle für Crash-Ereignisse
CREATE TABLE IF NOT EXISTS submarine_crashes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id VARCHAR(100) NOT NULL,
    message VARCHAR(500),
    sector_x INT,
    sector_y INT,
    sunk_pos_x DOUBLE,
    sunk_pos_y DOUBLE,
    sunk_pos_z DOUBLE,
    crashed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submarine_id) REFERENCES submarines(id) ON DELETE CASCADE,
    INDEX idx_submarine_id (submarine_id)
);

-- Tabelle für Arise-Ereignisse (Auftauchen)
CREATE TABLE IF NOT EXISTS submarine_arises (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id VARCHAR(100) NOT NULL,
    arise_pos_x DOUBLE,
    arise_pos_y DOUBLE,
    arise_pos_z DOUBLE,
    arisen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (submarine_id) REFERENCES submarines(id) ON DELETE CASCADE,
    INDEX idx_submarine_id (submarine_id)
);

-- Tabelle für Ship-Scan-Ergebnisse (scan -> depth/stddev)
CREATE TABLE IF NOT EXISTS ship_scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id VARCHAR(100) NOT NULL,
    sector_x INT,
    sector_y INT,
    depth INT,
    stddev DOUBLE,
    scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ship_id (ship_id),
    INDEX idx_scanned_at (scanned_at)
);

-- View für aktuelle Submarine-Übersicht (ohne Ships/Measurements)
CREATE OR REPLACE VIEW submarine_overview AS
SELECT 
    s.id,
    s.status,
    s.created_at,
    s.last_seen,
    p.pos_x,
    p.pos_y,
    p.pos_z,
    p.depth
FROM submarines s
LEFT JOIN submarine_positions p ON s.id = p.submarine_id
WHERE p.id = (
    SELECT MAX(id) FROM submarine_positions WHERE submarine_id = s.id
) OR p.id IS NULL;
