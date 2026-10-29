package com.smartfactory.model;

import java.time.Instant;

public class MachineConfiguration {
    private Long id;
    private Long machineId;
    private double tempWarningLimit;
    private double tempCriticalLimit;
    private double vibWarningLimit;
    private double vibCriticalLimit;
    private double nominalRpm;
    private int simulationIntervalMs;
    private Instant updatedAt;

    public MachineConfiguration() {
        this.tempWarningLimit = 80.0;
        this.tempCriticalLimit = 90.0;
        this.vibWarningLimit = 3.5;
        this.vibCriticalLimit = 5.0;
        this.nominalRpm = 1500.0;
        this.simulationIntervalMs = 2000;
        this.updatedAt = Instant.now();
    }

    public MachineConfiguration(Long machineId, double tempWarningLimit, double tempCriticalLimit,
                                double vibWarningLimit, double vibCriticalLimit, double nominalRpm,
                                int simulationIntervalMs) {
        this.machineId = machineId;
        this.tempWarningLimit = tempWarningLimit;
        this.tempCriticalLimit = tempCriticalLimit;
        this.vibWarningLimit = vibWarningLimit;
        this.vibCriticalLimit = vibCriticalLimit;
        this.nominalRpm = nominalRpm;
        this.simulationIntervalMs = simulationIntervalMs;
        this.updatedAt = Instant.now();
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

    public double getTempWarningLimit() {
        return tempWarningLimit;
    }

    public void setTempWarningLimit(double tempWarningLimit) {
        this.tempWarningLimit = tempWarningLimit;
    }

    public double getTempCriticalLimit() {
        return tempCriticalLimit;
    }

    public void setTempCriticalLimit(double tempCriticalLimit) {
        this.tempCriticalLimit = tempCriticalLimit;
    }

    public double getVibWarningLimit() {
        return vibWarningLimit;
    }

    public void setVibWarningLimit(double vibWarningLimit) {
        this.vibWarningLimit = vibWarningLimit;
    }

    public double getVibCriticalLimit() {
        return vibCriticalLimit;
    }

    public void setVibCriticalLimit(double vibCriticalLimit) {
        this.vibCriticalLimit = vibCriticalLimit;
    }

    public double getNominalRpm() {
        return nominalRpm;
    }

    public void setNominalRpm(double nominalRpm) {
        this.nominalRpm = nominalRpm;
    }

    public int getSimulationIntervalMs() {
        return simulationIntervalMs;
    }

    public void setSimulationIntervalMs(int simulationIntervalMs) {
        this.simulationIntervalMs = simulationIntervalMs;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
