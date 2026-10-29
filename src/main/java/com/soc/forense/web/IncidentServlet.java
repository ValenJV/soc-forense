package com.soc.forense.web;

import com.soc.forense.model.Incident;
import com.soc.forense.model.User;
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

/** Listado de incidentes y todas sus acciones (crear, editar, estado, plan, eliminar). */
@WebServlet("/incidentes")
public class IncidentServlet extends HttpServlet {

    private final IncidentService incidentService = new IncidentService();
    private final EvidenceService evidenceService = new EvidenceService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String status = req.getParameter("estado");
        List<Incident> all = incidentService.listAll();
        List<Incident> list = (status == null || status.isBlank())
                ? all
                : all.stream().filter(i -> status.equals(i.getStatus())).toList();
        req.setAttribute("incidents", list);
        req.setAttribute("estado", status);
        req.setAttribute("statusCounts", incidentService.countByStatus());
        req.setAttribute("evidences", evidenceService.listAll());
        Web.render(req, resp, "incidents/list", "Incidentes", "incidentes");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!Web.requireAnalyst(req, resp)) {
            return;
        }
        User user = Web.currentUser(req);
        String action = req.getParameter("action");
        Long id = Web.parseLong(req.getParameter("id"));
        String back = id != null ? "/incidentes/detalle?id=" + id : "/incidentes";

        try {
            switch (action == null ? "" : action) {
                case "create" -> {
                    Incident inc = incidentService.create(
                            req.getParameter("title"),
                            req.getParameter("severity"),
                            req.getParameter("summary"),
                            Web.parseLong(req.getParameter("evidenceId")),
                            req.getParameter("assignedTo"),
                            user);
                    Web.flash(req, "ok", "Incidente #" + inc.getId() + " registrado.");
                    back = "/incidentes/detalle?id=" + inc.getId();
                }
                case "update" -> {
                    requireId(id);
                    incidentService.update(id,
                            req.getParameter("title"),
                            req.getParameter("severity"),
                            req.getParameter("status"),
                            req.getParameter("summary"),
                            req.getParameter("assignedTo"));
                    Web.flash(req, "ok", "Cambios guardados.");
                }
                case "status" -> {
                    requireId(id);
                    incidentService.changeStatus(id, req.getParameter("status"));
                    Web.flash(req, "ok", "Estado actualizado.");
                }
                case "plan" -> {
                    requireId(id);
                    boolean byAi = incidentService.generateResponsePlan(id);
                    Web.flash(req, byAi ? "ok" : "info", byAi
                            ? "El agente redactó el plan de respuesta."
                            : "La IA no respondió; se generó el plan con la plantilla base.");
                }
                case "delete" -> {
                    requireId(id);
                    if (!user.isAdmin()) {
                        throw new IllegalArgumentException("Solo un administrador puede eliminar incidentes.");
                    }
                    incidentService.delete(id);
                    Web.flash(req, "ok", "Incidente eliminado.");
                    back = "/incidentes";
                }
                default -> Web.flash(req, "error", "Acción desconocida.");
            }
        } catch (IllegalArgumentException e) {
            Web.flash(req, "error", e.getMessage());
        } catch (RuntimeException e) {
            Web.flash(req, "error", "No se pudo completar la acción: " + e.getMessage());
        }
        Web.redirect(req, resp, back);
    }

    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Falta el identificador del incidente.");
        }
    }
}
