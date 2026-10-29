package com.soc.forense.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.soc.forense.ai.AiClient;
import com.soc.forense.ai.AiUnavailableException;
import com.soc.forense.ai.ChatMessage;
import com.soc.forense.ai.ForensicPrompts;
import com.soc.forense.ai.AiMetrics;
import com.soc.forense.ai.InferenceParams;
import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.ParsedLogDao;
import com.soc.forense.model.AnalysisResult;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.ParsedLog;
import com.soc.forense.util.Fmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El cerebro del sistema. Toma los registros ya parseados y decide cuales son
 * amenazas. Primero lo intenta con la IA (LM Studio), que da explicaciones ricas
 * y mapeo MITRE. Si la IA no responde, no se queda ciego: cae a un motor de
 * reglas que reconoce los patrones clasicos (fuerza bruta, escalada, cuentas
 * nuevas, barridos web). Asi el sistema siempre entrega hallazgos.
 */
public class AnalysisService {

    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final ParsedLogDao parsedLogDao = new ParsedLogDao();
    private final AnalysisResultDao analysisResultDao = new AnalysisResultDao();
    private final AiClient aiClient = new AiClient();
    private final Gson gson = new Gson();

    private static final int CHUNK = 60;

    /** Resultado del analisis para informar a la interfaz que motor se uso. */
    public static class Outcome {
        public int threats;
        public int critical;
        public boolean usedAi;
        public String note;
    }

    public Outcome analyze(Evidence ev) {
        List<ParsedLog> logs = parsedLogDao.findByEvidence(ev.getId());
        if (logs.isEmpty()) {
            evidenceDao.updateStatus(ev.getId(), "error", "No hay líneas parseadas para analizar");
            throw new RuntimeException("Primero hay que parsear la evidencia");
        }

        // Empezamos limpio para poder re-analizar sin duplicar hallazgos.
        analysisResultDao.deleteByEvidence(ev.getId());
        Outcome outcome = new Outcome();

        // 1) Las reglas recorren el archivo completo y ven patrones agregados (conteos por IP, secuencias).
        List<AnalysisResult> findings = analyzeWithRules(ev, logs);

        // 2) La IA lee las lineas con contexto y aporta explicacion, tecnicas y hallazgos que las reglas no cubren.
        String aiNote = null;
        if (aiClient.isEnabled() && aiClient.isReachable()) {
            List<ParsedLog> selection = selectForAi(logs);
            AiPass pass = analyzeWithAi(ev, selection);
            int[] merged = merge(findings, pass.results);
            int added = merged[0];
            int confirmed = merged[1];
            if (pass.chunksOk > 0) {
                AiMetrics.get().recordAgreement(confirmed, added);
            }
            if (pass.chunksOk == 0) {
                aiNote = "La IA no respondió; se usaron solo las reglas.";
                outcome.usedAi = false;
            } else {
                outcome.usedAi = true;
                StringBuilder n = new StringBuilder();
                if (confirmed > 0 && added > 0) {
                    n.append("La IA confirmó ").append(Fmt.cant(confirmed, "hallazgo", "hallazgos"))
                            .append(" de las reglas y agregó ").append(added).append('.');
                } else if (confirmed > 0) {
                    n.append("La IA confirmó ").append(Fmt.cant(confirmed, "hallazgo", "hallazgos")).append(" de las reglas.");
                } else if (added > 0) {
                    n.append("La IA agregó ").append(Fmt.cant(added, "hallazgo", "hallazgos")).append(" a los de las reglas.");
                } else {
                    n.append("La IA revisó el log y no encontró nada más que las reglas.");
                }
                if (selection.size() < logs.size()) {
                    n.append(" Leyó ").append(selection.size()).append(" líneas preseleccionadas de ").append(logs.size()).append('.');
                }
                if (pass.chunksFailed > 0) {
                    n.append(' ').append(Fmt.cant(pass.chunksFailed, "bloque quedó", "bloques quedaron")).append(" sin respuesta.");
                }
                aiNote = n.toString();
            }
        } else {
            aiNote = aiClient.isEnabled()
                    ? "La IA no estaba disponible; se aplicó el motor de reglas."
                    : "IA desactivada en la configuración; se aplicó el motor de reglas.";
        }

        findings.sort((x, y) -> Integer.compare(IncidentService.rank(y.getSeverity()), IncidentService.rank(x.getSeverity())));
        if (!findings.isEmpty()) {
            analysisResultDao.insertBatch(findings);
        }
        int criticalCount = 0;
        for (AnalysisResult r : findings) {
            if ("critical".equalsIgnoreCase(r.getSeverity())) {
                criticalCount++;
            }
        }
        evidenceDao.markAnalyzed(ev.getId(), findings.size(), criticalCount);
        outcome.threats = findings.size();
        outcome.critical = criticalCount;
        outcome.note = aiNote;
        return outcome;
    }

