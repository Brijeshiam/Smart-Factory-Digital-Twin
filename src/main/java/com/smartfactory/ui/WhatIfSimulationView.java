package com.smartfactory.ui;

import com.smartfactory.model.Machine;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.service.ProductionSimulationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class WhatIfSimulationView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;
    private final ProductionSimulationService whatIfService = new ProductionSimulationService();

    private final ComboBox<DigitalTwinMachine> machineCombo = new ComboBox<>();
    private final CheckBox chkTakeOffline = new CheckBox("Simulate Asset Offline / Down for Maintenance");
    private final Slider rateSlider = new Slider(-50, 50, 0);
    private final Label lblRateValue = new Label("+0%");
    private final ComboBox<TimeframeItem> timeframeCombo = new ComboBox<>();

    // Result Metric Labels
    private final Label lblBaselineOutput = new Label("0 units");
    private final Label lblScenarioOutput = new Label("0 units");
    private final Label lblDifference = new Label("0 units");
    private final Label lblPercentage = new Label("0.0%");

    private BarChart<String, Number> comparisonChart;
    private final XYChart.Series<String, Number> baselineSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> scenarioSeries = new XYChart.Series<>();

    public record TimeframeItem(int hours, String label) {
        @Override
        public String toString() { return label; }
    }

    public WhatIfSimulationView(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildHeader(),
                buildControlsAndResults(),
                buildChartSection(),
                buildDisclaimerNotice()
        );

        setContent(content);

        refreshControls();
    }

    private HBox buildHeader() {
        VBox titleBox = new VBox(4);
        Label title = new Label("PRODUCTION WHAT-IF IMPACT ANALYSIS");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Simulate asset downtime, shift rate modifications, and capacity planning without affecting live telemetry");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        return new HBox(titleBox);
    }

    private GridPane buildControlsAndResults() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(16);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(45);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(55);
        grid.getColumnConstraints().addAll(c1, c2);

        // Left Panel: Controls
        VBox controlsCard = new VBox(14);
        controlsCard.getStyleClass().add("card");

        Label cTitle = new Label("SCENARIO PARAMETERS");
        cTitle.getStyleClass().add("card-title");

        VBox selBox = new VBox(4);
        Label lblSel = new Label("Target Machine:");
        lblSel.setStyle("-fx-text-fill: #94A3B8;");
        machineCombo.setMaxWidth(Double.MAX_VALUE);
        machineCombo.setOnAction(e -> runCalculation());
        selBox.getChildren().addAll(lblSel, machineCombo);

        chkTakeOffline.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold;");
        chkTakeOffline.setOnAction(e -> {
            rateSlider.setDisable(chkTakeOffline.isSelected());
            runCalculation();
        });

        VBox sliderBox = new VBox(6);
        HBox sliderHeader = new HBox();
        Label lblSlider = new Label("Production Speed Adjustment:");
        lblSlider.setStyle("-fx-text-fill: #94A3B8;");
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        lblRateValue.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold;");
        sliderHeader.getChildren().addAll(lblSlider, r, lblRateValue);

        rateSlider.setShowTickLabels(true);
        rateSlider.setShowTickMarks(true);
        rateSlider.setMajorTickUnit(25);
        rateSlider.setMinorTickCount(4);
        rateSlider.setBlockIncrement(5);
        rateSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int rounded = newVal.intValue();
            lblRateValue.setText((rounded >= 0 ? "+" : "") + rounded + "%");
            runCalculation();
        });
        sliderBox.getChildren().addAll(sliderHeader, rateSlider);

        VBox timeBox = new VBox(4);
        Label lblTf = new Label("Projection Horizon:");
        lblTf.setStyle("-fx-text-fill: #94A3B8;");
        timeframeCombo.setMaxWidth(Double.MAX_VALUE);
        timeframeCombo.setOnAction(e -> runCalculation());
        timeBox.getChildren().addAll(lblTf, timeframeCombo);

        controlsCard.getChildren().addAll(cTitle, selBox, chkTakeOffline, sliderBox, timeBox);

        // Right Panel: Results
        VBox resultsCard = new VBox(14);
        resultsCard.getStyleClass().add("card");

        Label rTitle = new Label("PROJECTED FACTORY OUTPUT IMPACT");
        rTitle.getStyleClass().add("card-title");

        GridPane resGrid = new GridPane();
        resGrid.setHgap(16);
        resGrid.setVgap(12);

        ColumnConstraints rcol = new ColumnConstraints();
        rcol.setPercentWidth(50);
        resGrid.getColumnConstraints().addAll(rcol, rcol);

        resGrid.add(createResultMiniCard("BASELINE OUTPUT", lblBaselineOutput, "#94A3B8"), 0, 0);
        resGrid.add(createResultMiniCard("SCENARIO OUTPUT", lblScenarioOutput, "#38BDF8"), 1, 0);
        resGrid.add(createResultMiniCard("OUTPUT VARIANCE", lblDifference, "#F59E0B"), 0, 1);
        resGrid.add(createResultMiniCard("NET IMPACT (%)", lblPercentage, "#10B981"), 1, 1);

        resultsCard.getChildren().addAll(rTitle, resGrid);

        grid.add(controlsCard, 0, 0);
        grid.add(resultsCard, 1, 0);
        return grid;
    }

    private VBox createResultMiniCard(String title, Label valLabel, String colorHex) {
        VBox box = new VBox(4);
        box.setStyle("-fx-background-color: #0F172A; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-radius: 6px;");
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748B;");
        valLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");
        box.getChildren().addAll(t, valLabel);
        return box;
    }

    private VBox buildChartSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        Label title = new Label("COMPARATIVE CAPACITY PROJECTION");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Scenario Comparison");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Projected Factory Output (Units)");

        comparisonChart = new BarChart<>(xAxis, yAxis);
        comparisonChart.setAnimated(false);
        comparisonChart.setPrefHeight(260);

        baselineSeries.setName("Baseline Projection");
        scenarioSeries.setName("Simulated Scenario");

        comparisonChart.getData().addAll(baselineSeries, scenarioSeries);
        container.getChildren().addAll(title, comparisonChart);
        return container;
    }

    private HBox buildDisclaimerNotice() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: rgba(59, 130, 246, 0.1); -fx-border-color: #3B82F6; -fx-border-radius: 6px; -fx-padding: 12px; -fx-background-radius: 6px;");

        Label icon = new Label("ℹ");
        icon.setStyle("-fx-font-size: 18px; -fx-text-fill: #38BDF8; -fx-font-weight: bold;");

        Label text = new Label("Important Notice: What-if calculations are simulated models for strategic planning. " +
                "Outputs represent illustrative projections based on current equipment parameters and do not constitute actual mechanical performance guarantees.");
        text.setStyle("-fx-text-fill: #93C5FD; -fx-font-size: 12px;");
        text.setWrapText(true);

        box.getChildren().addAll(icon, text);
        return box;
    }

    public void refreshControls() {
        DigitalTwinMachine cur = machineCombo.getValue();
        machineCombo.getItems().clear();
        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            machineCombo.getItems().add(twin);
        }
        if (cur != null && machineCombo.getItems().contains(cur)) {
            machineCombo.setValue(cur);
        } else if (!machineCombo.getItems().isEmpty()) {
            machineCombo.setValue(machineCombo.getItems().getFirst());
        }

        if (timeframeCombo.getItems().isEmpty()) {
            timeframeCombo.getItems().addAll(
                    new TimeframeItem(8, "8-Hour Shift"),
                    new TimeframeItem(24, "24-Hour Production Day"),
                    new TimeframeItem(168, "7-Day Continuous Production")
            );
            timeframeCombo.setValue(timeframeCombo.getItems().getFirst());
        }
        runCalculation();
    }

    private void runCalculation() {
        DigitalTwinMachine twin = machineCombo.getValue();
        if (twin == null) return;

        boolean takeOffline = chkTakeOffline.isSelected();
        double rateChange = rateSlider.getValue();
        TimeframeItem tf = timeframeCombo.getValue();
        int hours = tf != null ? tf.hours() : 8;

        List<Machine> allMachines = new ArrayList<>();
        for (DigitalTwinMachine dt : digitalTwinService.getAllTwins()) {
            allMachines.add(dt.getMachine());
        }

        ProductionSimulationService.WhatIfResult result = whatIfService.calculateScenario(
                twin.getMachine(),
                takeOffline,
                rateChange,
                hours,
                allMachines
        );

        lblBaselineOutput.setText(String.format("%,.0f units", result.baselineOutput()));
        lblScenarioOutput.setText(String.format("%,.0f units", result.scenarioOutput()));

        String diffPrefix = result.outputDifference() >= 0 ? "+" : "";
        lblDifference.setText(String.format("%s%,.0f units", diffPrefix, result.outputDifference()));

        String pctPrefix = result.percentageImpact() >= 0 ? "+" : "";
        lblPercentage.setText(String.format("%s%.1f%%", pctPrefix, result.percentageImpact()));
        if (result.percentageImpact() < 0) {
            lblPercentage.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #EF4444;");
        } else if (result.percentageImpact() > 0) {
            lblPercentage.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #10B981;");
        } else {
            lblPercentage.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        }

        // Update bar chart
        baselineSeries.getData().clear();
        scenarioSeries.getData().clear();

        baselineSeries.getData().add(new XYChart.Data<>("Shift Window (" + hours + "h)", result.baselineOutput()));
        scenarioSeries.getData().add(new XYChart.Data<>("Shift Window (" + hours + "h)", result.scenarioOutput()));
    }
}
