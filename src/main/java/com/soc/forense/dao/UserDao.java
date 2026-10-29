package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de usuarios. Es la base del CRUD que usa el administrador
 * y de la autenticacion.
 */
public class UserDao {

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getBoolean("active"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) u.setCreatedAt(c.toLocalDateTime());
        Timestamp up = rs.getTimestamp("updated_at");
        if (up != null) u.setUpdatedAt(up.toLocalDateTime());
        Timestamp l = rs.getTimestamp("last_login_at");
        if (l != null) u.setLastLoginAt(l.toLocalDateTime());
        return u;
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario por nombre", e);
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario por email", e);
        }
        return Optional.empty();
    }

    public Optional<User> findById(long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuario por id", e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        List<User> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error listando usuarios", e);
        }
        return out;
    }

    /** Busqueda con filtro opcional de texto sobre usuario, email o nombre. */
    public List<User> search(String term) {
        if (term == null || term.isBlank()) return findAll();
        String sql = "SELECT * FROM users WHERE username LIKE ? OR email LIKE ? "
                + "OR full_name LIKE ? ORDER BY created_at DESC";
        String like = "%" + term.trim() + "%";
        List<User> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando usuarios", e);
        }
        return out;
    }

    public User create(User u) {
        String sql = "INSERT INTO users(username,email,full_name,password_hash,role,active) "
                + "VALUES(?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getFullName());
            ps.setString(4, u.getPasswordHash());
            ps.setString(5, u.getRole());
            ps.setBoolean(6, u.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) u.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando usuario", e);
        }
        return u;
    }

    /** Actualiza datos de perfil y rol. No toca la contrasena. */
    public void update(User u) {
        String sql = "UPDATE users SET email=?, full_name=?, role=?, active=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.getEmail());
            ps.setString(2, u.getFullName());
            ps.setString(3, u.getRole());
            ps.setBoolean(4, u.isActive());
            ps.setLong(5, u.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando usuario", e);
        }
    }

    public void updatePassword(long userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error cambiando contrasena", e);
        }
    }

    public void setActive(long userId, boolean active) {
        String sql = "UPDATE users SET active=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error cambiando estado", e);
        }
    }

    public void updateLastLogin(long userId) {
        String sql = "UPDATE users SET last_login_at=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando ultimo acceso", e);
        }
    }

    public void delete(long userId) {
        String sql = "DELETE FROM users WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando usuario", e);
        }
    }

    public long count() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando usuarios", e);
        }
        return 0;
    }

    public long countAdmins() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM users WHERE role='ADMIN' AND active=1")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando administradores", e);
        }
        return 0;
    }
}
