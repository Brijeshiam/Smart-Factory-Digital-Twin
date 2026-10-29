package com.smartfactory.ui;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.model.Alert;
import com.smartfactory.model.AlertSeverity;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.simulation.FaultScenario;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;

public class MainLayout extends BorderPane {

    private final DigitalTwinService digitalTwinService;

    // Center views
    private DashboardView dashboardView;
    private FactoryFloorView factoryFloorView;
    private MachineDetailsView machineDetailsView;
    private AlertsView alertsView;
    private HistoryView historyView;
    private WhatIfSimulationView whatIfSimulationView;
    private ConfigurationView configurationView;
    private PerformanceReportView performanceReportView;

    private final Map<String, Node> viewMap = new HashMap<>();
    private final Map<String, Button> navButtons = new HashMap<>();

    // Header controls
    private final Label lblPageTitle = new Label("Dashboard");
    private final Label lblPageSubtitle = new Label("Real-time factory telemetry overview");
    private final Button btnSimToggle = new Button("Start Simulation");
    private final ComboBox<DigitalTwinMachine> quickMachineCombo = new ComboBox<>();
    private final ComboBox<FaultScenario> quickFaultCombo = new ComboBox<>();

    // Sidebar status
    private final Label lblDbBadge = new Label("DB: Checking...");
    private final Label lblSimBadge = new Label("SIM: STOPPED");

    // Banner
    private final HBox alertBanner = new HBox(12);
    private final Label lblBannerText = new Label();

