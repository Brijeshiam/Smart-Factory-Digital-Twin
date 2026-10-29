package com.smartfactory.simulation;

import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;

import java.time.Instant;
import java.util.Random;

public class MachineSensorState {
    private final Long machineId;
    private final String machineCode;
    private double currentTemp;
    private double currentVibration;
    private double currentRpm;
    private long productionCount;
    private FaultScenario faultScenario;
    private boolean enabled;

    private final double nominalTemp;
    private final double nominalVibration;
    private final double nominalRpm;
    private final Random random = new Random();

    public MachineSensorState(Long machineId, String machineCode, double nominalRpm, long initialProduction) {
        this.machineId = machineId;
        this.machineCode = machineCode;
        this.nominalTemp = 62.0 + (random.nextDouble() * 8.0); // 62 - 70°C normal
        this.currentTemp = nominalTemp;
        this.nominalVibration = 1.4 + (random.nextDouble() * 0.8); // 1.4 - 2.2 mm/s
        this.currentVibration = nominalVibration;
        this.nominalRpm = nominalRpm > 0 ? nominalRpm : 1500.0;
        this.currentRpm = this.nominalRpm;
        this.productionCount = initialProduction;
        this.faultScenario = FaultScenario.NONE;
        this.enabled = true;
    }

    public synchronized SensorReading step() {
        if (!enabled) {
            // Machine is disabled or offline
            return new SensorReading(
                    machineId,
                    Instant.now(),
                    24.0, // Ambient
                    0.0,
                    0.0,
                    productionCount,
                    MachineState.OFFLINE,
                    "Machine is offline/disabled"
            );
        }

        // Apply realistic dynamics depending on FaultScenario
        switch (faultScenario) {
            case HIGH_TEMPERATURE:
                // Gradually climb towards 94°C
                currentTemp = approach(currentTemp, 94.5 + (random.nextDouble() * 2.0), 2.0);
                currentVibration = approach(currentVibration, nominalVibration + 0.5, 0.1);
                currentRpm = approach(currentRpm, nominalRpm * 0.95, 10.0);
                break;
            case MODERATE_TEMPERATURE:
                // Climb towards 84°C
                currentTemp = approach(currentTemp, 84.0 + (random.nextDouble() * 2.0), 0.8);
                currentVibration = approach(currentVibration, nominalVibration, 0.1);
                currentRpm = approach(currentRpm, nominalRpm, 10.0);
                break;
            case HIGH_VIBRATION:
                // Climb vibration towards 5.5 mm/s
                currentVibration = approach(currentVibration, 5.4 + (random.nextDouble() * 0.8), 0.3);
                currentTemp = approach(currentTemp, nominalTemp + 4.0, 0.5);
                currentRpm = approach(currentRpm, nominalRpm * 0.9, 15.0);
                break;
            case MODERATE_VIBRATION:
                // Climb vibration towards 4.2 mm/s
                currentVibration = approach(currentVibration, 4.1 + (random.nextDouble() * 0.4), 0.2);
                currentTemp = approach(currentTemp, nominalTemp + 2.0, 0.3);
                currentRpm = approach(currentRpm, nominalRpm, 10.0);
                break;
            case MOTOR_STALL:
                // Drop RPM sharply
                currentRpm = approach(currentRpm, 120.0 + (random.nextDouble() * 50.0), 180.0);
                currentTemp = approach(currentTemp, nominalTemp + 8.0, 0.6);
                currentVibration = approach(currentVibration, 4.5, 0.25);
                break;
            case NONE:
            default:
                // Settle smoothly back towards nominal baseline
                currentTemp = approach(currentTemp, nominalTemp + (random.nextDouble() * 2.0 - 1.0), 0.5);
                currentVibration = approach(currentVibration, nominalVibration + (random.nextDouble() * 0.3 - 0.15), 0.1);
                currentRpm = approach(currentRpm, nominalRpm + (random.nextDouble() * 20.0 - 10.0), 15.0);
                // Increment production during normal operation
                if (random.nextDouble() < 0.7) {
                    productionCount += 1;
                }
                break;
        }

        // Keep values strictly in physical bounds
        currentTemp = Math.max(15.0, Math.min(125.0, currentTemp));
        currentVibration = Math.max(0.0, Math.min(15.0, currentVibration));
        currentRpm = Math.max(0.0, Math.min(8000.0, currentRpm));

        return new SensorReading(
                machineId,
                Instant.now(),
                Math.round(currentTemp * 10.0) / 10.0,
                Math.round(currentVibration * 100.0) / 100.0,
                Math.round(currentRpm),
                productionCount,
                MachineState.NORMAL, // Status will be classified by ThresholdEvaluator
                faultScenario != FaultScenario.NONE ? "Simulated Fault: " + faultScenario.getDescription() : null
        );
    }

    private double approach(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }

    public synchronized void setFaultScenario(FaultScenario scenario) {
        this.faultScenario = scenario != null ? scenario : FaultScenario.NONE;
    }

    public synchronized FaultScenario getFaultScenario() {
        return faultScenario;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public Long getMachineId() {
        return machineId;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public double getCurrentTemp() {
        return currentTemp;
    }

    public double getCurrentVibration() {
        return currentVibration;
    }

    public double getCurrentRpm() {
        return currentRpm;
    }

    public long getProductionCount() {
        return productionCount;
    }
}
