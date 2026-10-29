package com.soc.forense.web;

import com.soc.forense.service.DashboardService;
import com.soc.forense.util.Web;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** JSON para refrescar el medidor del tablero sin recargar la pagina. */
@WebServlet("/api/tablero")
public class DashboardApiServlet extends HttpServlet {

    private final DashboardService dashboardService = new DashboardService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Web.json(resp, 200, dashboardService.snapshot(true));
    }
}
