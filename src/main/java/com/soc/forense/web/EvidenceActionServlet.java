package com.soc.forense.web;

import com.soc.forense.model.Evidence;
import com.soc.forense.model.Incident;
import com.soc.forense.model.User;
import com.soc.forense.service.AnalysisService;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.service.IncidentService;
import com.soc.forense.service.LogParserService;
import com.soc.forense.util.Fmt;
import com.soc.forense.util.Web;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/** Acciones sobre una evidencia: parsear, analizar, ambas, abrir incidente, eliminar. */
@WebServlet("/evidencias/accion")
public class EvidenceActionServlet extends HttpServlet {

    private final EvidenceService evidenceService = new EvidenceService();
    private final LogParserService parserService = new LogParserService();
    private final AnalysisService analysisService = new AnalysisService();
    private final IncidentService incidentService = new IncidentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!Web.requireAnalyst(req, resp)) {
            return;
        }
        User user = Web.currentUser(req);
        Long id = Web.parseLong(req.getParameter("id"));
        String action = req.getParameter("action");
        Optional<Evidence> opt = id == null ? Optional.empty() : evidenceService.find(id);
        if (opt.isEmpty()) {
            Web.flash(req, "error", "La evidencia no existe.");
            Web.redirect(req, resp, "/evidencias");
            return;
        }
        Evidence ev = opt.get();
        String back = "/evidencias/detalle?id=" + ev.getId();

        try {
            switch (action == null ? "" : action) {
                case "parse" -> {
                    int n = parserService.parse(ev);
                    Web.flash(req, "ok", Fmt.cant(n, "línea estructurada.", "líneas estructuradas."));
                }
                case "analyze" -> {
                    AnalysisService.Outcome out = analysisService.analyze(ev);
                    Web.flash(req, out.critical > 0 ? "error" : "ok",
                            resumen(out) + " " + out.note);
                }
                case "pipeline" -> {
                    int n = parserService.parse(ev);
                    ev = evidenceService.find(ev.getId()).orElse(ev);
                    AnalysisService.Outcome out = analysisService.analyze(ev);
                    Web.flash(req, out.critical > 0 ? "error" : "ok",
                            Fmt.cant(n, "línea, ", "líneas, ") + resumen(out) + " " + out.note);
                }
                case "incident" -> {
                    Incident inc = incidentService.createFromEvidence(ev.getId(), user);
                    Web.flash(req, "ok", "Incidente #" + inc.getId() + " abierto.");
                    back = "/incidentes/detalle?id=" + inc.getId();
                }
                case "delete" -> {
                    if (!user.isAdmin()) {
                        Web.flash(req, "error", "Solo un administrador puede eliminar evidencias.");
                        break;
                    }
                    evidenceService.delete(ev.getId());
                    Web.flash(req, "ok", "Evidencia " + ev.getFilename() + " eliminada.");
                    back = "/evidencias";
                }
                default -> Web.flash(req, "error", "Acción desconocida.");
            }
        } catch (RuntimeException e) {
            Web.flash(req, "error", e.getMessage());
        }
        Web.redirect(req, resp, back);
    }

    static String resumen(AnalysisService.Outcome out) {
        return Fmt.cant(out.threats, "hallazgo", "hallazgos") + ", " + Fmt.cant(out.critical, "crítico.", "críticos.");
    }
}
