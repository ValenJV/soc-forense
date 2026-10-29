package com.soc.forense.web;

import com.soc.forense.model.User;
import com.soc.forense.service.AgentService;
import com.soc.forense.util.Fmt;
import com.soc.forense.util.Web;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint JSON del chat. El navegador envia la pregunta con fetch y pinta la
 * respuesta sin recargar. Acciones: ask (preguntar) y clear (borrar historial).
 */
@WebServlet("/api/agente")
public class AgentApiServlet extends HttpServlet {

    private final AgentService agentService = new AgentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = Web.currentUser(req);
        String action = req.getParameter("action");

        if ("clear".equals(action)) {
            agentService.clear(user);
            Web.json(resp, 200, Map.of("ok", true));
            return;
        }

        try {
            AgentService.Reply reply = agentService.ask(user,
                    req.getParameter("message"),
                    Web.parseLong(req.getParameter("evidenceId")));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("usedAi", reply.usedAi);
            body.put("content", reply.message.getContent());
            body.put("time", Fmt.fecha(reply.message.getCreatedAt()));
            Web.json(resp, 200, body);
        } catch (IllegalArgumentException e) {
            Web.jsonError(resp, 400, e.getMessage());
        } catch (RuntimeException e) {
            Web.jsonError(resp, 500, "El agente falló: " + e.getMessage());
        }
    }
}
