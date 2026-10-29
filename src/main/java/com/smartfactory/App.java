package com.smartfactory;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.dao.*;
import com.smartfactory.model.Machine;
import com.smartfactory.model.MachineConfiguration;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.ui.MainLayout;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class App extends Application {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private DigitalTwinService digitalTwinService;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting Smart Factory Digital Twin Application...");

        // 1. Verify Database and apply schema/seed if connected
        boolean dbConnected = DatabaseConfig.getInstance().testConnection();
        if (!dbConnected) {
            logger.warn("PostgreSQL connection could not be established at startup.");
        } else {
            initializeDatabaseSchemaAndSeed();
        }

        // 2. Initialize DAOs
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        MachineDao machineDao = new MachineDao(dbConfig);
        ConfigurationDao configurationDao = new ConfigurationDao(dbConfig);
        SensorReadingDao sensorReadingDao = new SensorReadingDao(dbConfig);
        AlertDao alertDao = new AlertDao(dbConfig);
        ProductionRecordDao productionRecordDao = new ProductionRecordDao(dbConfig);

        // 3. Ensure the initial 3 machines exist
        ensureInitialMachines(machineDao, configurationDao);

        // 4. Initialize Digital Twin Service
        digitalTwinService = new DigitalTwinService(
                machineDao,
                configurationDao,
                sensorReadingDao,
                alertDao,
                productionRecordDao
        );
        digitalTwinService.initialize();

        // 5. Start sensor simulation automatically
        digitalTwinService.startSimulation();

        // 6. Build UI and Scene
        MainLayout mainLayout = new MainLayout(digitalTwinService);
        Scene scene = new Scene(mainLayout, 1360, 850);

        // Load stylesheet
        try {
            String cssPath = getClass().getResource("/styles/theme.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            logger.warn("Could not load theme.css: {}", e.getMessage());
        }

        primaryStage.setTitle("Smart Factory Digital Twin — Industrial IoT Condition Monitoring & SCADA");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setScene(scene);

        // 7. Graceful Application Shutdown
        primaryStage.setOnCloseRequest(event -> {
            logger.info("Application close requested. Performing graceful shutdown...");
            if (digitalTwinService != null) {
                digitalTwinService.shutdown();
            }
            DatabaseConfig.getInstance().close();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
        logger.info("Smart Factory Digital Twin UI launched successfully.");

        if (!dbConnected) {
            Platform.runLater(() -> showDatabaseConnectionWarning(primaryStage));
        }
    }

    private void initializeDatabaseSchemaAndSeed() {
        try {
            DatabaseConfig db = DatabaseConfig.getInstance();

            // Check if machines table exists
            boolean tablesExist = false;
            try (var conn = db.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT to_regclass('public.machines')")) {
                if (rs.next() && rs.getString(1) != null) {
                    tablesExist = true;
                }
            } catch (Exception ignored) {}

            if (!tablesExist) {
                logger.info("Database schema not detected. Executing database/schema.sql...");
                executeSqlFile("database/schema.sql");
                executeSqlFile("database/seed.sql");
            }
        } catch (Exception e) {
            logger.error("Error checking or applying database schema: {}", e.getMessage());
        }
    }

    private void executeSqlFile(String relativePath) {
        try {
            java.io.File file = new java.io.File(relativePath);
            if (file.exists()) {
                String sql = java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
                DatabaseConfig.getInstance().executeSqlScript(sql);
                logger.info("Successfully executed SQL script: {}", relativePath);
            }
        } catch (Exception e) {
            logger.warn("Could not execute SQL script {}: {}", relativePath, e.getMessage());
        }
    }

    private void ensureInitialMachines(MachineDao machineDao, ConfigurationDao configDao) {
        try {
            if (machineDao.count() == 0) {
                logger.info("Seeding initial 3 industrial machines into registry...");

                // 1. CNC Milling Machine
                Machine cnc = new Machine("CNC-01", "CNC Milling Machine", "Precision Bay 1", true);
                cnc = machineDao.save(cnc);
                configDao.saveOrUpdate(new MachineConfiguration(cnc.getId(), 80.0, 90.0, 3.5, 5.0, 3200.0, 2000));

                // 2. Industrial Motor
                Machine mot = new Machine("MOT-02", "Industrial Motor", "Pump Station 3", true);
                mot = machineDao.save(mot);
                configDao.saveOrUpdate(new MachineConfiguration(mot.getId(), 80.0, 90.0, 3.5, 5.0, 1800.0, 2000));

                // 3. Packaging Machine
                Machine pkg = new Machine("PKG-03", "Packaging Machine", "Assembly Line 2", true);
                pkg = machineDao.save(pkg);
                configDao.saveOrUpdate(new MachineConfiguration(pkg.getId(), 80.0, 90.0, 3.5, 5.0, 850.0, 2000));

                logger.info("Initial machine seed complete.");
            }
        } catch (Exception e) {
            logger.warn("Unable to query/seed machines from DB: {}", e.getMessage());
        }
    }

    private void showDatabaseConnectionWarning(Stage stage) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Database Connection Notice");
        alert.setHeaderText("PostgreSQL Database is Not Reachable");
        alert.setContentText(
                "The application attempted to connect to:\n" +
                DatabaseConfig.getInstance().getDbUrl() + "\n\n" +
                "You can continue using the desktop simulator in memory, or start PostgreSQL and reconnect via the Configuration screen."
        );
        alert.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
