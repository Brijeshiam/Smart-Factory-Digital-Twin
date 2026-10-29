package com.smartfactory.model;

import java.time.Instant;

public class Alert {
    private Long id;
    private Long machineId;
    private String machineCode; // Convenience field for UI display
    private String alertType;
    private AlertSeverity severity;
    private String description;
    private Instant createdAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private AlertStatus status;

    public Alert() {
        this.createdAt = Instant.now();
        this.status = AlertStatus.ACTIVE;
        this.severity = AlertSeverity.WARNING;
    }

    public Alert(Long machineId, String alertType, AlertSeverity severity, String description) {
        this.machineId = machineId;
        this.alertType = alertType;
        this.severity = severity != null ? severity : AlertSeverity.WARNING;
        this.description = description;
        this.createdAt = Instant.now();
        this.status = AlertStatus.ACTIVE;
    }

    public Alert(Long id, Long machineId, String alertType, AlertSeverity severity,
                 String description, Instant createdAt, Instant acknowledgedAt,
                 Instant resolvedAt, AlertStatus status) {
        this.id = id;
        this.machineId = machineId;
        this.alertType = alertType;
        this.severity = severity != null ? severity : AlertSeverity.WARNING;
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
        this.status = status != null ? status : AlertStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMachineId() {
        return machineId;
    }

    public void setMachineId(Long machineId) {
        this.machineId = machineId;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public boolean isAcknowledged() {
        return this.acknowledgedAt != null || this.status == AlertStatus.ACKNOWLEDGED;
    }

    public boolean isResolved() {
        return this.resolvedAt != null || this.status == AlertStatus.RESOLVED;
    }

    @Override
    public String toString() {
        return String.format("Alert[id=%d, machineId=%d, type=%s, severity=%s, status=%s]",
                id, machineId, alertType, severity, status);
    }
}
