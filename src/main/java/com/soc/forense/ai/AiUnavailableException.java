package com.soc.forense.ai;

/**
 * Se lanza cuando el agente de IA no responde o esta deshabilitado. La capa de
 * servicio la captura para seguir funcionando con el analisis por reglas.
 */
public class AiUnavailableException extends Exception {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
