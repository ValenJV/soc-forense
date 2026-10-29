-- =====================================================================
-- SOC Forense: esquema de base de datos para MySQL 8
-- ---------------------------------------------------------------------
-- NO es obligatorio ejecutarlo: la aplicación crea el esquema y las
-- tablas sola al arrancar, y siembra los usuarios iniciales.
-- Sirve si prefiere crearlo a mano desde MySQL Workbench
-- (File > Open SQL Script > ejecutar con el rayo).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS soc_forense CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE soc_forense;

-- Esquema de la base de datos soc_forense.
-- Se ejecuta automaticamente al arrancar la aplicacion (ver Database.java).
-- Cada sentencia debe terminar en ';' y los comentarios empiezan con '--'.

-- Usuarios del sistema y su rol.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(60) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    full_name VARCHAR(160),
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME NULL,
    INDEX idx_users_role (role),
    INDEX idx_users_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Evidencias: archivos de log subidos, con hash para cadena de custodia.
CREATE TABLE IF NOT EXISTS evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    log_type VARCHAR(30) NOT NULL DEFAULT 'generic',
    file_size BIGINT NOT NULL DEFAULT 0,
    sha256 CHAR(64) NOT NULL,
    stored_path VARCHAR(500),
    upload_user_id BIGINT,
    upload_username VARCHAR(60),
    status VARCHAR(20) NOT NULL DEFAULT 'ingested',
    line_count INT NOT NULL DEFAULT 0,
    threat_count INT NOT NULL DEFAULT 0,
    critical_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parsed_at DATETIME NULL,
    analyzed_at DATETIME NULL,
    INDEX idx_evidence_status (status),
    INDEX idx_evidence_sha (sha256),
    INDEX idx_evidence_user (upload_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lineas de log ya normalizadas tras el parseo.
CREATE TABLE IF NOT EXISTS parsed_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    line_number INT NOT NULL DEFAULT 0,
    event_time DATETIME NULL,
    hostname VARCHAR(120),
    service VARCHAR(120),
    pid INT NULL,
    username VARCHAR(120),
    action VARCHAR(255),
    target VARCHAR(255),
    source_ip VARCHAR(64),
    raw_line TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parsed_evidence (evidence_id),
    INDEX idx_parsed_user (username),
    INDEX idx_parsed_ip (source_ip),
    CONSTRAINT fk_parsed_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidences (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Resultados del analisis forense (hallazgos de amenazas).
CREATE TABLE IF NOT EXISTS analysis_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    parsed_log_id BIGINT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'low',
    threat_type VARCHAR(120),
    threat_description TEXT,
    mitre_technique VARCHAR(120),
    indicators TEXT,
    recommendation TEXT,
    ai_explanation TEXT,
    confidence DOUBLE NOT NULL DEFAULT 0,
    raw_evidence TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_result_evidence (evidence_id),
    INDEX idx_result_severity (severity),
    CONSTRAINT fk_result_evidence FOREIGN KEY (evidence_id)
        REFERENCES evidences (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Incidentes: casos de respuesta abiertos a partir de hallazgos.
CREATE TABLE IF NOT EXISTS incidents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'medium',
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    evidence_id BIGINT NULL,
    summary TEXT,
    response_plan MEDIUMTEXT,
    created_by_user_id BIGINT,
    created_by_username VARCHAR(60),
    assigned_to VARCHAR(60),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_incident_status (status),
    INDEX idx_incident_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historial de conversacion con el agente de IA.
CREATE TABLE IF NOT EXISTS agent_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content MEDIUMTEXT,
    evidence_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent_user (user_id),
    INDEX idx_agent_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
