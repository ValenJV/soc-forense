package com.soc.forense.filter;

import com.soc.forense.config.Database;
import com.soc.forense.dao.UserDao;
import com.soc.forense.model.User;
import com.soc.forense.util.Web;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;

/**
 * Punto unico de control para cada peticion:
 * 1. fuerza UTF-8 y cabeceras de seguridad,
 * 2. comprueba que MySQL este listo (y reintenta si no),
 * 3. exige sesion fuera de /login y /assets,
 * 4. recarga el usuario desde la base para aplicar al instante cambios de rol
 *    o desactivaciones hechas por un administrador,
 * 5. restringe /admin a administradores,
 * 6. valida el token CSRF en todo POST.
 */
@WebFilter(urlPatterns = "/*")
public class AuthFilter implements Filter {

    private static final long RETRY_MS = 5000;
    private volatile long lastDbAttempt = 0;
    private final UserDao userDao = new UserDao();

    @Override
    public void doFilter(ServletRequest sreq, ServletResponse sresp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) sreq;
        HttpServletResponse resp = (HttpServletResponse) sresp;

        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "same-origin");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (path.startsWith("/assets/") || path.equals("/favicon.ico")) {
            chain.doFilter(req, resp);
            return;
        }

        resp.setHeader("Cache-Control", "no-store");
        req.setAttribute("ctx", req.getContextPath());

        if (!ensureDatabase(req)) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            req.getRequestDispatcher("/WEB-INF/views/error/db.jsp").forward(req, resp);
            return;
        }

        HttpSession session = req.getSession();
        req.setAttribute("csrf", Web.csrfToken(session));

        boolean isApi = path.startsWith("/api/");
        boolean isPublic = path.equals("/login");

        User sessionUser = (User) session.getAttribute(Web.SESSION_USER);

        if (path.isEmpty() || path.equals("/")) {
            Web.redirect(req, resp, sessionUser == null ? "/login" : "/tablero");
            return;
        }

        if (!isPublic) {
            if (sessionUser == null) {
                if (isApi) {
                    Web.jsonError(resp, 401, "La sesión expiró. Vuelva a iniciar sesión.");
                } else {
                    Web.redirect(req, resp, "/login");
                }
                return;
            }
            Optional<User> fresh = userDao.findById(sessionUser.getId());
            if (fresh.isEmpty() || !fresh.get().isActive()) {
                session.invalidate();
                if (isApi) {
                    Web.jsonError(resp, 401, "Su cuenta ya no está activa.");
                } else {
                    Web.redirect(req, resp, "/login?inactiva=1");
                }
                return;
            }
            sessionUser = fresh.get();
            session.setAttribute(Web.SESSION_USER, sessionUser);
            req.setAttribute("currentUser", sessionUser);

            if (path.startsWith("/admin") && !sessionUser.isAdmin()) {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        if ("POST".equalsIgnoreCase(req.getMethod()) && !Web.validCsrf(req)) {
            if (isApi) {
                Web.jsonError(resp, 403, "Token de seguridad inválido. Recargue la página.");
            } else {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Token CSRF inválido");
            }
            return;
        }

        chain.doFilter(req, resp);
    }

    private boolean ensureDatabase(HttpServletRequest req) {
        if (Database.isReady()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastDbAttempt < RETRY_MS) {
            return false;
        }
        lastDbAttempt = now;
        try {
            Database.init();
            req.getServletContext().removeAttribute("dbError");
            return true;
        } catch (RuntimeException e) {
            req.getServletContext().setAttribute("dbError", e.getMessage());
            return false;
        }
    }
}
