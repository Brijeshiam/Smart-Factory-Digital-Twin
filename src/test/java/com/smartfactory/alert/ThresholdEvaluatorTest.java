package com.smartfactory.alert;

import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.MachineConfiguration;
import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ThresholdEvaluatorTest {

    private ThresholdEvaluator evaluator;
    private MachineConfiguration config;

    @BeforeEach
    void setUp() {
        evaluator = new ThresholdEvaluator();
        config = new MachineConfiguration();
        config.setTempWarningLimit(80.0);
        config.setTempCriticalLimit(90.0);
        config.setVibWarningLimit(3.5);
        config.setVibCriticalLimit(5.0);
    }

    @Test
    @DisplayName("Sensor readings within normal range should result in NORMAL state and high health score")
    void testNormalReading() {
        SensorReading reading = new SensorReading(1L, Instant.now(), 65.0, 1.8, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult result = evaluator.evaluate(reading, config);

        assertEquals(MachineState.NORMAL, result.machineState());
        assertEquals(AlertSeverity.INFO, result.highestSeverity());
        assertTrue(result.healthScore() >= 90.0, "Health score should be in optimal range");
        assertFalse(result.hasViolations());
    }

    @Test
    @DisplayName("Elevated temperature above 80C but below 90C should trigger WARNING")
    void testTemperatureWarning() {
        SensorReading reading = new SensorReading(1L, Instant.now(), 84.5, 2.0, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult result = evaluator.evaluate(reading, config);

        assertEquals(MachineState.WARNING, result.machineState());
        assertEquals(AlertSeverity.WARNING, result.highestSeverity());
        assertTrue(result.healthScore() < 90.0 && result.healthScore() >= 60.0);
        assertEquals(1, result.violations().size());
        assertEquals("TEMPERATURE_HIGH", result.violations().getFirst().alertType());
    }

    @Test
    @DisplayName("Temperature >= 90C should trigger CRITICAL state")
    void testTemperatureCritical() {
        SensorReading reading = new SensorReading(1L, Instant.now(), 92.0, 2.0, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult result = evaluator.evaluate(reading, config);

        assertEquals(MachineState.CRITICAL, result.machineState());
        assertEquals(AlertSeverity.CRITICAL, result.highestSeverity());
        assertTrue(result.healthScore() < 60.0);
    }

    @Test
    @DisplayName("Critical severity should take precedence over Warning severity across sensors")
    void testSeverityPrecedence() {
        // Temperature is in WARNING (82C), but Vibration is in CRITICAL (5.4 mm/s)
        SensorReading reading = new SensorReading(1L, Instant.now(), 82.0, 5.4, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult result = evaluator.evaluate(reading, config);

        assertEquals(MachineState.CRITICAL, result.machineState());
        assertEquals(AlertSeverity.CRITICAL, result.highestSeverity());
        assertEquals(2, result.violations().size());
    }

    @Test
    @DisplayName("Offline machines should evaluate to OFFLINE state with zero health score")
    void testOfflineReading() {
        SensorReading reading = new SensorReading(1L, Instant.now(), 20.0, 0.0, 0.0, 100, MachineState.OFFLINE, "Offline");
        ThresholdEvaluator.EvaluationResult result = evaluator.evaluate(reading, config);

        assertEquals(MachineState.OFFLINE, result.machineState());
        assertEquals(0.0, result.healthScore());
    }

    @Test
    @DisplayName("Hysteresis deadband should prevent chattering when reading fluctuates slightly below warning threshold")
    void testHysteresisDeadbandPreventsChattering() {
        // Warning threshold is 80.0C, deadband is 1.5C (effective reset limit is 78.5C)
        // 1. Reading at 79.5C with NORMAL previous state -> stays NORMAL
        SensorReading readingNormal = new SensorReading(1L, Instant.now(), 79.5, 2.0, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult resNormal = evaluator.evaluate(readingNormal, config, MachineState.NORMAL);
        assertEquals(MachineState.NORMAL, resNormal.machineState());
        assertFalse(resNormal.hasViolations());

        // 2. Reading at 79.5C when machine was previously in WARNING state -> stays in WARNING (deadband prevents flapping)
        ThresholdEvaluator.EvaluationResult resWarning = evaluator.evaluate(readingNormal, config, MachineState.WARNING);
        assertEquals(MachineState.WARNING, resWarning.machineState());
        assertTrue(resWarning.hasViolations());

        // 3. Reading drops below deadband (e.g. 77.5C < 78.5C) -> genuinely recovers to NORMAL
        SensorReading readingCooled = new SensorReading(1L, Instant.now(), 77.5, 2.0, 1500.0, 100, MachineState.NORMAL, null);
        ThresholdEvaluator.EvaluationResult resRecovered = evaluator.evaluate(readingCooled, config, MachineState.WARNING);
        assertEquals(MachineState.NORMAL, resRecovered.machineState());
        assertFalse(resRecovered.hasViolations());
    }
}
