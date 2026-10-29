package com.soc.forense.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pool de conexiones y arranque del esquema. Al iniciar la app crea la base de
 * datos y las tablas si no existen, y siembra un usuario admin. La idea es que
 * baste con tener MySQL corriendo: no hay que ejecutar scripts a mano.
 */
public final class Database {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static volatile HikariDataSource dataSource;

    private Database() {
    }

    public static synchronized void init() {
        if (dataSource != null) {
            return;
        }
        ensureSchemaExists();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.dbUrl());
        cfg.setUsername(AppConfig.dbUser());
        cfg.setPassword(AppConfig.dbPassword());
        cfg.setDriverClassName(AppConfig.dbDriver());
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(10000);
        cfg.setPoolName("soc-forense-pool");
        cfg.setInitializationFailTimeout(-1);
        HikariDataSource ds = new HikariDataSource(cfg);
        dataSource = ds;
        try {
            createTables();
            seedAdmin();
        } catch (RuntimeException e) {
            // Si algo falla no dejamos un pool a medias: el proximo intento
            // vuelve a empezar desde cero.
            ds.close();
            dataSource = null;
            throw e;
        }
        LOG.info("Base de datos lista");
    }

    /** true si el pool esta creado y las tablas verificadas. */
    public static synchronized boolean isReady() {
        return dataSource != null && !dataSource.isClosed();
    }

    /** Crea el schema soc_forense conectandose al servidor sin base concreta. */
    private static void ensureSchemaExists() {
        String url = AppConfig.dbUrl();
        String schema = "soc_forense";
        int slash = url.lastIndexOf('/');
        int q = url.indexOf('?', slash);
        if (slash > 0) {
            schema = q > 0 ? url.substring(slash + 1, q) : url.substring(slash + 1);
        }
        String serverUrl = (slash > 0 ? url.substring(0, slash) : url) + "/"
                + (q > 0 ? url.substring(q) : "");

        try {
            Class.forName(AppConfig.dbDriver());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se encontró el driver JDBC " + AppConfig.dbDriver()
                    + ". Si abrió el proyecto sin conexión a internet, Maven no pudo descargarlo.", e);
        }

        try (Connection c = java.sql.DriverManager.getConnection(
                serverUrl, AppConfig.dbUser(), AppConfig.dbPassword());
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            LOG.info("Schema " + schema + " verificado");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "No se pudo crear el schema. Revisa que MySQL este activo y las "
                    + "credenciales en application.properties", e);
            throw new IllegalStateException("Fallo de conexion a MySQL: " + e.getMessage(), e);
        }
    }

    private static void createTables() {
        List<String> statements = readSchemaStatements();
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String sql : statements) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
            LOG.info("Tablas verificadas/creadas");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error creando tablas", e);
            throw new IllegalStateException("Error creando tablas: " + e.getMessage(), e);
        }
    }

    private static List<String> readSchemaStatements() {
        List<String> out = new ArrayList<>();
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                LOG.warning("schema.sql no encontrado en el classpath");
                return out;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                        continue;
                    }
                    sb.append(line).append('\n');
                    if (trimmed.endsWith(";")) {
                        String stmt = sb.toString().trim();
                        out.add(stmt.substring(0, stmt.length() - 1));
                        sb.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error leyendo schema.sql", e);
        }
        return out;
    }

    /** Siembra el admin por defecto solo si no hay usuarios. */
    private static void seedAdmin() {
        try (Connection c = getConnection()) {
            boolean empty;
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                empty = rs.getInt(1) == 0;
            }
            if (!empty) {
                return;
            }
            String adminPass = AppConfig.get("seed.admin.password", "Admin1234");
            String analystPass = AppConfig.get("seed.analyst.password", "Analista1234");
            String hashAdmin = BCrypt.hashpw(adminPass, BCrypt.gensalt(10));
            String hashAnalyst = BCrypt.hashpw(analystPass, BCrypt.gensalt(10));
            try (var ps = c.prepareStatement(
                    "INSERT INTO users(username,email,full_name,password_hash,role,active) VALUES(?,?,?,?,?,1)")) {
                ps.setString(1, "admin");
                ps.setString(2, "admin@soc.local");
                ps.setString(3, "Administrador SOC");
                ps.setString(4, hashAdmin);
                ps.setString(5, "ADMIN");
                ps.executeUpdate();

                ps.setString(1, "analista");
                ps.setString(2, "analista@soc.local");
                ps.setString(3, "Analista de Turno");
                ps.setString(4, hashAnalyst);
                ps.setString(5, "ANALYST");
                ps.executeUpdate();
            }
            LOG.info("Usuarios semilla creados: admin y analista. Las contrasenas iniciales estan en application.properties");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo sembrar el admin", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            init();
        }
        return dataSource.getConnection();
    }

    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }
}
