package com.soc.forense.web;

import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.model.Incident;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.service.IncidentService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@WebServlet("/incidentes/detalle")
public class IncidentDetailServlet extends HttpServlet {

    private final IncidentService incidentService = new IncidentService();
    private final EvidenceService evidenceService = new EvidenceService();
    private final AnalysisResultDao analysisDao = new AnalysisResultDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long id = Web.parseLong(req.getParameter("id"));
        Optional<Incident> inc = id == null ? Optional.empty() : incidentService.find(id);
        if (inc.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Incident i = inc.get();
        req.setAttribute("incident", i);
        if (i.getEvidenceId() != null) {
            evidenceService.find(i.getEvidenceId()).ifPresent(ev -> req.setAttribute("evidence", ev));
            req.setAttribute("findings", analysisDao.findByEvidence(i.getEvidenceId()));
        } else {
            req.setAttribute("findings", List.of());
        }
        req.setAttribute("statuses", List.of("open", "investigating", "contained", "closed"));
        Web.render(req, resp, "incidents/detail", "Incidente #" + i.getId(), "incidentes");
    }
}
