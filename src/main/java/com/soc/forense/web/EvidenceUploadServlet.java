package com.soc.forense.web;

import com.soc.forense.config.AppConfig;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.User;
import com.soc.forense.service.AnalysisService;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.service.LogParserService;
import com.soc.forense.util.Fmt;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;

/**
 * Recibe el archivo de log. Si el analista marca la casilla, en la misma
 * peticion se parsea y se analiza, para ir del archivo a los hallazgos en un paso.
 */
@WebServlet("/evidencias/subir")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 100L * 1024 * 1024,
        maxRequestSize = 110L * 1024 * 1024)
public class EvidenceUploadServlet extends HttpServlet {

    private final EvidenceService evidenceService = new EvidenceService();
    private final LogParserService parserService = new LogParserService();
    private final AnalysisService analysisService = new AnalysisService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!Web.requireAnalyst(req, resp)) {
            return;
        }
        User user = Web.currentUser(req);

        Part part;
        try {
            part = req.getPart("file");
        } catch (IllegalStateException e) {
            Web.flash(req, "error", "El archivo supera el tamaño permitido.");
            Web.redirect(req, resp, "/evidencias");
            return;
        }
        if (part == null || part.getSize() == 0) {
            Web.flash(req, "error", "Seleccione un archivo de log que no esté vacío.");
            Web.redirect(req, resp, "/evidencias");
            return;
        }
        if (part.getSize() > AppConfig.maxUploadBytes()) {
            Web.flash(req, "error", "El archivo pesa más de "
                    + (AppConfig.maxUploadBytes() / (1024 * 1024)) + " MB.");
            Web.redirect(req, resp, "/evidencias");
            return;
        }

        long before = evidenceService.count();
        Evidence ev;
        try (InputStream in = part.getInputStream()) {
            ev = evidenceService.store(part.getSubmittedFileName(), in, user);
        } catch (RuntimeException e) {
            Web.flash(req, "error", "No se pudo guardar la evidencia: " + e.getMessage());
            Web.redirect(req, resp, "/evidencias");
            return;
        }
        boolean duplicate = evidenceService.count() == before;

        if (duplicate) {
            Web.flash(req, "info", "Ese archivo ya estaba cargado (mismo SHA-256). Se abre la evidencia existente.");
            Web.redirect(req, resp, "/evidencias/detalle?id=" + ev.getId());
            return;
        }

        if (req.getParameter("auto") != null) {
            try {
                int lines = parserService.parse(ev);
                ev = evidenceService.find(ev.getId()).orElse(ev);
                AnalysisService.Outcome out = analysisService.analyze(ev);
                Web.flash(req, out.critical > 0 ? "error" : "ok",
                        Fmt.cant(lines, "línea procesada, ", "líneas procesadas, ")
                        + EvidenceActionServlet.resumen(out) + " " + out.note);
            } catch (RuntimeException e) {
                Web.flash(req, "error", "Evidencia guardada, pero el procesamiento falló: " + e.getMessage());
            }
        } else {
            Web.flash(req, "ok", "Evidencia registrada. SHA-256 " + ev.getShortHash() + "…");
        }
        Web.redirect(req, resp, "/evidencias/detalle?id=" + ev.getId());
    }
}
