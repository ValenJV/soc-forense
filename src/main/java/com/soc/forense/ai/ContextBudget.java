package com.soc.forense.ai;

import com.soc.forense.config.AppConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Control de la ventana de contexto (context window). El modelo solo puede
 * procesar un numero maximo de tokens entre entrada y salida; si la peticion
 * lo supera, el servidor la rechaza o corta la respuesta. Antes de cada
 * llamada se estima el tamano y, si no cabe, se recorta en este orden:
 *
 * 1. se descartan los mensajes mas antiguos del historial del chat,
 * 2. se acorta el mensaje de contexto mas largo (nunca la instruccion base
 *    ni la pregunta actual),
 * 3. se reduce max_tokens hasta un minimo util.
 *
 * La estimacion usa ~4 caracteres por token, una aproximacion habitual para
 * textos en español e ingles; el margen de seguridad absorbe el error.
 */
public final class ContextBudget {

    static final int CHARS_PER_TOKEN = 4;
    static final int PER_MESSAGE_OVERHEAD = 4;
    static final int MIN_OUTPUT_TOKENS = 256;

    private ContextBudget() {
    }

    /** Resultado del ajuste: mensajes que se enviaran y datos para las metricas. */
    public static final class Fit {
        public final List<ChatMessage> messages;
        public final InferenceParams params;
        public final int promptTokens;
        public final int droppedMessages;
        public final boolean truncatedContext;

        Fit(List<ChatMessage> messages, InferenceParams params, int promptTokens, int dropped, boolean truncated) {
            this.messages = messages;
            this.params = params;
            this.promptTokens = promptTokens;
            this.droppedMessages = dropped;
            this.truncatedContext = truncated;
        }

        public boolean trimmed() {
            return droppedMessages > 0 || truncatedContext;
        }
    }

    public static int contextWindow() {
        return Math.max(1024, AppConfig.getInt("ai.context.window", 8192));
    }

    public static int safetyMargin() {
        return Math.max(0, AppConfig.getInt("ai.context.margin", 256));
    }

    public static int estimate(String text) {
        return text == null ? 0 : (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    public static int estimate(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage m : messages) {
            total += estimate(m.getContent()) + PER_MESSAGE_OVERHEAD;
        }
        return total;
    }

    public static Fit fit(List<ChatMessage> input, InferenceParams params) {
        return fit(input, params, contextWindow(), safetyMargin());
    }

    static Fit fit(List<ChatMessage> input, InferenceParams params, int window, int margin) {
        List<ChatMessage> msgs = new ArrayList<>(input);
        int reserved = params.getMaxTokens() > 0 ? params.getMaxTokens() : MIN_OUTPUT_TOKENS;
        int budget = window - margin - reserved;
        int dropped = 0;
        boolean truncated = false;

        // 1) Historial: se quitan los turnos mas antiguos (ni system ni el ultimo mensaje).
        while (estimate(msgs) > budget) {
            int idx = oldestHistoryIndex(msgs);
            if (idx < 0) {
                break;
            }
            msgs.remove(idx);
            dropped++;
        }

        // 2) Contexto: se acorta el mensaje system mas largo que no sea la instruccion base.
        if (estimate(msgs) > budget) {
            int idx = longestContextIndex(msgs);
            if (idx >= 0) {
                ChatMessage m = msgs.get(idx);
                int exceso = estimate(msgs) - budget;
                int keepChars = Math.max(200, m.getContent().length() - exceso * CHARS_PER_TOKEN);
                if (keepChars < m.getContent().length()) {
                    msgs.set(idx, new ChatMessage(m.getRole(), m.getContent().substring(0, keepChars)
                            + "\n[... contexto recortado para respetar la ventana de contexto del modelo ...]"));
                    truncated = true;
                }
            }
        }

        // 3) Salida: si aun no cabe, se reduce max_tokens (sin bajar del minimo util).
        int prompt = estimate(msgs);
        InferenceParams out = params;
        int available = window - margin - prompt;
        if (params.getMaxTokens() > 0 && available < params.getMaxTokens()) {
            out = params.withMaxTokens(Math.max(MIN_OUTPUT_TOKENS, available));
        }
        return new Fit(msgs, out, prompt, dropped, truncated);
    }

    private static int oldestHistoryIndex(List<ChatMessage> msgs) {
        for (int i = 0; i < msgs.size() - 1; i++) {
            if (!"system".equals(msgs.get(i).getRole())) {
                return i;
            }
        }
        return -1;
    }

    private static int longestContextIndex(List<ChatMessage> msgs) {
        int best = -1;
        int bestLen = 0;
        boolean firstSystemSeen = false;
        for (int i = 0; i < msgs.size() - 1; i++) {
            ChatMessage m = msgs.get(i);
            if (!"system".equals(m.getRole())) {
                continue;
            }
            if (!firstSystemSeen) {
                firstSystemSeen = true; // instruccion base del agente: no se toca
                continue;
            }
            int len = m.getContent() == null ? 0 : m.getContent().length();
            if (len > bestLen) {
                best = i;
                bestLen = len;
            }
        }
        return best;
    }
}
