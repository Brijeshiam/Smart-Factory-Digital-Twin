package com.smartfactory.ui;

import com.smartfactory.model.Alert;
import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.AlertStatus;
import com.smartfactory.model.Machine;
import com.smartfactory.service.DigitalTwinService;
import com.smartfactory.util.DateTimeUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class AlertsView extends ScrollPane {

    private final DigitalTwinService digitalTwinService;

    private final ComboBox<MachineFilterItem> machineFilter = new ComboBox<>();
    private final ComboBox<SeverityFilterItem> severityFilter = new ComboBox<>();
    private final ComboBox<StatusFilterItem> statusFilter = new ComboBox<>();
    private TableView<Alert> alertsTable;
    private final Label lblActiveCount = new Label("0 Active");

    public record MachineFilterItem(Long id, String label) {
        @Override
        public String toString() { return label; }
    }
    public record SeverityFilterItem(AlertSeverity severity, String label) {
        @Override
        public String toString() { return label; }
    }
    public record StatusFilterItem(AlertStatus status, String label) {
        @Override
        public String toString() { return label; }
    }

    public AlertsView(DigitalTwinService digitalTwinService) {
        this.digitalTwinService = digitalTwinService;

        setFitToWidth(true);
        getStyleClass().add("scroll-pane");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        content.getChildren().addAll(
                buildHeader(),
                buildFilterBar(),
                buildAlertsTableSection()
        );

        setContent(content);

        // Preload filters & table
        initFilters();
        refreshAlerts();

        // Listen for new/updated alerts
        digitalTwinService.getAlertLifecycleManager().addAlertListener(alert -> Platform.runLater(this::refreshAlerts));
    }

    private HBox buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label title = new Label("ALERTS & INCIDENT MANAGEMENT");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Threshold violation history, operator acknowledgement, and condition monitoring");
        subtitle.getStyleClass().add("page-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblActiveCount.getStyleClass().add("badge-critical");
        lblActiveCount.setStyle("-fx-font-size: 13px; -fx-padding: 6 14 6 14;");

        header.getChildren().addAll(titleBox, spacer, lblActiveCount);
        return header;
    }

    private HBox buildFilterBar() {
        HBox filterBar = new HBox(12);
        filterBar.getStyleClass().add("card");
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label lblMachine = new Label("Machine:");
        lblMachine.setStyle("-fx-text-fill: #94A3B8;");
        machineFilter.setPrefWidth(180);
        machineFilter.setOnAction(e -> refreshAlerts());

        Label lblSev = new Label("Severity:");
        lblSev.setStyle("-fx-text-fill: #94A3B8;");
        severityFilter.setPrefWidth(140);
        severityFilter.setOnAction(e -> refreshAlerts());

        Label lblStat = new Label("Status:");
        lblStat.setStyle("-fx-text-fill: #94A3B8;");
        statusFilter.setPrefWidth(160);
        statusFilter.setOnAction(e -> refreshAlerts());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnRefresh = new Button("Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> refreshAlerts());

        filterBar.getChildren().addAll(lblMachine, machineFilter, lblSev, severityFilter, lblStat, statusFilter, spacer, btnRefresh);
        return filterBar;
    }

    public void initFilters() {
        MachineFilterItem cur = machineFilter.getValue();
        machineFilter.getItems().clear();
        machineFilter.getItems().add(new MachineFilterItem(null, "All Machines"));
        for (var twin : digitalTwinService.getAllTwins()) {
            machineFilter.getItems().add(new MachineFilterItem(twin.getId(), twin.getMachineCode() + " - " + twin.getMachineName()));
        }
        if (cur != null) {
            machineFilter.setValue(cur);
        } else {
            machineFilter.setValue(machineFilter.getItems().getFirst());
        }

        severityFilter.getItems().clear();
        severityFilter.getItems().addAll(
                new SeverityFilterItem(null, "All Severities"),
                new SeverityFilterItem(AlertSeverity.WARNING, "Warning Only"),
                new SeverityFilterItem(AlertSeverity.CRITICAL, "Critical Only")
        );
        severityFilter.setValue(severityFilter.getItems().getFirst());

        statusFilter.getItems().clear();
        statusFilter.getItems().addAll(
                new StatusFilterItem(null, "All Statuses"),
                new StatusFilterItem(AlertStatus.ACTIVE, "Active Only"),
                new StatusFilterItem(AlertStatus.ACKNOWLEDGED, "Acknowledged Only"),
                new StatusFilterItem(AlertStatus.RESOLVED, "Resolved Only")
        );
        statusFilter.setValue(statusFilter.getItems().getFirst());
    }

    private VBox buildAlertsTableSection() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        alertsTable = new TableView<>();
        alertsTable.setPrefHeight(450);
        alertsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Alert, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(d -> new javafx.beans.property.SimpleLongProperty(d.getValue().getId()));
        colId.setMaxWidth(60);

        TableColumn<Alert, String> colMachine = new TableColumn<>("Machine");
        colMachine.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getMachineCode() != null ? d.getValue().getMachineCode() : "ID:" + d.getValue().getMachineId()
        ));
        colMachine.setMaxWidth(110);

        TableColumn<Alert, String> colSev = new TableColumn<>("Severity");
        colSev.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getSeverity().name()));
        colSev.setMaxWidth(110);
        colSev.setCellFactory(column -> new TableCell<>() {
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

        TableColumn<Alert, String> colType = new TableColumn<>("Alert Type");
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getAlertType()));
        colType.setMaxWidth(160);

        TableColumn<Alert, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));

        TableColumn<Alert, String> colCreated = new TableColumn<>("Created At");
        colCreated.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                DateTimeUtil.formatFull(d.getValue().getCreatedAt())
        ));
        colCreated.setMaxWidth(150);

        TableColumn<Alert, String> colAck = new TableColumn<>("Acknowledged At");
        colAck.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getAcknowledgedAt() != null ? DateTimeUtil.formatFull(d.getValue().getAcknowledgedAt()) : "--"
        ));
        colAck.setMaxWidth(150);

        TableColumn<Alert, String> colRes = new TableColumn<>("Resolved At");
        colRes.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getResolvedAt() != null ? DateTimeUtil.formatFull(d.getValue().getResolvedAt()) : "--"
        ));
        colRes.setMaxWidth(150);

        TableColumn<Alert, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getStatus().name()));
        colStatus.setMaxWidth(120);

        TableColumn<Alert, Void> colActions = new TableColumn<>("Operator Actions");
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnAck = new Button("Acknowledge");
            private final Button btnResolve = new Button("Resolve");
            private final HBox btnBox = new HBox(6, btnAck, btnResolve);

            {
                btnAck.getStyleClass().add("btn-primary");
                btnAck.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                btnAck.setOnAction(e -> {
                    Alert a = getTableView().getItems().get(getIndex());
                    if (a != null) {
                        digitalTwinService.getAlertLifecycleManager().acknowledgeAlert(a.getId());
                        refreshAlerts();
                    }
                });

                btnResolve.getStyleClass().add("btn-secondary");
                btnResolve.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                btnResolve.setOnAction(e -> {
                    Alert a = getTableView().getItems().get(getIndex());
                    if (a != null) {
                        digitalTwinService.getAlertLifecycleManager().manuallyResolveAlert(a.getId());
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
                    Alert a = getTableView().getItems().get(getIndex());
                    if (a == null || a.getStatus() == AlertStatus.RESOLVED) {
                        setGraphic(null);
                    } else if (a.getStatus() == AlertStatus.ACKNOWLEDGED) {
                        btnBox.getChildren().setAll(btnResolve);
                        setGraphic(btnBox);
                    } else {
                        btnBox.getChildren().setAll(btnAck, btnResolve);
                        setGraphic(btnBox);
                    }
                }
            }
        });

        alertsTable.getColumns().addAll(colId, colMachine, colSev, colType, colDesc, colCreated, colAck, colRes, colStatus, colActions);
        container.getChildren().add(alertsTable);
        return container;
    }

    public void refreshAlerts() {
        new Thread(() -> {
            try {
                Long mId = machineFilter.getValue() != null ? machineFilter.getValue().id() : null;
                AlertSeverity sev = severityFilter.getValue() != null ? severityFilter.getValue().severity() : null;
                AlertStatus stat = statusFilter.getValue() != null ? statusFilter.getValue().status() : null;

                List<Alert> alerts = digitalTwinService.getAlertDao().findFiltered(mId, sev, stat, 200);
                long activeCount = digitalTwinService.getAlertDao().countActiveAlerts();

                Platform.runLater(() -> {
                    alertsTable.getItems().setAll(alerts);
                    lblActiveCount.setText(activeCount + " Active Alerts");
                });
            } catch (Exception ignored) {}
        }).start();
    }
}
