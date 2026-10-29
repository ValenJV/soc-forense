package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.ParsedLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de las lineas de log ya parseadas.
 */
public class ParsedLogDao {

    private ParsedLog map(ResultSet rs) throws SQLException {
        ParsedLog p = new ParsedLog();
        p.setId(rs.getLong("id"));
        p.setEvidenceId(rs.getLong("evidence_id"));
        p.setLineNumber(rs.getInt("line_number"));
        Timestamp t = rs.getTimestamp("event_time");
        if (t != null) p.setEventTime(t.toLocalDateTime());
        p.setHostname(rs.getString("hostname"));
        p.setService(rs.getString("service"));
        int pid = rs.getInt("pid");
        p.setPid(rs.wasNull() ? null : pid);
        p.setUsername(rs.getString("username"));
        p.setAction(rs.getString("action"));
        p.setTarget(rs.getString("target"));
        p.setSourceIp(rs.getString("source_ip"));
        p.setRawLine(rs.getString("raw_line"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) p.setCreatedAt(c.toLocalDateTime());
        return p;
    }

    /** Inserta un lote de lineas parseadas en una sola transaccion. */
    public void insertBatch(List<ParsedLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        String sql = "INSERT INTO parsed_logs(evidence_id,line_number,event_time,hostname,service,"
                + "pid,username,action,target,source_ip,raw_line) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            for (ParsedLog p : logs) {
                ps.setLong(1, p.getEvidenceId());
                ps.setInt(2, p.getLineNumber());
                if (p.getEventTime() != null) {
                    ps.setTimestamp(3, Timestamp.valueOf(p.getEventTime()));
                } else {
                    ps.setNull(3, java.sql.Types.TIMESTAMP);
                }
                ps.setString(4, p.getHostname());
                ps.setString(5, p.getService());
                if (p.getPid() != null) {
                    ps.setInt(6, p.getPid());
                } else {
                    ps.setNull(6, java.sql.Types.INTEGER);
                }
                ps.setString(7, p.getUsername());
                ps.setString(8, p.getAction());
                ps.setString(9, p.getTarget());
                ps.setString(10, p.getSourceIp());
                ps.setString(11, p.getRawLine());
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
            // Recupera los ids generados y los asigna en orden.
            try (ResultSet keys = ps.getGeneratedKeys()) {
                int i = 0;
                while (keys.next() && i < logs.size()) {
                    logs.get(i++).setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando lote de logs", e);
        }
    }

    public List<ParsedLog> findByEvidence(long evidenceId) {
        String sql = "SELECT * FROM parsed_logs WHERE evidence_id=? ORDER BY line_number ASC";
        List<ParsedLog> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, evidenceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando logs de la evidencia", e);
        }
        return out;
    }

    public List<ParsedLog> findByEvidence(long evidenceId, int limit) {
        String sql = "SELECT * FROM parsed_logs WHERE evidence_id=? ORDER BY line_number ASC LIMIT ?";
        List<ParsedLog> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, evidenceId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando logs de la evidencia", e);
        }
        return out;
    }

    public long countByEvidence(long evidenceId) {
        String sql = "SELECT COUNT(*) FROM parsed_logs WHERE evidence_id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, evidenceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error contando logs", e);
        }
        return 0;
    }

    public void deleteByEvidence(long evidenceId) {
        String sql = "DELETE FROM parsed_logs WHERE evidence_id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, evidenceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error borrando logs de la evidencia", e);
        }
    }
}
