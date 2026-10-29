package com.soc.forense.web;

import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.ParsedLogDao;
import com.soc.forense.model.AnalysisResult;
import com.soc.forense.model.Evidence;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@WebServlet("/evidencias/detalle")
public class EvidenceDetailServlet extends HttpServlet {

    private static final int MAX_LINES = 500;

    private final EvidenceService evidenceService = new EvidenceService();
    private final ParsedLogDao parsedLogDao = new ParsedLogDao();
    private final AnalysisResultDao analysisDao = new AnalysisResultDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Long id = Web.parseLong(req.getParameter("id"));
        Optional<Evidence> ev = id == null ? Optional.empty() : evidenceService.find(id);
        if (ev.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        List<AnalysisResult> findings = analysisDao.findByEvidence(id);
        Set<Long> flagged = new HashSet<>();
        for (AnalysisResult r : findings) {
            if (r.getParsedLogId() != null) {
                flagged.add(r.getParsedLogId());
            }
        }
        req.setAttribute("evidence", ev.get());
        req.setAttribute("findings", findings);
        req.setAttribute("logs", parsedLogDao.findByEvidence(id, MAX_LINES));
        req.setAttribute("logTotal", parsedLogDao.countByEvidence(id));
        req.setAttribute("maxLines", MAX_LINES);
        req.setAttribute("flagged", flagged);
        Web.render(req, resp, "evidence/detail", ev.get().getFilename(), "evidencias");
    }
}
