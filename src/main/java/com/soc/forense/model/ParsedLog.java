package com.soc.forense.model;

import java.time.LocalDateTime;

/**
 * Una linea de log ya normalizada. El parser extrae los campos comunes
 * (timestamp, host, servicio, usuario, accion, IP de origen) para que el
 * agente de IA razone sobre datos estructurados y no sobre texto crudo.
 */
public class ParsedLog {

    private long id;
    private long evidenceId;
    private int lineNumber;
    private LocalDateTime eventTime;
    private String hostname;
    private String service;
    private Integer pid;
    private String username;
    private String action;
    private String target;
    private String sourceIp;
    private String rawLine;
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

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getRawLine() {
        return rawLine;
    }

    public void setRawLine(String rawLine) {
        this.rawLine = rawLine;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
