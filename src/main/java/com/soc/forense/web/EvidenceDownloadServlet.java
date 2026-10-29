package com.soc.forense.web;

import com.soc.forense.model.Evidence;
import com.soc.forense.service.EvidenceService;
import com.soc.forense.util.Web;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Descarga el archivo original tal cual se subio, con su hash en cabecera. */
@WebServlet("/evidencias/descargar")
public class EvidenceDownloadServlet extends HttpServlet {

    private final EvidenceService evidenceService = new EvidenceService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long id = Web.parseLong(req.getParameter("id"));
        Optional<Evidence> ev = id == null ? Optional.empty() : evidenceService.find(id);
        if (ev.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path file = evidenceService.resolveStoredFile(ev.get());
        if (file == null || !Files.exists(file)) {
            Web.flash(req, "error", "El archivo original ya no está en disco.");
            Web.redirect(req, resp, "/evidencias/detalle?id=" + id);
            return;
        }
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + ev.get().getFilename() + "\"");
        resp.setHeader("X-Evidence-SHA256", ev.get().getSha256());
        resp.setContentLengthLong(Files.size(file));
        Files.copy(file, resp.getOutputStream());
    }
}
