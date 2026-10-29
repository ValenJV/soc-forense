package com.soc.forense.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Funciones expuestas a las JSP mediante /WEB-INF/soc.tld. JSTL no sabe
 * formatear java.time, y las etiquetas en español se resuelven aqui para no
 * llenar las vistas de condicionales.
 */
public final class Fmt {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA_SEG = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private Fmt() {
    }

    public static String fecha(LocalDateTime t) {
        return t == null ? "—" : t.format(FECHA);
    }

    public static String fechaSeg(LocalDateTime t) {
        return t == null ? "—" : t.format(FECHA_SEG);
    }

    /** "hace 5 min", "hace 3 h", "hace 2 d". */
    public static String hace(LocalDateTime t) {
        if (t == null) {
            return "—";
        }
        long s = Duration.between(t, LocalDateTime.now()).getSeconds();
        if (s < 0) {
            return fecha(t);
        }
        if (s < 60) {
            return "hace segundos";
        }
        if (s < 3600) {
            return "hace " + (s / 60) + " min";
        }
        if (s < 86400) {
            return "hace " + (s / 3600) + " h";
        }
        if (s < 86400 * 30) {
            return "hace " + (s / 86400) + " d";
        }
        return fecha(t);
    }

    public static String severidad(String s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case "critical" -> "Crítica";
            case "high" -> "Alta";
            case "medium" -> "Media";
            case "low" -> "Baja";
            default -> s;
        };
    }

    public static String estadoEvidencia(String s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case "ingested" -> "Ingerida";
            case "parsed" -> "Parseada";
            case "analyzed" -> "Analizada";
            case "error" -> "Error";
            default -> s;
        };
    }

    public static String estadoIncidente(String s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case "open" -> "Abierto";
            case "investigating" -> "Investigando";
            case "contained" -> "Contenido";
            case "closed" -> "Cerrado";
            default -> s;
        };
    }

    public static String rol(String s) {
        if (s == null) {
            return "—";
        }
        return switch (s) {
            case "ADMIN" -> "Administrador";
            case "ANALYST" -> "Analista";
            case "VIEWER" -> "Observador";
            default -> s;
        };
    }

    /** Iniciales para el avatar del rail lateral. */
    public static String iniciales(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }

    /** "1 hallazgo", "3 hallazgos". */
    public static String cant(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    public static String recortar(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
