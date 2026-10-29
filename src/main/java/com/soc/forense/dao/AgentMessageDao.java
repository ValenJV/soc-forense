package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.AgentMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Historial de conversacion con el agente de IA. Se guarda por usuario para
 * poder retomar el hilo y para dar contexto a las siguientes preguntas.
 */
public class AgentMessageDao {

    private AgentMessage map(ResultSet rs) throws SQLException {
        AgentMessage m = new AgentMessage();
        m.setId(rs.getLong("id"));
        m.setUserId(rs.getLong("user_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        long ev = rs.getLong("evidence_id");
        if (!rs.wasNull()) m.setEvidenceId(ev);
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) m.setCreatedAt(c.toLocalDateTime());
        return m;
    }

    public AgentMessage create(AgentMessage m) {
        String sql = "INSERT INTO agent_messages (user_id, role, content, evidence_id) "
                + "VALUES (?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, m.getUserId());
            ps.setString(2, m.getRole());
            ps.setString(3, m.getContent());
            if (m.getEvidenceId() != null) ps.setLong(4, m.getEvidenceId());
            else ps.setNull(4, java.sql.Types.BIGINT);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) m.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error guardando mensaje del agente", e);
        }
        return m;
    }

    /** Ultimos mensajes del usuario, en orden cronologico ascendente. */
    public List<AgentMessage> recentByUser(long userId, int limit) {
        String sql = "SELECT * FROM agent_messages WHERE user_id = ? "
                + "ORDER BY id DESC LIMIT ?";
        List<AgentMessage> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error leyendo historial del agente", e);
        }
        Collections.reverse(out);
        return out;
    }

    public void clearForUser(long userId) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM agent_messages WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error limpiando historial del agente", e);
        }
    }
}
