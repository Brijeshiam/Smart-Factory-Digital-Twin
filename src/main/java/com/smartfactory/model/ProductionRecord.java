package com.smartfactory.model;

import java.time.Instant;

public class ProductionRecord {
    private Long id;
    private Long machineId;
    private Instant recordedAt;
    private long cycleCount;
    private int unitsProduced;
    private int operatingMinutes;
    private double efficiencyPercent;

    public ProductionRecord() {
        this.recordedAt = Instant.now();
        this.efficiencyPercent = 100.0;
    }

    public ProductionRecord(Long machineId, Instant recordedAt, long cycleCount,
                            int unitsProduced, int operatingMinutes, double efficiencyPercent) {
        this.machineId = machineId;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
        this.cycleCount = cycleCount;
        this.unitsProduced = unitsProduced;
        this.operatingMinutes = operatingMinutes;
        this.efficiencyPercent = efficiencyPercent;
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

    public long getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(long cycleCount) {
        this.cycleCount = cycleCount;
    }

    public int getUnitsProduced() {
        return unitsProduced;
    }

    public void setUnitsProduced(int unitsProduced) {
        this.unitsProduced = unitsProduced;
    }

    public int getOperatingMinutes() {
        return operatingMinutes;
    }

    public void setOperatingMinutes(int operatingMinutes) {
        this.operatingMinutes = operatingMinutes;
    }

    public double getEfficiencyPercent() {
        return efficiencyPercent;
    }

    public void setEfficiencyPercent(double efficiencyPercent) {
        this.efficiencyPercent = efficiencyPercent;
    }
}
