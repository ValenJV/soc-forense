package com.soc.forense.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Hallazgo del analisis: una amenaza detectada sobre una o varias lineas de
 * log, con su severidad, tecnica MITRE ATT&CK asociada, indicadores de
 * compromiso y la explicacion del agente de IA.
 */
public class AnalysisResult {

    private long id;
    private long evidenceId;
    private Long parsedLogId;
    private String severity;        // low, medium, high, critical
    private String threatType;
    private String threatDescription;
    private String mitreTechnique;  // ej: T1110 Brute Force
    private String indicators;      // JSON array serializado
    private String recommendation;
    private String aiExplanation;
    private double confidence;
    private String rawEvidence;     // linea/lineas de log que dispararon el hallazgo
    private LocalDateTime createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(long evidenceId) {
        this.evidenceId = evidenceId;
    }

    public Long getParsedLogId() {
        return parsedLogId;
    }

    public void setParsedLogId(Long parsedLogId) {
        this.parsedLogId = parsedLogId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getThreatType() {
        return threatType;
    }

    public void setThreatType(String threatType) {
        this.threatType = threatType;
    }

    public String getThreatDescription() {
        return threatDescription;
    }

    public void setThreatDescription(String threatDescription) {
        this.threatDescription = threatDescription;
    }

    public String getMitreTechnique() {
        return mitreTechnique;
    }

    public void setMitreTechnique(String mitreTechnique) {
        this.mitreTechnique = mitreTechnique;
    }

    public String getIndicators() {
        return indicators;
    }

    public void setIndicators(String indicators) {
        this.indicators = indicators;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getRawEvidence() {
        return rawEvidence;
    }

    public void setRawEvidence(String rawEvidence) {
        this.rawEvidence = rawEvidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getConfidencePercent() {
        return (int) Math.round(confidence * 100);
    }

    /** Convierte el JSON de indicadores en una lista simple para la vista. */
    public List<String> getIndicatorList() {
        List<String> out = new ArrayList<>();
        if (indicators == null || indicators.isBlank()) {
            return out;
        }
        String s = indicators.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        for (String part : s.split(",")) {
            String cleaned = part.trim().replaceAll("^\"|\"$", "").trim();
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }
}
