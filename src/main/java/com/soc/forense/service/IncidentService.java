package com.soc.forense.service;

import com.soc.forense.ai.AiClient;
import com.soc.forense.ai.AiUnavailableException;
import com.soc.forense.ai.ChatMessage;
import com.soc.forense.ai.ForensicPrompts;
import com.soc.forense.ai.InferenceParams;
import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.IncidentDao;
import com.soc.forense.model.AnalysisResult;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.Incident;
import com.soc.forense.model.User;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gestion de incidentes: abrirlos a mano o a partir de una evidencia analizada,
 * moverlos por su ciclo de vida y producir el plan de respuesta. El plan lo
 * redacta la IA con los hallazgos reales; si la IA no esta, se arma una plantilla
 * con los mismos datos para que el analista no empiece de cero.
 */
public class IncidentService {

    public static final Set<String> SEVERITIES = Set.of("low", "medium", "high", "critical");
    public static final Set<String> STATUSES = Set.of("open", "investigating", "contained", "closed");

    private final IncidentDao incidentDao = new IncidentDao();
    private final AnalysisResultDao analysisResultDao = new AnalysisResultDao();
    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final AiClient aiClient = new AiClient();

    public List<Incident> listAll() {
        return incidentDao.findAll();
    }

    public Optional<Incident> find(long id) {
        return incidentDao.findById(id);
    }

    public long countOpen() {
        return incidentDao.countOpen();
    }

    public Map<String, Long> countByStatus() {
        return incidentDao.countByStatus();
    }

    public Incident create(String title, String severity, String summary, Long evidenceId,
                           String assignedTo, User creator) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El incidente necesita un título.");
        }
        if (!SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("Severidad no válida.");
        }
        if (evidenceId != null && evidenceDao.findById(evidenceId).isEmpty()) {
            throw new IllegalArgumentException("La evidencia indicada no existe.");
        }
        Incident i = new Incident();
        i.setTitle(title.trim());
        i.setSeverity(severity);
        i.setStatus("open");
        i.setSummary(summary == null ? "" : summary.trim());
        i.setEvidenceId(evidenceId);
        i.setAssignedTo(blankToNull(assignedTo));
        i.setCreatedByUserId(creator.getId());
        i.setCreatedByUsername(creator.getUsername());
        return incidentDao.create(i);
    }

    /**
     * Abre un incidente directamente desde una evidencia ya analizada. Toma la
     * severidad mas alta de sus hallazgos y resume los tipos de amenaza.
     */
    public Incident createFromEvidence(long evidenceId, User creator) {
        Evidence ev = evidenceDao.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("La evidencia no existe."));
        List<AnalysisResult> findings = analysisResultDao.findByEvidence(evidenceId);
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("Esta evidencia no tiene hallazgos. Analícela primero.");
        }
        String worst = "low";
        Set<String> tipos = new LinkedHashSet<>();
        for (AnalysisResult r : findings) {
            if (rank(r.getSeverity()) > rank(worst)) {
                worst = r.getSeverity();
            }
            if (r.getThreatType() != null) {
                tipos.add(r.getThreatType());
            }
        }
        String title = "Incidente en " + ev.getFilename();
        String summary = findings.size() + " hallazgos en " + ev.getFilename()
                + " (SHA-256 " + ev.getShortHash() + "). Tipos: " + String.join(", ", tipos) + ".";
        return create(title, worst, summary, evidenceId, creator.getUsername(), creator);
    }

    public void update(long id, String title, String severity, String status, String summary, String assignedTo) {
        Incident i = incidentDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El incidente no existe."));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El incidente necesita un título.");
        }
        if (!SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("Severidad no válida.");
        }
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado no válido.");
        }
        i.setTitle(title.trim());
        i.setSeverity(severity);
        i.setStatus(status);
        i.setSummary(summary == null ? "" : summary.trim());
        i.setAssignedTo(blankToNull(assignedTo));
        incidentDao.update(i);
    }

    public void changeStatus(long id, String status) {
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado no válido.");
        }
        incidentDao.updateStatus(id, status);
    }

    public void delete(long id) {
        incidentDao.delete(id);
    }

    /**
     * Genera y guarda el plan de respuesta. Devuelve true si lo escribio la IA,
     * false si se uso la plantilla.
     */
    public boolean generateResponsePlan(long id) {
        Incident i = incidentDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El incidente no existe."));
        String context = buildContext(i);

        String plan;
        boolean byAi;
        try {
            if (!aiClient.isEnabled()) {
                throw new AiUnavailableException("IA deshabilitada");
            }
            plan = aiClient.chat(List.of(
                    ChatMessage.system(ForensicPrompts.SYSTEM_ANALISTA),
                    ChatMessage.user(ForensicPrompts.planRespuesta(context))), InferenceParams.plan());
            byAi = true;
        } catch (AiUnavailableException e) {
            plan = templatePlan(i, context);
            byAi = false;
        }
        i.setResponsePlan(plan);
        if ("open".equals(i.getStatus())) {
            i.setStatus("investigating");
        }
        incidentDao.update(i);
        return byAi;
    }

    private String buildContext(Incident i) {
        StringBuilder sb = new StringBuilder();
        sb.append("Título: ").append(i.getTitle()).append('\n');
        sb.append("Severidad: ").append(com.soc.forense.util.Fmt.severidad(i.getSeverity())).append('\n');
        if (i.getSummary() != null && !i.getSummary().isBlank()) {
            sb.append("Resumen: ").append(i.getSummary()).append('\n');
        }
        if (i.getEvidenceId() != null) {
            List<AnalysisResult> findings = analysisResultDao.findByEvidence(i.getEvidenceId());
            int n = 0;
            for (AnalysisResult r : findings) {
                if (n++ >= 25) {
                    sb.append("(hay más hallazgos, se omiten)\n");
                    break;
                }
                sb.append("- [").append(com.soc.forense.util.Fmt.severidad(r.getSeverity())).append("] ").append(r.getThreatType());
                if (r.getMitreTechnique() != null && !r.getMitreTechnique().isBlank()) {
                    sb.append(" (").append(r.getMitreTechnique()).append(')');
                }
                sb.append(": ").append(nz(r.getThreatDescription()));
                if (r.getRawEvidence() != null) {
                    sb.append(" | línea: ").append(r.getRawEvidence());
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String templatePlan(Incident i, String context) {
        return "1. Resumen del incidente\n"
                + context + "\n"
                + "2. Contención inmediata\n"
                + "- Bloquear en el perímetro las IPs que aparecen en los hallazgos.\n"
                + "- Deshabilitar temporalmente las cuentas implicadas y cerrar sus sesiones activas.\n"
                + "- Aislar de la red los equipos afectados si hubo acceso exitoso.\n\n"
                + "3. Erradicación\n"
                + "- Revisar cuentas creadas recientemente, claves SSH autorizadas y tareas programadas.\n"
                + "- Eliminar cualquier persistencia encontrada y aplicar parches pendientes.\n\n"
                + "4. Recuperación\n"
                + "- Rotar credenciales de las cuentas afectadas y de servicio.\n"
                + "- Restaurar servicios desde un estado conocido y vigilar los logs 72 horas.\n\n"
                + "5. Lecciones y prevención\n"
                + "- Activar bloqueo por intentos fallidos (fail2ban o equivalente) y MFA.\n"
                + "- Documentar la línea de tiempo y ajustar las alertas del SOC.\n\n"
                + "(Plan generado con plantilla porque la IA no estaba disponible. "
                + "Ajustelo con los detalles del caso.)";
    }

    public static int rank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
