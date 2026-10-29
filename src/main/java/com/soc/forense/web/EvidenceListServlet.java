package com.soc.forense.web;

import com.soc.forense.config.AppConfig;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/evidencias")
public class EvidenceListServlet extends HttpServlet {

    private final EvidenceService evidenceService = new EvidenceService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("evidences", evidenceService.listAll());
        req.setAttribute("maxUploadMb", AppConfig.maxUploadBytes() / (1024 * 1024));
        Web.render(req, resp, "evidence/list", "Evidencias", "evidencias");
    }
}