    public MainLayout(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        // Initialize views
        dashboardView = new DashboardView(digitalTwinService, this::navigateTo);
        factoryFloorView = new FactoryFloorView(digitalTwinService, (view, machineId) -> {
            navigateTo(view);
            if (machineDetailsView != null && machineId != null) {
                machineDetailsView.selectMachineById(machineId);
            }
        });
        machineDetailsView = new MachineDetailsView(digitalTwinService);
        alertsView = new AlertsView(digitalTwinService);
        historyView = new HistoryView(digitalTwinService);
        whatIfSimulationView = new WhatIfSimulationView(digitalTwinService);
        configurationView = new ConfigurationView(digitalTwinService, this::onConfigurationDataUpdated);
        performanceReportView = new PerformanceReportView(digitalTwinService);

        viewMap.put("dashboard", dashboardView);
        viewMap.put("floor", factoryFloorView);
        viewMap.put("machine_details", machineDetailsView);
        viewMap.put("alerts", alertsView);
        viewMap.put("history", historyView);
        viewMap.put("what_if", whatIfSimulationView);
        viewMap.put("config", configurationView);
        viewMap.put("performance", performanceReportView);

        // Build Shell Layout
        setLeft(buildSidebar());
        setTop(buildTopSection());

        // Default view
        navigateTo("dashboard");

        // Listen for twin updates to update status badges
        digitalTwinService.addTwinUpdateListener(twin -> Platform.runLater(this::updateStatusBadges));

        // Listen for new alerts to trigger in-app banner
        digitalTwinService.getAlertLifecycleManager().addAlertListener(alert -> Platform.runLater(() -> onNewAlert(alert)));

        // Initial status check
        updateStatusBadges();
        checkDatabaseConnectivity();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");

        // Brand Logo & Title
        VBox brandBox = new VBox(2);
        brandBox.setPadding(new Insets(0, 0, 16, 4));

        Label brandTitle = new Label("SMART FACTORY");
        brandTitle.getStyleClass().add("sidebar-title");

        Label brandSub = new Label("Digital Twin & IoT SCADA");
        brandSub.getStyleClass().add("sidebar-subtitle");

        brandBox.getChildren().addAll(brandTitle, brandSub);

        // Navigation Items
        VBox navBox = new VBox(4);
        navBox.getChildren().addAll(
                createNavButton("dashboard", "Dashboard Overview"),
                createNavButton("floor", "2D Factory Floor"),
                createNavButton("machine_details", "Machine Details"),
                createNavButton("alerts", "Alerts & Events"),
                createNavButton("history", "Historical Telemetry"),
                createNavButton("what_if", "What-If Capacity"),
                createNavButton("performance", "Performance Report"),
                createNavButton("config", "System Configuration")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Bottom Status Box
        VBox bottomBox = new VBox(8);
        bottomBox.setStyle("-fx-background-color: #0F172A; -fx-padding: 12px; -fx-background-radius: 8px; -fx-border-color: #334155; -fx-border-radius: 8px;");

        lblSimBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        lblDbBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        btnSimToggle.setMaxWidth(Double.MAX_VALUE);
        btnSimToggle.getStyleClass().add("btn-success");
        btnSimToggle.setOnAction(e -> toggleSimulation());

        bottomBox.getChildren().addAll(lblSimBadge, lblDbBadge, btnSimToggle);

        sidebar.getChildren().addAll(brandBox, new Separator(), navBox, spacer, bottomBox);
        return sidebar;
    }

    private Button createNavButton(String key, String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("nav-button");
        btn.setOnAction(e -> navigateTo(key));
        navButtons.put(key, btn);
        return btn;
    }

    private VBox buildTopSection() {
        VBox container = new VBox();

        // Alert Banner (Hidden by default)
        alertBanner.setAlignment(Pos.CENTER_LEFT);
        alertBanner.setStyle("-fx-background-color: #EF4444; -fx-padding: 8 20 8 20;");
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);

        lblBannerText.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 12px;");
        Region bSpacer = new Region();
        HBox.setHgrow(bSpacer, Priority.ALWAYS);

        Button btnDismissBanner = new Button("✕");
        btnDismissBanner.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnDismissBanner.setOnAction(e -> {
            alertBanner.setVisible(false);
            alertBanner.setManaged(false);
        });

        alertBanner.getChildren().addAll(lblBannerText, bSpacer, btnDismissBanner);

        // Header Bar
        HBox header = new HBox(16);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        lblPageTitle.getStyleClass().add("page-title");
        lblPageSubtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(lblPageTitle, lblPageSubtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Fault Injection Quick Bar
        HBox faultBox = new HBox(8);
        faultBox.setAlignment(Pos.CENTER_LEFT);

        Label lblQuick = new Label("Fault Injector:");
        lblQuick.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");

        quickMachineCombo.setPrefWidth(160);
        quickFaultCombo.getItems().setAll(FaultScenario.values());
        quickFaultCombo.setValue(FaultScenario.NONE);
        quickFaultCombo.setPrefWidth(210);

        Button btnInject = new Button("Inject");
        btnInject.getStyleClass().add("btn-danger");
        btnInject.setOnAction(e -> {
            DigitalTwinMachine twin = quickMachineCombo.getValue();
            FaultScenario scenario = quickFaultCombo.getValue();
            if (twin != null && scenario != null) {
                digitalTwinService.injectFault(twin.getId(), scenario);
                if (factoryFloorView != null) factoryFloorView.buildMachineCards();
            }
        });

        Button btnClearFaults = new Button("Clear All");
        btnClearFaults.getStyleClass().add("btn-secondary");
        btnClearFaults.setOnAction(e -> {
            for (DigitalTwinMachine dt : digitalTwinService.getAllTwins()) {
                digitalTwinService.clearFault(dt.getId());
            }
            quickFaultCombo.setValue(FaultScenario.NONE);
            if (factoryFloorView != null) factoryFloorView.buildMachineCards();
        });

        faultBox.getChildren().addAll(lblQuick, quickMachineCombo, quickFaultCombo, btnInject, btnClearFaults);
        header.getChildren().addAll(titleBox, spacer, faultBox);

        container.getChildren().addAll(alertBanner, header);
        return container;
    }

    public void navigateTo(String key) {
        Node view = viewMap.get(key);
        if (view != null) {
            setCenter(view);

            // Update nav active states
            for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
                entry.getValue().getStyleClass().remove("nav-button-active");
                if (entry.getKey().equals(key)) {
                    entry.getValue().getStyleClass().add("nav-button-active");
                }
            }

            // Update Page Title
            switch (key) {
                case "dashboard" -> {
                    lblPageTitle.setText("Main Dashboard");
                    lblPageSubtitle.setText("Real-time factory telemetry, active health index, and trend metrics");
                    if (dashboardView != null) dashboardView.refreshAllMetrics();
                }
                case "floor" -> {
                    lblPageTitle.setText("2D Factory Floor");
                    lblPageSubtitle.setText("Interactive spatial view of manufacturing assets and operating states");
                    if (factoryFloorView != null) factoryFloorView.buildMachineCards();
                }
                case "machine_details" -> {
                    lblPageTitle.setText("Machine Details");
                    lblPageSubtitle.setText("Asset drill-down, historical database logs, and condition analysis");
                    if (machineDetailsView != null) machineDetailsView.refreshMachineSelector();
                }
                case "alerts" -> {
                    lblPageTitle.setText("Alerts & Incidents");
                    lblPageSubtitle.setText("Active alarm log, threshold violations, and operator acknowledgement");
                    if (alertsView != null) alertsView.refreshAlerts();
                }
                case "history" -> {
                    lblPageTitle.setText("Historical Telemetry");
                    lblPageSubtitle.setText("Deep SQL audit log, time-series query parameters, and CSV export");
                }
                case "what_if" -> {
                    lblPageTitle.setText("What-If Simulation");
                    lblPageSubtitle.setText("Strategic downtime simulation and capacity impact estimation");
                }
                case "performance" -> {
                    lblPageTitle.setText("Performance Report");
                    lblPageSubtitle.setText("Machine health scores, production throughput, state distribution & live sensor analytics");
                    if (performanceReportView != null) performanceReportView.refreshSnapshot();
                }
                case "config" -> {
                    lblPageTitle.setText("System Configuration");
                    lblPageSubtitle.setText("Asset registration, threshold tuning, and database connectivity");
                    if (configurationView != null) {
                        configurationView.refreshTable();
                        configurationView.refreshThresholdCombo();
                    }
                }
            }
        }
    }

