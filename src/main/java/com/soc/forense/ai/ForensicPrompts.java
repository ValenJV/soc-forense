package com.soc.forense.ai;

/**
 * Instrucciones que definen la personalidad y el trabajo del agente. Estan en
 * un solo sitio para poder afinarlas sin tocar la logica.
 */
public final class ForensicPrompts {

    private ForensicPrompts() {
    }

    /** Rol base del agente para el chat y el analisis. */
    public static final String SYSTEM_ANALISTA =
            "Eres un analista forense de logs y respuesta a incidentes de un SOC. "
            + "Hablas español claro y directo. Explicas el porqué de cada hallazgo, "
            + "citas la evidencia concreta (líneas, usuarios, IPs) y propones acciones "
            + "de contención realistas. Cuando reconozcas una técnica, la referencias "
            + "con su identificador MITRE ATT&CK (por ejemplo T1110 para fuerza bruta). "
            + "No inventas datos: si algo no está en la evidencia, lo dices. "
            + "Priorizas por riesgo real, no por ruido.";

    /**
     * Pide al modelo que clasifique un lote de lineas y devuelva JSON estricto.
     * El {LOG} se reemplaza por las lineas numeradas.
     */
    public static String analisisLote(String logType, String numberedLines) {
        return "Analiza estas líneas de un log de tipo '" + logType + "'. "
            + "Devuelve ÚNICAMENTE un arreglo JSON, sin texto adicional ni bloques de código. "
            + "Cada elemento representa un hallazgo de seguridad y tiene exactamente estas claves:\n"
            + "  \"linea\": número de línea (entero),\n"
            + "  \"severidad\": uno de low, medium, high, critical,\n"
            + "  \"tipo_amenaza\": texto corto (ej: fuerza bruta SSH, escalada de privilegios),\n"
            + "  \"descripcion\": qué ocurre y por qué importa,\n"
            + "  \"mitre\": identificador ATT&CK si aplica (ej: T1110), o cadena vacia,\n"
            + "  \"indicadores\": arreglo de textos (IPs, usuarios, procesos observados),\n"
            + "  \"recomendacion\": acción concreta de contención,\n"
            + "  \"confianza\": número entre 0 y 1.\n"
            + "Solo incluye líneas que representen actividad sospechosa o relevante. "
            + "Si ninguna línea es relevante, devuelve un arreglo vacío [].\n\n"
            + "LINEAS:\n" + numberedLines;
    }

    /**
     * Genera un plan de respuesta a incidentes a partir de un resumen de hallazgos.
     */
    public static String planRespuesta(String contextoHallazgos) {
        return "Con base en estos hallazgos, redacta un plan de respuesta a incidentes "
            + "en español, ordenado y accionable. Usa estos apartados con estos títulos exactos:\n"
            + "1. Resumen del incidente\n"
            + "2. Contención inmediata\n"
            + "3. Erradicación\n"
            + "4. Recuperación\n"
            + "5. Lecciones y prevención\n"
            + "Se concreto: nombra usuarios, IPs y servicios cuando aparezcan en la evidencia. "
            + "No uses relleno.\n\nHALLAZGOS:\n" + contextoHallazgos;
    }
}
