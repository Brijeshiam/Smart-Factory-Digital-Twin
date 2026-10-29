package com.smartfactory.ui;

import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import com.smartfactory.service.DigitalTwinMachine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.util.DateTimeUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.*;

/**
 * PerformanceReportView – Machine performance analytics dashboard.
 *
 * Contains five rich visualisation panels:
 *  1. KPI summary strip (health avg, total production, uptime %)
 *  2. Bar chart  – Health Score per machine (colour-coded Normal/Warning/Critical)
 *  3. Bar chart  – Production Count per machine
 *  4. Pie chart  – Machine state distribution
 *  5. Area chart – Health score trend (sliding 40-point window, all machines)
 *  6. Line chart – Live multi-sensor comparison (Temp, Vib, RPM via tab pane)
 */
public class PerformanceReportView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;

    // KPI labels
    private final Label lblAvgHealth    = new Label("--");
    private final Label lblTotalProd    = new Label("--");
    private final Label lblNormalPct    = new Label("--");
    private final Label lblAvgTemp      = new Label("--");
    private final Label lblAvgVib       = new Label("--");
    private final Label lblAvgRpm       = new Label("--");

    // --- Chart 1: Health Score Bar Chart ---
    private BarChart<String, Number> healthBarChart;
    private final XYChart.Series<String, Number> healthSeries = new XYChart.Series<>();

    // --- Chart 2: Production Bar Chart ---
    private BarChart<String, Number> productionBarChart;
    private final XYChart.Series<String, Number> productionSeries = new XYChart.Series<>();

    // --- Chart 3: Pie Chart - State Distribution ---
    private PieChart stateDistPieChart;

    // --- Chart 4: Health Trend Area Chart (all machines) ---
    private AreaChart<String, Number> healthTrendChart;
    private final Map<Long, XYChart.Series<String, Number>> healthTrendSeriesMap = new LinkedHashMap<>();

    // --- Chart 5: Multi-Sensor Line Charts (Temp/Vib/RPM) in TabPane ---
    private LineChart<String, Number> liveCompTempChart;
    private LineChart<String, Number> liveCompVibChart;
    private LineChart<String, Number> liveCompRpmChart;
    private final Map<Long, XYChart.Series<String, Number>> compTempMap  = new LinkedHashMap<>();
    private final Map<Long, XYChart.Series<String, Number>> compVibMap   = new LinkedHashMap<>();
    private final Map<Long, XYChart.Series<String, Number>> compRpmMap   = new LinkedHashMap<>();

    private static final int TREND_WINDOW = 40;

    public PerformanceReportView(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(22);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildTopBar(),
                buildKpiStrip(),
                buildRow(buildHealthBarPanel(),    buildProductionBarPanel()),
                buildRow(buildPieChartPanel(),     buildHealthTrendPanel()),
                buildLiveSensorCompPanel()
        );

        setContent(content);

        // Initialise series for all machines on first load
        initSeriesForAllMachines();

        // Subscribe to live twin updates
        digitalTwinService.addTwinUpdateListener(twin ->
                Platform.runLater(() -> onTwinUpdated(twin)));

        // Render initial static snapshot
        refreshSnapshot();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Layout builders
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label title = new Label("MACHINE PERFORMANCE REPORT");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label(
                "Multi-dimensional analytics: health scores, production throughput, state distribution & live sensor trends");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnRefresh = new Button("⟳  Refresh Snapshot");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> refreshSnapshot());

        bar.getChildren().addAll(titleBox, spacer, btnRefresh);
        return bar;
    }

    private HBox buildKpiStrip() {
        HBox strip = new HBox(16);

        strip.getChildren().addAll(
                kpiCard("AVG HEALTH INDEX",   lblAvgHealth,  "#38BDF8", "Score 0–100"),
                kpiCard("TOTAL PRODUCTION",   lblTotalProd,  "#10B981", "Cumulative units"),
                kpiCard("MACHINES NORMAL",    lblNormalPct,  "#A3E635", "% of fleet in normal state"),
                kpiCard("AVG TEMPERATURE",    lblAvgTemp,    "#F59E0B", "Degrees Celsius"),
                kpiCard("AVG VIBRATION",      lblAvgVib,     "#FB923C", "mm / s"),
                kpiCard("AVG MOTOR SPEED",    lblAvgRpm,     "#C084FC", "RPM")
        );

        for (javafx.scene.Node node : strip.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return strip;
    }

    private VBox kpiCard(String title, Label valLabel, String accentHex, String sub) {
        VBox card = new VBox(5);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);

        // Coloured accent dot
        Circle dot = new Circle(5, Color.web(accentHex));

        HBox header = new HBox(8, dot, makeLabel(title, "card-title"));
        header.setAlignment(Pos.CENTER_LEFT);

        valLabel.setStyle(
                "-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + accentHex + ";");

        Label subLbl = new Label(sub);
        subLbl.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(header, valLabel, subLbl);
        return card;
    }

    private HBox buildRow(VBox left, VBox right) {
        HBox row = new HBox(16);
        HBox.setHgrow(left,  Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(left, right);
        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chart panel builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Chart 1 – Bar chart: Health Score per machine */
    private VBox buildHealthBarPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");

        Label title = sectionTitle("HEALTH SCORE BY MACHINE");
        Label sub   = new Label("Real-time proximity score (0 = critical, 100 = optimal)");
        sub.getStyleClass().add("card-subtitle");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Machine");
        xAxis.setAnimated(false);

        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Health Score");
        yAxis.setAnimated(false);

        healthBarChart = new BarChart<>(xAxis, yAxis);
        healthBarChart.setAnimated(false);
        healthBarChart.setLegendVisible(false);
        healthBarChart.setPrefHeight(270);
        healthBarChart.setBarGap(4);
        healthBarChart.setCategoryGap(20);

        healthSeries.setName("Health Score");
        healthBarChart.getData().add(healthSeries);

        panel.getChildren().addAll(title, sub, healthBarChart);
        return panel;
    }

    /** Chart 2 – Bar chart: Production Count per machine */
    private VBox buildProductionBarPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");

        Label title = sectionTitle("PRODUCTION COUNT BY MACHINE");
        Label sub   = new Label("Cumulative parts produced since simulation start");
        sub.getStyleClass().add("card-subtitle");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Machine");
        xAxis.setAnimated(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Units Produced");
        yAxis.setAnimated(false);

        productionBarChart = new BarChart<>(xAxis, yAxis);
        productionBarChart.setAnimated(false);
        productionBarChart.setLegendVisible(false);
        productionBarChart.setPrefHeight(270);
        productionBarChart.setBarGap(4);
        productionBarChart.setCategoryGap(20);

        productionSeries.setName("Production");
        productionBarChart.getData().add(productionSeries);

        panel.getChildren().addAll(title, sub, productionBarChart);
        return panel;
    }

    /** Chart 3 – Pie chart: state distribution */
    private VBox buildPieChartPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");

        Label title = sectionTitle("MACHINE STATE DISTRIBUTION");
        Label sub   = new Label("Live proportion of Normal / Warning / Critical / Offline machines");
        sub.getStyleClass().add("card-subtitle");

        stateDistPieChart = new PieChart();
        stateDistPieChart.setLabelsVisible(true);
        stateDistPieChart.setAnimated(true);
        stateDistPieChart.setLegendVisible(true);
        stateDistPieChart.setPrefHeight(280);
        stateDistPieChart.setStyle("-fx-background-color: transparent;");

        panel.getChildren().addAll(title, sub, stateDistPieChart);
        return panel;
    }

    /** Chart 4 – Area chart: Health score trend */
    private VBox buildHealthTrendPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");

        Label title = sectionTitle("HEALTH SCORE TREND (LIVE)");
        Label sub   = new Label("Sliding window of last " + TREND_WINDOW + " readings per machine");
        sub.getStyleClass().add("card-subtitle");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        xAxis.setAnimated(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);

        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        yAxis.setLabel("Health Score");
        yAxis.setAnimated(false);

        healthTrendChart = new AreaChart<>(xAxis, yAxis);
        healthTrendChart.setAnimated(false);
        healthTrendChart.setCreateSymbols(false);
        healthTrendChart.setPrefHeight(280);

        panel.getChildren().addAll(title, sub, healthTrendChart);
        return panel;
    }

    /** Chart 5 – Live multi-sensor comparison (Temp / Vib / RPM tabs) */
    private VBox buildLiveSensorCompPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("card");

        Label title = sectionTitle("LIVE SENSOR COMPARISON – ALL MACHINES");
        Label sub   = new Label(
                "Real-time per-machine overlay: Temperature, Vibration and Motor Speed trends");
        sub.getStyleClass().add("card-subtitle");

        liveCompTempChart = createCompLineChart("Temperature (°C)");
        liveCompVibChart  = createCompLineChart("Vibration (mm/s)");
        liveCompRpmChart  = createCompLineChart("Motor Speed (RPM)");

        Tab tabTemp = closedTab("🌡 Temperature",  liveCompTempChart);
        Tab tabVib  = closedTab("📳 Vibration",    liveCompVibChart);
        Tab tabRpm  = closedTab("⚙ Motor RPM",     liveCompRpmChart);

        TabPane tabPane = new TabPane(tabTemp, tabVib, tabRpm);
        tabPane.setStyle("-fx-background-color: transparent;");

        panel.getChildren().addAll(title, sub, tabPane);
        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Series initialisation
    // ─────────────────────────────────────────────────────────────────────────

    private void initSeriesForAllMachines() {
        for (DigitalTwinMachine twin : digitalTwinService.getAllTwins()) {
            long id = twin.getId();
            String code = twin.getMachineCode();

            // Health trend
            XYChart.Series<String, Number> hSeries = new XYChart.Series<>();
            hSeries.setName(code);
            healthTrendSeriesMap.put(id, hSeries);
            healthTrendChart.getData().add(hSeries);

            // Sensor comparison – Temp
            XYChart.Series<String, Number> tSeries = new XYChart.Series<>();
            tSeries.setName(code);
            compTempMap.put(id, tSeries);
            liveCompTempChart.getData().add(tSeries);

            // Sensor comparison – Vib
            XYChart.Series<String, Number> vSeries = new XYChart.Series<>();
            vSeries.setName(code);
            compVibMap.put(id, vSeries);
            liveCompVibChart.getData().add(vSeries);

            // Sensor comparison – RPM
            XYChart.Series<String, Number> rSeries = new XYChart.Series<>();
            rSeries.setName(code);
            compRpmMap.put(id, rSeries);
            liveCompRpmChart.getData().add(rSeries);

            // Backfill from recent history
            for (SensorReading r : twin.getRecentHistory()) {
                String t = DateTimeUtil.formatTimeOnly(r.getRecordedAt());
                tSeries.getData().add(new XYChart.Data<>(t, r.getTemperatureC()));
                vSeries.getData().add(new XYChart.Data<>(t, r.getVibrationMmS()));
                rSeries.getData().add(new XYChart.Data<>(t, r.getMotorRpm()));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data refresh helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full snapshot refresh – updates all static/aggregate charts.
     * Called on explicit Refresh button press and on every twin update.
     */
    public void refreshSnapshot() {
        Collection<DigitalTwinMachine> twins = digitalTwinService.getAllTwins();

        // ── KPI calculations ─────────────────────────────────────────────────
        int total = 0, normalCount = 0;
        long totalProd = 0;
        double healthSum = 0, tempSum = 0, vibSum = 0, rpmSum = 0;
        int sensorCount = 0;

        for (DigitalTwinMachine twin : twins) {
            total++;
            healthSum += twin.getHealthScore();
            totalProd += twin.getProductionCount();
            if (twin.getCurrentState() == MachineState.NORMAL) normalCount++;

            SensorReading r = twin.getLatestReading();
            if (r != null) {
                tempSum += r.getTemperatureC();
                vibSum  += r.getVibrationMmS();
                rpmSum  += r.getMotorRpm();
                sensorCount++;
            }
        }

        if (total > 0) {
            lblAvgHealth.setText(String.format("%.1f", healthSum / total));
            lblNormalPct.setText(String.format("%.0f%%", (normalCount * 100.0) / total));
        }
        lblTotalProd.setText(String.format("%,d", totalProd));

        if (sensorCount > 0) {
            lblAvgTemp.setText(String.format("%.1f °C",  tempSum / sensorCount));
            lblAvgVib.setText( String.format("%.2f",     vibSum  / sensorCount));
            lblAvgRpm.setText( String.format("%.0f",     rpmSum  / sensorCount));
        }

        // ── Health bar chart ─────────────────────────────────────────────────
        healthSeries.getData().clear();
        for (DigitalTwinMachine twin : twins) {
            XYChart.Data<String, Number> bar =
                    new XYChart.Data<>(twin.getMachineCode(), twin.getHealthScore());
            healthSeries.getData().add(bar);
        }
        // Colour bars after they are rendered
        Platform.runLater(() -> colourHealthBars(twins));

        // ── Production bar chart ─────────────────────────────────────────────
        productionSeries.getData().clear();
        for (DigitalTwinMachine twin : twins) {
            productionSeries.getData().add(
                    new XYChart.Data<>(twin.getMachineCode(), twin.getProductionCount()));
        }
        // Colour production bars
        Platform.runLater(() -> colourProductionBars());

        // ── Pie chart – state distribution ───────────────────────────────────
        int normalC = 0, warningC = 0, criticalC = 0, offlineC = 0;
        for (DigitalTwinMachine twin : twins) {
            switch (twin.getCurrentState()) {
                case NORMAL   -> normalC++;
                case WARNING  -> warningC++;
                case CRITICAL -> criticalC++;
                case OFFLINE  -> offlineC++;
            }
        }
        stateDistPieChart.getData().clear();
        if (normalC   > 0) stateDistPieChart.getData().add(new PieChart.Data("Normal ("   + normalC   + ")", normalC));
        if (warningC  > 0) stateDistPieChart.getData().add(new PieChart.Data("Warning ("  + warningC  + ")", warningC));
        if (criticalC > 0) stateDistPieChart.getData().add(new PieChart.Data("Critical (" + criticalC + ")", criticalC));
        if (offlineC  > 0) stateDistPieChart.getData().add(new PieChart.Data("Offline ("  + offlineC  + ")", offlineC));

        // Apply slice colours after render
        Platform.runLater(this::colourPieSlices);
    }

    /** Called on every live twin update – appends to trend / comparison charts */
    private void onTwinUpdated(DigitalTwinMachine twin) {
        SensorReading reading = twin.getLatestReading();
        if (reading == null) return;

        String timeStr = DateTimeUtil.formatTimeOnly(reading.getRecordedAt());
        long id = twin.getId();

        // ── Health trend area chart ───────────────────────────────────────────
        XYChart.Series<String, Number> hSeries =
                healthTrendSeriesMap.computeIfAbsent(id, k -> {
                    XYChart.Series<String, Number> s = new XYChart.Series<>();
                    s.setName(twin.getMachineCode());
                    healthTrendChart.getData().add(s);
                    return s;
                });
        hSeries.getData().add(new XYChart.Data<>(timeStr, twin.getHealthScore()));
        if (hSeries.getData().size() > TREND_WINDOW) hSeries.getData().remove(0);

        // ── Live sensor comparison charts ─────────────────────────────────────
        appendCompPoint(compTempMap, liveCompTempChart, twin, reading.getTemperatureC(), timeStr);
        appendCompPoint(compVibMap,  liveCompVibChart,  twin, reading.getVibrationMmS(), timeStr);
        appendCompPoint(compRpmMap,  liveCompRpmChart,  twin, reading.getMotorRpm(),     timeStr);

        // ── Refresh snapshot-based charts (KPIs, bars, pie) periodically ─────
        refreshSnapshot();
    }

    private void appendCompPoint(Map<Long, XYChart.Series<String, Number>> map,
                                 LineChart<String, Number> chart,
                                 DigitalTwinMachine twin, double value, String time) {
        XYChart.Series<String, Number> series = map.computeIfAbsent(twin.getId(), id -> {
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(twin.getMachineCode());
            chart.getData().add(s);
            return s;
        });
        series.getData().add(new XYChart.Data<>(time, value));
        if (series.getData().size() > TREND_WINDOW) series.getData().remove(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Post-render colouring (JavaFX CSS overrides via setStyle on nodes)
    // ─────────────────────────────────────────────────────────────────────────

    private void colourHealthBars(Collection<DigitalTwinMachine> twins) {
        // Map machine code → state for bar colouring
        Map<String, MachineState> stateMap = new HashMap<>();
        for (DigitalTwinMachine twin : twins) {
            stateMap.put(twin.getMachineCode(), twin.getCurrentState());
        }

        for (XYChart.Data<String, Number> data : healthSeries.getData()) {
            if (data.getNode() == null) continue;
            MachineState state = stateMap.getOrDefault(data.getXValue(), MachineState.NORMAL);
            String color = switch (state) {
                case NORMAL   -> "rgba(16, 185, 129, 0.85)";   // emerald
                case WARNING  -> "rgba(245, 158, 11, 0.85)";   // amber
                case CRITICAL -> "rgba(239, 68, 68, 0.85)";    // red
                case OFFLINE  -> "rgba(100, 116, 139, 0.85)";  // slate
            };
            data.getNode().setStyle(
                    "-fx-bar-fill: " + color + "; -fx-background-radius: 4px;");
        }
    }

    private void colourProductionBars() {
        // Cycle through a set of vibrant palette colours
        String[] palette = {
                "rgba(56, 189, 248, 0.85)",   // sky
                "rgba(163, 230, 53, 0.85)",   // lime
                "rgba(192, 132, 252, 0.85)",  // violet
                "rgba(251, 146, 60, 0.85)",   // orange
                "rgba(52, 211, 153, 0.85)",   // emerald tint
                "rgba(250, 204, 21, 0.85)",   // yellow
        };
        int i = 0;
        for (XYChart.Data<String, Number> data : productionSeries.getData()) {
            if (data.getNode() == null) continue;
            String color = palette[i % palette.length];
            data.getNode().setStyle(
                    "-fx-bar-fill: " + color + "; -fx-background-radius: 4px;");
            i++;
        }
    }

    private void colourPieSlices() {
        for (PieChart.Data slice : stateDistPieChart.getData()) {
            String label = slice.getName();
            String color;
            if (label.startsWith("Normal"))   color = "#10B981";
            else if (label.startsWith("Warn")) color = "#F59E0B";
            else if (label.startsWith("Crit")) color = "#EF4444";
            else                               color = "#64748B";

            if (slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Small utility helpers
    // ─────────────────────────────────────────────────────────────────────────

    private LineChart<String, Number> createCompLineChart(String yLabel) {
        CategoryAxis x = new CategoryAxis();
        x.setLabel("Time");
        x.setAnimated(false);
        x.setTickLabelsVisible(false);
        x.setTickMarkVisible(false);

        NumberAxis y = new NumberAxis();
        y.setLabel(yLabel);
        y.setAnimated(false);

        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setPrefHeight(300);
        return chart;
    }

    private Tab closedTab(String text, javafx.scene.Node content) {
        Tab tab = new Tab(text, content);
        tab.setClosable(false);
        return tab;
    }

    private Label sectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #94A3B8; -fx-letter-spacing: 0.5px;");
        return lbl;
    }

    private Label makeLabel(String text, String styleClass) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add(styleClass);
        return lbl;
    }
}
