package com.soc.forense.service;

import com.soc.forense.ai.AiClient;
import com.soc.forense.ai.AiUnavailableException;
import com.soc.forense.ai.ChatMessage;
import com.soc.forense.ai.ForensicPrompts;
import com.soc.forense.ai.InferenceParams;
import com.soc.forense.ai.LogRetriever;
import com.soc.forense.dao.AgentMessageDao;
import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.IncidentDao;
import com.soc.forense.dao.ParsedLogDao;
import com.soc.forense.model.AgentMessage;
import com.soc.forense.model.AnalysisResult;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.ParsedLog;
import com.soc.forense.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Agente conversacional del SOC. Antes de cada respuesta arma un contexto con
 * el estado real de la plataforma (conteos, amenazas criticas, incidentes
 * abiertos) y, si el analista elige una evidencia, con sus hallazgos y una
 * muestra de lineas. Asi el modelo responde sobre datos concretos y no en el
 * aire. Sin IA, responde con un resumen armado desde la base de datos.
 */
public class AgentService {

    private static final int HISTORY = 12;
    private static final int MAX_INPUT = 4000;

    private final AgentMessageDao messageDao = new AgentMessageDao();
    private final AnalysisResultDao analysisDao = new AnalysisResultDao();
    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final ParsedLogDao parsedLogDao = new ParsedLogDao();
    private final IncidentDao incidentDao = new IncidentDao();
    private final AiClient aiClient = new AiClient();

    public static class Reply {
        public AgentMessage message;
        public boolean usedAi;
    }

    public List<AgentMessage> history(User user) {
        return messageDao.recentByUser(user.getId(), 50);
    }

    public void clear(User user) {
        messageDao.clearForUser(user.getId());
    }

    public Reply ask(User user, String text, Long evidenceId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Escriba una pregunta para el agente.");
        }
        String question = text.trim();
        if (question.length() > MAX_INPUT) {
            question = question.substring(0, MAX_INPUT);
        }

        // Historial previo (antes de guardar la pregunta actual).
        List<AgentMessage> previous = messageDao.recentByUser(user.getId(), HISTORY);

