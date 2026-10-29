package com.soc.forense.web;

import com.soc.forense.model.User;
import com.soc.forense.service.UserService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * CRUD completo de usuarios para el administrador. El filtro ya bloquea /admin
 * a quien no sea ADMIN; aqui se valida de nuevo por si alguien mapea otra ruta.
 */
@WebServlet("/admin/usuarios")
public class AdminUsersServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!Web.requireAdmin(req, resp)) {
            return;
        }
        String q = req.getParameter("q");
        List<User> users = (q == null || q.isBlank()) ? userService.listAll() : userService.search(q.trim());
        req.setAttribute("users", users);
        req.setAttribute("q", q);
        req.setAttribute("total", userService.count());
        Long editId = Web.parseLong(req.getParameter("editar"));
        if (editId != null) {
            userService.findById(editId).ifPresent(u -> req.setAttribute("editing", u));
        }
        req.setAttribute("roles", List.of("ADMIN", "ANALYST", "VIEWER"));
        Web.render(req, resp, "admin/users", "Usuarios", "usuarios");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!Web.requireAdmin(req, resp)) {
            return;
        }
        User me = Web.currentUser(req);
        String action = req.getParameter("action");
        Long id = Web.parseLong(req.getParameter("id"));

        try {
            switch (action == null ? "" : action) {
                case "create" -> {
                    String pass = req.getParameter("password");
                    if (pass == null || !pass.equals(req.getParameter("password2"))) {
                        throw new IllegalArgumentException("Las contraseñas no coinciden.");
                    }
                    User u = userService.create(
                            req.getParameter("username"),
                            req.getParameter("email"),
                            req.getParameter("fullName"),
                            req.getParameter("role"),
                            pass,
                            req.getParameter("active") != null);
                    Web.flash(req, "ok", "Usuario " + u.getUsername() + " creado.");
                }
                case "update" -> {
                    requireId(id);
                    String role = req.getParameter("role");
                    boolean active = req.getParameter("active") != null;
                    if (id == me.getId() && (!"ADMIN".equals(role) || !active)) {
                        throw new IllegalArgumentException(
                                "No puede quitarse el rol de administrador ni desactivarse a sí mismo.");
                    }
                    userService.update(id, req.getParameter("email"), req.getParameter("fullName"), role, active);
                    Web.flash(req, "ok", "Usuario actualizado.");
                }
                case "password" -> {
                    requireId(id);
                    String pass = req.getParameter("password");
                    if (pass == null || !pass.equals(req.getParameter("password2"))) {
                        throw new IllegalArgumentException("Las contraseñas no coinciden.");
                    }
                    userService.resetPassword(id, pass);
                    Web.flash(req, "ok", "Contraseña restablecida.");
                }
                case "toggle" -> {
                    requireId(id);
                    if (id == me.getId()) {
                        throw new IllegalArgumentException("No puede desactivar su propia cuenta.");
                    }
                    User u = userService.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
                    userService.setActive(id, !u.isActive());
                    Web.flash(req, "ok", u.getUsername() + (u.isActive() ? " desactivado." : " activado."));
                }
                case "delete" -> {
                    requireId(id);
                    userService.delete(id, me.getId());
                    Web.flash(req, "ok", "Usuario eliminado.");
                }
                default -> Web.flash(req, "error", "Acción desconocida.");
            }
        } catch (IllegalArgumentException e) {
            Web.flash(req, "error", e.getMessage());
            if ("update".equals(action) || "password".equals(action)) {
                Web.redirect(req, resp, "/admin/usuarios?editar=" + id);
                return;
            }
        }
        Web.redirect(req, resp, "/admin/usuarios");
    }

    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Falta el identificador del usuario.");
        }
    }
}
