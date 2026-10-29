package com.soc.forense.web;

import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.Incident;
import com.soc.forense.service.DashboardService;
import com.soc.forense.service.IncidentService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/tablero")
public class DashboardServlet extends HttpServlet {

    private final DashboardService dashboardService = new DashboardService();
    private final AnalysisResultDao analysisDao = new AnalysisResultDao();
    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final IncidentService incidentService = new IncidentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // El estado de la IA se consulta despues por /api/tablero para no frenar la carga.
        req.setAttribute("stats", dashboardService.snapshot(false));
        req.setAttribute("criticalThreats", analysisDao.findCriticalThreats(6));

        List<Evidence> all = evidenceDao.findAll();
        req.setAttribute("recentEvidence", all.subList(0, Math.min(5, all.size())));

        List<Incident> incidents = incidentService.listAll().stream()
                .filter(i -> !"closed".equals(i.getStatus()))
                .limit(5)
                .toList();
        req.setAttribute("openIncidentList", incidents);

        Web.render(req, resp, "dashboard/index", "Tablero", "tablero");
    }
}
