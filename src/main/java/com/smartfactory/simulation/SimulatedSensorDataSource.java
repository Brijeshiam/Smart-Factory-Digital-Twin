package com.smartfactory.simulation;

import com.smartfactory.model.Machine;
import com.smartfactory.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SimulatedSensorDataSource implements SensorDataSource {
    private static final Logger logger = LoggerFactory.getLogger(SimulatedSensorDataSource.class);

    private final Map<Long, MachineSensorState> machineStates = new ConcurrentHashMap<>();
    private final List<Consumer<SensorReading>> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> simulationTask;
    private volatile boolean running = false;
    private volatile int intervalMs = 2000;

    public SimulatedSensorDataSource() {}

    public synchronized void registerMachine(Machine machine, double nominalRpm, long initialProduction) {
        if (machine != null && machine.getId() != null) {
            MachineSensorState state = new MachineSensorState(
                    machine.getId(),
                    machine.getMachineCode(),
                    nominalRpm,
                    initialProduction
            );
            state.setEnabled(machine.isEnabled());
            machineStates.put(machine.getId(), state);
            logger.info("Registered machine {} (ID: {}) for sensor simulation", machine.getMachineCode(), machine.getId());
        }
    }

    public synchronized void unregisterMachine(Long machineId) {
        machineStates.remove(machineId);
    }

    public synchronized void setMachineEnabled(Long machineId, boolean enabled) {
        MachineSensorState state = machineStates.get(machineId);
        if (state != null) {
            state.setEnabled(enabled);
        }
    }

    public void stepMachine(Long machineId) {
        MachineSensorState state = machineStates.get(machineId);
        if (state != null) {
            SensorReading reading = state.step();
            notifyListeners(reading);
        }
    }

    @Override
    public synchronized void start() {
        if (running) return;

        logger.info("Starting sensor simulation engine with interval {} ms...", intervalMs);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SensorSimulation-Thread");
            t.setDaemon(true);
            return t;
        });

        simulationTask = scheduler.scheduleAtFixedRate(
                this::runSimulationCycle,
                0,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;

        logger.info("Stopping sensor simulation engine...");
        if (simulationTask != null) {
            simulationTask.cancel(false);
            simulationTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void setIntervalMs(int intervalMs) {
        this.intervalMs = Math.max(200, intervalMs);
        if (running) {
            stop();
            start();
        }
    }

    @Override
    public int getIntervalMs() {
        return intervalMs;
    }

    @Override
    public void injectFault(Long machineId, FaultScenario scenario) {
        MachineSensorState state = machineStates.get(machineId);
        if (state != null) {
            state.setFaultScenario(scenario);
            logger.warn("Injected fault '{}' on machine ID {}", scenario.getDescription(), machineId);
        }
    }

    @Override
    public void clearFault(Long machineId) {
        MachineSensorState state = machineStates.get(machineId);
        if (state != null) {
            state.setFaultScenario(FaultScenario.NONE);
            logger.info("Cleared fault on machine ID {}, returning to normal operation", machineId);
        }
    }

    public FaultScenario getMachineFault(Long machineId) {
        MachineSensorState state = machineStates.get(machineId);
        return state != null ? state.getFaultScenario() : FaultScenario.NONE;
    }

    @Override
    public void addReadingListener(Consumer<SensorReading> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeReadingListener(Consumer<SensorReading> listener) {
        listeners.remove(listener);
    }

    private void runSimulationCycle() {
        try {
            for (MachineSensorState machineState : machineStates.values()) {
                SensorReading reading = machineState.step();
                notifyListeners(reading);
            }
        } catch (Exception e) {
            logger.error("Error during simulation tick: {}", e.getMessage(), e);
        }
    }

    private void notifyListeners(SensorReading reading) {
        for (Consumer<SensorReading> listener : listeners) {
            try {
                listener.accept(reading);
            } catch (Exception e) {
                logger.error("Error notifying sensor reading listener: {}", e.getMessage(), e);
            }
        }
    }

    public Collection<MachineSensorState> getAllMachineStates() {
        return Collections.unmodifiableCollection(machineStates.values());
    }
}
