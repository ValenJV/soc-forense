package com.soc.forense.web;

import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.model.AnalysisResult;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Todos los hallazgos de todas las evidencias, con filtro por severidad y texto. */
@WebServlet("/hallazgos")
public class FindingsServlet extends HttpServlet {

    private final AnalysisResultDao analysisDao = new AnalysisResultDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String sev = req.getParameter("sev");
        String q = req.getParameter("q");
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<AnalysisResult> all = analysisDao.findAll();
        List<AnalysisResult> filtered = all.stream()
                .filter(r -> sev == null || sev.isBlank() || sev.equals(r.getSeverity()))
                .filter(r -> needle.isEmpty() || contains(r, needle))
                .toList();

        req.setAttribute("findings", filtered);
        req.setAttribute("total", all.size());
        req.setAttribute("sev", sev);
        req.setAttribute("q", q);
        req.setAttribute("severityCounts", analysisDao.countBySeverity());
        Web.render(req, resp, "analysis/results", "Hallazgos", "hallazgos");
    }

    private boolean contains(AnalysisResult r, String needle) {
        return low(r.getThreatType()).contains(needle)
                || low(r.getThreatDescription()).contains(needle)
                || low(r.getMitreTechnique()).contains(needle)
                || low(r.getIndicators()).contains(needle)
                || low(r.getRawEvidence()).contains(needle);
    }

    private String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
