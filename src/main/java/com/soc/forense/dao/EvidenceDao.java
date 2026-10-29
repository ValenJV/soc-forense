package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.Evidence;

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
 * Acceso a datos de evidencias (archivos de log subidos).
 */
public class EvidenceDao {

    private Evidence map(ResultSet rs) throws SQLException {
        Evidence e = new Evidence();
        e.setId(rs.getLong("id"));
        e.setFilename(rs.getString("filename"));
        e.setLogType(rs.getString("log_type"));
        e.setFileSize(rs.getLong("file_size"));
        e.setSha256(rs.getString("sha256"));
        e.setStoredPath(rs.getString("stored_path"));
        e.setUploadUserId(rs.getLong("upload_user_id"));
        e.setUploadUsername(rs.getString("upload_username"));
        e.setStatus(rs.getString("status"));
        e.setLineCount(rs.getInt("line_count"));
        e.setThreatCount(rs.getInt("threat_count"));
        e.setCriticalCount(rs.getInt("critical_count"));
        e.setErrorMessage(rs.getString("error_message"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) e.setCreatedAt(c.toLocalDateTime());
        Timestamp p = rs.getTimestamp("parsed_at");
        if (p != null) e.setParsedAt(p.toLocalDateTime());
        Timestamp a = rs.getTimestamp("analyzed_at");
        if (a != null) e.setAnalyzedAt(a.toLocalDateTime());
        return e;
    }

    public Evidence create(Evidence e) {
        String sql = "INSERT INTO evidences(filename,log_type,file_size,sha256,stored_path,"
                + "upload_user_id,upload_username,status,line_count,threat_count,critical_count) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.getFilename());
            ps.setString(2, e.getLogType());
            ps.setLong(3, e.getFileSize());
            ps.setString(4, e.getSha256());
            ps.setString(5, e.getStoredPath());
            ps.setLong(6, e.getUploadUserId());
            ps.setString(7, e.getUploadUsername());
            ps.setString(8, e.getStatus() == null ? "ingested" : e.getStatus());
            ps.setInt(9, e.getLineCount());
            ps.setInt(10, e.getThreatCount());
            ps.setInt(11, e.getCriticalCount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) e.setId(keys.getLong(1));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Error creando evidencia", ex);
        }
        return e;
    }

    public Optional<Evidence> findById(long id) {
        String sql = "SELECT * FROM evidences WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando evidencia", e);
        }
        return Optional.empty();
    }

    public Optional<Evidence> findBySha256(String sha256) {
        String sql = "SELECT * FROM evidences WHERE sha256 = ? LIMIT 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error buscando por hash", e);
        }
        return Optional.empty();
    }

    public List<Evidence> findAll() {
        String sql = "SELECT * FROM evidences ORDER BY created_at DESC";
        List<Evidence> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error listando evidencias", e);
        }
        return out;
    }

    public List<Evidence> findByUser(long userId) {
        String sql = "SELECT * FROM evidences WHERE upload_user_id = ? ORDER BY created_at DESC";
        List<Evidence> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando evidencias del usuario", e);
        }
        return out;
    }

    public void updateStatus(long id, String status, String errorMessage) {
        String sql = "UPDATE evidences SET status=?, error_message=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, errorMessage);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error actualizando estado de evidencia", e);
        }
    }

    public void markParsed(long id, int lineCount) {
        String sql = "UPDATE evidences SET status='parsed', line_count=?, parsed_at=?, threat_count=0, critical_count=0, analyzed_at=NULL, error_message=NULL WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lineCount);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marcando como parseada", e);
        }
    }

    public void markAnalyzed(long id, int threatCount, int criticalCount) {
        String sql = "UPDATE evidences SET status='analyzed', threat_count=?, critical_count=?, "
                + "analyzed_at=? WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, threatCount);
            ps.setInt(2, criticalCount);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marcando como analizada", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM evidences WHERE id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error eliminando evidencia", e);
        }
    }

    public long count() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM evidences")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando evidencias", e);
        }
        return 0;
    }

    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM evidences WHERE status=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error contando por estado", e);
        }
        return 0;
    }
}
