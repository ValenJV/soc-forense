package com.soc.forense.web;

import com.soc.forense.model.User;
import com.soc.forense.service.AuthService;
import com.soc.forense.util.LoginThrottle;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (Web.currentUser(req) != null) {
            Web.redirect(req, resp, "/tablero");
            return;
        }
        if (req.getParameter("salida") != null) {
            req.setAttribute("info", "Sesión cerrada.");
        }
        if (req.getParameter("inactiva") != null) {
            req.setAttribute("error", "Su cuenta fue desactivada. Hable con un administrador.");
        }
        req.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String ip = req.getRemoteAddr();
        req.setAttribute("username", username);

        long locked = LoginThrottle.secondsLocked(username, ip);
        if (locked > 0) {
            req.setAttribute("error", "Demasiados intentos fallidos. Espere " + locked + " segundos.");
            req.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(req, resp);
            return;
        }

        Optional<User> user = authService.authenticate(username, password);
        if (user.isEmpty()) {
            LoginThrottle.fail(username, ip);
            req.setAttribute("error", "Usuario o contraseña incorrectos.");
            req.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(req, resp);
            return;
        }

        LoginThrottle.success(username, ip);
        HttpSession session = req.getSession();
        req.changeSessionId(); // evita fijacion de sesion
        session.removeAttribute(Web.SESSION_CSRF); // token nuevo tras autenticar
        session.setAttribute(Web.SESSION_USER, user.get());
        Web.flash(req, "ok", "Bienvenido, " + displayName(user.get()) + ".");
        Web.redirect(req, resp, "/tablero");
    }

    private String displayName(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }
}
