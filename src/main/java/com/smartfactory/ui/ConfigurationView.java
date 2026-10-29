package com.smartfactory.ui;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.model.Machine;
import com.smartfactory.model.MachineConfiguration;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.util.ValidationUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ConfigurationView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;
    private final Runnable onDataUpdated;

    // Table of machines
    private final TableView<DigitalTwinMachine> machineTable = new TableView<>();

    // Add Machine Form Fields
    private final TextField txtAddCode = new TextField();
    private final TextField txtAddName = new TextField();
    private final TextField txtAddLocation = new TextField();
    private final CheckBox chkAddEnabled = new CheckBox("Enabled");

    // Threshold Configuration Form Fields
    private final ComboBox<DigitalTwinMachine> thresholdMachineCombo = new ComboBox<>();
    private final TextField txtTempWarn = new TextField();
    private final TextField txtTempCrit = new TextField();
    private final TextField txtVibWarn = new TextField();
    private final TextField txtVibCrit = new TextField();
    private final TextField txtNominalRpm = new TextField();

    // Simulation Interval
    private final Slider intervalSlider = new Slider(500, 5000, 2000);
    private final Label lblIntervalVal = new Label("2000 ms");

    // Database Connection
    private final Label lblDbStatus = new Label("Checking...");
    private final Label lblDbUrl = new Label();

    public ConfigurationView(DigitalTwinService digitalTwinService, Runnable onDataUpdated) {
        this.digitalTwinService = digitalTwinService;
        this.onDataUpdated = onDataUpdated;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildHeader(),
                buildDatabasePanel(),
                buildMachineManagementSection(),
                buildThresholdConfigSection(),
                buildSimulationConfigSection()
        );

        setContent(content);

        refreshTable();
        refreshThresholdCombo();
        checkDatabaseStatus();
    }

    private HBox buildHeader() {
        VBox titleBox = new VBox(4);
        Label title = new Label("SYSTEM & ASSET CONFIGURATION");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Manage industrial assets, tune threshold limits, and adjust simulation clock");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        return new HBox(titleBox);
    }

    private VBox buildDatabasePanel() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label title = new Label("POSTGRESQL DATABASE CONNECTIVITY");
        title.getStyleClass().add("card-title");

        HBox infoBox = new HBox(20);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        lblDbUrl.setText("URL: " + DatabaseConfig.getInstance().getDbUrl());
        lblDbUrl.setStyle("-fx-text-fill: #94A3B8; -fx-font-family: monospace;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnTest = new Button("Test / Reconnect");
        btnTest.getStyleClass().add("btn-secondary");
        btnTest.setOnAction(e -> checkDatabaseStatus());

        infoBox.getChildren().addAll(lblDbUrl, spacer, lblDbStatus, btnTest);
        card.getChildren().addAll(title, infoBox);
        return card;
    }

    private VBox buildMachineManagementSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label title = new Label("FACTORY ASSET REGISTRY");
        title.getStyleClass().add("card-title");

        // Machine Table
        machineTable.setPrefHeight(200);
        machineTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DigitalTwinMachine, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(d -> new javafx.beans.property.SimpleLongProperty(d.getValue().getId()));
        colId.setMaxWidth(60);

        TableColumn<DigitalTwinMachine, String> colCode = new TableColumn<>("Machine Code");
        colCode.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getMachineCode()));

        TableColumn<DigitalTwinMachine, String> colName = new TableColumn<>("Asset Name");
        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getMachineName()));

        TableColumn<DigitalTwinMachine, String> colLoc = new TableColumn<>("Location");
        colLoc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getLocation()));

        TableColumn<DigitalTwinMachine, String> colEnabled = new TableColumn<>("Enabled");
        colEnabled.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().isEnabled() ? "YES" : "NO"));
        colEnabled.setMaxWidth(70);

        TableColumn<DigitalTwinMachine, String> colStatus = new TableColumn<>("Live State");
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCurrentState().name()));
        colStatus.setMaxWidth(100);
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

        TableColumn<DigitalTwinMachine, Void> colActions = new TableColumn<>("Toggle");
        colActions.setMaxWidth(100);
        colActions.setCellFactory(c -> new TableCell<>() {
            private final Button btnToggle = new Button("Toggle");
            {
                btnToggle.getStyleClass().add("btn-secondary");
                btnToggle.setStyle("-fx-font-size: 11px; -fx-padding: 3 8 3 8;");
                btnToggle.setOnAction(e -> {
                    DigitalTwinMachine twin = getTableView().getItems().get(getIndex());
                    if (twin != null) {
                        Machine m = twin.getMachine();
                        m.setEnabled(!m.isEnabled());
                        digitalTwinService.saveMachine(m, twin.getConfiguration());
                        refreshTable();
                        if (onDataUpdated != null) onDataUpdated.run();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnToggle);
            }
        });

        machineTable.getColumns().addAll(colId, colCode, colName, colLoc, colEnabled, colStatus, colActions);

        // Add New Machine Form
        TitledPane addPane = new TitledPane();
        addPane.setText("Register New Machine Asset");
        addPane.setExpanded(false);

        HBox form = new HBox(12);
        form.setAlignment(Pos.CENTER_LEFT);
        form.setPadding(new Insets(10));

        txtAddCode.setPromptText("Code (e.g. ROB-04)");
        txtAddCode.setPrefWidth(140);

        txtAddName.setPromptText("Asset Name (e.g. Robot Arm)");
        txtAddName.setPrefWidth(200);

        txtAddLocation.setPromptText("Location (e.g. Cell 4)");
        txtAddLocation.setPrefWidth(160);

        chkAddEnabled.setSelected(true);

        Button btnAdd = new Button("Add Asset");
        btnAdd.getStyleClass().add("btn-primary");
        btnAdd.setOnAction(e -> handleAddMachine());

        form.getChildren().addAll(txtAddCode, txtAddName, txtAddLocation, chkAddEnabled, btnAdd);
        addPane.setContent(form);

        card.getChildren().addAll(title, machineTable, addPane);
        return card;
    }

    private VBox buildThresholdConfigSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label title = new Label("SENSOR THRESHOLD & NOMINAL TUNING");
        title.getStyleClass().add("card-title");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label lblSel = new Label("Select Asset to Configure:");
        lblSel.setStyle("-fx-text-fill: #94A3B8;");
        thresholdMachineCombo.setPrefWidth(280);
        thresholdMachineCombo.setOnAction(e -> loadThresholdsForSelected());
        topRow.getChildren().addAll(lblSel, thresholdMachineCombo);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(16);
        formGrid.setVgap(12);

        formGrid.add(new Label("Temp Warning Limit (°C):"), 0, 0);
        formGrid.add(txtTempWarn, 1, 0);

        formGrid.add(new Label("Temp Critical Limit (°C):"), 2, 0);
        formGrid.add(txtTempCrit, 3, 0);

        formGrid.add(new Label("Vibration Warning (mm/s):"), 0, 1);
        formGrid.add(txtVibWarn, 1, 1);

        formGrid.add(new Label("Vibration Critical (mm/s):"), 2, 1);
        formGrid.add(txtVibCrit, 3, 1);

        formGrid.add(new Label("Nominal RPM:"), 0, 2);
        formGrid.add(txtNominalRpm, 1, 2);

        Button btnSaveThresholds = new Button("Save Threshold Settings");
        btnSaveThresholds.getStyleClass().add("btn-success");
        btnSaveThresholds.setOnAction(e -> handleSaveThresholds());

        card.getChildren().addAll(title, topRow, formGrid, btnSaveThresholds);
        return card;
    }

    private VBox buildSimulationConfigSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label title = new Label("SIMULATION FREQUENCY & ENGINE CONTROL");
        title.getStyleClass().add("card-title");

        HBox sliderRow = new HBox(16);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        Label lblLabel = new Label("Telemetry Generation Interval:");
        lblLabel.setStyle("-fx-text-fill: #94A3B8;");

        intervalSlider.setShowTickLabels(true);
        intervalSlider.setShowTickMarks(true);
        intervalSlider.setMajorTickUnit(1000);
        intervalSlider.setBlockIncrement(250);
        intervalSlider.setValue(digitalTwinService.getSimulationInterval());
        intervalSlider.setPrefWidth(300);

        lblIntervalVal.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");
        lblIntervalVal.setText(digitalTwinService.getSimulationInterval() + " ms");

        intervalSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int ms = newVal.intValue();
            lblIntervalVal.setText(ms + " ms");
        });

        Button btnApplyInterval = new Button("Apply Interval");
        btnApplyInterval.getStyleClass().add("btn-primary");
        btnApplyInterval.setOnAction(e -> {
            int interval = (int) intervalSlider.getValue();
            digitalTwinService.setSimulationInterval(interval);
            showInfoAlert("Simulation Interval Updated", "Sensor readings will now generate every " + interval + " ms.");
        });

        sliderRow.getChildren().addAll(lblLabel, intervalSlider, lblIntervalVal, btnApplyInterval);
        card.getChildren().addAll(title, sliderRow);
        return card;
    }

    private void handleAddMachine() {
        try {
            String code = txtAddCode.getText();
            String name = txtAddName.getText();
            String loc = txtAddLocation.getText();
            boolean enabled = chkAddEnabled.isSelected();

            ValidationUtil.validateMachineCode(code);
            ValidationUtil.validateMachineName(name);

            Machine machine = new Machine(code.trim().toUpperCase(), name.trim(), loc != null ? loc.trim() : "Floor A", enabled);
            MachineConfiguration config = new MachineConfiguration();

            digitalTwinService.saveMachine(machine, config);

            txtAddCode.clear();
            txtAddName.clear();
            txtAddLocation.clear();
            chkAddEnabled.setSelected(true);

            refreshTable();
            refreshThresholdCombo();
            if (onDataUpdated != null) onDataUpdated.run();

            showInfoAlert("Machine Added", "Asset " + machine.getMachineCode() + " successfully registered and live telemetry started.");
        } catch (Exception e) {
            showErrorAlert("Validation Error", e.getMessage());
        }
    }

    private void handleSaveThresholds() {
        DigitalTwinMachine twin = thresholdMachineCombo.getValue();
        if (twin == null) return;

        try {
            double tw = Double.parseDouble(txtTempWarn.getText());
            double tc = Double.parseDouble(txtTempCrit.getText());
            double vw = Double.parseDouble(txtVibWarn.getText());
            double vc = Double.parseDouble(txtVibCrit.getText());
            double rpm = Double.parseDouble(txtNominalRpm.getText());

            ValidationUtil.validateThresholds(tw, tc, vw, vc);

            MachineConfiguration cfg = twin.getConfiguration();
            if (cfg == null) {
                cfg = new MachineConfiguration();
                cfg.setMachineId(twin.getId());
            }

            cfg.setTempWarningLimit(tw);
            cfg.setTempCriticalLimit(tc);
            cfg.setVibWarningLimit(vw);
            cfg.setVibCriticalLimit(vc);
            cfg.setNominalRpm(rpm);

            digitalTwinService.updateMachineConfiguration(cfg);
            if (onDataUpdated != null) onDataUpdated.run();

            showInfoAlert("Thresholds Saved", "Thresholds updated for machine " + twin.getMachineCode());
        } catch (NumberFormatException e) {
            showErrorAlert("Input Error", "Please enter valid numeric values for all threshold fields.");
        } catch (Exception e) {
            showErrorAlert("Error", e.getMessage());
        }
    }

    private void loadThresholdsForSelected() {
        DigitalTwinMachine twin = thresholdMachineCombo.getValue();
        if (twin == null) return;

        MachineConfiguration cfg = twin.getConfiguration();
        if (cfg != null) {
            txtTempWarn.setText(String.valueOf(cfg.getTempWarningLimit()));
            txtTempCrit.setText(String.valueOf(cfg.getTempCriticalLimit()));
            txtVibWarn.setText(String.valueOf(cfg.getVibWarningLimit()));
            txtVibCrit.setText(String.valueOf(cfg.getVibCriticalLimit()));
            txtNominalRpm.setText(String.valueOf(cfg.getNominalRpm()));
        }
    }

    public void refreshTable() {
        machineTable.getItems().setAll(digitalTwinService.getAllTwins());
    }

    public void refreshThresholdCombo() {
        thresholdMachineCombo.getItems().setAll(digitalTwinService.getAllTwins());
        if (!thresholdMachineCombo.getItems().isEmpty()) {
            thresholdMachineCombo.setValue(thresholdMachineCombo.getItems().getFirst());
            loadThresholdsForSelected();
        }
    }

    private void checkDatabaseStatus() {
        lblDbStatus.setText("Connecting...");
        lblDbStatus.setStyle("-fx-text-fill: #F59E0B;");

        new Thread(() -> {
            boolean connected = DatabaseConfig.getInstance().testConnection();
            Platform.runLater(() -> {
                if (connected) {
                    lblDbStatus.setText("CONNECTED (PostgreSQL)");
                    lblDbStatus.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                } else {
                    lblDbStatus.setText("DISCONNECTED");
                    lblDbStatus.setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                }
            });
        }).start();
    }

    private void showInfoAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
