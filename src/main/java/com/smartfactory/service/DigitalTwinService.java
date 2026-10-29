package com.smartfactory.service;

import com.smartfactory.alert.AlertLifecycleManager;
import com.smartfactory.alert.ThresholdEvaluator;
import com.smartfactory.dao.*;
import com.smartfactory.model.*;
import com.smartfactory.simulation.FaultScenario;
import com.smartfactory.simulation.SensorDataSource;
import com.smartfactory.simulation.SimulatedSensorDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DigitalTwinService {
    private static final Logger logger = LoggerFactory.getLogger(DigitalTwinService.class);

    private final MachineDao machineDao;
    private final ConfigurationDao configurationDao;
    private final SensorReadingDao sensorReadingDao;
    private final AlertDao alertDao;
    private final ProductionRecordDao productionRecordDao;

    private final SimulatedSensorDataSource sensorDataSource;
    private final ThresholdEvaluator thresholdEvaluator;
    private final AlertLifecycleManager alertLifecycleManager;

    private final Map<Long, DigitalTwinMachine> twins = new ConcurrentHashMap<>();
    private final List<Consumer<DigitalTwinMachine>> twinUpdateListeners = new CopyOnWriteArrayList<>();

    // Background thread pool for non-blocking asynchronous database writes
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DigitalTwin-DB-Worker");
        t.setDaemon(true);
        return t;
    });

    public DigitalTwinService(MachineDao machineDao, ConfigurationDao configurationDao,
                              SensorReadingDao sensorReadingDao, AlertDao alertDao,
                              ProductionRecordDao productionRecordDao) {
        this.machineDao = machineDao;
        this.configurationDao = configurationDao;
        this.sensorReadingDao = sensorReadingDao;
        this.alertDao = alertDao;
        this.productionRecordDao = productionRecordDao;

        this.sensorDataSource = new SimulatedSensorDataSource();
        this.thresholdEvaluator = new ThresholdEvaluator();
        this.alertLifecycleManager = new AlertLifecycleManager(alertDao);

        // Register listener for simulated sensor readings
        this.sensorDataSource.addReadingListener(this::onSensorReadingReceived);
    }

    public synchronized void initialize() {
        logger.info("Initializing Digital Twin Engine...");
        twins.clear();

        List<Machine> machines;
        try {
            machines = machineDao.findAll();
        } catch (Exception e) {
            logger.warn("Database is unreachable ({}), loading default 3 assets in standby/offline mode.", e.getMessage());
            machines = List.of(
                    new Machine(1L, "CNC-01", "CNC Milling Machine", "Precision Bay 1", true, null, null),
                    new Machine(2L, "MOT-02", "Industrial Motor", "Pump Station 3", true, null, null),
                    new Machine(3L, "PKG-03", "Packaging Machine", "Assembly Line 2", true, null, null)
            );
        }

        for (Machine machine : machines) {
            MachineConfiguration config = null;
            try {
                config = configurationDao.findByMachineId(machine.getId()).orElse(null);
            } catch (Exception ignored) {}

            if (config == null) {
                config = new MachineConfiguration();
                config.setMachineId(machine.getId());
                if ("CNC-01".equals(machine.getMachineCode())) config.setNominalRpm(3200.0);
                else if ("MOT-02".equals(machine.getMachineCode())) config.setNominalRpm(1800.0);
                else config.setNominalRpm(850.0);
            }

            DigitalTwinMachine twin = new DigitalTwinMachine(machine, config);

            // Preload recent history from database if available
            try {
                List<SensorReading> recent = sensorReadingDao.findRecentByMachineId(machine.getId(), 50);
                for (int i = recent.size() - 1; i >= 0; i--) {
                    SensorReading r = recent.get(i);
                    ThresholdEvaluator.EvaluationResult res = thresholdEvaluator.evaluate(r, config);
                    twin.updateReading(r, res.machineState(), res.healthScore());
                }
            } catch (Exception e) {
                logger.debug("Could not preload sensor history for machine {}: {}", machine.getId(), e.getMessage());
            }

            twins.put(machine.getId(), twin);

            // Register with simulation data source
            long initialProd = twin.getLatestReading() != null ? twin.getLatestReading().getProductionCount() : 0L;
            sensorDataSource.registerMachine(machine, config.getNominalRpm(), initialProd);
        }
        logger.info("Digital Twin Engine initialized with {} machines.", twins.size());
    }

    private void onSensorReadingReceived(SensorReading reading) {
        if (reading == null || reading.getMachineId() == null) return;

        DigitalTwinMachine twin = twins.get(reading.getMachineId());
        if (twin == null) return;

        // 1. Evaluate thresholds & health with hysteresis support
        MachineConfiguration config = twin.getConfiguration();
        ThresholdEvaluator.EvaluationResult evalResult = thresholdEvaluator.evaluate(
                reading, config, twin.getCurrentState()
        );

        reading.setMachineStatus(evalResult.machineState());
        if (evalResult.hasViolations()) {
            reading.setAlertMessage(evalResult.violations().getFirst().description());
        }

        // 2. Synchronize virtual twin state
        twin.updateReading(reading, evalResult.machineState(), evalResult.healthScore());

        // 3. Reconcile alerts (duplicate prevention + automatic recovery resolution)
        alertLifecycleManager.reconcile(twin.getId(), twin.getMachineCode(), evalResult.violations());

        // 4. Asynchronously persist to database (does not block simulation or JavaFX thread)
        dbExecutor.submit(() -> {
            try {
                sensorReadingDao.save(reading);
            } catch (Exception e) {
                logger.error("Async DB save failed for sensor reading: {}", e.getMessage());
            }
        });

        // 5. Notify UI listeners
        notifyTwinUpdated(twin);
    }

    public void addTwinUpdateListener(Consumer<DigitalTwinMachine> listener) {
        twinUpdateListeners.add(listener);
    }

    public void removeTwinUpdateListener(Consumer<DigitalTwinMachine> listener) {
        twinUpdateListeners.remove(listener);
    }

    private void notifyTwinUpdated(DigitalTwinMachine twin) {
        for (Consumer<DigitalTwinMachine> listener : twinUpdateListeners) {
            try {
                listener.accept(twin);
            } catch (Exception e) {
                logger.error("Error in twin update listener: {}", e.getMessage());
            }
        }
    }

    public void startSimulation() {
        sensorDataSource.start();
    }

    public void stopSimulation() {
        sensorDataSource.stop();
    }

    public boolean isSimulationRunning() {
        return sensorDataSource.isRunning();
    }

    public void setSimulationInterval(int ms) {
        sensorDataSource.setIntervalMs(ms);
    }

    public int getSimulationInterval() {
        return sensorDataSource.getIntervalMs();
    }

    public void injectFault(Long machineId, FaultScenario scenario) {
        sensorDataSource.injectFault(machineId, scenario);
    }

    public void clearFault(Long machineId) {
        sensorDataSource.clearFault(machineId);
    }

    public FaultScenario getMachineFault(Long machineId) {
        return sensorDataSource.getMachineFault(machineId);
    }

    public DigitalTwinMachine getTwin(Long machineId) {
        return twins.get(machineId);
    }

    public Collection<DigitalTwinMachine> getAllTwins() {
        return Collections.unmodifiableCollection(twins.values());
    }

    public AlertLifecycleManager getAlertLifecycleManager() {
        return alertLifecycleManager;
    }

    public MachineDao getMachineDao() {
        return machineDao;
    }

    public ConfigurationDao getConfigurationDao() {
        return configurationDao;
    }

    public SensorReadingDao getSensorReadingDao() {
        return sensorReadingDao;
    }

    public AlertDao getAlertDao() {
        return alertDao;
    }

    public ProductionRecordDao getProductionRecordDao() {
        return productionRecordDao;
    }

    public ThresholdEvaluator getThresholdEvaluator() {
        return thresholdEvaluator;
    }

    public void updateMachineConfiguration(MachineConfiguration config) {
        configurationDao.saveOrUpdate(config);
        DigitalTwinMachine twin = twins.get(config.getMachineId());
        if (twin != null) {
            twin.setConfiguration(config);
            // Re-arm detection for this machine so new threshold settings apply immediately
            alertLifecycleManager.rearmMachine(config.getMachineId());
        }
    }

    public void saveMachine(Machine machine, MachineConfiguration config) {
        boolean isNew = machine.getId() == null;
        Machine saved;
        try {
            saved = machineDao.save(machine);
        } catch (Exception e) {
            logger.warn("Could not persist machine to DB (offline fallback): {}", e.getMessage());
            if (machine.getId() == null) {
                // Generate in-memory fallback ID based on highest existing ID + 1
                long maxId = twins.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
                machine.setId(maxId + 1);
            }
            saved = machine;
        }

        if (config != null) {
            config.setMachineId(saved.getId());
            try {
                configurationDao.saveOrUpdate(config);
            } catch (Exception ignored) {}
        }

        if (isNew) {
            DigitalTwinMachine twin = new DigitalTwinMachine(saved, config);
            twins.put(saved.getId(), twin);
            sensorDataSource.registerMachine(saved, config != null ? config.getNominalRpm() : 1500.0, 0L);
            // Immediately generate first sensor reading so the machine is live right away!
            sensorDataSource.stepMachine(saved.getId());
        } else {
            DigitalTwinMachine twin = twins.get(saved.getId());
            if (twin != null) {
                twin.getMachine().setEnabled(saved.isEnabled());
                sensorDataSource.setMachineEnabled(saved.getId(), saved.isEnabled());
                // Immediately step to reflect new enabled/disabled status
                sensorDataSource.stepMachine(saved.getId());
            }
        }
    }

    public void shutdown() {
        logger.info("Shutting down Digital Twin Service...");
        sensorDataSource.stop();
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
