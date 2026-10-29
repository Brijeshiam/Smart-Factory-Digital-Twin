package com.smartfactory.dao;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.exception.DatabaseException;
import com.smartfactory.model.Alert;
import com.smartfactory.model.AlertSeverity;
import com.smartfactory.model.AlertStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AlertDao {
    private static final Logger logger = LoggerFactory.getLogger(AlertDao.class);
    private final DatabaseConfig dbConfig;

    public AlertDao() {
        this(DatabaseConfig.getInstance());
    }

    public AlertDao(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public Alert save(Alert alert) {
        String sql = "INSERT INTO alerts (machine_id, alert_type, severity, description, created_at, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = alert.getCreatedAt() != null ? alert.getCreatedAt() : Instant.now();
            alert.setCreatedAt(now);

            stmt.setLong(1, alert.getMachineId());
            stmt.setString(2, alert.getAlertType());
            stmt.setString(3, alert.getSeverity().name());
            stmt.setString(4, alert.getDescription());
            stmt.setTimestamp(5, Timestamp.from(now));
            stmt.setString(6, alert.getStatus().name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    alert.setId(rs.getLong(1));
                }
            }
            return alert;
        } catch (SQLException e) {
            logger.error("Failed to insert alert: {}", e.getMessage());
            throw new DatabaseException("Failed to persist alert", e);
        }
    }

    public Optional<Alert> findActiveByMachineAndType(Long machineId, String alertType) {
        String sql = "SELECT a.id, a.machine_id, m.machine_code, a.alert_type, a.severity, a.description, " +
                     "a.created_at, a.acknowledged_at, a.resolved_at, a.status " +
                     "FROM alerts a " +
                     "LEFT JOIN machines m ON a.machine_id = m.id " +
                     "WHERE a.machine_id = ? AND a.alert_type = ? AND a.status IN ('ACTIVE', 'ACKNOWLEDGED') " +
                     "ORDER BY a.created_at DESC LIMIT 1";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, machineId);
            stmt.setString(2, alertType);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check active alert for machine {} type {}: {}", machineId, alertType, e.getMessage());
            throw new DatabaseException("Failed to query active alert", e);
        }
        return Optional.empty();
    }

    public List<Alert> findActiveAlerts() {
        List<Alert> alerts = new ArrayList<>();
        String sql = "SELECT a.id, a.machine_id, m.machine_code, a.alert_type, a.severity, a.description, " +
                     "a.created_at, a.acknowledged_at, a.resolved_at, a.status " +
                     "FROM alerts a " +
                     "LEFT JOIN machines m ON a.machine_id = m.id " +
                     "WHERE a.status IN ('ACTIVE', 'ACKNOWLEDGED') " +
                     "ORDER BY a.created_at DESC LIMIT 100";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                alerts.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query active and acknowledged alerts: {}", e.getMessage());
            throw new DatabaseException("Failed to query active alerts", e);
        }
        return alerts;
    }

    public Optional<Alert> findById(Long alertId) {
        String sql = "SELECT a.id, a.machine_id, m.machine_code, a.alert_type, a.severity, a.description, " +
                     "a.created_at, a.acknowledged_at, a.resolved_at, a.status " +
                     "FROM alerts a " +
                     "LEFT JOIN machines m ON a.machine_id = m.id " +
                     "WHERE a.id = ?";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, alertId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query alert by id {}: {}", alertId, e.getMessage());
        }
        return Optional.empty();
    }

    public boolean updateAlert(Alert alert) {
        String sql = "UPDATE alerts SET severity = ?, description = ?, status = ?, " +
                     "acknowledged_at = ?, resolved_at = ? WHERE id = ?";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, alert.getSeverity().name());
            stmt.setString(2, alert.getDescription());
            stmt.setString(3, alert.getStatus().name());
            stmt.setTimestamp(4, alert.getAcknowledgedAt() != null ? Timestamp.from(alert.getAcknowledgedAt()) : null);
            stmt.setTimestamp(5, alert.getResolvedAt() != null ? Timestamp.from(alert.getResolvedAt()) : null);
            stmt.setLong(6, alert.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to update alert {}: {}", alert.getId(), e.getMessage());
            return false;
        }
    }

    public List<Alert> findRecentAlerts(int limit) {
        return findFiltered(null, null, null, limit);
    }

    public List<Alert> findFiltered(Long machineId, AlertSeverity severity, AlertStatus status, int limit) {
        List<Alert> alerts = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.machine_id, m.machine_code, a.alert_type, a.severity, a.description, " +
                "a.created_at, a.acknowledged_at, a.resolved_at, a.status " +
                "FROM alerts a " +
                "LEFT JOIN machines m ON a.machine_id = m.id WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (machineId != null) {
            sql.append("AND a.machine_id = ? ");
            params.add(machineId);
        }
        if (severity != null) {
            sql.append("AND a.severity = ? ");
            params.add(severity.name());
        }
        if (status != null) {
            sql.append("AND a.status = ? ");
            params.add(status.name());
        }

        sql.append("ORDER BY a.created_at DESC LIMIT ?");
        params.add(limit <= 0 ? 100 : limit);

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    alerts.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query alerts: {}", e.getMessage());
            throw new DatabaseException("Failed to query alerts", e);
        }
        return alerts;
    }

    public boolean acknowledgeAlert(Long alertId) {
        String sql = "UPDATE alerts SET acknowledged_at = ?, status = 'ACKNOWLEDGED' " +
                     "WHERE id = ? AND status = 'ACTIVE'";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setLong(2, alertId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to acknowledge alert {}: {}", alertId, e.getMessage());
            throw new DatabaseException("Failed to acknowledge alert", e);
        }
    }

    public boolean resolveAlert(Long alertId) {
        String sql = "UPDATE alerts SET resolved_at = ?, status = 'RESOLVED' " +
                     "WHERE id = ? AND status != 'RESOLVED'";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setLong(2, alertId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to resolve alert {}: {}", alertId, e.getMessage());
            throw new DatabaseException("Failed to resolve alert", e);
        }
    }

    public int resolveActiveAlertsForMachineAndType(Long machineId, String alertType) {
        String sql = "UPDATE alerts SET resolved_at = ?, status = 'RESOLVED' " +
                     "WHERE machine_id = ? AND alert_type = ? AND status != 'RESOLVED'";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setLong(2, machineId);
            stmt.setString(3, alertType);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to resolve active alerts for machine {} type {}: {}", machineId, alertType, e.getMessage());
            throw new DatabaseException("Failed to resolve active alerts", e);
        }
    }

    public long countActiveAlerts() {
        String sql = "SELECT COUNT(*) FROM alerts WHERE status IN ('ACTIVE', 'ACKNOWLEDGED')";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count active alerts: {}", e.getMessage());
            throw new DatabaseException("Failed to count active alerts", e);
        }
    }

    private Alert mapResultSet(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp ackTs = rs.getTimestamp("acknowledged_at");
        Timestamp resTs = rs.getTimestamp("resolved_at");

        Alert alert = new Alert(
                rs.getLong("id"),
                rs.getLong("machine_id"),
                rs.getString("alert_type"),
                AlertSeverity.fromString(rs.getString("severity")),
                rs.getString("description"),
                createdTs != null ? createdTs.toInstant() : Instant.now(),
                ackTs != null ? ackTs.toInstant() : null,
                resTs != null ? resTs.toInstant() : null,
                AlertStatus.fromString(rs.getString("status"))
        );
        try {
            alert.setMachineCode(rs.getString("machine_code"));
        } catch (Exception ignored) {}
        return alert;
    }
}
