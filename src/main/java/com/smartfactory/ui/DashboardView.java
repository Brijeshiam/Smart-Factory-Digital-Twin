package com.smartfactory.ui;

import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.AlertStatus;
import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.util.DateTimeUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

// Explicitly use model Alert
import com.smartfactory.model.Alert;

public class DashboardView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;
    private final Consumer<String> navigationHandler;

    // Summary Metric Labels
    private final Label lblTotalMachines = new Label("0");
    private final Label lblNormalMachines = new Label("0");
    private final Label lblWarningMachines = new Label("0");
    private final Label lblCriticalMachines = new Label("0");
    private final Label lblTotalProduction = new Label("0");
    private final Label lblFactoryHealth = new Label("100%");
    private final Label lblLastUpdate = new Label("--:--:--");
    private final Label lblSimStatus = new Label("Stopped");

    // Real-Time Charts
    private LineChart<String, Number> tempChart;
    private LineChart<String, Number> vibChart;
    private LineChart<String, Number> rpmChart;

    private final Map<Long, XYChart.Series<String, Number>> tempSeriesMap = new HashMap<>();
    private final Map<Long, XYChart.Series<String, Number>> vibSeriesMap = new HashMap<>();
    private final Map<Long, XYChart.Series<String, Number>> rpmSeriesMap = new HashMap<>();

    // Recent Alerts Table
    private TableView<Alert> recentAlertsTable;

    public DashboardView(DigitalTwinService digitalTwinService, Consumer<String> navigationHandler) {
        this.digitalTwinService = digitalTwinService;
        this.navigationHandler = navigationHandler;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(
                buildSummaryCards(),
                buildLiveChartsSection(),
                buildRecentAlertsSection()
        );

        setContent(content);

        // Listen for twin updates to refresh UI smoothly
        digitalTwinService.addTwinUpdateListener(twin -> Platform.runLater(() -> onTwinUpdated(twin)));

        // Preload initial data
        refreshAllMetrics();
    }

    private GridPane buildSummaryCards() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        // Ensure 4 columns on standard resolutions
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            grid.getColumnConstraints().add(col);
        }

        grid.add(createCard("TOTAL MACHINES", lblTotalMachines, "Active & Standby assets"), 0, 0);
        grid.add(createStatusCard("NORMAL OPERATING", lblNormalMachines, "#10B981", "Optimal operational condition"), 1, 0);
        grid.add(createStatusCard("WARNING STATE", lblWarningMachines, "#F59E0B", "Threshold violations detected"), 2, 0);
        grid.add(createStatusCard("CRITICAL STATE", lblCriticalMachines, "#EF4444", "Urgent intervention needed"), 3, 0);

        grid.add(createCard("TOTAL PRODUCTION", lblTotalProduction, "Cumulative factory units"), 0, 1);
        grid.add(createCard("FACTORY HEALTH", lblFactoryHealth, "Illustrative condition index"), 1, 1);
        grid.add(createCard("LAST TELEMETRY", lblLastUpdate, "Sensor stream timestamp"), 2, 1);
        grid.add(createCard("SIMULATION STATE", lblSimStatus, "Configured @ " + digitalTwinService.getSimulationInterval() + "ms"), 3, 1);

        return grid;
    }

    private VBox createCard(String title, Label valLabel, String sub) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("card-title");

        valLabel.getStyleClass().add("card-value");

        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(titleLbl, valLabel, subLbl);
        return card;
    }

    private VBox createStatusCard(String title, Label valLabel, String colorHex, String sub) {
        VBox card = createCard(title, valLabel, sub);
        valLabel.setStyle("-fx-text-fill: " + colorHex + ";");
        return card;
    }

    private VBox buildLiveChartsSection() {
        VBox container = new VBox(16);
        container.getStyleClass().add("card");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("REAL-TIME MULTI-MACHINE TELEMETRY");
        title.getStyleClass().add("page-title");
        title.setStyle("-fx-font-size: 16px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label legend = new Label("Live 2-second streaming buffer");
        legend.getStyleClass().add("card-subtitle");

        header.getChildren().addAll(title, spacer, legend);

        // Create Charts
        tempChart = createLineChart("Temperature (°C)");
        vibChart = createLineChart("Vibration (mm/s)");
        rpmChart = createLineChart("Motor Speed (RPM)");

        // Initialize series for each machine
        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            XYChart.Series<String, Number> tSeries = new XYChart.Series<>();
            tSeries.setName(twin.getMachineCode() + " Temp");
            tempSeriesMap.put(twin.getId(), tSeries);
            tempChart.getData().add(tSeries);

            XYChart.Series<String, Number> vSeries = new XYChart.Series<>();
            vSeries.setName(twin.getMachineCode() + " Vib");
            vibSeriesMap.put(twin.getId(), vSeries);
            vibChart.getData().add(vSeries);

            XYChart.Series<String, Number> rSeries = new XYChart.Series<>();
            rSeries.setName(twin.getMachineCode() + " RPM");
            rpmSeriesMap.put(twin.getId(), rSeries);
            rpmChart.getData().add(rSeries);

            // Preload history
            for (SensorReading r : twin.getRecentHistory()) {
                String timeStr = DateTimeUtil.formatTimeOnly(r.getRecordedAt());
                tSeries.getData().add(new XYChart.Data<>(timeStr, r.getTemperatureC()));
                vSeries.getData().add(new XYChart.Data<>(timeStr, r.getVibrationMmS()));
                rSeries.getData().add(new XYChart.Data<>(timeStr, r.getMotorRpm()));
            }
        }

        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: transparent;");

        Tab tabTemp = new Tab("Temperature Stream", tempChart);
        tabTemp.setClosable(false);

        Tab tabVib = new Tab("Vibration Stream", vibChart);
        tabVib.setClosable(false);

        Tab tabRpm = new Tab("Motor Speed Stream", rpmChart);
        tabRpm.setClosable(false);

        tabPane.getTabs().addAll(tabTemp, tabVib, tabRpm);
        container.getChildren().addAll(header, tabPane);
        return container;
    }

    private LineChart<String, Number> createLineChart(String yAxisLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        xAxis.setAnimated(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yAxisLabel);
        yAxis.setAnimated(false);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false); // Critical for high-performance JavaFX live charts
        chart.setCreateSymbols(false);
        chart.setPrefHeight(300);
        return chart;
    }

    private VBox buildRecentAlertsSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("RECENT ALERTS & CONDITIONS");
        title.getStyleClass().add("page-title");
        title.setStyle("-fx-font-size: 16px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnViewAll = new Button("View All Alerts");
        btnViewAll.getStyleClass().add("btn-secondary");
        btnViewAll.setOnAction(e -> navigationHandler.accept("alerts"));

        header.getChildren().addAll(title, spacer, btnViewAll);

        recentAlertsTable = new TableView<>();
        recentAlertsTable.setPrefHeight(180);
        recentAlertsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Alert, String> colMachine = new TableColumn<>("Machine");
        colMachine.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getMachineCode() != null ? data.getValue().getMachineCode() : "ID:" + data.getValue().getMachineId()
        ));

        TableColumn<Alert, String> colSeverity = new TableColumn<>("Severity");
        colSeverity.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getSeverity().name()
        ));
        colSeverity.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    if ("CRITICAL".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-critical");
                    } else if ("WARNING".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-warning");
                    } else {
                        badge.getStyleClass().add("badge-normal");
                    }
                    setGraphic(badge);
                }
            }
        });

        TableColumn<Alert, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));

        TableColumn<Alert, String> colTime = new TableColumn<>("Created At");
        colTime.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                DateTimeUtil.formatFull(data.getValue().getCreatedAt())
        ));

        TableColumn<Alert, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus().name()));

        TableColumn<Alert, Void> colAction = new TableColumn<>("Action");
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnAck = new Button("Acknowledge");
            private final Button btnResolve = new Button("Resolve");
            private final HBox btnBox = new HBox(6, btnAck, btnResolve);

            {
                btnAck.getStyleClass().add("btn-primary");
                btnAck.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                btnAck.setOnAction(event -> {
                    Alert alert = getTableView().getItems().get(getIndex());
                    if (alert != null) {
                        digitalTwinService.getAlertLifecycleManager().acknowledgeAlert(alert.getId());
                        refreshAlerts();
                    }
                });

                btnResolve.getStyleClass().add("btn-secondary");
                btnResolve.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                btnResolve.setOnAction(event -> {
                    Alert alert = getTableView().getItems().get(getIndex());
                    if (alert != null) {
                        digitalTwinService.getAlertLifecycleManager().manuallyResolveAlert(alert.getId());
                        refreshAlerts();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Alert alert = getTableView().getItems().get(getIndex());
                    if (alert == null || alert.getStatus() == AlertStatus.RESOLVED) {
                        setGraphic(null);
                    } else if (alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
                        btnBox.getChildren().setAll(btnResolve);
                        setGraphic(btnBox);
                    } else {
                        btnBox.getChildren().setAll(btnAck, btnResolve);
                        setGraphic(btnBox);
                    }
                }
            }
        });

        recentAlertsTable.getColumns().addAll(colMachine, colSeverity, colDesc, colTime, colStatus, colAction);
        container.getChildren().addAll(header, recentAlertsTable);

        refreshAlerts();
        return container;
    }

    private void onTwinUpdated(DigitalTwinMachine twin) {
        SensorReading reading = twin.getLatestReading();
        if (reading == null) return;

        String timeStr = DateTimeUtil.formatTimeOnly(reading.getRecordedAt());

        // Update charts (auto-registering series for new machines dynamically)
        appendChartPoint(tempSeriesMap, tempChart, twin, "Temp", timeStr, reading.getTemperatureC());
        appendChartPoint(vibSeriesMap, vibChart, twin, "Vib", timeStr, reading.getVibrationMmS());
        appendChartPoint(rpmSeriesMap, rpmChart, twin, "RPM", timeStr, reading.getMotorRpm());

        refreshAllMetrics();
    }

    private void appendChartPoint(Map<Long, XYChart.Series<String, Number>> map, LineChart<String, Number> chart,
                                  DigitalTwinMachine twin, String labelSuffix, String time, Number val) {
        XYChart.Series<String, Number> series = map.computeIfAbsent(twin.getId(), id -> {
            XYChart.Series<String, Number> newSeries = new XYChart.Series<>();
            newSeries.setName(twin.getMachineCode() + " " + labelSuffix);
            chart.getData().add(newSeries);
            return newSeries;
        });

        series.getData().add(new XYChart.Data<>(time, val));
        // Keep sliding window of last 25 points
        if (series.getData().size() > 25) {
            series.getData().remove(0);
        }
    }

    public void refreshAllMetrics() {
        int total = 0;
        int normal = 0;
        int warning = 0;
        int critical = 0;
        long totalProd = 0;
        double healthSum = 0;
        Instant latestTime = null;

        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            total++;
            totalProd += twin.getProductionCount();
            healthSum += twin.getHealthScore();

            if (twin.getLastUpdated() != null) {
                if (latestTime == null || twin.getLastUpdated().isAfter(latestTime)) {
                    latestTime = twin.getLastUpdated();
                }
            }

            switch (twin.getCurrentState()) {
                case NORMAL -> normal++;
                case WARNING -> warning++;
                case CRITICAL -> critical++;
                default -> {}
            }
        }

        lblTotalMachines.setText(String.valueOf(total));
        lblNormalMachines.setText(String.valueOf(normal));
        lblWarningMachines.setText(String.valueOf(warning));
        lblCriticalMachines.setText(String.valueOf(critical));
        lblTotalProduction.setText(String.format("%,d", totalProd));

        double avgHealth = total > 0 ? (healthSum / total) : 100.0;
        lblFactoryHealth.setText(String.format("%.1f%%", avgHealth));
        lblLastUpdate.setText(DateTimeUtil.formatTimeOnly(latestTime));
        lblSimStatus.setText(digitalTwinService.isSimulationRunning() ? "RUNNING" : "STOPPED");
        lblSimStatus.setStyle(digitalTwinService.isSimulationRunning() ? "-fx-text-fill: #10B981;" : "-fx-text-fill: #94A3B8;");

        refreshAlerts();
    }

    private void refreshAlerts() {
        try {
            List<Alert> recent = digitalTwinService.getAlertDao().findRecentAlerts(15);
            recentAlertsTable.getItems().setAll(recent);
        } catch (Exception ignored) {}
    }
}
