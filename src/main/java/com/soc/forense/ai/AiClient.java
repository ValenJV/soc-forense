package com.soc.forense.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.soc.forense.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente hacia un modelo de lenguaje servido con la API de OpenAI. Esta pensado
 * para LM Studio corriendo en local (http://localhost:1234/v1) pero funciona con
 * cualquier servicio compatible (Ollama, vLLM, proveedores en la nube), lo que
 * evita el bloqueo con un proveedor: cambiar de servidor es cambiar ai.base.url.
 *
 * En cada llamada aplica los parametros de inferencia del perfil (temperatura,
 * top-p, top-k, max tokens, penalizaciones), ajusta la peticion a la ventana de
 * contexto, limita cuantas consultas corren a la vez y registra metricas.
 * Reintenta con espera creciente si la primera llamada falla, y respeta el
 * tiempo maximo configurado.
 */
public class AiClient {

    private static final Logger LOG = Logger.getLogger(AiClient.class.getName());
    private static final Gson GSON = new Gson();
    private static final int MAX_ATTEMPTS = 3;

    /** Limite de consultas simultaneas al modelo: protege la RAM/GPU del equipo. */
    private static final int MAX_CONCURRENT = Math.max(1, AppConfig.getInt("ai.max.concurrent", 2));
    private static final Semaphore SLOTS = new Semaphore(MAX_CONCURRENT, true);

    private final HttpClient http;

    public AiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static int maxConcurrent() {
        return MAX_CONCURRENT;
    }

    public static int inUse() {
        return MAX_CONCURRENT - SLOTS.availablePermits();
    }

    /** Indica si el agente esta habilitado por configuracion. */
    public boolean isEnabled() {
        return AppConfig.aiEnabled();
    }

    /**
     * Comprueba rapido si el servidor de IA responde. Sirve para mostrar el
     * estado en el tablero sin bloquear la interfaz.
     */
    public boolean isReachable() {
        if (!isEnabled()) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AppConfig.aiBaseUrl() + "/models"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + AppConfig.aiApiKey())
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /** Compatibilidad: conversa con el perfil de chat usando otra temperatura. */
    public String chat(List<ChatMessage> messages, double temperature) throws AiUnavailableException {
        return chat(messages, InferenceParams.chat().withTemperature(temperature));
    }

    /**
     * Envia una conversacion y devuelve el texto de la respuesta del modelo.
     * Lanza AiUnavailableException si tras los reintentos no hay respuesta, para
     * que la capa de servicio pueda caer al analisis por reglas.
     */
    public String chat(List<ChatMessage> messages, InferenceParams params) throws AiUnavailableException {
        if (!isEnabled()) {
            throw new AiUnavailableException("El agente de IA está deshabilitado en la configuración");
        }

        ContextBudget.Fit fit = ContextBudget.fit(messages, params);
        if (fit.trimmed()) {
            LOG.info("IA " + params.getProfile() + ": peticion ajustada a la ventana de contexto ("
                    + fit.droppedMessages + " mensajes antiguos descartados"
                    + (fit.truncatedContext ? ", contexto recortado" : "") + ")");
        }

        boolean acquired;
        try {
            acquired = SLOTS.tryAcquire(AppConfig.getInt("ai.queue.wait.seconds", 30), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Consulta a la IA interrumpida", e);
        }
        if (!acquired) {
            AiMetrics.get().recordRejectedBusy();
            throw new AiUnavailableException("El modelo está ocupado con otras consultas (límite ai.max.concurrent="
                    + MAX_CONCURRENT + ")");
        }
        try {
            return send(fit);
        } finally {
            SLOTS.release();
        }
    }

    private String send(ContextBudget.Fit fit) throws AiUnavailableException {
        InferenceParams params = fit.params;
        JsonObject payload = new JsonObject();
        payload.addProperty("model", AppConfig.aiModel());
        params.applyTo(payload);
        payload.addProperty("stream", false);
        JsonArray msgs = new JsonArray();
        for (ChatMessage m : fit.messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.getRole());
            o.addProperty("content", m.getContent());
            msgs.add(o);
        }
        payload.add("messages", msgs);

        String body = GSON.toJson(payload);
        int timeout = AppConfig.aiTimeoutSeconds();
        Exception last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.nanoTime();
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(AppConfig.aiBaseUrl() + "/chat/completions"))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + AppConfig.aiApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code >= 200 && code < 300) {
                    Parsed p = extractContent(res.body(), fit.promptTokens);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    AiMetrics.get().recordSuccess(params.getProfile(), ms, p.promptTokens, p.completionTokens,
                            p.cutByMaxTokens, fit.trimmed());
                    return p.content;
                }
                last = new AiUnavailableException("El servidor de IA respondió con código " + code
                        + (res.body() == null ? "" : ": " + recortar(res.body(), 200)));
                LOG.warning("IA intento " + attempt + " codigo " + code);
                // Un 4xx (salvo 408 y 429) no mejora reintentando: por ejemplo, LM Studio sin modelo cargado.
                if (code >= 400 && code < 500 && code != 408 && code != 429) {
                    break;
                }
            } catch (HttpTimeoutException e) {
                // El modelo esta ocupado o es lento; repetir la misma peticion solo duplica la espera.
                last = e;
                LOG.warning("IA sin respuesta en " + timeout + " s; no se reintenta");
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                AiMetrics.get().recordFailure(params.getProfile(), fit.trimmed());
                throw new AiUnavailableException("Consulta a la IA interrumpida", e);
            } catch (AiUnavailableException e) {
                // Respuesta 200 pero con un formato que no se puede leer: reintentar no ayuda.
                AiMetrics.get().recordFailure(params.getProfile(), fit.trimmed());
                throw e;
            } catch (Exception e) {
                last = e;
                LOG.log(Level.WARNING, "IA intento " + attempt + " fallo: " + e.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                sleep(attempt * 1200L);
            }
        }
        AiMetrics.get().recordFailure(params.getProfile(), fit.trimmed());
        throw new AiUnavailableException("No se obtuvo respuesta del servidor de IA", last);
    }

    /** Quita el razonamiento interno que algunos modelos (Qwen3, DeepSeek-R1) anteponen a la respuesta. */
    static String stripReasoning(String text) {
        if (text == null) {
            return "";
        }
        String out = text.replaceAll("(?s)<think>.*?</think>", "");
        // Si el bloque quedo abierto (respuesta cortada), se descarta desde <think> hasta el final.
        int open = out.indexOf("<think>");
        if (open >= 0) {
            out = out.substring(0, open);
        }
        return out.trim();
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Respuesta ya interpretada, con el consumo de tokens que informa el servidor. */
    private static final class Parsed {
        String content;
        int promptTokens;
        int completionTokens;
        boolean cutByMaxTokens;
    }

    private Parsed extractContent(String responseBody, int estimatedPrompt) throws AiUnavailableException {
        try {
            JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiUnavailableException("La respuesta de IA no trae choices");
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject message = first.getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                throw new AiUnavailableException("La respuesta de IA no trae contenido");
            }
            if (message.get("content").isJsonNull()) {
                throw new AiUnavailableException("La respuesta de IA llegó sin texto");
            }
            String raw = message.get("content").getAsString();
            String content = stripReasoning(raw);
            if (content.isEmpty()) {
                throw new AiUnavailableException("La respuesta de IA llegó vacía");
            }
            Parsed p = new Parsed();
            p.content = content;
            p.cutByMaxTokens = first.has("finish_reason") && !first.get("finish_reason").isJsonNull()
                    && "length".equals(first.get("finish_reason").getAsString());
            // Si el servidor no informa el uso, se estima con la misma regla de la ventana de contexto.
            p.promptTokens = estimatedPrompt;
            p.completionTokens = ContextBudget.estimate(raw);
            if (root.has("usage") && root.get("usage").isJsonObject()) {
                JsonObject usage = root.getAsJsonObject("usage");
                if (usage.has("prompt_tokens") && !usage.get("prompt_tokens").isJsonNull()) {
                    p.promptTokens = usage.get("prompt_tokens").getAsInt();
                }
                if (usage.has("completion_tokens") && !usage.get("completion_tokens").isJsonNull()) {
                    p.completionTokens = usage.get("completion_tokens").getAsInt();
                }
            }
            if (p.cutByMaxTokens) {
                LOG.warning("IA: la respuesta se cortó al llegar a max_tokens; considere subir ai.<perfil>.max.tokens");
            }
            return p;
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("No se pudo interpretar la respuesta de IA", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