        AgentMessage userMsg = new AgentMessage("user", question);
        userMsg.setUserId(user.getId());
        userMsg.setEvidenceId(evidenceId);
        userMsg.setCreatedAt(java.time.LocalDateTime.now());
        messageDao.create(userMsg);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(ForensicPrompts.SYSTEM_ANALISTA
                + " Respondes dentro de la plataforma SOC Forense. Usa listas cortas cuando ayuden "
                + "y no repitas la pregunta."));
        messages.add(ChatMessage.system("ESTADO ACTUAL DE LA PLATAFORMA:\n" + platformContext()));
        if (evidenceId != null) {
            messages.add(ChatMessage.system("EVIDENCIA SELECCIONADA:\n" + evidenceContext(evidenceId, question)));
        }
        for (AgentMessage m : previous) {
            if ("assistant".equals(m.getRole())) {
                messages.add(ChatMessage.assistant(m.getContent()));
            } else if ("user".equals(m.getRole())) {
                messages.add(ChatMessage.user(m.getContent()));
            }
        }
        messages.add(ChatMessage.user(question));

        Reply reply = new Reply();
        String answer;
        try {
            if (!aiClient.isEnabled()) {
                throw new AiUnavailableException("IA deshabilitada");
            }
            answer = aiClient.chat(messages, InferenceParams.chat());
            reply.usedAi = true;
        } catch (AiUnavailableException e) {
            answer = offlineAnswer(question, evidenceId);
            reply.usedAi = false;
        }

        AgentMessage botMsg = new AgentMessage("assistant", answer);
        botMsg.setUserId(user.getId());
        botMsg.setEvidenceId(evidenceId);
        botMsg.setCreatedAt(java.time.LocalDateTime.now());
        reply.message = messageDao.create(botMsg);
        if (reply.message.getCreatedAt() == null) {
            reply.message.setCreatedAt(botMsg.getCreatedAt());
        }
        return reply;
    }

    // ---------- construccion de contexto ----------

    private String platformContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("Evidencias cargadas: ").append(evidenceDao.count()).append('\n');
        Map<String, Long> sev = analysisDao.countBySeverity();
        sb.append("Hallazgos por severidad: crítica ").append(sev.getOrDefault("critical", 0L))
                .append(", alta ").append(sev.getOrDefault("high", 0L))
                .append(", media ").append(sev.getOrDefault("medium", 0L))
                .append(", baja ").append(sev.getOrDefault("low", 0L)).append('\n');
        sb.append("Incidentes abiertos: ").append(incidentDao.countOpen()).append('\n');
        List<AnalysisResult> critical = analysisDao.findCriticalThreats(8);
        if (!critical.isEmpty()) {
            sb.append("Amenazas más graves recientes:\n");
            for (AnalysisResult r : critical) {
                sb.append("- evidencia #").append(r.getEvidenceId()).append(" [").append(r.getSeverity())
                        .append("] ").append(r.getThreatType());
                if (r.getMitreTechnique() != null && !r.getMitreTechnique().isBlank()) {
                    sb.append(" (").append(r.getMitreTechnique()).append(')');
                }
                sb.append(" indicadores: ").append(r.getIndicatorList()).append('\n');
            }
        }
        return sb.toString();
    }

    private String evidenceContext(long evidenceId, String question) {
        Optional<Evidence> opt = evidenceDao.findById(evidenceId);
        if (opt.isEmpty()) {
            return "La evidencia indicada no existe.";
        }
        Evidence ev = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Archivo: ").append(ev.getFilename()).append(" | tipo: ").append(ev.getLogType())
                .append(" | estado: ").append(ev.getStatus()).append(" | líneas: ").append(ev.getLineCount())
                .append(" | SHA-256: ").append(ev.getSha256()).append('\n');
        List<AnalysisResult> findings = analysisDao.findByEvidence(evidenceId);
        sb.append("Hallazgos (").append(findings.size()).append("):\n");
        int n = 0;
        for (AnalysisResult r : findings) {
            if (n++ >= 30) {
                break;
            }
            sb.append("- [").append(r.getSeverity()).append("] ").append(r.getThreatType()).append(": ")
                    .append(r.getThreatDescription() == null ? "" : r.getThreatDescription());
            if (r.getRawEvidence() != null) {
                sb.append(" | ").append(r.getRawEvidence());
            }
            sb.append('\n');
        }
        // RAG: en vez de las primeras lineas, se recuperan las mas relevantes para la pregunta.
        List<ParsedLog> sample = LogRetriever.retrieve(parsedLogDao.findByEvidence(evidenceId), question,
                com.soc.forense.config.AppConfig.getInt("ai.rag.lines", 40));
        if (!sample.isEmpty()) {
            sb.append("Líneas del log más relevantes para la pregunta (recuperadas, en orden):\n");
            for (ParsedLog p : sample) {
                sb.append(p.getLineNumber()).append(": ").append(p.getRawLine()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Respuesta sin modelo. No pretende conversar: entrega los datos que el
     * analista suele pedir primero, sacados directo de la base.
     */
    private String offlineAnswer(String question, Long evidenceId) {
        String q = question.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("El modelo de IA no responde en este momento (revise que LM Studio esté abierto, con un modelo cargado y "
                + "el servidor local activo). Mientras tanto, esto es lo que dicen los datos:\n\n");

        if (evidenceId != null) {
            List<AnalysisResult> findings = analysisDao.findByEvidence(evidenceId);
            sb.append("Evidencia #").append(evidenceId).append(": ").append(findings.size()).append(" hallazgos.\n");
            appendFindings(sb, findings, 10);
        } else {
            sb.append("Hallazgos por severidad: ");
            Map<String, Long> sev = analysisDao.countBySeverity();
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Long> e : sev.entrySet()) {
                parts.add(com.soc.forense.util.Fmt.severidad(e.getKey()).toLowerCase(Locale.ROOT) + " " + e.getValue());
            }
            sb.append(String.join(", ", parts)).append(".\n");
            sb.append("Incidentes abiertos: ").append(incidentDao.countOpen()).append(".\n\n");
            appendFindings(sb, analysisDao.findCriticalThreats(8), 8);
        }

        if (q.contains("ip") || q.contains("bloque")) {
            Map<String, Integer> ips = collectIps(evidenceId);
            if (!ips.isEmpty()) {
                sb.append("\nIndicadores con formato de IP vistos en hallazgos: ");
                sb.append(String.join(", ", ips.keySet())).append(".\n");
            }
        }
        return sb.toString();
    }

    private void appendFindings(StringBuilder sb, List<AnalysisResult> findings, int max) {
        int n = 0;
        for (AnalysisResult r : findings) {
            if (n++ >= max) {
                break;
            }
            sb.append("- [").append(com.soc.forense.util.Fmt.severidad(r.getSeverity())).append("] ")
                    .append(r.getThreatType());
            if (r.getMitreTechnique() != null && !r.getMitreTechnique().isBlank()) {
                sb.append(" · ").append(r.getMitreTechnique());
            }
            if (r.getRecommendation() != null && !r.getRecommendation().isBlank()) {
                sb.append("\n  Acción sugerida: ").append(r.getRecommendation());
            }
            sb.append('\n');
        }
        if (findings.isEmpty()) {
            sb.append("No hay hallazgos registrados todavía. Cargue y analice una evidencia.\n");
        }
    }

    private Map<String, Integer> collectIps(Long evidenceId) {
        List<AnalysisResult> list = evidenceId != null
                ? analysisDao.findByEvidence(evidenceId)
                : analysisDao.findCriticalThreats(50);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (AnalysisResult r : list) {
            for (String ind : r.getIndicatorList()) {
                if (ind.matches("(\\d{1,3}\\.){3}\\d{1,3}")) {
                    out.merge(ind, 1, Integer::sum);
                }
            }
        }
        return out;
    }
}
