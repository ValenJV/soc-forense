package com.soc.forense.ai;

import com.google.gson.JsonObject;
import com.soc.forense.config.AppConfig;

import java.util.Locale;

/**
 * Parametros de inferencia que controlan el comportamiento del modelo de
 * lenguaje en cada peticion. Cada caso de uso tiene su propio perfil, porque
 * no se le pide lo mismo al modelo cuando clasifica lineas en JSON que cuando
 * conversa o redacta un plan:
 *
 * - temperature: aleatoriedad. Baja = respuestas deterministas y repetibles.
 * - top_p: muestreo de nucleo. Solo considera las palabras cuya probabilidad
 *   acumulada alcanza este porcentaje.
 * - top_k: solo considera las k palabras mas probables en cada paso (0 = no se envia).
 * - max_tokens: longitud maxima de la respuesta (0 = sin limite explicito).
 * - frequency_penalty / presence_penalty: castigan repetir palabras o temas.
 *
 * Todos se leen de application.properties con la forma ai.&lt;perfil&gt;.&lt;parametro&gt;
 * (por ejemplo ai.chat.temperature) y se validan contra los rangos de la API.
 */
public final class InferenceParams {

    public static final String ANALISIS = "analisis";
    public static final String CHAT = "chat";
    public static final String PLAN = "plan";

    private final String profile;
    private final double temperature;
    private final double topP;
    private final int topK;
    private final int maxTokens;
    private final double frequencyPenalty;
    private final double presencePenalty;

    public InferenceParams(String profile, double temperature, double topP, int topK, int maxTokens,
                           double frequencyPenalty, double presencePenalty) {
        this.profile = profile == null ? CHAT : profile;
        this.temperature = clamp(temperature, 0.0, 2.0);
        this.topP = clamp(topP, 0.01, 1.0);
        this.topK = Math.max(0, topK);
        this.maxTokens = Math.max(0, maxTokens);
        this.frequencyPenalty = clamp(frequencyPenalty, -2.0, 2.0);
        this.presencePenalty = clamp(presencePenalty, -2.0, 2.0);
    }

    /**
     * Analisis de evidencias: salida JSON estricta y repetible. Las penalizaciones
     * van en 0 porque el JSON repite claves a proposito ("linea", "severidad"...)
     * y castigar esa repeticion rompe el formato.
     */
    public static InferenceParams analisis() {
        return forProfile(ANALISIS, 0.1, 0.9, 40, 3000, 0.0, 0.0);
    }

    /** Chat del agente: algo de variedad y sin repetir frases en respuestas largas. */
    public static InferenceParams chat() {
        return forProfile(CHAT, 0.3, 0.9, 40, 1024, 0.3, 0.2);
    }

    /** Plan de respuesta: redaccion mas libre pero con estructura fija. */
    public static InferenceParams plan() {
        return forProfile(PLAN, 0.4, 0.95, 50, 1500, 0.2, 0.1);
    }

    public static InferenceParams byName(String profile) {
        return switch (profile == null ? "" : profile.toLowerCase(Locale.ROOT)) {
            case ANALISIS -> analisis();
            case PLAN -> plan();
            default -> chat();
        };
    }

    private static InferenceParams forProfile(String p, double temp, double topP, int topK, int maxTokens,
                                              double freq, double pres) {
        String k = "ai." + p + ".";
        return new InferenceParams(p,
                AppConfig.getDouble(k + "temperature", temp),
                AppConfig.getDouble(k + "top.p", topP),
                AppConfig.getInt(k + "top.k", topK),
                AppConfig.getInt(k + "max.tokens", maxTokens),
                AppConfig.getDouble(k + "frequency.penalty", freq),
                AppConfig.getDouble(k + "presence.penalty", pres));
    }

    /** Copia con otra temperatura (compatibilidad con llamadas antiguas). */
    public InferenceParams withTemperature(double t) {
        return new InferenceParams(profile, t, topP, topK, maxTokens, frequencyPenalty, presencePenalty);
    }

    /** Copia con otro limite de salida; lo usa el control de ventana de contexto. */
    public InferenceParams withMaxTokens(int m) {
        return new InferenceParams(profile, temperature, topP, topK, m, frequencyPenalty, presencePenalty);
    }

    /** Escribe los parametros en el cuerpo de la peticion (formato API de OpenAI / LM Studio). */
    public void applyTo(JsonObject payload) {
        payload.addProperty("temperature", temperature);
        payload.addProperty("top_p", topP);
        if (topK > 0) {
            // top_k no es parte de la API oficial de OpenAI, pero LM Studio, Ollama y vLLM lo aceptan.
            payload.addProperty("top_k", topK);
        }
        if (maxTokens > 0) {
            payload.addProperty("max_tokens", maxTokens);
        }
        payload.addProperty("frequency_penalty", frequencyPenalty);
        payload.addProperty("presence_penalty", presencePenalty);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    public String getProfile() {
        return profile;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTopP() {
        return topP;
    }

    public int getTopK() {
        return topK;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    @Override
    public String toString() {
        return profile + "{temperature=" + temperature + ", top_p=" + topP + ", top_k=" + topK
                + ", max_tokens=" + maxTokens + ", frequency_penalty=" + frequencyPenalty
                + ", presence_penalty=" + presencePenalty + "}";
    }
}
