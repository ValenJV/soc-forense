package com.soc.forense.web;

import com.soc.forense.model.User;
import com.soc.forense.service.AuthService;
import com.soc.forense.service.UserService;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Cada usuario puede ver su cuenta y cambiar su propia contraseña. */
@WebServlet("/perfil")
public class ProfileServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Web.render(req, resp, "auth/profile", "Mi cuenta", "perfil");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User me = Web.currentUser(req);
        String current = req.getParameter("current");
        String nueva = req.getParameter("password");
        String repetir = req.getParameter("password2");

        try {
            if (authService.authenticate(me.getUsername(), current).isEmpty()) {
                throw new IllegalArgumentException("La contraseña actual no es correcta.");
            }
            if (nueva == null || !nueva.equals(repetir)) {
                throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
            }
            userService.resetPassword(me.getId(), nueva);
            Web.flash(req, "ok", "Contraseña actualizada.");
        } catch (IllegalArgumentException e) {
            Web.flash(req, "error", e.getMessage());
        }
        Web.redirect(req, resp, "/perfil");
    }
}
