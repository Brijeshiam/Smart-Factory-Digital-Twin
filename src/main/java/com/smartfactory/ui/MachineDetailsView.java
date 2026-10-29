package com.smartfactory.ui;

import com.smartfactory.alert.ThresholdEvaluator;
import com.smartfactory.model.*;
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

import java.util.List;

public class MachineDetailsView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;

    private final ComboBox<DigitalTwinMachine> machineSelector = new ComboBox<>();
    private final Label lblCode = new Label();
    private final Label lblName = new Label();
    private final Label lblLocation = new Label();
    private final Label lblStateBadge = new Label();
    private final Label lblTemp = new Label();
    private final Label lblVib = new Label();
    private final Label lblRpm = new Label();
    private final Label lblProd = new Label();
    private final Label lblHealth = new Label();
    private final Label lblLastUpdate = new Label();

    // Threshold labels
    private final Label lblTempLimits = new Label();
    private final Label lblVibLimits = new Label();
    private final Label lblNominalRpm = new Label();

    // Machine-Specific Charts
    private LineChart<String, Number> detailChart;
    private final XYChart.Series<String, Number> tempSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> vibSeries = new XYChart.Series<>();

    // Historical Table from Database
    private TableView<SensorReading> historyTable;
    private final ComboBox<Integer> limitSelector = new ComboBox<>();

    public MachineDetailsView(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildTopBar(),
                buildMetadataSection(),
                buildTelemetryGauges(),
                buildChartSection(),
                buildHistoricalTableSection()
        );

        setContent(content);

        // Preload selector
        refreshMachineSelector();

        // Listen for twin updates
        digitalTwinService.addTwinUpdateListener(twin -> Platform.runLater(() -> {
            DigitalTwinMachine selected = machineSelector.getValue();
            if (selected != null && selected.getId().equals(twin.getId())) {
                populateMachineData(twin);
            }
        }));
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("MACHINE TELEMETRY & DIGITAL TWIN DETAILS");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Individual asset drill-down, synchronized parameters and historical telemetry");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label selLabel = new Label("Select Asset:");
        selLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        machineSelector.setPrefWidth(260);
        machineSelector.setOnAction(e -> {
            DigitalTwinMachine sel = machineSelector.getValue();
            if (sel != null) {
                populateMachineData(sel);
                loadDatabaseHistory(sel.getId());
            }
        });

        bar.getChildren().addAll(titleBox, spacer, selLabel, machineSelector);
        return bar;
    }

    private HBox buildMetadataSection() {
        HBox box = new HBox(16);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER_LEFT);

        lblCode.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");
        lblName.setStyle("-fx-font-size: 14px; -fx-text-fill: #94A3B8;");
        lblLocation.setStyle("-fx-font-size: 13px; -fx-text-fill: #38BDF8;");

        VBox mainInfo = new VBox(4, lblCode, lblName, lblLocation);
        mainInfo.setPrefWidth(280);

        Separator sep1 = new Separator(javafx.geometry.Orientation.VERTICAL);

        VBox thresholdBox = new VBox(4);
        Label tTitle = new Label("CONFIGURED OPERATING LIMITS");
        tTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        lblTempLimits.setStyle("-fx-font-size: 12px; -fx-text-fill: #F8FAFC;");
        lblVibLimits.setStyle("-fx-font-size: 12px; -fx-text-fill: #F8FAFC;");
        lblNominalRpm.setStyle("-fx-font-size: 12px; -fx-text-fill: #F8FAFC;");
        thresholdBox.getChildren().addAll(tTitle, lblTempLimits, lblVibLimits, lblNominalRpm);
        thresholdBox.setPrefWidth(280);

        Separator sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        VBox stateBox = new VBox(6);
        Label sTitle = new Label("CURRENT OPERATING CONDITION");
        sTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        lblStateBadge.getStyleClass().add("badge-normal");
        lblLastUpdate.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        stateBox.getChildren().addAll(sTitle, lblStateBadge, lblLastUpdate);

        box.getChildren().addAll(mainInfo, sep1, thresholdBox, sep2, stateBox);
        return box;
    }

    private GridPane buildTelemetryGauges() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        for (int i = 0; i < 5; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(20);
            grid.getColumnConstraints().add(col);
        }

        grid.add(createGaugeCard("TEMPERATURE", lblTemp, "Deg Celsius (°C)"), 0, 0);
        grid.add(createGaugeCard("VIBRATION", lblVib, "Velocity (mm/s)"), 1, 0);
        grid.add(createGaugeCard("MOTOR SPEED", lblRpm, "Revolutions (RPM)"), 2, 0);
        grid.add(createGaugeCard("PRODUCTION COUNT", lblProd, "Total parts produced"), 3, 0);
        grid.add(createGaugeCard("HEALTH INDEX", lblHealth, "Proximity score (0-100)"), 4, 0);

        return grid;
    }

    private VBox createGaugeCard(String title, Label valLabel, String sub) {
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

    private VBox buildChartSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        Label title = new Label("REAL-TIME TEMPERATURE & VIBRATION TREND");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Recorded Time");
        xAxis.setAnimated(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Sensor Magnitude");
        yAxis.setAnimated(false);

        detailChart = new LineChart<>(xAxis, yAxis);
        detailChart.setAnimated(false);
        detailChart.setCreateSymbols(false);
        detailChart.setPrefHeight(260);

        tempSeries.setName("Temperature (°C)");
        vibSeries.setName("Vibration (mm/s)");

        detailChart.getData().addAll(tempSeries, vibSeries);
        container.getChildren().addAll(title, detailChart);
        return container;
    }

    private VBox buildHistoricalTableSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("POSTGRESQL HISTORICAL TELEMETRY LOGS");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblRows = new Label("Rows to fetch:");
        lblRows.setStyle("-fx-text-fill: #94A3B8;");

        limitSelector.getItems().addAll(25, 50, 100, 250);
        limitSelector.setValue(50);
        limitSelector.setOnAction(e -> {
            DigitalTwinMachine sel = machineSelector.getValue();
            if (sel != null) loadDatabaseHistory(sel.getId());
        });

        Button btnRefresh = new Button("Refresh DB Logs");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> {
            DigitalTwinMachine sel = machineSelector.getValue();
            if (sel != null) loadDatabaseHistory(sel.getId());
        });

        header.getChildren().addAll(title, spacer, lblRows, limitSelector, btnRefresh);

        historyTable = new TableView<>();
        historyTable.setPrefHeight(220);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SensorReading, String> colTime = new TableColumn<>("Timestamp");
        colTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                DateTimeUtil.formatFull(d.getValue().getRecordedAt())
        ));

        TableColumn<SensorReading, String> colTemp = new TableColumn<>("Temp (°C)");
        colTemp.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f", d.getValue().getTemperatureC())
        ));

        TableColumn<SensorReading, String> colVib = new TableColumn<>("Vibration (mm/s)");
        colVib.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", d.getValue().getVibrationMmS())
        ));

        TableColumn<SensorReading, String> colRpm = new TableColumn<>("Motor RPM");
        colRpm.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.0f", d.getValue().getMotorRpm())
        ));

        TableColumn<SensorReading, String> colProd = new TableColumn<>("Production Count");
        colProd.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%,d", d.getValue().getProductionCount())
        ));

        TableColumn<SensorReading, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMachineStatus().name()
        ));

        TableColumn<SensorReading, String> colAlert = new TableColumn<>("Condition Message");
        colAlert.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getAlertMessage() != null ? d.getValue().getAlertMessage() : "Normal"
        ));

        historyTable.getColumns().addAll(colTime, colTemp, colVib, colRpm, colProd, colStatus, colAlert);
        container.getChildren().addAll(header, historyTable);
        return container;
    }

    public void selectMachineById(Long machineId) {
        for (DigitalTwinMachine twin : machineSelector.getItems()) {
            if (twin.getId().equals(machineId)) {
                machineSelector.setValue(twin);
                populateMachineData(twin);
                loadDatabaseHistory(twin.getId());
                break;
            }
        }
    }

    public void refreshMachineSelector() {
        DigitalTwinMachine currentSel = machineSelector.getValue();
        machineSelector.getItems().setAll(digitalTwinService.getAllTwins());

        if (currentSel != null) {
            selectMachineById(currentSel.getId());
        } else if (!machineSelector.getItems().isEmpty()) {
            machineSelector.setValue(machineSelector.getItems().getFirst());
            populateMachineData(machineSelector.getItems().getFirst());
            loadDatabaseHistory(machineSelector.getItems().getFirst().getId());
        }
    }

    private void populateMachineData(DigitalTwinMachine twin) {
        lblCode.setText(twin.getMachineCode());
        lblName.setText(twin.getMachineName());
        lblLocation.setText("Location: " + twin.getLocation());

        MachineConfiguration cfg = twin.getConfiguration();
        if (cfg != null) {
            lblTempLimits.setText(String.format("Temp Warn: %.1f°C | Crit: %.1f°C", cfg.getTempWarningLimit(), cfg.getTempCriticalLimit()));
            lblVibLimits.setText(String.format("Vib Warn: %.2f mm/s | Crit: %.2f mm/s", cfg.getVibWarningLimit(), cfg.getVibCriticalLimit()));
            lblNominalRpm.setText(String.format("Nominal RPM: %.0f RPM", cfg.getNominalRpm()));
        }

        lblStateBadge.setText(twin.getCurrentState().name());
        lblStateBadge.getStyleClass().removeAll("badge-normal", "badge-warning", "badge-critical", "badge-offline");
        switch (twin.getCurrentState()) {
            case NORMAL -> lblStateBadge.getStyleClass().add("badge-normal");
            case WARNING -> lblStateBadge.getStyleClass().add("badge-warning");
            case CRITICAL -> lblStateBadge.getStyleClass().add("badge-critical");
            case OFFLINE -> lblStateBadge.getStyleClass().add("badge-offline");
        }

        lblLastUpdate.setText("Updated: " + DateTimeUtil.formatTimeOnly(twin.getLastUpdated()));

        SensorReading reading = twin.getLatestReading();
        if (reading != null) {
            lblTemp.setText(String.format("%.1f °C", reading.getTemperatureC()));
            lblVib.setText(String.format("%.2f mm/s", reading.getVibrationMmS()));
            lblRpm.setText(String.format("%.0f RPM", reading.getMotorRpm()));
            lblProd.setText(String.format("%,d", reading.getProductionCount()));

            // Update real-time chart sliding window
            String timeStr = DateTimeUtil.formatTimeOnly(reading.getRecordedAt());
            tempSeries.getData().add(new XYChart.Data<>(timeStr, reading.getTemperatureC()));
            vibSeries.getData().add(new XYChart.Data<>(timeStr, reading.getVibrationMmS()));

            if (tempSeries.getData().size() > 30) tempSeries.getData().remove(0);
            if (vibSeries.getData().size() > 30) vibSeries.getData().remove(0);
        }

        lblHealth.setText(String.format("%.0f / 100", twin.getHealthScore()));
    }

    private void loadDatabaseHistory(Long machineId) {
        new Thread(() -> {
            try {
                int limit = limitSelector.getValue() != null ? limitSelector.getValue() : 50;
                List<SensorReading> list = digitalTwinService.getSensorReadingDao().findRecentByMachineId(machineId, limit);
                Platform.runLater(() -> historyTable.getItems().setAll(list));
            } catch (Exception e) {
                // Ignore DB read failure during offline state
            }
        }).start();
    }
}
