package com.smartfactory.simulation;

import com.smartfactory.model.SensorReading;

import java.util.function.Consumer;

public interface SensorDataSource {
    void start();
    void stop();
    boolean isRunning();
    void setIntervalMs(int intervalMs);
    int getIntervalMs();
    void injectFault(Long machineId, FaultScenario scenario);
    void clearFault(Long machineId);
    void addReadingListener(Consumer<SensorReading> listener);
    void removeReadingListener(Consumer<SensorReading> listener);
}
