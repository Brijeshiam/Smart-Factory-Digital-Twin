package com.smartfactory.model;

import java.time.Instant;

public class SensorReading {
    private Long id;
    private Long machineId;
    private Instant recordedAt;
    private double temperatureC;
    private double vibrationMmS;
    private double motorRpm;
    private long productionCount;
    private MachineState machineStatus;
    private String alertMessage;

    public SensorReading() {
        this.recordedAt = Instant.now();
        this.machineStatus = MachineState.NORMAL;
    }

    public SensorReading(Long machineId, Instant recordedAt, double temperatureC,
                         double vibrationMmS, double motorRpm, long productionCount,
                         MachineState machineStatus, String alertMessage) {
        this.machineId = machineId;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
        this.temperatureC = temperatureC;
        this.vibrationMmS = vibrationMmS;
        this.motorRpm = motorRpm;
        this.productionCount = productionCount;
        this.machineStatus = machineStatus != null ? machineStatus : MachineState.NORMAL;
        this.alertMessage = alertMessage;
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

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public double getVibrationMmS() {
        return vibrationMmS;
    }

    public void setVibrationMmS(double vibrationMmS) {
        this.vibrationMmS = vibrationMmS;
    }

    public double getMotorRpm() {
        return motorRpm;
    }

    public void setMotorRpm(double motorRpm) {
        this.motorRpm = motorRpm;
    }

    public long getProductionCount() {
        return productionCount;
    }

    public void setProductionCount(long productionCount) {
        this.productionCount = productionCount;
    }

    public MachineState getMachineStatus() {
        return machineStatus;
    }

    public void setMachineStatus(MachineState machineStatus) {
        this.machineStatus = machineStatus;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
    }

    @Override
    public String toString() {
        return String.format("SensorReading[machineId=%d, temp=%.1f°C, vib=%.2f mm/s, rpm=%.0f, status=%s]",
                machineId, temperatureC, vibrationMmS, motorRpm, machineStatus);
    }
}
