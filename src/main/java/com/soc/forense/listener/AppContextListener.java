package com.soc.forense.listener;

import com.soc.forense.config.Database;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Arranca la base de datos al desplegar y la cierra al detener Tomcat. Si
 * MySQL no esta disponible al inicio, la app igual se despliega: el filtro
 * reintenta la conexion y muestra una pagina con instrucciones.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            Database.init();
            sce.getServletContext().removeAttribute("dbError");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "La base de datos no arranco. Se reintentara en la primera peticion.", e);
            sce.getServletContext().setAttribute("dbError", e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        Database.shutdown();
        // Evita los avisos de fuga de memoria de Tomcat al redesplegar desde NetBeans.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getClassLoader() == cl) {
                try {
                    DriverManager.deregisterDriver(d);
                } catch (Exception ignored) {
                }
            }
        }
        try {
            Class<?> cleanup = Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
            cleanup.getMethod("checkedShutdown").invoke(null);
        } catch (Exception ignored) {
        }
    }
}