    /**
     * Si el hallazgo de la IA apunta a una linea que ya marco una regla, se fusionan:
     * gana la severidad mas alta, la explicacion de la IA y la union de indicadores.
     * Devuelve {hallazgos nuevos de la IA, hallazgos de reglas que la IA confirmo}.
     */
    private int[] merge(List<AnalysisResult> base, List<AnalysisResult> fromAi) {
        int added = 0;
        java.util.Set<AnalysisResult> confirmed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (AnalysisResult ai : fromAi) {
            AnalysisResult same = null;
            if (ai.getParsedLogId() != null) {
                for (AnalysisResult r : base) {
                    if (ai.getParsedLogId().equals(r.getParsedLogId())) {
                        same = r;
                        break;
                    }
                }
            }
            if (same == null) {
                base.add(ai);
                added++;
                continue;
            }
            if (IncidentService.rank(ai.getSeverity()) > IncidentService.rank(same.getSeverity())) {
                same.setSeverity(ai.getSeverity());
            }
            if (ai.getMitreTechnique() != null && !ai.getMitreTechnique().isBlank()) {
                same.setMitreTechnique(ai.getMitreTechnique());
            }
            if (ai.getRecommendation() != null && !ai.getRecommendation().isBlank()) {
                same.setRecommendation(ai.getRecommendation());
            }
            confirmed.add(same);
            same.setAiExplanation("Confirmado por la IA: " + nz(ai.getThreatDescription()));
            same.setConfidence(Math.max(same.getConfidence(), ai.getConfidence()));
            java.util.LinkedHashSet<String> iocs = new java.util.LinkedHashSet<>(same.getIndicatorList());
            iocs.addAll(ai.getIndicatorList());
            same.setIndicators(gson.toJson(new ArrayList<>(iocs)));
        }
        return new int[]{added, confirmed.size()};
    }

    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)fail|invalid|denied|refused|accepted|sudo|su:|useradd|usermod|passwd|authorized_keys|"
            + "error|segfault|root|wget|curl|chmod|crontab|nc |bash|powershell|\\s[45]\\d\\d\\s|select|union|script|\\.\\./");

    /** En logs grandes solo se envian al modelo las lineas con senales; las reglas ya cubren el resto. */
    private List<ParsedLog> selectForAi(List<ParsedLog> logs) {
        int max = com.soc.forense.config.AppConfig.getInt("ai.max.lines", 480);
        if (logs.size() <= max) {
            return logs;
        }
        List<ParsedLog> out = new ArrayList<>();
        for (ParsedLog p : logs) {
            if (p.getRawLine() != null && SUSPICIOUS.matcher(p.getRawLine()).find()) {
                out.add(p);
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ---------- via IA ----------

    /** Resultado de la pasada de IA, que puede ser parcial si algun bloque falla. */
    private static class AiPass {
        List<AnalysisResult> results = new ArrayList<>();
        int chunksOk;
        int chunksFailed;
    }

    private AiPass analyzeWithAi(Evidence ev, List<ParsedLog> logs) {
        AiPass pass = new AiPass();
        Map<Integer, ParsedLog> byLine = new HashMap<>();
        for (ParsedLog p : logs) {
            byLine.put(p.getLineNumber(), p);
        }

        for (int i = 0; i < logs.size(); i += CHUNK) {
            List<ParsedLog> slice = logs.subList(i, Math.min(i + CHUNK, logs.size()));
            StringBuilder numbered = new StringBuilder();
            for (ParsedLog p : slice) {
                numbered.append(p.getLineNumber()).append(": ").append(p.getRawLine()).append('\n');
            }
            List<ChatMessage> messages = List.of(
                    ChatMessage.system(ForensicPrompts.SYSTEM_ANALISTA),
                    ChatMessage.user(ForensicPrompts.analisisLote(ev.getLogType(), numbered.toString())));
            try {
                String reply = aiClient.chat(messages, InferenceParams.analisis());
                pass.results.addAll(parseAiJson(ev, reply, byLine));
                pass.chunksOk++;
            } catch (AiUnavailableException e) {
                pass.chunksFailed++;
                // Dos bloques seguidos sin respuesta: el servidor se cayo, no tiene sentido seguir esperando.
                if (pass.chunksOk == 0 && pass.chunksFailed >= 2) {
                    break;
                }
            }
        }
        return pass;
    }

    private List<AnalysisResult> parseAiJson(Evidence ev, String reply, Map<Integer, ParsedLog> byLine) {
        List<AnalysisResult> out = new ArrayList<>();
        String json = extractJsonArray(reply);
        if (json == null) {
            return out;
        }
        JsonArray arr;
        try {
            arr = JsonParser.parseString(json).getAsJsonArray();
        } catch (RuntimeException e) {
            return out;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            AnalysisResult r = new AnalysisResult();
            r.setEvidenceId(ev.getId());
            int linea = asInt(o, "linea", -1);
            if (linea > 0 && byLine.containsKey(linea)) {
                ParsedLog p = byLine.get(linea);
                r.setParsedLogId(p.getId());
                r.setRawEvidence(p.getRawLine());
            }
            r.setSeverity(normalizeSeverity(asString(o, "severidad", "medium")));
            r.setThreatType(asString(o, "tipo_amenaza", "Actividad sospechosa"));
            r.setThreatDescription(asString(o, "descripcion", ""));
            r.setMitreTechnique(asString(o, "mitre", ""));
            r.setRecommendation(asString(o, "recomendacion", ""));
            r.setAiExplanation("Hallazgo de la IA local.");
            r.setConfidence(asDouble(o, "confianza", 0.6));
            if (o.has("indicadores") && o.get("indicadores").isJsonArray()) {
                r.setIndicators(gson.toJson(o.get("indicadores")));
            }
            out.add(r);
        }
        return out;
    }

    private String extractJsonArray(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    // ---------- motor de reglas de respaldo ----------

    private static final Pattern FAILED_PW = Pattern.compile("Failed password|authentication failure|Invalid user");
    private static final Pattern ACCEPTED = Pattern.compile("Accepted password|Accepted publickey");
    private static final Pattern SUDO_ROOT = Pattern.compile("sudo:.*COMMAND=|USER=root ; COMMAND=");
    private static final Pattern NEW_ACCOUNT = Pattern.compile("useradd\\[|new user|new group");
    private static final Pattern IP_RE = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern SENSITIVE_CMD = Pattern.compile(
            "useradd|usermod|adduser|passwd|visudo|sudoers|authorized_keys|/etc/shadow|chmod [0-7]*4[0-7]{3}|crontab|nc -e|bash -i");
    private static final Pattern NEW_USER_NAME = Pattern.compile("name=([\\w.-]+)");
    private static final Pattern WEB_ATTACK = Pattern.compile(
            "(?i)(union(\\s|%20|\\+)+select|%27(\\s|%20|\\+)*or|'\\s*or\\s*'|\\.\\./\\.\\./|%2e%2e%2f|<script|%3cscript|sqlmap|nikto|/etc/passwd)");

    private List<AnalysisResult> analyzeWithRules(Evidence ev, List<ParsedLog> logs) {
        List<AnalysisResult> out = new ArrayList<>();

        // 1) Fuerza bruta: contar fallos por IP.
        Map<String, Integer> failsByIp = new HashMap<>();
        Map<String, ParsedLog> sampleByIp = new HashMap<>();
        Map<String, java.util.Set<String>> usersByIp = new HashMap<>();
        for (ParsedLog p : logs) {
            String raw = p.getRawLine();
            if (raw == null) {
                continue;
            }
            if (FAILED_PW.matcher(raw).find()) {
                String ip = p.getSourceIp() != null ? p.getSourceIp() : firstIp(raw);
                if (ip != null) {
                    failsByIp.merge(ip, 1, Integer::sum);
                    sampleByIp.putIfAbsent(ip, p);
                    if (p.getUsername() != null) {
                        usersByIp.computeIfAbsent(ip, k -> new java.util.LinkedHashSet<>()).add(p.getUsername());
                    }
                }
            }
        }
        for (Map.Entry<String, Integer> e : failsByIp.entrySet()) {
            if (e.getValue() >= 5) {
                ParsedLog p = sampleByIp.get(e.getKey());
                String sev = e.getValue() >= 20 ? "critical" : (e.getValue() >= 10 ? "high" : "medium");
                AnalysisResult r = base(ev, p, sev);
                r.setThreatType("Fuerza bruta de autenticación");
                r.setMitreTechnique("T1110");
                r.setThreatDescription("Se observaron " + e.getValue()
                        + " intentos fallidos de autenticación desde " + e.getKey()
                        + ". Es un patrón típico de ataque por diccionario o fuerza bruta.");
                r.setRecommendation("Bloquear la IP " + e.getKey()
                        + " en el firewall, revisar si alguna sesión tuvo éxito y forzar rotación de credenciales.");
                List<String> iocs = new ArrayList<>(List.of(e.getKey(), e.getValue() + " intentos"));
                java.util.Set<String> tried = usersByIp.getOrDefault(e.getKey(), java.util.Set.of());
                tried.stream().limit(4).forEach(iocs::add);
                if (!tried.isEmpty()) {
                    r.setThreatDescription(r.getThreatDescription() + " Cuentas probadas: " + String.join(", ", tried) + ".");
                }
                r.setIndicators(gson.toJson(iocs));
                r.setConfidence(0.9);
                out.add(r);
            }
        }

        // 2) Acceso exitoso desde una IP que ya venía fallando: posible compromiso.
        for (ParsedLog p : logs) {
            String raw = p.getRawLine();
            if (raw == null) {
                continue;
            }
            if (ACCEPTED.matcher(raw).find()) {
                String ip = p.getSourceIp() != null ? p.getSourceIp() : firstIp(raw);
                if (ip != null && failsByIp.getOrDefault(ip, 0) >= 5) {
                    AnalysisResult r = base(ev, p, "critical");
                    r.setThreatType("Posible acceso tras fuerza bruta");
                    r.setMitreTechnique("T1110");
                    r.setThreatDescription("La IP " + ip + " logró autenticarse después de múltiples fallos. "
                            + "Sugiere una cuenta potencialmente comprometida.");
                    r.setRecommendation("Aislar la cuenta afectada, invalidar sus sesiones y analizar la actividad posterior.");
                    r.setIndicators(gson.toJson(p.getUsername() == null ? List.of(ip) : List.of(ip, p.getUsername())));
                    if (p.getUsername() != null) {
                        r.setThreatDescription("La IP " + ip + " entró con la cuenta " + p.getUsername()
                                + " después de múltiples fallos. Hay que tratar esa cuenta como comprometida.");
                    }
                    r.setConfidence(0.85);
                    out.add(r);
                }
            }
        }

        // 3) Uso de sudo. Si el comando toca cuentas, claves o permisos, sube a alta.
        for (ParsedLog p : logs) {
            String raw = p.getRawLine();
            if (raw == null || !SUDO_ROOT.matcher(raw).find() || !raw.contains("root")) {
                continue;
            }
            boolean sensible = SENSITIVE_CMD.matcher(raw).find();
            AnalysisResult r = base(ev, p, sensible ? "high" : "medium");
            if (sensible) {
                r.setThreatType("Comando sensible con privilegios de root");
                r.setMitreTechnique(raw.contains("authorized_keys") ? "T1098.004" : "T1136.001");
                r.setThreatDescription("Se ejecutó como root un comando que modifica cuentas, claves SSH o permisos. "
                        + "Es la forma habitual de dejar persistencia después de entrar.");
                r.setRecommendation("Verificar quién lo ejecutó y por qué. Revisar /etc/passwd, sudoers y los "
                        + "authorized_keys de todas las cuentas.");
                r.setConfidence(0.8);
            } else {
                r.setThreatType("Uso de privilegios elevados (sudo)");
                r.setMitreTechnique("T1548.003");
                r.setThreatDescription("Ejecución de comandos como root vía sudo. Conviene confirmar que la acción estaba autorizada.");
                r.setRecommendation("Confirmar con el responsable del equipo que el comando era legítimo.");
                r.setConfidence(0.55);
            }
            if (p.getUsername() != null) {
                r.setIndicators(gson.toJson(List.of(p.getUsername())));
            }
            out.add(r);
        }

        // 4) Creación de cuentas registrada por el propio sistema (no la línea de sudo, ya cubierta arriba).
        for (ParsedLog p : logs) {
            String raw = p.getRawLine();
            if (raw != null && !raw.contains("COMMAND=") && NEW_ACCOUNT.matcher(raw).find()) {
                AnalysisResult r = base(ev, p, "high");
                r.setThreatType("Creación de cuenta o grupo");
                r.setMitreTechnique("T1136.001");
                r.setThreatDescription("Se creó una cuenta o un grupo local. Los atacantes suelen crear "
                        + "cuentas para conservar el acceso.");
                r.setRecommendation("Verificar que la cuenta corresponde a un alta autorizada; si no, bloquearla y eliminarla.");
                Matcher name = NEW_USER_NAME.matcher(raw);
                if (name.find()) {
                    r.setIndicators(gson.toJson(List.of(name.group(1))));
                }
                r.setConfidence(0.7);
                out.add(r);
            }
        }

        // 5) Firmas de ataque web: inyección SQL, recorrido de rutas, herramientas de escaneo conocidas.
        for (ParsedLog p : logs) {
            String raw = p.getRawLine();
            if (raw == null) {
                continue;
            }
            boolean esWeb = "http".equals(p.getService()) || raw.contains("HTTP/");
            Matcher w = WEB_ATTACK.matcher(raw);
            if (esWeb && w.find()) {
                String ip = p.getSourceIp() != null ? p.getSourceIp() : firstIp(raw);
                AnalysisResult r = base(ev, p, "high");
                r.setThreatType("Intento de explotación web");
                r.setMitreTechnique("T1190");
                r.setThreatDescription("La petición contiene una firma de ataque (" + w.group() + "). "
                        + "Si la respuesta fue 200, hay que confirmar si la aplicación es vulnerable.");
                r.setRecommendation("Revisar el código del recurso atacado, bloquear la IP en el WAF y buscar "
                        + "más peticiones del mismo origen.");
                if (ip != null) {
                    r.setIndicators(gson.toJson(List.of(ip)));
                }
                r.setConfidence(0.8);
                out.add(r);
            }
        }

        // 6) Barrido de rutas web (muchos 404 desde una IP).
        Map<String, Integer> notFoundByIp = new HashMap<>();
        Map<String, ParsedLog> webSample = new HashMap<>();
        for (ParsedLog p : logs) {
            String action = p.getAction();
            if (action != null && action.contains("-> 404") && p.getSourceIp() != null) {
                notFoundByIp.merge(p.getSourceIp(), 1, Integer::sum);
                webSample.putIfAbsent(p.getSourceIp(), p);
            }
        }
        for (Map.Entry<String, Integer> e : notFoundByIp.entrySet()) {
            if (e.getValue() >= 15) {
                AnalysisResult r = base(ev, webSample.get(e.getKey()), "medium");
                r.setThreatType("Barrido / enumeración web");
                r.setMitreTechnique("T1595");
                r.setThreatDescription("La IP " + e.getKey() + " generó " + e.getValue()
                        + " respuestas 404, patrón típico de enumeración de rutas o escaneo automatizado.");
                r.setRecommendation("Aplicar limitación de tasa y revisar reglas del WAF para esa IP.");
                r.setIndicators(gson.toJson(List.of(e.getKey(), e.getValue() + " respuestas 404")));
                r.setConfidence(0.75);
                out.add(r);
            }
        }

        return out;
    }

    private AnalysisResult base(Evidence ev, ParsedLog p, String severity) {
        AnalysisResult r = new AnalysisResult();
        r.setEvidenceId(ev.getId());
        r.setSeverity(severity);
        if (p != null) {
            r.setParsedLogId(p.getId());
            r.setRawEvidence(p.getRawLine());
        }
        r.setAiExplanation("Detectado por el motor de reglas.");
        return r;
    }

    private String firstIp(String s) {
        Matcher m = IP_RE.matcher(s);
        return m.find() ? m.group() : null;
    }

    // ---------- utilidades JSON ----------

    private String normalizeSeverity(String s) {
        if (s == null) {
            return "medium";
        }
        String v = s.toLowerCase(Locale.ROOT).trim();
        return switch (v) {
            case "low", "medium", "high", "critical" -> v;
            case "baja" -> "low";
            case "media" -> "medium";
            case "alta" -> "high";
            case "critica", "crítica" -> "critical";
            default -> "medium";
        };
    }

    private String asString(JsonObject o, String key, String def) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsString();
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private int asInt(JsonObject o, String key, int def) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsInt();
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private double asDouble(JsonObject o, String key, double def) {
        try {
            if (o.has(key) && !o.get(key).isJsonNull()) {
                return o.get(key).getAsDouble();
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }
}
