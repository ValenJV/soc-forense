package com.soc.forense.ai;

import com.soc.forense.config.AppConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metricas operativas del agente de IA desde el ultimo arranque: rendimiento
 * (latencia, tasa de exito), consumo (tokens y costo estimado), precision
 * (concordancia entre la IA y el motor de reglas) y uso de recursos
 * (peticiones rechazadas por saturacion, recortes de contexto).
 *
 * Se guardan en memoria a proposito: no agregan tablas ni carga a MySQL y se
 * reinician con el servidor. Son seguras para varios hilos.
 */
public final class AiMetrics {

    private static final AiMetrics INSTANCE = new AiMetrics();
    private static final int WINDOW = 200;

    private final Map<String, Stats> byProfile = new LinkedHashMap<>();
    private long rejectedBusy;
    private long rulesConfirmed;
    private long aiAdded;
    private long analysesWithAi;
    private final long startedAt = System.currentTimeMillis();

    private AiMetrics() {
        for (String p : new String[]{InferenceParams.ANALISIS, InferenceParams.CHAT, InferenceParams.PLAN}) {
            byProfile.put(p, new Stats());
        }
    }

    public static AiMetrics get() {
        return INSTANCE;
    }

    private static final class Stats {
        long calls;
        long ok;
        long failed;
        long truncatedOutput;
        long contextTrims;
        long promptTokens;
        long completionTokens;
        long totalLatencyMs;
        long maxLatencyMs;
        final long[] recent = new long[WINDOW];
        int recentCount;
        int recentPos;
    }

    private Stats stats(String profile) {
        return byProfile.computeIfAbsent(profile == null ? InferenceParams.CHAT : profile, k -> new Stats());
    }

    public synchronized void recordSuccess(String profile, long latencyMs, int promptTokens, int completionTokens,
                                           boolean cutByMaxTokens, boolean contextTrimmed) {
        Stats s = stats(profile);
        s.calls++;
        s.ok++;
        s.promptTokens += promptTokens;
        s.completionTokens += completionTokens;
        s.totalLatencyMs += latencyMs;
        s.maxLatencyMs = Math.max(s.maxLatencyMs, latencyMs);
        s.recent[s.recentPos] = latencyMs;
        s.recentPos = (s.recentPos + 1) % WINDOW;
        s.recentCount = Math.min(WINDOW, s.recentCount + 1);
        if (cutByMaxTokens) {
            s.truncatedOutput++;
        }
        if (contextTrimmed) {
            s.contextTrims++;
        }
    }

    public synchronized void recordFailure(String profile, boolean contextTrimmed) {
        Stats s = stats(profile);
        s.calls++;
        s.failed++;
        if (contextTrimmed) {
            s.contextTrims++;
        }
    }

    public synchronized void recordRejectedBusy() {
        rejectedBusy++;
    }

    /** Resultado de fusionar IA y reglas en un analisis de evidencia. */
    public synchronized void recordAgreement(int confirmed, int added) {
        analysesWithAi++;
        rulesConfirmed += Math.max(0, confirmed);
        aiAdded += Math.max(0, added);
    }

    /** Costo por millon de tokens (USD). Con un modelo local es 0; con un proveedor en la nube, su tarifa. */
    public static double inputPricePerMillion() {
        return AppConfig.getDouble("ai.cost.input.per.million", 0.0);
    }

    public static double outputPricePerMillion() {
        return AppConfig.getDouble("ai.cost.output.per.million", 0.0);
    }

    /** Foto de las metricas lista para la vista. */
    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        long totalIn = 0;
        long totalOut = 0;
        long totalCalls = 0;
        long totalOk = 0;
        for (Map.Entry<String, Stats> e : byProfile.entrySet()) {
            Stats s = e.getValue();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("profile", e.getKey());
            r.put("calls", s.calls);
            r.put("ok", s.ok);
            r.put("failed", s.failed);
            r.put("successRate", s.calls == 0 ? "—" : pct(s.ok, s.calls));
            r.put("avgLatency", s.ok == 0 ? "—" : ms(s.totalLatencyMs / s.ok));
            r.put("p95Latency", s.recentCount == 0 ? "—" : ms(p95(s)));
            r.put("maxLatency", s.ok == 0 ? "—" : ms(s.maxLatencyMs));
            r.put("promptTokens", s.promptTokens);
            r.put("completionTokens", s.completionTokens);
            r.put("truncatedOutput", s.truncatedOutput);
            r.put("contextTrims", s.contextTrims);
            rows.add(r);
            totalIn += s.promptTokens;
            totalOut += s.completionTokens;
            totalCalls += s.calls;
            totalOk += s.ok;
        }
        m.put("profiles", rows);
        m.put("totalCalls", totalCalls);
        m.put("successRate", totalCalls == 0 ? "—" : pct(totalOk, totalCalls));
        m.put("promptTokens", totalIn);
        m.put("completionTokens", totalOut);
        double cost = totalIn / 1_000_000.0 * inputPricePerMillion() + totalOut / 1_000_000.0 * outputPricePerMillion();
        m.put("estimatedCost", String.format(java.util.Locale.ROOT, "%.4f", cost));
        m.put("rejectedBusy", rejectedBusy);
        m.put("analysesWithAi", analysesWithAi);
        m.put("rulesConfirmed", rulesConfirmed);
        m.put("aiAdded", aiAdded);
        long total = rulesConfirmed + aiAdded;
        m.put("agreementRate", total == 0 ? "—" : pct(rulesConfirmed, total));
        m.put("uptimeMinutes", (System.currentTimeMillis() - startedAt) / 60000);
        return m;
    }

    private static long p95(Stats s) {
        long[] copy = Arrays.copyOf(s.recent, s.recentCount);
        Arrays.sort(copy);
        int idx = (int) Math.ceil(0.95 * copy.length) - 1;
        return copy[Math.max(0, idx)];
    }

    private static String pct(long part, long total) {
        return Math.round(part * 100.0 / total) + " %";
    }

    private static String ms(long v) {
        return v >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f s", v / 1000.0) : v + " ms";
    }
}
