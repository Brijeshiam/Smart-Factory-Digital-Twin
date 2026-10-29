package com.smartfactory.ui;

import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.simulation.FaultScenario;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class FactoryFloorView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;
    private final BiConsumer<String, Long> navigationWithParam;
    private final FlowPane floorGrid;
    private final Map<Long, VBox> cardMap = new HashMap<>();

    public FactoryFloorView(DigitalTwinService digitalTwinService, BiConsumer<String, Long> navigationWithParam) {
        this.digitalTwinService = digitalTwinService;
        this.navigationWithParam = navigationWithParam;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("2D FACTORY FLOOR OVERVIEW");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Real-time physical asset health synchronization & condition monitoring");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label legend = new Label("Click any asset to inspect detailed telemetry & history");
        legend.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: 500;");

        header.getChildren().addAll(titleBox, spacer, legend);

        // Grid for machine cards
        floorGrid = new FlowPane();
        floorGrid.setHgap(20);
        floorGrid.setVgap(20);
        floorGrid.setPrefWrapLength(1000);

        root.getChildren().addAll(header, floorGrid);
        setContent(root);

        // Prepopulate cards
        buildMachineCards();

        // Listen for twin updates to update card visuals
        digitalTwinService.addTwinUpdateListener(twin -> Platform.runLater(() -> updateMachineCard(twin)));
    }

    public void buildMachineCards() {
        floorGrid.getChildren().clear();
        cardMap.clear();

        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            VBox card = createMachineCard(twin);
            cardMap.put(twin.getId(), card);
            floorGrid.getChildren().add(card);
        }
    }

    private VBox createMachineCard(DigitalTwinMachine twin) {
        VBox card = new VBox(12);
        card.setPrefWidth(320);
        card.setMinWidth(300);
        applyCardStyle(card, twin.getCurrentState());

        // Header: Code, Name & Status Badge
        HBox cardHeader = new HBox(8);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        VBox codeBox = new VBox(2);
        Label codeLbl = new Label(twin.getMachineCode());
        codeLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label nameLbl = new Label(twin.getMachineName() + " (" + twin.getLocation() + ")");
        nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        codeBox.getChildren().addAll(codeLbl, nameLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label();
        badge.setId("status-badge-" + twin.getId());
        updateBadge(badge, twin);

        cardHeader.getChildren().addAll(codeBox, spacer, badge);

        // Telemetry Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(12);
        metricsGrid.setVgap(8);

        SensorReading reading = twin.getLatestReading();
        double temp = reading != null ? reading.getTemperatureC() : 0.0;
        double vib = reading != null ? reading.getVibrationMmS() : 0.0;
        double rpm = reading != null ? reading.getMotorRpm() : 0.0;
        long prod = reading != null ? reading.getProductionCount() : 0L;

        Label lblTemp = new Label(String.format("%.1f °C", temp));
        lblTemp.setId("temp-val-" + twin.getId());
        lblTemp.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label lblVib = new Label(String.format("%.2f mm/s", vib));
        lblVib.setId("vib-val-" + twin.getId());
        lblVib.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label lblRpm = new Label(String.format("%.0f RPM", rpm));
        lblRpm.setId("rpm-val-" + twin.getId());
        lblRpm.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        Label lblProd = new Label(String.format("%,d", prod));
        lblProd.setId("prod-val-" + twin.getId());
        lblProd.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

        metricsGrid.add(createMetricBox("Temperature", lblTemp), 0, 0);
        metricsGrid.add(createMetricBox("Vibration", lblVib), 1, 0);
        metricsGrid.add(createMetricBox("Motor Speed", lblRpm), 0, 1);
        metricsGrid.add(createMetricBox("Production Output", lblProd), 1, 1);

        // Health Score Progress Bar
        VBox healthBox = new VBox(4);
        HBox healthHeader = new HBox();
        Label healthTitle = new Label("Illustrative Health Index");
        healthTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        Label healthVal = new Label(String.format("%.0f / 100", twin.getHealthScore()));
        healthVal.setId("health-val-" + twin.getId());
        healthVal.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        healthHeader.getChildren().addAll(healthTitle, hSpacer, healthVal);

        ProgressBar healthBar = new ProgressBar(twin.getHealthScore() / 100.0);
        healthBar.setId("health-bar-" + twin.getId());
        healthBar.setMaxWidth(Double.MAX_VALUE);
        healthBar.setStyle("-fx-accent: " + twin.getCurrentState().getHexColor() + ";");
        healthBox.getChildren().addAll(healthHeader, healthBar);

        // Quick Fault Scenario Injector
        HBox faultBox = new HBox(8);
        faultBox.setAlignment(Pos.CENTER_LEFT);

        ComboBox<FaultScenario> faultCombo = new ComboBox<>();
        faultCombo.getItems().addAll(FaultScenario.values());
        faultCombo.setValue(digitalTwinService.getMachineFault(twin.getId()));
        faultCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(faultCombo, Priority.ALWAYS);
        faultCombo.setOnAction(e -> {
            FaultScenario selected = faultCombo.getValue();
            if (selected != null) {
                digitalTwinService.injectFault(twin.getId(), selected);
            }
        });

        faultBox.getChildren().add(faultCombo);

        // Action Buttons
        HBox actions = new HBox(8);
        Button btnInspect = new Button("Inspect Details");
        btnInspect.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnInspect, Priority.ALWAYS);
        btnInspect.getStyleClass().add("btn-primary");
        btnInspect.setOnAction(e -> navigationWithParam.accept("machine_details", twin.getId()));

        actions.getChildren().add(btnInspect);

        card.getChildren().addAll(cardHeader, metricsGrid, healthBox, new Separator(), faultBox, actions);
        return card;
    }

    private VBox createMetricBox(String label, Label valueLabel) {
        VBox box = new VBox(2);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        box.getChildren().addAll(lbl, valueLabel);
        return box;
    }

    private void updateMachineCard(DigitalTwinMachine twin) {
        VBox card = cardMap.get(twin.getId());
        if (card == null) {
            card = createMachineCard(twin);
            cardMap.put(twin.getId(), card);
            floorGrid.getChildren().add(card);
        }

        applyCardStyle(card, twin.getCurrentState());

        Label badge = (Label) card.lookup("#status-badge-" + twin.getId());
        if (badge != null) {
            updateBadge(badge, twin);
        }

        SensorReading reading = twin.getLatestReading();
        if (reading != null) {
            Label lblTemp = (Label) card.lookup("#temp-val-" + twin.getId());
            if (lblTemp != null) lblTemp.setText(String.format("%.1f °C", reading.getTemperatureC()));

            Label lblVib = (Label) card.lookup("#vib-val-" + twin.getId());
            if (lblVib != null) lblVib.setText(String.format("%.2f mm/s", reading.getVibrationMmS()));

            Label lblRpm = (Label) card.lookup("#rpm-val-" + twin.getId());
            if (lblRpm != null) lblRpm.setText(String.format("%.0f RPM", reading.getMotorRpm()));

            Label lblProd = (Label) card.lookup("#prod-val-" + twin.getId());
            if (lblProd != null) lblProd.setText(String.format("%,d", reading.getProductionCount()));
        }

        Label healthVal = (Label) card.lookup("#health-val-" + twin.getId());
        if (healthVal != null) healthVal.setText(String.format("%.0f / 100", twin.getHealthScore()));

        ProgressBar healthBar = (ProgressBar) card.lookup("#health-bar-" + twin.getId());
        if (healthBar != null) {
            healthBar.setProgress(twin.getHealthScore() / 100.0);
            healthBar.setStyle("-fx-accent: " + twin.getCurrentState().getHexColor() + ";");
        }
    }

    private void applyCardStyle(VBox card, MachineState state) {
        card.getStyleClass().removeAll("machine-card-normal", "machine-card-warning", "machine-card-critical", "machine-card-offline");
        switch (state) {
            case NORMAL -> card.getStyleClass().add("machine-card-normal");
            case WARNING -> card.getStyleClass().add("machine-card-warning");
            case CRITICAL -> card.getStyleClass().add("machine-card-critical");
            case OFFLINE -> card.getStyleClass().add("machine-card-offline");
        }
    }

    private void applyBadgeStyle(Label badge, MachineState state) {
        badge.getStyleClass().removeAll("badge-normal", "badge-warning", "badge-critical", "badge-offline");
        switch (state) {
            case NORMAL -> badge.getStyleClass().add("badge-normal");
            case WARNING -> badge.getStyleClass().add("badge-warning");
            case CRITICAL -> badge.getStyleClass().add("badge-critical");
            case OFFLINE -> badge.getStyleClass().add("badge-offline");
        }
    }

    private void updateBadge(Label badge, DigitalTwinMachine twin) {
        boolean isAck = digitalTwinService.getAlertLifecycleManager().isMachineAlertAcknowledged(twin.getId());
        boolean isSuppressed = digitalTwinService.getAlertLifecycleManager().isMachineSuppressed(twin.getId());

        if (twin.getCurrentState() == MachineState.WARNING && isAck) {
            badge.setText("WARNING (ACK)");
            badge.getStyleClass().removeAll("badge-normal", "badge-warning", "badge-critical", "badge-offline");
            badge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.25); -fx-text-fill: #FBBF24; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-background-radius: 4px;");
        } else if (twin.getCurrentState() == MachineState.WARNING && isSuppressed) {
            badge.setText("RESOLVED (STANDBY)");
            badge.getStyleClass().removeAll("badge-normal", "badge-warning", "badge-critical", "badge-offline");
            badge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #34D399; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-background-radius: 4px;");
        } else {
            badge.setText(twin.getCurrentState().name());
            badge.setStyle(null);
            applyBadgeStyle(badge, twin.getCurrentState());
        }
    }
}
