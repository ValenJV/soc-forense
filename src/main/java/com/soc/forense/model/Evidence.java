package com.soc.forense.model;

import java.time.LocalDateTime;

/**
 * Un archivo de log subido a la plataforma. Se guarda su hash SHA-256
 * para cadena de custodia y se le sigue el estado a lo largo del pipeline
 * (ingested -> parsed -> analyzed).
 */
public class Evidence {

    private long id;
    private String filename;
    private String logType;      // auth, sudo, audit, syslog, apache, nginx, firewall, windows, generic
    private long fileSize;
    private String sha256;
    private String storedPath;
    private long uploadUserId;
    private String uploadUsername; // desnormalizado para listados
    private String status;       // ingested, parsed, analyzed, error
    private int lineCount;
    private int threatCount;
    private int criticalCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime parsedAt;
    private LocalDateTime analyzedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public long getUploadUserId() {
        return uploadUserId;
    }

    public void setUploadUserId(long uploadUserId) {
        this.uploadUserId = uploadUserId;
    }

    public String getUploadUsername() {
        return uploadUsername;
    }

    public void setUploadUsername(String uploadUsername) {
        this.uploadUsername = uploadUsername;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public int getThreatCount() {
        return threatCount;
    }

    public void setThreatCount(int threatCount) {
        this.threatCount = threatCount;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(LocalDateTime parsedAt) {
        this.parsedAt = parsedAt;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public String getShortHash() {
        return sha256 == null ? "" : sha256.substring(0, Math.min(12, sha256.length()));
    }

    public String getHumanSize() {
        long b = fileSize;
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.1f MB", b / (1024.0 * 1024.0));
    }
}
