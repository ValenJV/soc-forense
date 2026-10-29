package com.soc.forense.web;

import com.soc.forense.util.Web;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        Web.redirect(req, resp, "/login?salida=1");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Cerrar sesion por GET no se permite (CSRF); se vuelve al tablero.
        Web.redirect(req, resp, "/tablero");
    }
}
