package com.smartfactory.alert;

import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.MachineConfiguration;
import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThresholdEvaluator {

    public record ThresholdViolation(
            String alertType,
            AlertSeverity severity,
            String description,
            String metricName,
            double currentValue,
            double thresholdValue
    ) {}

    public record EvaluationResult(
            MachineState machineState,
            AlertSeverity highestSeverity,
            double healthScore,
            List<ThresholdViolation> violations
    ) {
        public boolean hasViolations() {
            return !violations.isEmpty();
        }
    }

    public static final double TEMP_DEADBAND = 1.5; // °C hysteresis to prevent chattering
    public static final double VIB_DEADBAND = 0.25; // mm/s hysteresis to prevent chattering

    /**
     * Evaluates a sensor reading against machine configuration thresholds without previous state.
     */
    public EvaluationResult evaluate(SensorReading reading, MachineConfiguration config) {
        return evaluate(reading, config, null);
    }

    /**
     * Evaluates a sensor reading against machine configuration thresholds, applying hysteresis
     * deadband if the machine was previously in an elevated state (WARNING or CRITICAL) to prevent
     * noisy fluctuations near boundary limits from repeatedly toggling alerts.
     */
    public EvaluationResult evaluate(SensorReading reading, MachineConfiguration config, MachineState previousState) {
        if (reading == null) {
            return new EvaluationResult(MachineState.OFFLINE, AlertSeverity.INFO, 0.0, Collections.emptyList());
        }

        if (reading.getMachineStatus() == MachineState.OFFLINE) {
            return new EvaluationResult(MachineState.OFFLINE, AlertSeverity.INFO, 0.0, Collections.emptyList());
        }

        // Use defaults if config is null
        double tempWarn = config != null ? config.getTempWarningLimit() : 80.0;
        double tempCrit = config != null ? config.getTempCriticalLimit() : 90.0;
        double vibWarn = config != null ? config.getVibWarningLimit() : 3.5;
        double vibCrit = config != null ? config.getVibCriticalLimit() : 5.0;

        // Apply hysteresis deadbands when recovering from elevated state
        double effectiveTempWarn = (previousState == MachineState.WARNING || previousState == MachineState.CRITICAL)
                ? (tempWarn - TEMP_DEADBAND) : tempWarn;
        double effectiveTempCrit = (previousState == MachineState.CRITICAL)
                ? (tempCrit - TEMP_DEADBAND) : tempCrit;

        double effectiveVibWarn = (previousState == MachineState.WARNING || previousState == MachineState.CRITICAL)
                ? (vibWarn - VIB_DEADBAND) : vibWarn;
        double effectiveVibCrit = (previousState == MachineState.CRITICAL)
                ? (vibCrit - VIB_DEADBAND) : vibCrit;

        List<ThresholdViolation> violations = new ArrayList<>();

        // 1. Evaluate Temperature
        double temp = reading.getTemperatureC();
        if (temp >= effectiveTempCrit) {
            violations.add(new ThresholdViolation(
                    "TEMPERATURE_HIGH",
                    AlertSeverity.CRITICAL,
                    String.format("Critical temperature detected: %.1f°C exceeds critical limit of %.1f°C", temp, tempCrit),
                    "Temperature",
                    temp,
                    tempCrit
            ));
        } else if (temp >= effectiveTempWarn) {
            violations.add(new ThresholdViolation(
                    "TEMPERATURE_HIGH",
                    AlertSeverity.WARNING,
                    String.format("Warning temperature elevated: %.1f°C exceeds warning limit of %.1f°C", temp, tempWarn),
                    "Temperature",
                    temp,
                    tempWarn
            ));
        }

        // 2. Evaluate Vibration
        double vib = reading.getVibrationMmS();
        if (vib >= effectiveVibCrit) {
            violations.add(new ThresholdViolation(
                    "VIBRATION_EXCESSIVE",
                    AlertSeverity.CRITICAL,
                    String.format("Critical vibration level: %.2f mm/s exceeds critical limit of %.2f mm/s", vib, vibCrit),
                    "Vibration",
                    vib,
                    vibCrit
            ));
        } else if (vib >= effectiveVibWarn) {
            violations.add(new ThresholdViolation(
                    "VIBRATION_EXCESSIVE",
                    AlertSeverity.WARNING,
                    String.format("Warning vibration level: %.2f mm/s exceeds warning limit of %.2f mm/s", vib, vibWarn),
                    "Vibration",
                    vib,
                    vibWarn
            ));
        }

        // 3. Determine Highest Severity & Overall Machine State
        AlertSeverity highest = AlertSeverity.INFO;
        boolean hasCritical = false;
        boolean hasWarning = false;

        for (ThresholdViolation v : violations) {
            if (v.severity() == AlertSeverity.CRITICAL) {
                hasCritical = true;
                highest = AlertSeverity.CRITICAL;
            } else if (v.severity() == AlertSeverity.WARNING && !hasCritical) {
                hasWarning = true;
                highest = AlertSeverity.WARNING;
            }
        }

        MachineState machineState;
        if (hasCritical) {
            machineState = MachineState.CRITICAL;
        } else if (hasWarning) {
            machineState = MachineState.WARNING;
        } else {
            machineState = MachineState.NORMAL;
        }

        // 4. Calculate Illustrative Health Score (0 - 100)
        double healthScore = calculateHealthScore(temp, tempWarn, tempCrit, vib, vibWarn, vibCrit);

        return new EvaluationResult(machineState, highest, healthScore, Collections.unmodifiableList(violations));
    }

    /**
     * Calculates an illustrative health score from 0.0 to 100.0.
     * Note: This is an illustrative indicator based on threshold proximity and must be
     * calibrated to equipment specifications for real-world production.
     */
    public static double calculateHealthScore(double temp, double tempWarn, double tempCrit,
                                              double vib, double vibWarn, double vibCrit) {
        // Temperature penalty
        double tempPenalty = 0.0;
        if (temp > tempWarn) {
            double over = temp - tempWarn;
            double span = Math.max(1.0, tempCrit - tempWarn);
            tempPenalty = Math.min(50.0, (over / span) * 35.0);
            if (temp >= tempCrit) {
                tempPenalty += Math.min(25.0, (temp - tempCrit) * 5.0);
            }
        }

        // Vibration penalty
        double vibPenalty = 0.0;
        if (vib > vibWarn) {
            double over = vib - vibWarn;
            double span = Math.max(0.5, vibCrit - vibWarn);
            vibPenalty = Math.min(50.0, (over / span) * 35.0);
            if (vib >= vibCrit) {
                vibPenalty += Math.min(25.0, (vib - vibCrit) * 10.0);
            }
        }

        double score = 100.0 - (tempPenalty + vibPenalty);
        score = Math.max(0.0, Math.min(100.0, score));
        return Math.round(score * 10.0) / 10.0;
    }
}
