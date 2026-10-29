package com.smartfactory.alert;

import com.smartfactory.dao.AlertDao;
import com.smartfactory.model.Alert;
import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.AlertStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AlertLifecycleManager {
    private static final Logger logger = LoggerFactory.getLogger(AlertLifecycleManager.class);

    /**
     * Tracks an alert condition that has been manually resolved by an operator.
     * Prevents recurring active alerts while the process condition remains in the same violation state.
     */
    public record SuppressedCondition(
            AlertSeverity resolvedSeverity,
            Instant resolvedAt,
            String description
    ) {}

    private final AlertDao alertDao;
    // Key: "machineId:alertType" -> current active Alert
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    // Key: "machineId:alertType" -> manually resolved / suppressed condition
    private final Map<String, SuppressedCondition> suppressedConditions = new ConcurrentHashMap<>();
    private final List<Consumer<Alert>> alertListeners = new ArrayList<>();

    // Re-alert cooldown duration (default: 15 minutes). If condition persists longer than this, re-alert.
    private Duration reAlertCooldown = Duration.ofMinutes(15);

    public AlertLifecycleManager(AlertDao alertDao) {
        this.alertDao = alertDao;
        loadExistingActiveAlerts();
    }

    private void loadExistingActiveAlerts() {
        try {
            List<Alert> existing = alertDao.findActiveAlerts();
            for (Alert alert : existing) {
                String key = makeKey(alert.getMachineId(), alert.getAlertType());
                activeAlerts.put(key, alert);
            }
            logger.info("Loaded {} active alerts from database.", activeAlerts.size());
        } catch (Exception e) {
            logger.warn("Could not preload active alerts: {}", e.getMessage());
        }
    }

    /**
     * Reconciles current violations with active alerts and suppressed conditions.
     * - Prevents duplicate active alerts for repeated abnormal readings.
     * - Respects operator manual resolution: suppresses re-alerting until condition clears or escalates.
     * - Escalates severity to CRITICAL if a resolved or acknowledged WARNING worsens.
     * - Automatically clears suppression and resolves alerts when sensor values recover to normal.
     */
    public synchronized void reconcile(Long machineId, String machineCode, List<ThresholdEvaluator.ThresholdViolation> currentViolations) {
        Set<String> activeViolationTypes = new HashSet<>();

        // 1. Process current violations
        for (ThresholdEvaluator.ThresholdViolation violation : currentViolations) {
            activeViolationTypes.add(violation.alertType());
            String key = makeKey(machineId, violation.alertType());
            Alert existing = activeAlerts.get(key);

            if (existing == null) {
                // Check database directly as well
                Optional<Alert> dbActive = alertDao.findActiveByMachineAndType(machineId, violation.alertType());
                if (dbActive.isPresent()) {
                    existing = dbActive.get();
                    activeAlerts.put(key, existing);
                }
            }

            if (existing == null) {
                // Check if this condition was manually resolved by operator
                SuppressedCondition suppressed = suppressedConditions.get(key);
                if (suppressed != null) {
                    boolean isEscalated = (suppressed.resolvedSeverity() == AlertSeverity.WARNING
                            && violation.severity() == AlertSeverity.CRITICAL);
                    boolean isCooldownExpired = reAlertCooldown != null
                            && Duration.between(suppressed.resolvedAt(), Instant.now()).compareTo(reAlertCooldown) > 0;

                    if (!isEscalated && !isCooldownExpired) {
                        // Condition persists at the resolved severity level.
                        // Do NOT resurrect the alert! Operator has already acknowledged and resolved it.
                        continue;
                    }

                    // Condition escalated or cooldown expired -> lift suppression
                    suppressedConditions.remove(key);
                    if (isEscalated) {
                        logger.warn("Condition on machine {} type {} escalated to CRITICAL despite prior resolution: {}",
                                machineCode, violation.alertType(), violation.description());
                    } else {
                        logger.warn("Re-alert cooldown expired for machine {} type {}", machineCode, violation.alertType());
                    }
                }

                // New alert condition!
                Alert newAlert = new Alert(machineId, violation.alertType(), violation.severity(), violation.description());
                newAlert.setMachineCode(machineCode);
                try {
                    newAlert = alertDao.save(newAlert);
                    activeAlerts.put(key, newAlert);
                    logger.warn("Created new {} alert for machine {}: {}", violation.severity(), machineCode, violation.description());
                    notifyAlertListeners(newAlert);
                } catch (Exception e) {
                    logger.error("Failed to persist new alert: {}", e.getMessage());
                }
            } else {
                // Condition persists - update severity if it escalated
                if (violation.severity() == AlertSeverity.CRITICAL && existing.getSeverity() == AlertSeverity.WARNING) {
                    existing.setSeverity(AlertSeverity.CRITICAL);
                    existing.setDescription(violation.description());
                    // Re-activate alert if it was previously acknowledged at WARNING level
                    if (existing.getStatus() == AlertStatus.ACKNOWLEDGED) {
                        existing.setStatus(AlertStatus.ACTIVE);
                        existing.setAcknowledgedAt(null);
                    }
                    try {
                        alertDao.updateAlert(existing);
                    } catch (Exception ignored) {}
                    logger.warn("Escalated alert severity to CRITICAL for machine {} type {}", machineCode, violation.alertType());
                    notifyAlertListeners(existing);
                }
            }
        }

        // 2. Resolve alerts that are no longer violating thresholds (sensor recovery)
        List<String> keysToCheck = new ArrayList<>();
        for (String key : activeAlerts.keySet()) {
            if (key.startsWith(machineId + ":")) {
                keysToCheck.add(key);
            }
        }

        for (String key : keysToCheck) {
            String alertType = key.substring(key.indexOf(':') + 1);
            if (!activeViolationTypes.contains(alertType)) {
                // Condition cleared!
                Alert resolved = activeAlerts.remove(key);
                if (resolved != null) {
                    resolved.setResolvedAt(Instant.now());
                    resolved.setStatus(AlertStatus.RESOLVED);
                    try {
                        alertDao.resolveAlert(resolved.getId());
                        logger.info("Auto-resolved alert for machine {} type {} (condition recovered)", machineCode, alertType);
                        notifyAlertListeners(resolved);
                    } catch (Exception e) {
                        logger.error("Failed to mark alert as resolved in DB: {}", e.getMessage());
                    }
                }
            }
        }

        // Clear suppression for recovered conditions so future violations will alert fresh
        List<String> suppressedKeysToCheck = new ArrayList<>();
        for (String key : suppressedConditions.keySet()) {
            if (key.startsWith(machineId + ":")) {
                suppressedKeysToCheck.add(key);
            }
        }
        for (String key : suppressedKeysToCheck) {
            String alertType = key.substring(key.indexOf(':') + 1);
            if (!activeViolationTypes.contains(alertType)) {
                suppressedConditions.remove(key);
                logger.info("Condition cleared on machine {} type {}. Threshold detector re-armed.", machineCode, alertType);
            }
        }
    }

    public synchronized boolean acknowledgeAlert(Long alertId) {
        boolean success = alertDao.acknowledgeAlert(alertId);
        if (success) {
            for (Alert alert : activeAlerts.values()) {
                if (alert.getId().equals(alertId)) {
                    alert.setAcknowledgedAt(Instant.now());
                    alert.setStatus(AlertStatus.ACKNOWLEDGED);
                    notifyAlertListeners(alert);
                    break;
                }
            }
        }
        return success;
    }

    public synchronized boolean manuallyResolveAlert(Long alertId) {
        Alert found = null;
        for (Alert alert : activeAlerts.values()) {
            if (alert.getId().equals(alertId)) {
                found = alert;
                break;
            }
        }
        if (found == null) {
            found = alertDao.findById(alertId).orElse(null);
        }

        boolean success = alertDao.resolveAlert(alertId);
        if (success) {
            if (found != null) {
                found.setStatus(AlertStatus.RESOLVED);
                found.setResolvedAt(Instant.now());
                String key = makeKey(found.getMachineId(), found.getAlertType());
                activeAlerts.remove(key);
                // Suppress this condition so it does not immediately resurrect on next tick
                suppressedConditions.put(key, new SuppressedCondition(
                        found.getSeverity(),
                        Instant.now(),
                        found.getDescription()
                ));
                notifyAlertListeners(found);
                logger.info("Manually resolved alert {} (machine {} type {}). Condition suppressed until recovery or escalation.",
                        alertId, found.getMachineId(), found.getAlertType());
            } else {
                activeAlerts.values().removeIf(a -> a.getId().equals(alertId));
            }
        }
        return success;
    }

    /**
     * Clears all suppressions for a machine (e.g. when thresholds are updated in configuration).
     */
    public synchronized void rearmMachine(Long machineId) {
        suppressedConditions.keySet().removeIf(key -> key.startsWith(machineId + ":"));
        logger.info("Re-armed alert detection for machine ID {}", machineId);
    }

    public boolean isMachineAlertAcknowledged(Long machineId) {
        boolean hasActive = false;
        for (Alert a : activeAlerts.values()) {
            if (a.getMachineId().equals(machineId)) {
                hasActive = true;
                if (a.getStatus() != AlertStatus.ACKNOWLEDGED) {
                    return false;
                }
            }
        }
        return hasActive;
    }

    public boolean isMachineSuppressed(Long machineId) {
        for (String key : suppressedConditions.keySet()) {
            if (key.startsWith(machineId + ":")) {
                return true;
            }
        }
        return false;
    }

    public Collection<Alert> getActiveAlerts() {
        return Collections.unmodifiableCollection(activeAlerts.values());
    }

    public Map<String, SuppressedCondition> getSuppressedConditions() {
        return Collections.unmodifiableMap(suppressedConditions);
    }

    public void setReAlertCooldown(Duration cooldown) {
        this.reAlertCooldown = cooldown;
    }

    public Duration getReAlertCooldown() {
        return reAlertCooldown;
    }

    public void addAlertListener(Consumer<Alert> listener) {
        alertListeners.add(listener);
    }

    private void notifyAlertListeners(Alert alert) {
        for (Consumer<Alert> listener : alertListeners) {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                logger.error("Error in alert listener: {}", e.getMessage());
            }
        }
    }

    private String makeKey(Long machineId, String alertType) {
        return machineId + ":" + alertType;
    }
}
