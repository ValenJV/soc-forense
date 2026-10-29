package com.soc.forense.service;

import com.soc.forense.dao.UserDao;
import com.soc.forense.model.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reglas de negocio del CRUD de usuarios. Valida datos, aplica el hash de la
 * contraseña y protege invariantes como no quedarnos sin ningun administrador.
 */
public class UserService {

    public static final Set<String> ROLES = Set.of("ADMIN", "ANALYST", "VIEWER");

    private final UserDao userDao = new UserDao();

    public List<User> listAll() {
        return userDao.findAll();
    }

    public List<User> search(String term) {
        if (term == null || term.isBlank()) {
            return userDao.findAll();
        }
        return userDao.search(term.trim());
    }

    public Optional<User> findById(long id) {
        return userDao.findById(id);
    }

    public long count() {
        return userDao.count();
    }

    /**
     * Crea un usuario nuevo. Lanza IllegalArgumentException con un mensaje claro
     * si algo no valida; el servlet lo muestra en la pantalla.
     */
    public User create(String username, String email, String fullName,
                       String role, String rawPassword, boolean active) {
        validateUsername(username);
        validateEmail(email);
        validateRole(role);
        validatePassword(rawPassword);

        if (userDao.findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
        }
        if (userDao.findByEmail(email.trim()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        }

        User u = new User();
        u.setUsername(username.trim());
        u.setEmail(email.trim());
        u.setFullName(fullName == null ? "" : fullName.trim());
        u.setRole(role.toUpperCase());
        u.setPasswordHash(AuthService.hashPassword(rawPassword));
        u.setActive(active);
        return userDao.create(u);
    }

    /**
     * Actualiza perfil y rol. No toca la contraseña. Protege el ultimo admin:
     * no permite degradar al unico administrador que queda.
     */
    public void update(long id, String email, String fullName, String role, boolean active) {
        User u = userDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
        validateEmail(email);
        validateRole(role);

        Optional<User> byEmail = userDao.findByEmail(email.trim());
        if (byEmail.isPresent() && byEmail.get().getId() != id) {
            throw new IllegalArgumentException("Otro usuario ya usa ese correo.");
        }

        boolean degradaAdmin = u.isAdmin() && !"ADMIN".equalsIgnoreCase(role);
        boolean desactivaAdmin = u.isAdmin() && !active;
        if ((degradaAdmin || desactivaAdmin) && userDao.countAdmins() <= 1) {
            throw new IllegalArgumentException(
                    "No puede dejar el sistema sin administradores activos.");
        }

        u.setEmail(email.trim());
        u.setFullName(fullName == null ? "" : fullName.trim());
        u.setRole(role.toUpperCase());
        u.setActive(active);
        userDao.update(u);
    }

    public void resetPassword(long id, String rawPassword) {
        validatePassword(rawPassword);
        userDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
        userDao.updatePassword(id, AuthService.hashPassword(rawPassword));
    }

    public void setActive(long id, boolean active) {
        User u = userDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
        if (u.isAdmin() && !active && userDao.countAdmins() <= 1) {
            throw new IllegalArgumentException(
                    "No puede desactivar al único administrador.");
        }
        userDao.setActive(id, active);
    }

    public void delete(long id, long currentUserId) {
        if (id == currentUserId) {
            throw new IllegalArgumentException("No puede eliminar su propia cuenta.");
        }
        User u = userDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe."));
        if (u.isAdmin() && userDao.countAdmins() <= 1) {
            throw new IllegalArgumentException(
                    "No puede eliminar al único administrador.");
        }
        userDao.delete(id);
        // El historial del agente es personal: se va con la cuenta.
        new com.soc.forense.dao.AgentMessageDao().clearForUser(id);
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("El nombre de usuario debe tener al menos 3 caracteres.");
        }
        if (!username.trim().matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("El usuario solo admite letras, números, punto, guion y guion bajo.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
    }

    private void validateRole(String role) {
        if (role == null || !ROLES.contains(role.toUpperCase())) {
            throw new IllegalArgumentException("Rol inválido; use ADMIN, ANALYST o VIEWER.");
        }
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
    }
}
