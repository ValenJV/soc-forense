package com.soc.forense.dao;

import com.soc.forense.config.Database;
import com.soc.forense.model.AnalysisResult;

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

/**
 * Acceso a datos de los hallazgos del analisis forense. Incluye consultas
 * de agregacion que alimentan el panel principal.
 */
public class AnalysisResultDao {

    private AnalysisResult map(ResultSet rs) throws SQLException {
        AnalysisResult a = new AnalysisResult();
        a.setId(rs.getLong("id"));
        a.setEvidenceId(rs.getLong("evidence_id"));
        long plid = rs.getLong("parsed_log_id");
        a.setParsedLogId(rs.wasNull() ? null : plid);
        a.setSeverity(rs.getString("severity"));
        a.setThreatType(rs.getString("threat_type"));
        a.setThreatDescription(rs.getString("threat_description"));
        a.setMitreTechnique(rs.getString("mitre_technique"));
        a.setIndicators(rs.getString("indicators"));
        a.setRecommendation(rs.getString("recommendation"));
        a.setAiExplanation(rs.getString("ai_explanation"));
        a.setConfidence(rs.getDouble("confidence"));
        a.setRawEvidence(rs.getString("raw_evidence"));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) a.setCreatedAt(c.toLocalDateTime());
        return a;
    }

    public void insertBatch(List<AnalysisResult> results) {
        if (results == null || results.isEmpty()) return;
        String sql = "INSERT INTO analysis_results(evidence_id,parsed_log_id,severity,threat_type,"
                + "threat_description,mitre_technique,indicators,recommendation,ai_explanation,"
                + "confidence,raw_evidence) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (AnalysisResult a : results) {
                ps.setLong(1, a.getEvidenceId());
                if (a.getParsedLogId() != null) {
                    ps.setLong(2, a.getParsedLogId());
                } else {
                    ps.setNull(2, java.sql.Types.BIGINT);
                }
                ps.setString(3, a.getSeverity());
                ps.setString(4, a.getThreatType());
                ps.setString(5, a.getThreatDescription());
                ps.setString(6, a.getMitreTechnique());
                ps.setString(7, a.getIndicators());
                ps.setString(8, a.getRecommendation());
                ps.setString(9, a.getAiExplanation());
                ps.setDouble(10, a.getConfidence());
                ps.setString(11, a.getRawEvidence());
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error insertando hallazgos", e);
        }
    }

    public AnalysisResult create(AnalysisResult a) {
        String sql = "INSERT INTO analysis_results(evidence_id,parsed_log_id,severity,threat_type,"
                + "threat_description,mitre_technique,indicators,recommendation,ai_explanation,"
                + "confidence,raw_evidence) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.getEvidenceId());
            if (a.getParsedLogId() != null) {
                ps.setLong(2, a.getParsedLogId());
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setString(3, a.getSeverity());
            ps.setString(4, a.getThreatType());
            ps.setString(5, a.getThreatDescription());
            ps.setString(6, a.getMitreTechnique());
            ps.setString(7, a.getIndicators());
            ps.setString(8, a.getRecommendation());
            ps.setString(9, a.getAiExplanation());
            ps.setDouble(10, a.getConfidence());
            ps.setString(11, a.getRawEvidence());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) a.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creando hallazgo", e);
        }
        return a;
    }

    public List<AnalysisResult> findByEvidence(long evidenceId) {
        String sql = "SELECT * FROM analysis_results WHERE evidence_id=? "
                + "ORDER BY FIELD(severity,'critical','high','medium','low'), confidence DESC";
        return query(sql, evidenceId);
    }

    public List<AnalysisResult> findAll() {
        String sql = "SELECT * FROM analysis_results "
                + "ORDER BY FIELD(severity,'critical','high','medium','low'), created_at DESC";
        List<AnalysisResult> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error listando hallazgos", e);
        }
        return out;
    }

    /** Amenazas mas graves para el panel, con limite. */
    public List<AnalysisResult> findCriticalThreats(int limit) {
        String sql = "SELECT * FROM analysis_results WHERE severity IN ('critical','high') "
                + "ORDER BY FIELD(severity,'critical','high'), confidence DESC LIMIT ?";
        List<AnalysisResult> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listando amenazas criticas", e);
        }
        return out;
    }

    private List<AnalysisResult> query(String sql, long param) {
        List<AnalysisResult> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error consultando hallazgos", e);
        }
        return out;
    }

    /** Distribucion de hallazgos por severidad, en orden fijo. */
    public Map<String, Long> countBySeverity() {
        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("critical", 0L);
        dist.put("high", 0L);
        dist.put("medium", 0L);
        dist.put("low", 0L);
        String sql = "SELECT severity, COUNT(*) AS total FROM analysis_results GROUP BY severity";
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String sev = rs.getString("severity");
                if (sev != null && dist.containsKey(sev)) {
                    dist.put(sev, rs.getLong("total"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error agregando por severidad", e);
        }
        return dist;
    }

    public long count() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM analysis_results")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando hallazgos", e);
        }
        return 0;
    }

    public long countCritical() {
        try (Connection c = Database.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM analysis_results WHERE severity='critical'")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Error contando criticos", e);
        }
        return 0;
    }

    public void deleteByEvidence(long evidenceId) {
        String sql = "DELETE FROM analysis_results WHERE evidence_id=?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, evidenceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error borrando hallazgos", e);
        }
    }
}
