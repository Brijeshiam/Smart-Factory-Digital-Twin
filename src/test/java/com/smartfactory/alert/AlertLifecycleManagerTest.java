package com.smartfactory.alert;

import com.smartfactory.dao.AlertDao;
import com.smartfactory.model.Alert;
import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.AlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class AlertLifecycleManagerTest {

    private AlertDao testAlertDao;
    private AlertLifecycleManager manager;
    private final List<Alert> alertStore = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        alertStore.clear();

        // Anonymous in-memory subclass of AlertDao to test AlertLifecycleManager logic in isolation
        testAlertDao = new AlertDao(null) {
            @Override
            public Alert save(Alert alert) {
                alert.setId(idGenerator.getAndIncrement());
                alertStore.add(alert);
                return alert;
            }

            @Override
            public Optional<Alert> findActiveByMachineAndType(Long machineId, String alertType) {
                return alertStore.stream()
                        .filter(a -> a.getMachineId().equals(machineId)
                                && a.getAlertType().equals(alertType)
                                && (a.getStatus() == AlertStatus.ACTIVE || a.getStatus() == AlertStatus.ACKNOWLEDGED))
                        .findFirst();
            }

            @Override
            public List<Alert> findActiveAlerts() {
                return alertStore.stream()
                        .filter(a -> a.getStatus() == AlertStatus.ACTIVE || a.getStatus() == AlertStatus.ACKNOWLEDGED)
                        .toList();
            }

            @Override
            public boolean acknowledgeAlert(Long alertId) {
                for (Alert a : alertStore) {
                    if (a.getId().equals(alertId) && a.getStatus() == AlertStatus.ACTIVE) {
                        a.setStatus(AlertStatus.ACKNOWLEDGED);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean resolveAlert(Long alertId) {
                for (Alert a : alertStore) {
                    if (a.getId().equals(alertId)) {
                        a.setStatus(AlertStatus.RESOLVED);
                        return true;
                    }
                }
                return false;
            }
        };

        manager = new AlertLifecycleManager(testAlertDao);
    }

    @Test
    @DisplayName("Initial violation should create a new ACTIVE alert")
    void testNewAlertCreation() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temperature elevated",
                "Temperature",
                84.0,
                80.0
        );

        manager.reconcile(1L, "CNC-01", List.of(violation));

        assertEquals(1, alertStore.size());
        Alert created = alertStore.getFirst();
        assertEquals(AlertStatus.ACTIVE, created.getStatus());
        assertEquals("TEMPERATURE_HIGH", created.getAlertType());
    }

    @Test
    @DisplayName("Repeated abnormal readings should NOT create duplicate active alerts")
    void testDuplicateAlertPrevention() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temperature elevated",
                "Temperature",
                84.0,
                80.0
        );

        // Reconcile 5 times in a row with the same condition
        for (int i = 0; i < 5; i++) {
            manager.reconcile(1L, "CNC-01", List.of(violation));
        }

        assertEquals(1, alertStore.size(), "Should only have created exactly one alert, not 5");
    }

    @Test
    @DisplayName("Acknowledging an alert should update status to ACKNOWLEDGED but remain active condition")
    void testAlertAcknowledgement() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(violation));

        Alert alert = alertStore.getFirst();
        boolean acked = manager.acknowledgeAlert(alert.getId());

        assertTrue(acked);
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        assertTrue(alert.isAcknowledged());
        assertFalse(alert.isResolved());
    }

    @Test
    @DisplayName("When sensor recovers (empty violations), active alert should be automatically resolved")
    void testAutomaticAlertResolutionOnRecovery() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(violation));

        Alert alert = alertStore.getFirst();
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());

        // Condition clears!
        manager.reconcile(1L, "CNC-01", Collections.emptyList());

        assertEquals(AlertStatus.RESOLVED, alert.getStatus());
        assertTrue(alert.isResolved());
        assertTrue(manager.getActiveAlerts().isEmpty());
    }

    @Test
    @DisplayName("Manually resolving an alert should suppress recurring alerts while condition continues")
    void testManualResolveSuppressesRecurringAlert() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp elevated",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(violation));

        Alert alert = alertStore.getFirst();
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());

        // Operator acknowledges and manually resolves it
        manager.acknowledgeAlert(alert.getId());
        boolean resolved = manager.manuallyResolveAlert(alert.getId());
        assertTrue(resolved);
        assertEquals(AlertStatus.RESOLVED, alert.getStatus());
        assertTrue(manager.isMachineSuppressed(1L));

        // Next 5 simulation cycles run, condition is still violating threshold (machine hasn't cooled down yet)
        for (int i = 0; i < 5; i++) {
            manager.reconcile(1L, "CNC-01", List.of(violation));
        }

        // Should NOT create any new alerts! Total alerts remain 1
        assertEquals(1, alertStore.size(), "Should suppress new alerts while ongoing condition was already resolved");
        assertTrue(manager.getActiveAlerts().isEmpty());
    }

    @Test
    @DisplayName("Suppression should be lifted and alert created if condition escalates from WARNING to CRITICAL")
    void testSuppressionEscalatesToCritical() {
        ThresholdEvaluator.ThresholdViolation warnViolation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp elevated",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(warnViolation));

        Alert alert = alertStore.getFirst();
        manager.manuallyResolveAlert(alert.getId());

        // Condition escalates to CRITICAL (e.g. 92°C)
        ThresholdEvaluator.ThresholdViolation critViolation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.CRITICAL,
                "Critical temp elevated",
                "Temperature",
                92.0,
                90.0
        );
        manager.reconcile(1L, "CNC-01", List.of(critViolation));

        // Critical alert MUST be created
        assertEquals(2, alertStore.size(), "Escalation to CRITICAL should create a new alert");
        Alert escalatedAlert = alertStore.get(1);
        assertEquals(AlertSeverity.CRITICAL, escalatedAlert.getSeverity());
        assertEquals(AlertStatus.ACTIVE, escalatedAlert.getStatus());
    }

    @Test
    @DisplayName("Condition recovery should clear suppression and re-arm detector for future violations")
    void testSuppressionReArmedOnRecovery() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp elevated",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(violation));
        Alert alert = alertStore.getFirst();
        manager.manuallyResolveAlert(alert.getId());

        // Condition clears (temperature dropped below threshold)
        manager.reconcile(1L, "CNC-01", Collections.emptyList());
        assertFalse(manager.isMachineSuppressed(1L), "Suppression should be cleared when condition recovers");

        // Temperature rises again in the future
        manager.reconcile(1L, "CNC-01", List.of(violation));

        // Should create a brand new alert for the new episode
        assertEquals(2, alertStore.size(), "Should create a new alert after previous condition had cleared");
    }

    @Test
    @DisplayName("rearmMachine should clear suppressions when machine configuration is updated")
    void testRearmMachine() {
        ThresholdEvaluator.ThresholdViolation violation = new ThresholdEvaluator.ThresholdViolation(
                "TEMPERATURE_HIGH",
                AlertSeverity.WARNING,
                "Warning temp elevated",
                "Temperature",
                84.0,
                80.0
        );
        manager.reconcile(1L, "CNC-01", List.of(violation));
        Alert alert = alertStore.getFirst();
        manager.manuallyResolveAlert(alert.getId());
        assertTrue(manager.isMachineSuppressed(1L));

        // Operator updates threshold settings for machine 1
        manager.rearmMachine(1L);
        assertFalse(manager.isMachineSuppressed(1L), "Suppression should be reset upon threshold reconfiguration");
    }
}
