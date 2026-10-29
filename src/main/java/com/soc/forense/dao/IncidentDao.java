package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.Incident;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso a datos de incidentes. Un incidente es el caso de respuesta que se
 * abre a partir de uno o varios hallazgos, con su plan de contencion.
 */
public class IncidentDao {

    private Incident map(ResultSet rs) throws SQLException {
        Incident i = new Incident();
        i.setId(rs.getLong("id"));
        i.setTitle(rs.getString("title"));
        i.setSeverity(rs.getString("severity"));
        i.setStatus(rs.getString("status"));
        long ev = rs.getLong("evidence_id");
        if (!rs.wasNull()) i.setEvidenceId(ev);
        i.setSummary(rs.getString("summary"));
        i.setResponsePlan(rs.getString("response_plan"));
        i.setCreatedByUserId(rs.getLong("created_by_user_id"));
        i.setCreatedByUsername(rs.getString("created_by_username"));
        i.setAssignedTo(rs.getString("assigned_to"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) i.setCreatedAt(c.toLocalDateTime());
        Timestamp u = rs.getTimestamp("updated_at");
        if (u != null) i.setUpdatedAt(u.toLocalDateTime());
        return i;
    }

    public Incident create(Incident i) {
        String sql = "INSERT INTO incidents "
                + "(title, severity, status, evidence_id, summary, response_plan, "
                + "created_by_user_id, created_by_username, assigned_to) "
                + "VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, i.getTitle());
            ps.setString(2, i.getSeverity());
            ps.setString(3, i.getStatus() == null ? "open" : i.getStatus());
            if (i.getEvidenceId() != null) ps.setLong(4, i.getEvidenceId());
            else ps.setNull(4, java.sql.Types.BIGINT);
            ps.setString(5, i.getSummary());
            ps.setString(6, i.getResponsePlan());
            ps.setLong(7, i.getCreatedByUserId());
            ps.setString(8, i.getCreatedByUsername());
            ps.setString(9, i.getAssignedTo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) i.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando incidente", e);
        }
        return i;
    }

    public Optional<Incident> findById(long id) {
        String sql = "SELECT * FROM incidents WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando incidente", e);
        }
        return Optional.empty();
    }

    public List<Incident> findAll() {
        String sql = "SELECT * FROM incidents ORDER BY "
                + "FIELD(severity,'critical','high','medium','low'), created_at DESC";
        List<Incident> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error listando incidentes", e);
        }
        return out;
    }

    public void update(Incident i) {
        String sql = "UPDATE incidents SET title=?, severity=?, status=?, summary=?, "
                + "response_plan=?, assigned_to=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, i.getTitle());
            ps.setString(2, i.getSeverity());
            ps.setString(3, i.getStatus());
            ps.setString(4, i.getSummary());
            ps.setString(5, i.getResponsePlan());
            ps.setString(6, i.getAssignedTo());
            ps.setLong(7, i.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando incidente", e);
        }
    }

    public void updateStatus(long id, String status) {
        String sql = "UPDATE incidents SET status=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error cambiando estado de incidente", e);
        }
    }

    public void delete(long id) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM incidents WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando incidente", e);
        }
    }

    public long count() {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM incidents");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando incidentes", e);
        }
        return 0;
    }

    public long countOpen() {
        String sql = "SELECT COUNT(*) FROM incidents WHERE status <> 'closed'";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando incidentes abiertos", e);
        }
        return 0;
    }

    /** Conteo por estado, en orden de flujo, para el tablero. */
    public Map<String, Long> countByStatus() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("open", 0L);
        out.put("investigating", 0L);
        out.put("contained", 0L);
        out.put("closed", 0L);
        String sql = "SELECT status, COUNT(*) AS n FROM incidents GROUP BY status";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getString("status"), rs.getLong("n"));
        } catch (SQLException e) {
            throw new RuntimeException("Error agrupando incidentes por estado", e);
        }
        return out;
    }
}
