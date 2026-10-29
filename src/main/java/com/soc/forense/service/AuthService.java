package com.soc.forense.service;

import com.soc.forense.dao.UserDao;
import com.soc.forense.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

/**
 * Autenticacion. Verifica credenciales contra el hash bcrypt guardado y
 * actualiza la fecha del ultimo acceso. No maneja la sesion: eso lo hace el
 * servlet con HttpSession.
 */
public class AuthService {

    private final UserDao userDao = new UserDao();

    /**
     * Comprueba usuario y contraseña. Devuelve el usuario si todo cuadra y la
     * cuenta esta activa; si no, un Optional vacio (no distingue el motivo hacia
     * afuera para no dar pistas a quien intente adivinar).
     */
    public Optional<User> authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null
                || username.isBlank() || rawPassword.isBlank()) {
            return Optional.empty();
        }
        Optional<User> found = userDao.findByUsername(username.trim());
        if (found.isEmpty()) {
            // Igual gastamos tiempo verificando un hash falso para no filtrar
            // por el tiempo de respuesta si el usuario existe o no.
            BCrypt.checkpw(rawPassword, "$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Vdd8Va6QsXk5U1Q5.p0oM5iC5pT2W");
            return Optional.empty();
        }
        User u = found.get();
        if (!u.isActive()) {
            return Optional.empty();
        }
        boolean ok;
        try {
            ok = BCrypt.checkpw(rawPassword, u.getPasswordHash());
        } catch (IllegalArgumentException e) {
            ok = false;
        }
        if (!ok) {
            return Optional.empty();
        }
        userDao.updateLastLogin(u.getId());
        return Optional.of(u);
    }

    public static String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(10));
    }
}