    private void toggleSimulation() {
        if (digitalTwinService.isSimulationRunning()) {
            digitalTwinService.stopSimulation();
            btnSimToggle.setText("Start Simulation");
            btnSimToggle.getStyleClass().remove("btn-danger");
            btnSimToggle.getStyleClass().add("btn-success");
            lblSimBadge.setText("SIM: STOPPED");
            lblSimBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        } else {
            digitalTwinService.startSimulation();
            btnSimToggle.setText("Stop Simulation");
            btnSimToggle.getStyleClass().remove("btn-success");
            btnSimToggle.getStyleClass().add("btn-danger");
            lblSimBadge.setText("SIM: RUNNING (" + digitalTwinService.getSimulationInterval() + "ms)");
            lblSimBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #10B981;");
        }
        if (dashboardView != null) dashboardView.refreshAllMetrics();
    }

    private void updateStatusBadges() {
        if (digitalTwinService.isSimulationRunning()) {
            lblSimBadge.setText("SIM: RUNNING (" + digitalTwinService.getSimulationInterval() + "ms)");
            lblSimBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #10B981;");
            btnSimToggle.setText("Stop Simulation");
            btnSimToggle.getStyleClass().remove("btn-success");
            if (!btnSimToggle.getStyleClass().contains("btn-danger")) {
                btnSimToggle.getStyleClass().add("btn-danger");
            }
        } else {
            lblSimBadge.setText("SIM: STOPPED");
            lblSimBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
            btnSimToggle.setText("Start Simulation");
            btnSimToggle.getStyleClass().remove("btn-danger");
            if (!btnSimToggle.getStyleClass().contains("btn-success")) {
                btnSimToggle.getStyleClass().add("btn-success");
            }
        }

        // Keep quick machine combo populated
        if (quickMachineCombo.getItems().isEmpty()) {
            quickMachineCombo.getItems().setAll(digitalTwinService.getAllTwins());
            if (!quickMachineCombo.getItems().isEmpty()) {
                quickMachineCombo.setValue(quickMachineCombo.getItems().getFirst());
            }
        }
    }

    private void checkDatabaseConnectivity() {
        new Thread(() -> {
            boolean ok = DatabaseConfig.getInstance().testConnection();
            Platform.runLater(() -> {
                if (ok) {
                    lblDbBadge.setText("DB: CONNECTED (PostgreSQL)");
                    lblDbBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #10B981;");
                } else {
                    lblDbBadge.setText("DB: OFFLINE / DISCONNECTED");
                    lblDbBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #EF4444;");
                }
            });
        }).start();
    }

    private void onNewAlert(Alert alert) {
        if (alert != null && alert.getSeverity() == AlertSeverity.CRITICAL && alert.getStatus() != com.smartfactory.model.AlertStatus.RESOLVED) {
            lblBannerText.setText("CRITICAL INCIDENT: " + alert.getMachineCode() + " - " + alert.getDescription());
            alertBanner.setVisible(true);
            alertBanner.setManaged(true);
        }
    }

    private void onConfigurationDataUpdated() {
        quickMachineCombo.getItems().setAll(digitalTwinService.getAllTwins());
        if (!quickMachineCombo.getItems().isEmpty()) {
            quickMachineCombo.setValue(quickMachineCombo.getItems().getFirst());
        }
        if (factoryFloorView != null) factoryFloorView.buildMachineCards();
        if (machineDetailsView != null) machineDetailsView.refreshMachineSelector();
        if (dashboardView != null) dashboardView.refreshAllMetrics();
        if (whatIfSimulationView != null) whatIfSimulationView.refreshControls();
        if (historyView != null) historyView.initFilters();
        if (alertsView != null) alertsView.initFilters();
    }
}
