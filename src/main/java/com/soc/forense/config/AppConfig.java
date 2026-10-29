package com.soc.forense.config;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuracion central. Lee application.properties del classpath y deja que
 * variables de entorno del mismo nombre (en mayusculas, con puntos como
 * guiones bajos) las sobreescriban. Asi el proyecto arranca sin tocar nada,
 * pero se puede reconfigurar sin recompilar.
 */
public final class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                PROPS.load(in);
                LOG.info("application.properties cargado");
            } else {
                LOG.warning("No se encontro application.properties; se usan valores por defecto");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error leyendo application.properties", e);
        }
    }

    private AppConfig() {
    }

    public static String get(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)).trim());
    }

    // Base de datos
    public static String dbUrl() {
        return get("db.url",
                "jdbc:mysql://localhost:3306/soc_forense?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8");
    }

    /**
     * Clase del driver JDBC. Si no se indica, se deduce de la URL:
     * jdbc:mariadb usa el driver de MariaDB y cualquier otra el de MySQL.
     */
    public static String dbDriver() {
        String explicit = get("db.driver", "");
        if (!explicit.isBlank()) {
            return explicit.trim();
        }
        return dbUrl().startsWith("jdbc:mariadb:") ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver";
    }

    public static String dbUser() {
        return get("db.user", "root");
    }

    public static String dbPassword() {
        return get("db.password", "root");
    }

    // Almacenamiento de evidencias
    public static String evidenceDir() {
        return get("evidence.dir", System.getProperty("java.io.tmpdir") + "/soc-forense-evidence");
    }

    public static long maxUploadBytes() {
        return getInt("upload.max.mb", 25) * 1024L * 1024L;
    }

    // Agente de IA
    public static String aiBaseUrl() {
        return get("ai.base.url", "http://localhost:1234/v1");
    }

    public static String aiModel() {
        return get("ai.model", "local-model");
    }

    public static String aiApiKey() {
        return get("ai.api.key", "lm-studio");
    }

    public static int aiTimeoutSeconds() {
        return getInt("ai.timeout.seconds", 90);
    }

    public static boolean aiEnabled() {
        return getBool("ai.enabled", true);
    }
}
