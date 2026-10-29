package com.soc.forense.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.soc.forense.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Pequenas utilidades para no repetir codigo en los servlets: usuario actual,
 * token CSRF, mensajes flash, render de vistas y respuestas JSON.
 */
public final class Web {

    public static final String SESSION_USER = "user";
    public static final String SESSION_CSRF = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Web() {
    }

    public static User currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (User) s.getAttribute(SESSION_USER);
    }

    /** Devuelve el token CSRF de la sesion, creandolo si hace falta. */
    public static String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_CSRF);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_CSRF, token);
        }
        return token;
    }

    /** Comparacion en tiempo constante para no filtrar el token por timing. */
    public static boolean validCsrf(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) {
            return false;
        }
        String expected = (String) s.getAttribute(SESSION_CSRF);
        String sent = req.getHeader("X-CSRF-Token");
        if (sent == null) {
            sent = req.getParameter("_csrf");
        }
        if (expected == null || sent == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(expected.getBytes(), sent.getBytes());
    }

    public static void flash(HttpServletRequest req, String type, String message) {
        HttpSession s = req.getSession();
        s.setAttribute("flashType", type);
        s.setAttribute("flashMessage", message);
    }

    /** Pasa el flash de la sesion al request para que la vista lo muestre una sola vez. */
    public static void popFlash(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) {
            return;
        }
        Object msg = s.getAttribute("flashMessage");
        if (msg != null) {
            req.setAttribute("flashType", s.getAttribute("flashType"));
            req.setAttribute("flashMessage", msg);
            s.removeAttribute("flashType");
            s.removeAttribute("flashMessage");
        }
    }

    public static void render(HttpServletRequest req, HttpServletResponse resp, String view,
                              String title, String active) throws ServletException, IOException {
        popFlash(req);
        req.setAttribute("pageTitle", title);
        req.setAttribute("active", active);
        req.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(req, resp);
    }

    public static void redirect(HttpServletRequest req, HttpServletResponse resp, String path) throws IOException {
        resp.sendRedirect(req.getContextPath() + path);
    }

    public static void json(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(body));
    }

    public static void jsonError(HttpServletResponse resp, int status, String message) throws IOException {
        json(resp, status, Map.of("ok", false, "error", message));
    }

    public static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void forbidden(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /** true si el usuario puede cargar, parsear y analizar. Si no, responde 403. */
    public static boolean requireAnalyst(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = currentUser(req);
        if (u == null || !u.canAnalyze()) {
            forbidden(req, resp);
            return false;
        }
        return true;
    }

    public static boolean requireAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = currentUser(req);
        if (u == null || !u.isAdmin()) {
            forbidden(req, resp);
            return false;
        }
        return true;
    }
}
