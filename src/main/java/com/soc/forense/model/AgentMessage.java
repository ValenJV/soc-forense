package com.soc.forense.model;

import java.time.LocalDateTime;

/**
 * Mensaje de la conversacion con el agente de IA. Se guarda por usuario para
 * mantener el hilo del analisis y poder retomarlo.
 */
public class AgentMessage {

    private long id;
    private long userId;
    private String role;   // user, assistant, system
    private String content;
    private Long evidenceId; // contexto opcional
    private LocalDateTime createdAt;

    public AgentMessage() {
    }

    public AgentMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(Long evidenceId) {
        this.evidenceId = evidenceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
