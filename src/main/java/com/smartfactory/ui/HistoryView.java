package com.smartfactory.ui;

import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.util.CsvExporter;
import com.smartfactory.util.DateTimeUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class HistoryView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;

    private final ComboBox<MachineFilterItem> machineCombo = new ComboBox<>();
    private final ComboBox<MachineStateFilterItem> statusCombo = new ComboBox<>();
    private final ComboBox<TimePresetItem> timePresetCombo = new ComboBox<>();
    private final ComboBox<Integer> limitCombo = new ComboBox<>();
    private final TableView<SensorReading> table = new TableView<>();
    private final Label lblRecordCount = new Label("0 records loaded");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    public record MachineFilterItem(Long id, String label) {
        @Override
        public String toString() { return label; }
    }
    public record MachineStateFilterItem(MachineState state, String label) {
        @Override
        public String toString() { return label; }
    }
    public record TimePresetItem(int hours, String label) {
        @Override
        public String toString() { return label; }
    }

    public HistoryView(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildHeader(),
                buildFilterBar(),
                buildTableSection()
        );

        setContent(content);

        initFilters();
        executeHistoryQuery();
    }

    private HBox buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("HISTORICAL TELEMETRY & AUDIT LOGS");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Deep query inspection of PostgreSQL historical sensor timeseries & CSV export");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnExport = new Button("Export to CSV");
        btnExport.getStyleClass().add("btn-success");
        btnExport.setOnAction(e -> exportToCsv());

        header.getChildren().addAll(titleBox, spacer, btnExport);
        return header;
    }

    private HBox buildFilterBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("card");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label lblMachine = new Label("Asset:");
        lblMachine.setStyle("-fx-text-fill: #94A3B8;");
        machineCombo.setPrefWidth(200);

        Label lblStatus = new Label("Status:");
        lblStatus.setStyle("-fx-text-fill: #94A3B8;");
        statusCombo.setPrefWidth(140);

        Label lblTime = new Label("Timeframe:");
        lblTime.setStyle("-fx-text-fill: #94A3B8;");
        timePresetCombo.setPrefWidth(150);

        Label lblLimit = new Label("Max Rows:");
        lblLimit.setStyle("-fx-text-fill: #94A3B8;");
        limitCombo.getItems().addAll(50, 100, 250, 500, 1000);
        limitCombo.setValue(100);
        limitCombo.setPrefWidth(90);

        Button btnQuery = new Button("Run Query");
        btnQuery.getStyleClass().add("btn-primary");
        btnQuery.setOnAction(e -> executeHistoryQuery());

        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);

        bar.getChildren().addAll(lblMachine, machineCombo, lblStatus, statusCombo, lblTime, timePresetCombo, lblLimit, limitCombo, btnQuery, progressIndicator);
        return bar;
    }

    public void initFilters() {
        MachineFilterItem cur = machineCombo.getValue();
        machineCombo.getItems().clear();
        machineCombo.getItems().add(new MachineFilterItem(null, "All Assets"));
        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            machineCombo.getItems().add(new MachineFilterItem(twin.getId(), twin.getMachineCode() + " - " + twin.getMachineName()));
        }
        if (cur != null) {
            machineCombo.setValue(cur);
        } else {
            machineCombo.setValue(machineCombo.getItems().getFirst());
        }

        statusCombo.getItems().clear();
        statusCombo.getItems().addAll(
                new MachineStateFilterItem(null, "All Conditions"),
                new MachineStateFilterItem(MachineState.NORMAL, "Normal Only"),
                new MachineStateFilterItem(MachineState.WARNING, "Warning Only"),
                new MachineStateFilterItem(MachineState.CRITICAL, "Critical Only"),
                new MachineStateFilterItem(MachineState.OFFLINE, "Offline Only")
        );
        statusCombo.setValue(statusCombo.getItems().getFirst());

        timePresetCombo.getItems().clear();
        timePresetCombo.getItems().addAll(
                new TimePresetItem(1, "Past 1 Hour"),
                new TimePresetItem(6, "Past 6 Hours"),
                new TimePresetItem(24, "Past 24 Hours"),
                new TimePresetItem(168, "Past 7 Days"),
                new TimePresetItem(0, "All Historical Time")
        );
        timePresetCombo.setValue(timePresetCombo.getItems().get(2)); // Default past 24h
    }

    private VBox buildTableSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        HBox topInfo = new HBox(12);
        topInfo.setAlignment(Pos.CENTER_LEFT);
        lblRecordCount.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");
        topInfo.getChildren().add(lblRecordCount);

        table.setPrefHeight(450);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<SensorReading, String> colMachine = new TableColumn<>("Machine ID");
        colMachine.setCellValueFactory(d -> {
            Long mId = d.getValue().getMachineId();
            DigitalTwinMachine twin = digitalTwinService.getTwin(mId);
            return new javafx.beans.property.SimpleStringProperty(twin != null ? twin.getMachineCode() : "ID:" + mId);
        });
        colMachine.setMaxWidth(120);

        TableColumn<SensorReading, String> colTime = new TableColumn<>("Timestamp");
        colTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                DateTimeUtil.formatFull(d.getValue().getRecordedAt())
        ));
        colTime.setMaxWidth(160);

        TableColumn<SensorReading, String> colTemp = new TableColumn<>("Temperature (°C)");
        colTemp.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", d.getValue().getTemperatureC())
        ));

        TableColumn<SensorReading, String> colVib = new TableColumn<>("Vibration (mm/s)");
        colVib.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.2f", d.getValue().getVibrationMmS())
        ));

        TableColumn<SensorReading, String> colRpm = new TableColumn<>("Motor Speed (RPM)");
        colRpm.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.0f", d.getValue().getMotorRpm())
        ));

        TableColumn<SensorReading, String> colProd = new TableColumn<>("Cumulative Output");
        colProd.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                String.format("%,d", d.getValue().getProductionCount())
        ));

        TableColumn<SensorReading, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMachineStatus().name()
        ));
        colStatus.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("CRITICAL".equalsIgnoreCase(item)) badge.getStyleClass().add("badge-critical");
                    else if ("WARNING".equalsIgnoreCase(item)) badge.getStyleClass().add("badge-warning");
                    else if ("NORMAL".equalsIgnoreCase(item)) badge.getStyleClass().add("badge-normal");
                    else badge.getStyleClass().add("badge-offline");
                    setGraphic(badge);
                }
            }
        });

        TableColumn<SensorReading, String> colAlert = new TableColumn<>("Alert / Condition");
        colAlert.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getAlertMessage() != null ? d.getValue().getAlertMessage() : "Normal"
        ));

        table.getColumns().addAll(colMachine, colTime, colTemp, colVib, colRpm, colProd, colStatus, colAlert);
        container.getChildren().addAll(topInfo, table);
        return container;
    }

    private void executeHistoryQuery() {
        progressIndicator.setVisible(true);

        Long machineId = machineCombo.getValue() != null ? machineCombo.getValue().id() : null;
        MachineState state = statusCombo.getValue() != null ? statusCombo.getValue().state() : null;
        TimePresetItem timePreset = timePresetCombo.getValue();
        int limit = limitCombo.getValue() != null ? limitCombo.getValue() : 100;

        Instant start = null;
        Instant end = Instant.now();
        if (timePreset != null && timePreset.hours() > 0) {
            start = end.minus(timePreset.hours(), ChronoUnit.HOURS);
        }

        final Instant fStart = start;
        new Thread(() -> {
            try {
                List<SensorReading> readings = digitalTwinService.getSensorReadingDao()
                        .findHistorical(machineId, fStart, end, state, limit, 0);
                long totalCount = digitalTwinService.getSensorReadingDao()
                        .countHistorical(machineId, fStart, end, state);

                Platform.runLater(() -> {
                    table.getItems().setAll(readings);
                    lblRecordCount.setText(String.format("Showing %d of %,d matching records from PostgreSQL", readings.size(), totalCount));
                    progressIndicator.setVisible(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblRecordCount.setText("Error querying PostgreSQL: " + e.getMessage());
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void exportToCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Historical Telemetry CSV");
        chooser.setInitialFileName("smart_factory_telemetry_" + System.currentTimeMillis() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File targetFile = chooser.showSaveDialog(getScene().getWindow());
        if (targetFile != null) {
            new Thread(() -> {
                try {
                    // Export all currently queried items
                    List<SensorReading> items = table.getItems();
                    MachineFilterItem mf = machineCombo.getValue();
                    String mCode = (mf != null && mf.id() != null) ? mf.label() : "ALL_MACHINES";
                    CsvExporter.exportSensorReadings(items, mCode, targetFile);

                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("CSV Export Successful");
                        alert.setHeaderText("Historical Data Exported");
                        alert.setContentText(String.format("Successfully exported %d records to:\n%s", items.size(), targetFile.getAbsolutePath()));
                        alert.showAndWait();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("CSV Export Failed");
                        alert.setHeaderText("Error saving CSV file");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        }
    }
}
