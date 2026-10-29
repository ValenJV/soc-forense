package com.soc.forense.web;

import com.soc.forense.config.AppConfig;
import com.soc.forense.model.User;
import com.soc.forense.service.AgentService;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/agente")
public class AgentServlet extends HttpServlet {

    private final AgentService agentService = new AgentService();
    private final EvidenceService evidenceService = new EvidenceService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User user = Web.currentUser(req);
        req.setAttribute("history", agentService.history(user));
        req.setAttribute("evidences", evidenceService.listAll());
        req.setAttribute("selectedEvidence", Web.parseLong(req.getParameter("evidencia")));
        req.setAttribute("aiModel", AppConfig.aiModel());
        Web.render(req, resp, "agent/chat", "Agente IA", "agente");
    }
}
