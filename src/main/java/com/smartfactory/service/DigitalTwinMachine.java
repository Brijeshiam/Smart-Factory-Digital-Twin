package com.smartfactory.service;

import com.smartfactory.model.Machine;
import com.smartfactory.model.MachineConfiguration;
import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DigitalTwinMachine {
    private final Machine machine;
    private MachineConfiguration configuration;
    private SensorReading latestReading;
    private MachineState currentState;
    private double healthScore;
    private long productionCount;
    private Instant lastUpdated;
    private final LinkedList<SensorReading> recentHistory = new LinkedList<>();
    private static final int MAX_HISTORY_POINTS = 80;

    public DigitalTwinMachine(Machine machine, MachineConfiguration configuration) {
        this.machine = machine;
        this.configuration = configuration != null ? configuration : new MachineConfiguration();
        this.currentState = machine.isEnabled() ? MachineState.NORMAL : MachineState.OFFLINE;
        this.healthScore = machine.isEnabled() ? 100.0 : 0.0;
        this.productionCount = 0;
        this.lastUpdated = Instant.now();
    }

    public synchronized void updateReading(SensorReading reading, MachineState calculatedState, double calculatedHealth) {
        this.latestReading = reading;
        this.currentState = calculatedState;
        this.healthScore = calculatedHealth;
        this.productionCount = reading.getProductionCount();
        this.lastUpdated = reading.getRecordedAt();

        recentHistory.addLast(reading);
        while (recentHistory.size() > MAX_HISTORY_POINTS) {
            recentHistory.removeFirst();
        }
    }

    public Machine getMachine() {
        return machine;
    }

    public Long getId() {
        return machine.getId();
    }

    public String getMachineCode() {
        return machine.getMachineCode();
    }

    public String getMachineName() {
        return machine.getMachineName();
    }

    public String getLocation() {
        return machine.getLocation();
    }

    public boolean isEnabled() {
        return machine.isEnabled();
    }

    public synchronized MachineConfiguration getConfiguration() {
        return configuration;
    }

    public synchronized void setConfiguration(MachineConfiguration configuration) {
        this.configuration = configuration;
    }

    public synchronized SensorReading getLatestReading() {
        return latestReading;
    }

    public synchronized MachineState getCurrentState() {
        return currentState;
    }

    public synchronized double getHealthScore() {
        return healthScore;
    }

    public synchronized long getProductionCount() {
        return productionCount;
    }

    public synchronized Instant getLastUpdated() {
        return lastUpdated;
    }

    public synchronized List<SensorReading> getRecentHistory() {
        return Collections.unmodifiableList(new ArrayList<>(recentHistory));
    }
}
